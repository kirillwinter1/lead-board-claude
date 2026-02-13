package com.leadboard.status;

import com.leadboard.config.repository.*;
import com.leadboard.config.service.WorkflowConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatusMappingServiceTest {

    @Mock
    private ProjectConfigurationRepository configRepo;
    @Mock
    private WorkflowRoleRepository roleRepo;
    @Mock
    private IssueTypeMappingRepository issueTypeRepo;
    @Mock
    private StatusMappingRepository statusMappingRepo;
    @Mock
    private LinkTypeMappingRepository linkTypeRepo;

    private StatusMappingService service;

    @BeforeEach
    void setUp() {
        // Return empty results from all repos so loadConfiguration() would find nothing
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.empty());

        // Create a REAL WorkflowConfigService with mocked repos.
        // Do NOT call init() — caches stay empty, so fallback substring matching is used.
        WorkflowConfigService workflowConfigService = new WorkflowConfigService(
                configRepo, roleRepo, issueTypeRepo, statusMappingRepo, linkTypeRepo, new ObjectMapper()
        );

        service = new StatusMappingService(workflowConfigService);
    }

    // ==================== Status Category Tests ====================

    @Nested
    class CategorizeTests {

        @Test
        void categorizeEpicDone() {
            assertEquals(StatusCategory.DONE, service.categorizeEpic("Done", null));
            assertEquals(StatusCategory.DONE, service.categorizeEpic("Closed", null));
            assertEquals(StatusCategory.DONE, service.categorizeEpic("Готово", null));
        }

        @Test
        void categorizeEpicInProgress() {
            assertEquals(StatusCategory.IN_PROGRESS, service.categorizeEpic("Developing", null));
            assertEquals(StatusCategory.IN_PROGRESS, service.categorizeEpic("В разработке", null));
            assertEquals(StatusCategory.IN_PROGRESS, service.categorizeEpic("E2E Testing", null));
        }

        @Test
        void categorizeEpicTodo() {
            assertEquals(StatusCategory.TODO, service.categorizeEpic("Backlog", null));
            assertEquals(StatusCategory.TODO, service.categorizeEpic("To Do", null));
            assertEquals(StatusCategory.TODO, service.categorizeEpic("Бэклог", null));
            assertEquals(StatusCategory.TODO, service.categorizeEpic("New", null));
        }

        @Test
        void categorizeStoryDone() {
            assertEquals(StatusCategory.DONE, service.categorizeStory("Done", null));
            assertEquals(StatusCategory.DONE, service.categorizeStory("Готово", null));
        }

        @Test
        void categorizeStoryInProgress() {
            assertEquals(StatusCategory.IN_PROGRESS, service.categorizeStory("Development", null));
            assertEquals(StatusCategory.IN_PROGRESS, service.categorizeStory("Testing", null));
            assertEquals(StatusCategory.IN_PROGRESS, service.categorizeStory("Разработка", null));
        }

        @Test
        void categorizeStoryTodo() {
            assertEquals(StatusCategory.TODO, service.categorizeStory("New", null));
            assertEquals(StatusCategory.TODO, service.categorizeStory("Ready", null));
            assertEquals(StatusCategory.TODO, service.categorizeStory("Новый", null));
        }

        @Test
        void categorizeByIssueTypeEpic() {
            assertEquals(StatusCategory.TODO, service.categorize("Backlog", "Epic", null));
            assertEquals(StatusCategory.DONE, service.categorize("Done", "Эпик", null));
        }

        @Test
        void categorizeByIssueTypeSubtask() {
            assertEquals(StatusCategory.IN_PROGRESS, service.categorize("In Progress", "Sub-task", null));
            assertEquals(StatusCategory.DONE, service.categorize("Done", "Подзадача", null));
        }

        @Test
        void categorizeCaseInsensitive() {
            assertEquals(StatusCategory.DONE, service.categorizeEpic("DONE", null));
            assertEquals(StatusCategory.DONE, service.categorizeEpic("done", null));
            assertEquals(StatusCategory.DONE, service.categorizeEpic("Done", null));
        }

        @Test
        void categorizeUnknownStatusFallsBackToSubstring() {
            // Unknown status containing "progress" should be IN_PROGRESS
            assertEquals(StatusCategory.IN_PROGRESS, service.categorizeStory("Unknown Progress Status", null));
            // Unknown status containing "done" should be DONE
            assertEquals(StatusCategory.DONE, service.categorizeStory("Almost Done Maybe", null));
        }

        @Test
        void categorizeNullStatusReturnsTodo() {
            assertEquals(StatusCategory.TODO, service.categorizeEpic(null, null));
        }
    }

    // ==================== Phase Detection Tests ====================

    @Nested
    class PhaseTests {

        @Test
        void determinePhaseByStatus() {
            assertEquals("SA", service.determinePhase("Analysis", null, null));
            assertEquals("SA", service.determinePhase("Анализ", null, null));
            assertEquals("DEV", service.determinePhase("Development", null, null));
            assertEquals("DEV", service.determinePhase("Разработка", null, null));
            assertEquals("QA", service.determinePhase("Testing", null, null));
            assertEquals("QA", service.determinePhase("Тестирование", null, null));
        }

        @Test
        void determinePhaseByIssueType() {
            // SA issue types
            assertEquals("SA", service.determinePhase("In Progress", "Аналитика", null));

            // QA issue types
            assertEquals("QA", service.determinePhase("In Progress", "Testing", null));
            assertEquals("QA", service.determinePhase("New", "Bug", null));
            assertEquals("QA", service.determinePhase("New", "Тестирование", null));
        }

        @Test
        void determinePhaseStatusSubstringTakesPrecedenceOverIssueTypeSubstring() {
            // WCS checks status substring before issueType substring.
            // Status "Testing" contains "test" -> QA, even though issueType "Аналитика" would suggest SA
            assertEquals("QA", service.determinePhase("Testing", "Аналитика", null));
            // Status "Development" does not match any status substring, so issueType is checked
            // IssueType "Testing" contains "test" -> QA
            assertEquals("QA", service.determinePhase("Development", "Testing", null));
        }

        @Test
        void determinePhaseDefaultsToDev() {
            assertEquals("DEV", service.determinePhase("Unknown Status", "Task", null));
            assertEquals("DEV", service.determinePhase("New", "Story", null));
        }

        @Test
        void determinePhaseNullStatusDefaultsToDev() {
            assertEquals("DEV", service.determinePhase(null, "Story", null));
        }

        @Test
        void determinePhaseFallbackSubstring() {
            // Even custom statuses with keywords should work
            assertEquals("SA", service.determinePhase("Custom Analysis Review", null, null));
            assertEquals("QA", service.determinePhase("QA Review Process", null, null));
        }
    }

    // ==================== isDone / isInProgress Tests ====================

    @Nested
    class StatusCheckTests {

        @Test
        void isDoneReturnsTrueForDoneStatuses() {
            assertTrue(service.isDone("Done", null));
            assertTrue(service.isDone("Closed", null));
            assertTrue(service.isDone("Resolved", null));
            assertTrue(service.isDone("Готово", null));
        }

        @Test
        void isDoneReturnsFalseForOtherStatuses() {
            assertFalse(service.isDone("In Progress", null));
            assertFalse(service.isDone("Backlog", null));
            assertFalse(service.isDone("New", null));
        }

        @Test
        void isDoneReturnsFalseForNull() {
            assertFalse(service.isDone(null, null));
        }

        @Test
        void isDoneFallbackSubstring() {
            // Even custom statuses with "done" should work
            assertTrue(service.isDone("Task is Done", null));
            assertTrue(service.isDone("Выполнено", null)); // contains "выполнен"
        }

        @Test
        void isInProgressReturnsTrueForInProgressStatuses() {
            assertTrue(service.isInProgress("Development", null));
            assertTrue(service.isInProgress("Testing", null));
            assertTrue(service.isInProgress("Analysis", null));
            assertTrue(service.isInProgress("Разработка", null));
        }

        @Test
        void isInProgressReturnsFalseForOtherStatuses() {
            assertFalse(service.isInProgress("Done", null));
            assertFalse(service.isInProgress("Backlog", null));
        }

        @Test
        void isInProgressReturnsFalseForNull() {
            assertFalse(service.isInProgress(null, null));
        }
    }

    // ==================== Rough Estimate Allowed Tests ====================

    @Nested
    class RoughEstimateTests {

        @Test
        void isAllowedForRoughEstimateTodoStatuses() {
            assertTrue(service.isAllowedForRoughEstimate("Backlog", null));
            assertTrue(service.isAllowedForRoughEstimate("To Do", null));
            assertTrue(service.isAllowedForRoughEstimate("New", null));
            assertTrue(service.isAllowedForRoughEstimate("Бэклог", null));
        }

        @Test
        void isAllowedForRoughEstimateInProgressNotAllowed() {
            assertFalse(service.isAllowedForRoughEstimate("Developing", null));
            assertFalse(service.isAllowedForRoughEstimate("In Progress", null));
        }

        @Test
        void isAllowedForRoughEstimateDoneNotAllowed() {
            assertFalse(service.isAllowedForRoughEstimate("Done", null));
            assertFalse(service.isAllowedForRoughEstimate("Closed", null));
        }

        @Test
        void isAllowedForRoughEstimateNullReturnsFalse() {
            assertFalse(service.isAllowedForRoughEstimate(null, null));
        }
    }

    // ==================== Team Override Tests ====================

    @Nested
    class TeamOverrideTests {

        @Test
        void teamOverrideIsIgnored_statusesCategorizedByFallback() {
            // Override is ignored — all categorization uses WCS fallback substring matching
            StatusMappingConfig override = new StatusMappingConfig(
                    new WorkflowConfig(
                            List.of("Custom Backlog", "Custom New"),
                            List.of(),
                            List.of()
                    ),
                    null, null, null, null, null
            );

            // "Custom Backlog" -> WCS fallback: no DONE/IN_PROGRESS substring match -> NEW -> allowed
            assertTrue(service.isAllowedForRoughEstimate("Custom Backlog", override));
            // "Custom New" -> same path -> NEW -> allowed
            assertTrue(service.isAllowedForRoughEstimate("Custom New", override));
        }

        @Test
        void teamOverrideIsIgnored_defaultFallbackStillApplies() {
            StatusMappingConfig override = new StatusMappingConfig(
                    new WorkflowConfig(
                            List.of("Only This Status"),
                            List.of(),
                            List.of()
                    ),
                    null, null, null, null, null
            );

            // "Only This Status" -> WCS fallback: no substring match -> NEW -> allowed
            assertTrue(service.isAllowedForRoughEstimate("Only This Status", override));
            // Override is ignored, so "Backlog" is still categorized by WCS fallback -> NEW -> allowed
            assertTrue(service.isAllowedForRoughEstimate("Backlog", override));
        }

        @Test
        void teamOverrideIsIgnored_doneStatusesCategorizedBySubstring() {
            StatusMappingConfig override = new StatusMappingConfig(
                    new WorkflowConfig(
                            List.of(),
                            List.of(),
                            List.of("Завершено", "Выполнено")
                    ),
                    null, null, null, null, null
            );

            // Override is ignored, but these statuses match via WCS substring fallback:
            // "Завершено" contains "завершен" -> DONE
            assertEquals(StatusCategory.DONE, service.categorizeEpic("Завершено", override));
            // "Выполнено" contains "выполнен" -> DONE
            assertEquals(StatusCategory.DONE, service.categorizeEpic("Выполнено", override));
        }

        @Test
        void teamOverrideIsIgnored_phaseDetectionUsesFallback() {
            StatusMappingConfig override = new StatusMappingConfig(
                    null, null, null,
                    new PhaseMapping(
                            List.of("Custom Analysis"),
                            List.of("Custom Dev"),
                            List.of("Custom QA"),
                            List.of("CustomSAType"),
                            List.of("CustomQAType")
                    ),
                    null, null
            );

            // Override is ignored, but these work via WCS status substring fallback:
            // "Custom Analysis" contains "analysis" -> SA
            assertEquals("SA", service.determinePhase("Custom Analysis", null, override));
            // "Custom Dev" has no matching substring -> defaults to DEV
            assertEquals("DEV", service.determinePhase("Custom Dev", null, override));
            // "Custom QA" contains "qa" -> QA
            assertEquals("QA", service.determinePhase("Custom QA", null, override));
        }
    }

    // ==================== Config Retrieval Tests ====================

    @Nested
    class ConfigTests {

        @Test
        void getEffectiveConfigReturnsNonNull() {
            StatusMappingConfig effective = service.getEffectiveConfig(null);
            assertNotNull(effective);
            assertNotNull(effective.epicWorkflow());
            assertNotNull(effective.storyWorkflow());
            assertNotNull(effective.subtaskWorkflow());
        }

        @Test
        void getEffectiveConfigIgnoresOverride() {
            StatusMappingConfig override = new StatusMappingConfig(
                    new WorkflowConfig(List.of("Custom"), List.of(), List.of()),
                    null, null, null, null, null
            );

            StatusMappingConfig effective = service.getEffectiveConfig(override);

            // Override is ignored — config comes from WCS which has empty caches
            // so all status lists are empty
            assertNotNull(effective);
            assertNotNull(effective.epicWorkflow());
            assertTrue(effective.epicWorkflow().todoStatuses().isEmpty());
            assertTrue(effective.epicWorkflow().inProgressStatuses().isEmpty());
            assertTrue(effective.epicWorkflow().doneStatuses().isEmpty());
        }

        @Test
        void getDefaultConfigReturnsEmptyListsWhenNoDB() {
            // With empty WCS caches (no DB data), all status name lists are empty
            StatusMappingConfig config = service.getDefaultConfig();
            assertNotNull(config);
            assertTrue(config.epicWorkflow().todoStatuses().isEmpty());
            assertTrue(config.storyWorkflow().inProgressStatuses().isEmpty());
            assertTrue(config.subtaskWorkflow().doneStatuses().isEmpty());
        }
    }
}

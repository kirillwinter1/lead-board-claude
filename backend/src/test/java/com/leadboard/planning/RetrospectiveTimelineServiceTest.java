package com.leadboard.planning;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.planning.dto.RetrospectiveResult;
import com.leadboard.planning.dto.RetrospectiveResult.*;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetrospectiveTimelineServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private StatusChangelogRepository changelogRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    private RetrospectiveTimelineService service;

    @BeforeEach
    void setUp() {
        service = new RetrospectiveTimelineService(issueRepository, changelogRepository, workflowConfigService);

        // Default role determination: status-based
        when(workflowConfigService.determinePhase(eq("In Analysis"), isNull())).thenReturn("SA");
        when(workflowConfigService.determinePhase(eq("In Development"), isNull())).thenReturn("DEV");
        when(workflowConfigService.determinePhase(eq("In Testing"), isNull())).thenReturn("QA");
        when(workflowConfigService.determinePhase(eq("Done"), isNull())).thenReturn("DEV");
        when(workflowConfigService.determinePhase(eq("To Do"), isNull())).thenReturn("DEV");
        when(workflowConfigService.determinePhase(eq("Backlog"), isNull())).thenReturn("DEV");

        // Default status categorization
        when(workflowConfigService.categorize(eq("In Analysis"), any())).thenReturn(StatusCategory.IN_PROGRESS);
        when(workflowConfigService.categorize(eq("In Development"), any())).thenReturn(StatusCategory.IN_PROGRESS);
        when(workflowConfigService.categorize(eq("In Testing"), any())).thenReturn(StatusCategory.IN_PROGRESS);
        when(workflowConfigService.categorize(eq("Done"), any())).thenReturn(StatusCategory.DONE);
        when(workflowConfigService.categorize(eq("To Do"), any())).thenReturn(StatusCategory.NEW);
        when(workflowConfigService.categorize(eq("Backlog"), any())).thenReturn(StatusCategory.NEW);

        // isDone
        when(workflowConfigService.isDone(eq("Done"), any())).thenReturn(true);
        when(workflowConfigService.isDone(eq("In Analysis"), any())).thenReturn(false);
        when(workflowConfigService.isDone(eq("In Development"), any())).thenReturn(false);
        when(workflowConfigService.isDone(eq("In Testing"), any())).thenReturn(false);
        when(workflowConfigService.isDone(eq("To Do"), any())).thenReturn(false);
        when(workflowConfigService.isDone(eq("Backlog"), any())).thenReturn(false);
    }

    private JiraIssueEntity createStory(String key, String status, String parentKey) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setSummary("Story " + key);
        entity.setStatus(status);
        entity.setIssueType("Story");
        entity.setParentKey(parentKey);
        entity.setBoardCategory("STORY");
        entity.setTeamId(1L);
        return entity;
    }

    private StatusChangelogEntity createTransition(String issueKey, String fromStatus, String toStatus, OffsetDateTime at) {
        StatusChangelogEntity entity = new StatusChangelogEntity();
        entity.setIssueKey(issueKey);
        entity.setIssueId("id-" + issueKey);
        entity.setFromStatus(fromStatus);
        entity.setToStatus(toStatus);
        entity.setTransitionedAt(at);
        return entity;
    }

    @Nested
    class LinearFlow {
        @Test
        void shouldCalculateLinearSaDevQaDone() {
            JiraIssueEntity story = createStory("PROJ-1", "Done", "PROJ-100");
            JiraIssueEntity epic = createStory("PROJ-100", "In Progress", null);
            epic.setBoardCategory("EPIC");

            when(issueRepository.findByBoardCategoryAndTeamId("STORY", 1L))
                    .thenReturn(List.of(story));
            when(issueRepository.findByIssueKey("PROJ-100"))
                    .thenReturn(Optional.of(epic));

            OffsetDateTime base = OffsetDateTime.of(2025, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC);
            List<StatusChangelogEntity> transitions = List.of(
                    createTransition("PROJ-1", "To Do", "In Analysis", base),
                    createTransition("PROJ-1", "In Analysis", "In Development", base.plusDays(3)),
                    createTransition("PROJ-1", "In Development", "In Testing", base.plusDays(8)),
                    createTransition("PROJ-1", "In Testing", "Done", base.plusDays(10))
            );
            when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List.of("PROJ-1")))
                    .thenReturn(transitions);

            RetrospectiveResult result = service.calculateRetrospective(1L);

            assertNotNull(result);
            assertEquals(1, result.epics().size());
            RetroEpic retroEpic = result.epics().get(0);
            assertEquals("PROJ-100", retroEpic.epicKey());

            assertEquals(1, retroEpic.stories().size());
            RetroStory retroStory = retroEpic.stories().get(0);
            assertEquals("PROJ-1", retroStory.storyKey());
            assertTrue(retroStory.completed());
            assertEquals(100, retroStory.progressPercent());

            // Phases: SA, DEV, QA
            assertEquals(3, retroStory.phases().size());
            assertNotNull(retroStory.phases().get("SA"));
            assertNotNull(retroStory.phases().get("DEV"));
            assertNotNull(retroStory.phases().get("QA"));

            // SA: Jan 10 -> Jan 13 (3 days)
            RetroPhase saPhase = retroStory.phases().get("SA");
            assertEquals(base.toLocalDate(), saPhase.startDate());
            assertEquals(base.plusDays(3).toLocalDate(), saPhase.endDate());
            assertEquals(3, saPhase.durationDays());
            assertFalse(saPhase.active());

            // DEV: Jan 13 -> Jan 18 (5 days)
            RetroPhase devPhase = retroStory.phases().get("DEV");
            assertEquals(base.plusDays(3).toLocalDate(), devPhase.startDate());
            assertEquals(base.plusDays(8).toLocalDate(), devPhase.endDate());
            assertEquals(5, devPhase.durationDays());

            // QA: Jan 18 -> Jan 20 (2 days)
            RetroPhase qaPhase = retroStory.phases().get("QA");
            assertEquals(base.plusDays(8).toLocalDate(), qaPhase.startDate());
            assertEquals(base.plusDays(10).toLocalDate(), qaPhase.endDate());
            assertEquals(2, qaPhase.durationDays());
        }
    }

    @Nested
    class StoryInProgress {
        @Test
        void shouldShowActivePhaseWithoutEndDate() {
            JiraIssueEntity story = createStory("PROJ-2", "In Development", "PROJ-100");
            JiraIssueEntity epic = createStory("PROJ-100", "In Progress", null);
            epic.setBoardCategory("EPIC");

            when(issueRepository.findByBoardCategoryAndTeamId("STORY", 1L))
                    .thenReturn(List.of(story));
            when(issueRepository.findByIssueKey("PROJ-100"))
                    .thenReturn(Optional.of(epic));

            OffsetDateTime base = OffsetDateTime.of(2025, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC);
            List<StatusChangelogEntity> transitions = List.of(
                    createTransition("PROJ-2", "To Do", "In Analysis", base),
                    createTransition("PROJ-2", "In Analysis", "In Development", base.plusDays(3))
            );
            when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List.of("PROJ-2")))
                    .thenReturn(transitions);

            RetrospectiveResult result = service.calculateRetrospective(1L);

            RetroStory retroStory = result.epics().get(0).stories().get(0);
            assertFalse(retroStory.completed());
            assertNull(retroStory.endDate()); // active story

            // SA phase closed
            RetroPhase saPhase = retroStory.phases().get("SA");
            assertFalse(saPhase.active());
            assertNotNull(saPhase.endDate());

            // DEV phase active
            RetroPhase devPhase = retroStory.phases().get("DEV");
            assertTrue(devPhase.active());
            assertNull(devPhase.endDate());
        }
    }

    @Nested
    class Rework {
        @Test
        void shouldMergeReworkPhases() {
            // DEV -> QA -> DEV -> QA -> Done (rework)
            JiraIssueEntity story = createStory("PROJ-3", "Done", "PROJ-100");
            JiraIssueEntity epic = createStory("PROJ-100", "In Progress", null);
            epic.setBoardCategory("EPIC");

            when(issueRepository.findByBoardCategoryAndTeamId("STORY", 1L))
                    .thenReturn(List.of(story));
            when(issueRepository.findByIssueKey("PROJ-100"))
                    .thenReturn(Optional.of(epic));

            OffsetDateTime base = OffsetDateTime.of(2025, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC);
            List<StatusChangelogEntity> transitions = List.of(
                    createTransition("PROJ-3", "To Do", "In Development", base),
                    createTransition("PROJ-3", "In Development", "In Testing", base.plusDays(5)),
                    createTransition("PROJ-3", "In Testing", "In Development", base.plusDays(7)),
                    createTransition("PROJ-3", "In Development", "In Testing", base.plusDays(9)),
                    createTransition("PROJ-3", "In Testing", "Done", base.plusDays(11))
            );
            when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List.of("PROJ-3")))
                    .thenReturn(transitions);

            RetrospectiveResult result = service.calculateRetrospective(1L);
            RetroStory retroStory = result.epics().get(0).stories().get(0);

            // DEV phase: firstStart=base, lastEnd=base+9 (merged rework)
            RetroPhase devPhase = retroStory.phases().get("DEV");
            assertEquals(base.toLocalDate(), devPhase.startDate());
            assertEquals(base.plusDays(9).toLocalDate(), devPhase.endDate());

            // QA phase: firstStart=base+5, lastEnd=base+11
            RetroPhase qaPhase = retroStory.phases().get("QA");
            assertEquals(base.plusDays(5).toLocalDate(), qaPhase.startDate());
            assertEquals(base.plusDays(11).toLocalDate(), qaPhase.endDate());

            assertTrue(retroStory.completed());
        }
    }

    @Nested
    class SkippedRole {
        @Test
        void shouldHandleDirectDevToQaSkippingSa() {
            JiraIssueEntity story = createStory("PROJ-4", "Done", "PROJ-100");
            JiraIssueEntity epic = createStory("PROJ-100", "Done", null);
            epic.setBoardCategory("EPIC");

            when(issueRepository.findByBoardCategoryAndTeamId("STORY", 1L))
                    .thenReturn(List.of(story));
            when(issueRepository.findByIssueKey("PROJ-100"))
                    .thenReturn(Optional.of(epic));

            OffsetDateTime base = OffsetDateTime.of(2025, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC);
            List<StatusChangelogEntity> transitions = List.of(
                    createTransition("PROJ-4", "To Do", "In Development", base),
                    createTransition("PROJ-4", "In Development", "Done", base.plusDays(5))
            );
            when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List.of("PROJ-4")))
                    .thenReturn(transitions);

            RetrospectiveResult result = service.calculateRetrospective(1L);
            RetroStory retroStory = result.epics().get(0).stories().get(0);

            // Only DEV phase, no SA or QA
            assertEquals(1, retroStory.phases().size());
            assertNotNull(retroStory.phases().get("DEV"));
            assertNull(retroStory.phases().get("SA"));
            assertNull(retroStory.phases().get("QA"));
        }
    }

    @Nested
    class BacklogReturn {
        @Test
        void shouldClosePhaseOnBacklogReturn() {
            JiraIssueEntity story = createStory("PROJ-5", "To Do", "PROJ-100");
            // Story returned to backlog — should be filtered out (it's in NEW status)
            when(issueRepository.findByBoardCategoryAndTeamId("STORY", 1L))
                    .thenReturn(List.of(story));

            RetrospectiveResult result = service.calculateRetrospective(1L);

            // Story in To Do (NEW) should be filtered out
            assertTrue(result.epics().isEmpty());
        }
    }

    @Nested
    class NoTransitions {
        @Test
        void shouldSkipStoriesWithNoTransitions() {
            JiraIssueEntity story = createStory("PROJ-6", "In Development", "PROJ-100");

            when(issueRepository.findByBoardCategoryAndTeamId("STORY", 1L))
                    .thenReturn(List.of(story));
            when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List.of("PROJ-6")))
                    .thenReturn(List.of());

            RetrospectiveResult result = service.calculateRetrospective(1L);

            // Story with no transitions should produce no result
            assertTrue(result.epics().isEmpty());
        }
    }

    @Nested
    class EpicGrouping {
        @Test
        void shouldGroupStoriesByEpic() {
            JiraIssueEntity story1 = createStory("PROJ-1", "Done", "PROJ-100");
            JiraIssueEntity story2 = createStory("PROJ-2", "Done", "PROJ-100");
            JiraIssueEntity story3 = createStory("PROJ-3", "Done", "PROJ-200");

            JiraIssueEntity epic1 = createStory("PROJ-100", "In Progress", null);
            epic1.setBoardCategory("EPIC");
            epic1.setSummary("Epic 100");
            JiraIssueEntity epic2 = createStory("PROJ-200", "In Progress", null);
            epic2.setBoardCategory("EPIC");
            epic2.setSummary("Epic 200");

            when(issueRepository.findByBoardCategoryAndTeamId("STORY", 1L))
                    .thenReturn(List.of(story1, story2, story3));
            when(issueRepository.findByIssueKey("PROJ-100")).thenReturn(Optional.of(epic1));
            when(issueRepository.findByIssueKey("PROJ-200")).thenReturn(Optional.of(epic2));

            OffsetDateTime base = OffsetDateTime.of(2025, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC);
            when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(anyList()))
                    .thenReturn(List.of(
                            createTransition("PROJ-1", "To Do", "In Development", base),
                            createTransition("PROJ-1", "In Development", "Done", base.plusDays(3)),
                            createTransition("PROJ-2", "To Do", "In Development", base.plusDays(1)),
                            createTransition("PROJ-2", "In Development", "Done", base.plusDays(5)),
                            createTransition("PROJ-3", "To Do", "In Development", base.plusDays(2)),
                            createTransition("PROJ-3", "In Development", "Done", base.plusDays(6))
                    ));

            RetrospectiveResult result = service.calculateRetrospective(1L);

            assertEquals(2, result.epics().size());

            // Find epics by key
            RetroEpic epicResult100 = result.epics().stream()
                    .filter(e -> "PROJ-100".equals(e.epicKey())).findFirst().orElse(null);
            RetroEpic epicResult200 = result.epics().stream()
                    .filter(e -> "PROJ-200".equals(e.epicKey())).findFirst().orElse(null);

            assertNotNull(epicResult100);
            assertNotNull(epicResult200);
            assertEquals(2, epicResult100.stories().size());
            assertEquals(1, epicResult200.stories().size());
        }
    }
}

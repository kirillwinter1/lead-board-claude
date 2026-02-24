package com.leadboard.config.service;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.status.StatusCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowConfigService: Score Weight & Status Name methods")
class WorkflowConfigServiceScoreTest {

    @Mock private ProjectConfigurationRepository configRepo;
    @Mock private WorkflowRoleRepository roleRepo;
    @Mock private IssueTypeMappingRepository issueTypeRepo;
    @Mock private StatusMappingRepository statusMappingRepo;
    @Mock private LinkTypeMappingRepository linkTypeRepo;
    @Mock private JiraProperties jiraProperties;

    private WorkflowConfigService service;

    @BeforeEach
    void setUp() {
        when(jiraProperties.getProjectKey()).thenReturn("TEST");

        ProjectConfigurationEntity config = new ProjectConfigurationEntity();
        config.setId(1L);
        config.setProjectKey("TEST");
        config.setDefault(true);
        when(configRepo.findByProjectKey("TEST")).thenReturn(Optional.of(config));

        when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of());
        when(linkTypeRepo.findByConfigId(1L)).thenReturn(List.of());

        service = new WorkflowConfigService(
                configRepo, roleRepo, issueTypeRepo, statusMappingRepo,
                linkTypeRepo, new ObjectMapper(), jiraProperties);
    }

    // ==================== getDefaultScoreWeightForCategory ====================

    @Nested
    @DisplayName("getDefaultScoreWeightForCategory")
    class DefaultScoreWeight {

        @Test
        void epicNewGivesNegativeScore() {
            assertEquals(-5, service.getDefaultScoreWeightForCategory(StatusCategory.NEW, BoardCategory.EPIC));
        }

        @Test
        void epicRequirementsGives8() {
            assertEquals(8, service.getDefaultScoreWeightForCategory(StatusCategory.REQUIREMENTS, BoardCategory.EPIC));
        }

        @Test
        void epicPlannedGives15() {
            assertEquals(15, service.getDefaultScoreWeightForCategory(StatusCategory.PLANNED, BoardCategory.EPIC));
        }

        @Test
        void epicInProgressGives25() {
            assertEquals(25, service.getDefaultScoreWeightForCategory(StatusCategory.IN_PROGRESS, BoardCategory.EPIC));
        }

        @Test
        void epicDoneGivesZero() {
            assertEquals(0, service.getDefaultScoreWeightForCategory(StatusCategory.DONE, BoardCategory.EPIC));
        }

        @Test
        void storyNewGivesZero() {
            assertEquals(0, service.getDefaultScoreWeightForCategory(StatusCategory.NEW, BoardCategory.STORY));
        }

        @Test
        void storyInProgressGives50() {
            assertEquals(50, service.getDefaultScoreWeightForCategory(StatusCategory.IN_PROGRESS, BoardCategory.STORY));
        }

        @Test
        void storyPlannedGives30() {
            assertEquals(30, service.getDefaultScoreWeightForCategory(StatusCategory.PLANNED, BoardCategory.STORY));
        }

        @Test
        void nullInputsGiveZero() {
            assertEquals(0, service.getDefaultScoreWeightForCategory(null, BoardCategory.EPIC));
            assertEquals(0, service.getDefaultScoreWeightForCategory(StatusCategory.NEW, null));
        }

        @Test
        void todoNormalizesToNew() {
            assertEquals(-5, service.getDefaultScoreWeightForCategory(StatusCategory.TODO, BoardCategory.EPIC));
            assertEquals(0, service.getDefaultScoreWeightForCategory(StatusCategory.TODO, BoardCategory.STORY));
        }
    }

    // ==================== getStatusScoreWeightWithFallback ====================

    @Nested
    @DisplayName("getStatusScoreWeightWithFallback")
    class ScoreWeightWithFallback {

        @Test
        void usesDbWeightWhenAvailable() {
            // Setup status mapping with score_weight = 42
            StatusMappingEntity mapping = new StatusMappingEntity();
            mapping.setIssueCategory(BoardCategory.EPIC);
            mapping.setJiraStatusName("Custom Status");
            mapping.setStatusCategory(StatusCategory.IN_PROGRESS);
            mapping.setScoreWeight(42);
            mapping.setSortOrder(1);
            when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of(mapping));

            // Reload config to pick up the mapping
            service.clearCache();

            int weight = service.getStatusScoreWeightWithFallback("Custom Status", BoardCategory.EPIC);
            assertEquals(42, weight);
        }

        @Test
        void fallsBackToCategoryWhenDbReturnsZero() {
            // No status mappings configured → DB returns 0
            when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of());

            // But we have issue type mapping so categorize works
            StatusMappingEntity mapping = new StatusMappingEntity();
            mapping.setIssueCategory(BoardCategory.EPIC);
            mapping.setJiraStatusName("Developing");
            mapping.setStatusCategory(StatusCategory.IN_PROGRESS);
            mapping.setScoreWeight(0); // no explicit weight
            mapping.setSortOrder(1);
            when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of(mapping));

            service.clearCache();

            // Should categorize as IN_PROGRESS and return default weight 25
            int weight = service.getStatusScoreWeightWithFallback("Developing", BoardCategory.EPIC);
            assertEquals(25, weight);
        }

        @Test
        void nullStatusGivesZero() {
            assertEquals(0, service.getStatusScoreWeightWithFallback(null, BoardCategory.EPIC));
        }

        @Test
        void nullCategoryGivesZero() {
            assertEquals(0, service.getStatusScoreWeightWithFallback("Done", null));
        }
    }

    // ==================== getFirstStatusNameForCategory ====================

    @Nested
    @DisplayName("getFirstStatusNameForCategory")
    class FirstStatusName {

        @Test
        void returnsConfiguredStatusName() {
            StatusMappingEntity mapping = new StatusMappingEntity();
            mapping.setIssueCategory(BoardCategory.STORY);
            mapping.setJiraStatusName("Готово");
            mapping.setStatusCategory(StatusCategory.DONE);
            mapping.setScoreWeight(0);
            mapping.setSortOrder(99);
            when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of(mapping));

            service.clearCache();

            String name = service.getFirstStatusNameForCategory(StatusCategory.DONE, BoardCategory.STORY);
            assertEquals("Готово", name);
        }

        @Test
        void fallsBackToHardcodedDefault() {
            when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of());
            service.clearCache();

            assertEquals("Done", service.getFirstStatusNameForCategory(StatusCategory.DONE, BoardCategory.STORY));
            assertEquals("In Progress", service.getFirstStatusNameForCategory(StatusCategory.IN_PROGRESS, BoardCategory.EPIC));
            assertEquals("New", service.getFirstStatusNameForCategory(StatusCategory.NEW, BoardCategory.EPIC));
        }

        @Test
        void bugFallsBackToStoryMappings() {
            StatusMappingEntity storyMapping = new StatusMappingEntity();
            storyMapping.setIssueCategory(BoardCategory.STORY);
            storyMapping.setJiraStatusName("Выполнено");
            storyMapping.setStatusCategory(StatusCategory.DONE);
            storyMapping.setScoreWeight(0);
            storyMapping.setSortOrder(99);
            when(statusMappingRepo.findByConfigId(1L)).thenReturn(List.of(storyMapping));

            service.clearCache();

            // BUG has no mappings, should fall back to STORY
            String name = service.getFirstStatusNameForCategory(StatusCategory.DONE, BoardCategory.BUG);
            assertEquals("Выполнено", name);
        }

        @Test
        void nullInputsReturnNull() {
            assertNull(service.getFirstStatusNameForCategory(null, BoardCategory.EPIC));
            assertNull(service.getFirstStatusNameForCategory(StatusCategory.DONE, null));
        }
    }
}

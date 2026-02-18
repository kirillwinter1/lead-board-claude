package com.leadboard.config.service;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.status.StatusCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for F38: Incremental Workflow Configuration.
 * Tests registerUnknownTypeIfNeeded(), detectStatusesForIssueType(),
 * and null board_category handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("F38: Incremental Workflow Configuration")
class IncrementalWorkflowConfigTest {

    @Mock private JiraMetadataService jiraMetadataService;
    @Mock private ProjectConfigurationRepository configRepo;
    @Mock private WorkflowRoleRepository roleRepo;
    @Mock private IssueTypeMappingRepository issueTypeRepo;
    @Mock private StatusMappingRepository statusMappingRepo;
    @Mock private LinkTypeMappingRepository linkTypeRepo;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private JiraProperties jiraProperties;
    @Mock private com.leadboard.sync.JiraIssueRepository jiraIssueRepo;

    private MappingAutoDetectService service;

    @BeforeEach
    void setUp() {
        service = new MappingAutoDetectService(
                jiraMetadataService, configRepo, roleRepo, issueTypeRepo,
                statusMappingRepo, linkTypeRepo, workflowConfigService, jiraProperties,
                jiraIssueRepo
        );

        when(jiraProperties.getProjectKey()).thenReturn(null);

        ProjectConfigurationEntity config = new ProjectConfigurationEntity();
        config.setId(1L);
        config.setName("Default");
        config.setDefault(true);
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config));

        when(issueTypeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusMappingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== registerUnknownTypeIfNeeded ====================

    @Nested
    @DisplayName("registerUnknownTypeIfNeeded()")
    class RegisterUnknownTypeTests {

        @Test
        @DisplayName("should create mapping with null board_category for unknown type")
        void shouldCreateMappingWithNullCategory() {
            when(issueTypeRepo.findByConfigIdAndJiraTypeName(1L, "NewType")).thenReturn(Optional.empty());

            service.registerUnknownTypeIfNeeded("NewType");

            ArgumentCaptor<IssueTypeMappingEntity> captor = ArgumentCaptor.forClass(IssueTypeMappingEntity.class);
            verify(issueTypeRepo).save(captor.capture());

            IssueTypeMappingEntity saved = captor.getValue();
            assertEquals("NewType", saved.getJiraTypeName());
            assertNull(saved.getBoardCategory());
            assertEquals(1L, saved.getConfigId());
        }

        @Test
        @DisplayName("should be idempotent — skip if type already registered")
        void shouldSkipIfAlreadyRegistered() {
            IssueTypeMappingEntity existing = new IssueTypeMappingEntity();
            existing.setJiraTypeName("ExistingType");
            existing.setBoardCategory(BoardCategory.STORY);
            when(issueTypeRepo.findByConfigIdAndJiraTypeName(1L, "ExistingType"))
                    .thenReturn(Optional.of(existing));

            service.registerUnknownTypeIfNeeded("ExistingType");

            verify(issueTypeRepo, never()).save(any());
        }

        @Test
        @DisplayName("should skip null type name")
        void shouldSkipNullTypeName() {
            service.registerUnknownTypeIfNeeded(null);
            verify(issueTypeRepo, never()).save(any());
        }

        @Test
        @DisplayName("should be idempotent — skip if type registered with null category")
        void shouldSkipIfRegisteredWithNullCategory() {
            IssueTypeMappingEntity existing = new IssueTypeMappingEntity();
            existing.setJiraTypeName("PendingType");
            existing.setBoardCategory(null); // Already registered but unmapped
            when(issueTypeRepo.findByConfigIdAndJiraTypeName(1L, "PendingType"))
                    .thenReturn(Optional.of(existing));

            service.registerUnknownTypeIfNeeded("PendingType");

            verify(issueTypeRepo, never()).save(any());
        }
    }

    // ==================== detectStatusesForIssueType ====================

    @Nested
    @DisplayName("detectStatusesForIssueType()")
    class DetectStatusesTests {

        @BeforeEach
        void setupRoles() {
            WorkflowRoleEntity sa = new WorkflowRoleEntity();
            sa.setCode("SA");
            WorkflowRoleEntity dev = new WorkflowRoleEntity();
            dev.setCode("DEV");
            WorkflowRoleEntity qa = new WorkflowRoleEntity();
            qa.setCode("QA");
            when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of(sa, dev, qa));
        }

        @Test
        @DisplayName("should create status mappings from Jira metadata")
        void shouldCreateStatusMappings() {
            when(jiraMetadataService.getStatuses()).thenReturn(List.of(
                    statusGroup("Story", List.of(
                            status("New", "new"),
                            status("In Progress", "indeterminate"),
                            status("Done", "done")
                    ))
            ));
            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.STORY))
                    .thenReturn(List.of());

            int count = service.detectStatusesForIssueType("Story", BoardCategory.STORY);

            assertEquals(3, count);

            ArgumentCaptor<StatusMappingEntity> captor = ArgumentCaptor.forClass(StatusMappingEntity.class);
            verify(statusMappingRepo, times(3)).save(captor.capture());

            List<StatusMappingEntity> saved = captor.getAllValues();
            assertTrue(saved.stream().anyMatch(s ->
                    "New".equals(s.getJiraStatusName()) && s.getStatusCategory() == StatusCategory.NEW));
            assertTrue(saved.stream().anyMatch(s ->
                    "Done".equals(s.getJiraStatusName()) && s.getStatusCategory() == StatusCategory.DONE));
            assertTrue(saved.stream().anyMatch(s ->
                    "In Progress".equals(s.getJiraStatusName()) && s.getStatusCategory() == StatusCategory.IN_PROGRESS));
        }

        @Test
        @DisplayName("should skip already existing statuses")
        void shouldSkipExistingStatuses() {
            when(jiraMetadataService.getStatuses()).thenReturn(List.of(
                    statusGroup("Epic", List.of(
                            status("New", "new"),
                            status("Done", "done")
                    ))
            ));

            StatusMappingEntity existingNew = new StatusMappingEntity();
            existingNew.setJiraStatusName("New");
            existingNew.setSortOrder(10);
            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.EPIC))
                    .thenReturn(List.of(existingNew));

            int count = service.detectStatusesForIssueType("Epic", BoardCategory.EPIC);

            assertEquals(1, count); // Only "Done" is new
        }

        @Test
        @DisplayName("should return 0 when no statuses found for type")
        void shouldReturnZeroWhenNoStatusesFound() {
            when(jiraMetadataService.getStatuses()).thenReturn(List.of(
                    statusGroup("OtherType", List.of(
                            status("Open", "new")
                    ))
            ));
            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.STORY))
                    .thenReturn(List.of());

            int count = service.detectStatusesForIssueType("Story", BoardCategory.STORY);

            assertEquals(0, count);
            verify(statusMappingRepo, never()).save(any());
        }

        @Test
        @DisplayName("should clear cache after detecting statuses")
        void shouldClearCacheAfterDetection() {
            when(jiraMetadataService.getStatuses()).thenReturn(List.of(
                    statusGroup("Bug", List.of(
                            status("Open", "new"),
                            status("Closed", "done")
                    ))
            ));
            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.STORY))
                    .thenReturn(List.of());

            service.detectStatusesForIssueType("Bug", BoardCategory.STORY);

            verify(workflowConfigService).clearCache();
        }

        @Test
        @DisplayName("should not clear cache when no new statuses detected")
        void shouldNotClearCacheWhenNoNewStatuses() {
            when(jiraMetadataService.getStatuses()).thenReturn(List.of());
            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.EPIC))
                    .thenReturn(List.of());

            service.detectStatusesForIssueType("Epic", BoardCategory.EPIC);

            verify(workflowConfigService, never()).clearCache();
        }

        @Test
        @DisplayName("should set correct issue category on saved mappings")
        void shouldSetCorrectIssueCategory() {
            when(jiraMetadataService.getStatuses()).thenReturn(List.of(
                    statusGroup("Эпик", List.of(
                            status("Новое", "new")
                    ))
            ));
            when(statusMappingRepo.findByConfigIdAndIssueCategory(1L, BoardCategory.EPIC))
                    .thenReturn(List.of());

            service.detectStatusesForIssueType("Эпик", BoardCategory.EPIC);

            ArgumentCaptor<StatusMappingEntity> captor = ArgumentCaptor.forClass(StatusMappingEntity.class);
            verify(statusMappingRepo).save(captor.capture());

            assertEquals(BoardCategory.EPIC, captor.getValue().getIssueCategory());
        }
    }

    // ==================== Helper methods ====================

    private Map<String, Object> statusGroup(String issueType, List<Map<String, Object>> statuses) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issueType", issueType);
        m.put("statuses", statuses);
        return m;
    }

    private Map<String, Object> status(String name, String categoryKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("statusCategory", categoryKey);
        m.put("statusCategoryName", categoryKey.substring(0, 1).toUpperCase() + categoryKey.substring(1));
        return m;
    }
}

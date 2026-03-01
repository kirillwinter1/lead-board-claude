package com.leadboard.config.service;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.status.StatusCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for F47: Per-Project Workflow Configuration.
 * Verifies merged loading, per-project auto-detect isolation, and first-wins conflict resolution.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("F47: Per-Project Workflow Configuration")
class PerProjectWorkflowConfigTest {

    @Mock private ProjectConfigurationRepository configRepo;
    @Mock private WorkflowRoleRepository roleRepo;
    @Mock private IssueTypeMappingRepository issueTypeRepo;
    @Mock private StatusMappingRepository statusMappingRepo;
    @Mock private LinkTypeMappingRepository linkTypeRepo;
    @Mock private JiraConfigResolver jiraConfigResolver;
    @Mock private JiraMetadataService jiraMetadataService;
    @Mock private com.leadboard.sync.JiraIssueRepository jiraIssueRepo;
    @Mock private WorkflowConfigService workflowConfigService;

    private MappingAutoDetectService autoDetectService;

    @BeforeEach
    void setUp() {
        autoDetectService = new MappingAutoDetectService(
                jiraMetadataService, configRepo, roleRepo, issueTypeRepo,
                statusMappingRepo, linkTypeRepo, workflowConfigService, jiraConfigResolver,
                jiraIssueRepo
        );

        when(roleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(issueTypeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statusMappingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(linkTypeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(configRepo.save(any())).thenAnswer(inv -> {
            ProjectConfigurationEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(System.nanoTime());
            return e;
        });
    }

    // ==================== Per-project auto-detect isolation ====================

    @Test
    @DisplayName("autoDetectForProject creates separate config per project key")
    void autoDetectForProject_createsSeparateConfig() {
        // Config for PROJ1 exists
        ProjectConfigurationEntity config1 = createConfig(1L, "PROJ1", true);
        when(configRepo.findByProjectKey("PROJ1")).thenReturn(Optional.of(config1));
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config1));

        // Config for PROJ2 does NOT exist yet
        when(configRepo.findByProjectKey("PROJ2")).thenReturn(Optional.empty());

        // Jira metadata for PROJ2
        when(jiraMetadataService.getIssueTypes("PROJ2")).thenReturn(List.of(
                Map.of("name", "Task", "subtask", false, "id", "1"),
                Map.of("name", "Bug", "subtask", false, "id", "2")
        ));
        when(jiraMetadataService.getStatuses("PROJ2")).thenReturn(List.of());
        when(jiraMetadataService.getLinkTypes()).thenReturn(List.of());
        when(roleRepo.findByConfigIdOrderBySortOrderAsc(anyLong())).thenReturn(List.of());

        // Execute
        var result = autoDetectService.autoDetectForProject("PROJ2");

        // Verify a new config was created for PROJ2
        ArgumentCaptor<ProjectConfigurationEntity> captor = ArgumentCaptor.forClass(ProjectConfigurationEntity.class);
        verify(configRepo, atLeastOnce()).save(captor.capture());

        boolean foundProj2 = captor.getAllValues().stream()
                .anyMatch(c -> "PROJ2".equals(c.getProjectKey()));
        assertTrue(foundProj2, "Should create config for PROJ2");

        // Verify PROJ1's config was NOT cleared
        verify(roleRepo, never()).deleteByConfigId(1L);
        verify(issueTypeRepo, never()).deleteByConfigId(1L);
        verify(statusMappingRepo, never()).deleteByConfigId(1L);

        assertEquals(2, result.issueTypeCount());
    }

    @Test
    @DisplayName("autoDetectForProject does NOT touch other project's mappings")
    void autoDetectForProject_isolatesFromOtherProject() {
        // Config for PROJ1 already exists with mappings
        ProjectConfigurationEntity config1 = createConfig(1L, "PROJ1", true);
        when(configRepo.findByProjectKey("PROJ1")).thenReturn(Optional.of(config1));
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config1));

        // Config for PROJ2 exists too
        ProjectConfigurationEntity config2 = createConfig(2L, "PROJ2", false);
        when(configRepo.findByProjectKey("PROJ2")).thenReturn(Optional.of(config2));

        // Jira returns types for PROJ2
        when(jiraMetadataService.getIssueTypes("PROJ2")).thenReturn(List.of(
                Map.of("name", "Epic", "subtask", false, "id", "10")
        ));
        when(jiraMetadataService.getStatuses("PROJ2")).thenReturn(List.of());
        when(jiraMetadataService.getLinkTypes()).thenReturn(List.of());
        when(roleRepo.findByConfigIdOrderBySortOrderAsc(anyLong())).thenReturn(List.of());

        // Execute auto-detect for PROJ2
        autoDetectService.autoDetectForProject("PROJ2");

        // Verify only PROJ2's config (id=2) was cleared, NOT PROJ1 (id=1)
        verify(roleRepo).deleteByConfigId(2L);
        verify(issueTypeRepo).deleteByConfigId(2L);
        verify(statusMappingRepo).deleteByConfigId(2L);
        verify(linkTypeRepo).deleteByConfigId(2L);

        verify(roleRepo, never()).deleteByConfigId(1L);
        verify(issueTypeRepo, never()).deleteByConfigId(1L);
        verify(statusMappingRepo, never()).deleteByConfigId(1L);
        verify(linkTypeRepo, never()).deleteByConfigId(1L);
    }

    // ==================== Per-project unknown type registration ====================

    @Test
    @DisplayName("registerUnknownTypeIfNeeded with projectKey registers in correct config")
    void registerUnknownType_perProject() {
        ProjectConfigurationEntity config2 = createConfig(2L, "PROJ2", false);
        when(configRepo.findByProjectKey("PROJ2")).thenReturn(Optional.of(config2));
        when(issueTypeRepo.findByConfigIdAndJiraTypeName(2L, "CustomType")).thenReturn(Optional.empty());

        autoDetectService.registerUnknownTypeIfNeeded("CustomType", "PROJ2");

        ArgumentCaptor<IssueTypeMappingEntity> captor = ArgumentCaptor.forClass(IssueTypeMappingEntity.class);
        verify(issueTypeRepo).save(captor.capture());
        assertEquals(2L, captor.getValue().getConfigId());
        assertEquals("CustomType", captor.getValue().getJiraTypeName());
        assertNull(captor.getValue().getBoardCategory());
    }

    // ==================== isConfigEmptyForProject ====================

    @Test
    @DisplayName("isConfigEmptyForProject returns true when no config exists")
    void isConfigEmptyForProject_noConfig() {
        when(configRepo.findByProjectKey("NEW_PROJ")).thenReturn(Optional.empty());
        assertTrue(autoDetectService.isConfigEmptyForProject("NEW_PROJ"));
    }

    @Test
    @DisplayName("isConfigEmptyForProject returns false when config has mappings")
    void isConfigEmptyForProject_hasData() {
        ProjectConfigurationEntity config = createConfig(5L, "EXISTING", false);
        when(configRepo.findByProjectKey("EXISTING")).thenReturn(Optional.of(config));
        when(roleRepo.findByConfigIdOrderBySortOrderAsc(5L)).thenReturn(List.of(new WorkflowRoleEntity()));
        when(issueTypeRepo.findByConfigId(5L)).thenReturn(List.of());

        assertFalse(autoDetectService.isConfigEmptyForProject("EXISTING"));
    }

    // ==================== getOrCreateConfigIdForProject ====================

    @Test
    @DisplayName("getOrCreateConfigIdForProject: first project gets isDefault=true")
    void firstProjectIsDefault() {
        when(configRepo.findByProjectKey("FIRST")).thenReturn(Optional.empty());
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.empty());

        autoDetectService.getOrCreateConfigIdForProject("FIRST");

        ArgumentCaptor<ProjectConfigurationEntity> captor = ArgumentCaptor.forClass(ProjectConfigurationEntity.class);
        verify(configRepo).save(captor.capture());
        assertTrue(captor.getValue().isDefault(), "First project should be default");
        assertEquals("FIRST", captor.getValue().getProjectKey());
    }

    @Test
    @DisplayName("getOrCreateConfigIdForProject: second project gets isDefault=false")
    void secondProjectNotDefault() {
        ProjectConfigurationEntity existing = createConfig(1L, "PROJ1", true);
        when(configRepo.findByProjectKey("PROJ2")).thenReturn(Optional.empty());
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(existing));

        autoDetectService.getOrCreateConfigIdForProject("PROJ2");

        ArgumentCaptor<ProjectConfigurationEntity> captor = ArgumentCaptor.forClass(ProjectConfigurationEntity.class);
        verify(configRepo).save(captor.capture());
        assertFalse(captor.getValue().isDefault(), "Second project should NOT be default");
        assertEquals("PROJ2", captor.getValue().getProjectKey());
    }

    // ==================== Helpers ====================

    private ProjectConfigurationEntity createConfig(Long id, String projectKey, boolean isDefault) {
        ProjectConfigurationEntity config = new ProjectConfigurationEntity();
        config.setId(id);
        config.setName(projectKey);
        config.setProjectKey(projectKey);
        config.setDefault(isDefault);
        return config;
    }
}

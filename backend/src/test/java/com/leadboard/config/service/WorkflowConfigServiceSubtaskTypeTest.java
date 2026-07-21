package com.leadboard.config.service;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Regression: getSubtaskTypeName must resolve the original type name from the merged view
 * across ALL project configs, not only the default config. In a multi-project tenant where a
 * role's subtask type is mapped only in a non-default config, the old lookup (defaultConfigId
 * only) returned null and callers sent Jira the bare role code ("DEV") -> "issuetype is not valid".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowConfigService.getSubtaskTypeName across multiple project configs")
class WorkflowConfigServiceSubtaskTypeTest {

    @Mock private ProjectConfigurationRepository configRepo;
    @Mock private WorkflowRoleRepository roleRepo;
    @Mock private IssueTypeMappingRepository issueTypeRepo;
    @Mock private StatusMappingRepository statusMappingRepo;
    @Mock private LinkTypeMappingRepository linkTypeRepo;
    @Mock private JiraConfigResolver jiraConfigResolver;

    private WorkflowConfigService service;

    @BeforeEach
    void setUp() {
        when(jiraConfigResolver.getProjectKey()).thenReturn("PROJ1");
        when(jiraConfigResolver.getAllProjectKeys()).thenReturn(List.of("PROJ1", "PROJ2"));

        ProjectConfigurationEntity config1 = config(1L, "PROJ1", true);
        ProjectConfigurationEntity config2 = config(2L, "PROJ2", false);
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config1));
        when(configRepo.findAllByProjectKeyIn(List.of("PROJ1", "PROJ2")))
                .thenReturn(new ArrayList<>(List.of(config1, config2)));

        when(roleRepo.findByConfigIdOrderBySortOrderAsc(anyLong())).thenReturn(List.of());
        when(statusMappingRepo.findByConfigId(anyLong())).thenReturn(List.of());
        when(linkTypeRepo.findByConfigId(anyLong())).thenReturn(List.of());

        // The DEV subtask type is mapped ONLY in the second (non-default) config.
        when(issueTypeRepo.findByConfigId(1L)).thenReturn(List.of());
        when(issueTypeRepo.findByConfigId(2L)).thenReturn(List.of(
                mapping("Разработка", BoardCategory.SUBTASK, "DEV")));

        service = new WorkflowConfigService(
                configRepo, roleRepo, issueTypeRepo, statusMappingRepo,
                linkTypeRepo, new ObjectMapper(), jiraConfigResolver);
    }

    @Test
    @DisplayName("resolves subtask type name mapped only in a non-default config")
    void resolvesSubtaskTypeFromSecondConfig() {
        assertEquals("Разработка", service.getSubtaskTypeName("DEV"));
    }

    @Test
    @DisplayName("returns null for a role with no subtask mapping in any config")
    void returnsNullForUnmappedRole() {
        assertNull(service.getSubtaskTypeName("QA"));
    }

    private ProjectConfigurationEntity config(Long id, String key, boolean isDefault) {
        ProjectConfigurationEntity c = new ProjectConfigurationEntity();
        c.setId(id);
        c.setName(key);
        c.setProjectKey(key);
        c.setDefault(isDefault);
        return c;
    }

    private IssueTypeMappingEntity mapping(String typeName, BoardCategory cat, String roleCode) {
        IssueTypeMappingEntity m = new IssueTypeMappingEntity();
        m.setConfigId(2L);
        m.setJiraTypeName(typeName);
        m.setBoardCategory(cat);
        m.setWorkflowRoleCode(roleCode);
        return m;
    }
}

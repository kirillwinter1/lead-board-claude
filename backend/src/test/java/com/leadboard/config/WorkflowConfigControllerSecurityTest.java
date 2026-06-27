package com.leadboard.config;

import com.leadboard.auth.SessionRepository;
import com.leadboard.config.controller.WorkflowConfigController;
import com.leadboard.config.dto.WorkflowRoleDto;
import com.leadboard.config.entity.ProjectConfigurationEntity;
import com.leadboard.config.repository.*;
import com.leadboard.config.service.MappingAutoDetectService;
import com.leadboard.config.service.MappingValidationService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for {@link WorkflowConfigController} (LAUNCH_PLAN L6 RBAC gap).
 *
 * <p>Unlike {@link WorkflowConfigControllerTest} this test does NOT disable the
 * security filters, so the class-level {@code @PreAuthorize("hasRole('ADMIN')")}
 * is actually enforced. It proves under-privileged callers are rejected and
 * never reach the service layer, while ADMIN succeeds.</p>
 */
@WebMvcTest(WorkflowConfigController.class)
@AutoConfigureMockMvc
@Import(WorkflowConfigControllerSecurityTest.MethodSecurityConfig.class)
class WorkflowConfigControllerSecurityTest {

    /** Forces @PreAuthorize advice in this slice so role checks are exercised. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectConfigurationRepository configRepo;
    @MockBean
    private WorkflowRoleRepository roleRepo;
    @MockBean
    private IssueTypeMappingRepository issueTypeRepo;
    @MockBean
    private StatusMappingRepository statusMappingRepo;
    @MockBean
    private LinkTypeMappingRepository linkTypeRepo;
    @MockBean
    private WorkflowConfigService workflowConfigService;
    @MockBean
    private MappingValidationService validationService;
    @MockBean
    private MappingAutoDetectService autoDetectService;
    @MockBean
    private JiraIssueRepository jiraIssueRepository;
    @MockBean
    private JiraConfigResolver jiraConfigResolver;
    @MockBean
    private com.leadboard.jira.JiraClient jiraClient;

    // Beans required to build the security filter chain
    @MockBean
    private SessionRepository sessionRepository;
    @MockBean
    private AppProperties appProperties;
    @MockBean
    private TenantUserRepository tenantUserRepository;
    @MockBean
    private TenantRepository tenantRepository;
    @MockBean
    private com.leadboard.config.ObservabilityMetrics observabilityMetrics;

    private static final String ROLES_BODY = """
            [{"code":"SA","displayName":"Analyst","color":"#blue","sortOrder":1,"isDefault":false}]
            """;

    @BeforeEach
    void setUp() {
        // LeadBoardAuthenticationFilter reads the session cookie name; give it a
        // real Session so the (cookie-less) request leaves the @WithMockUser
        // SecurityContext intact and @PreAuthorize is the real gate under test.
        Mockito.lenient().when(appProperties.getSession()).thenReturn(new AppProperties.Session());
    }

    @Test
    void updateRoles_unauthenticated_is4xx() throws Exception {
        mockMvc.perform(put("/api/admin/workflow-config/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROLES_BODY)
                        .with(csrf()))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(roleRepo);
        verifyNoInteractions(workflowConfigService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void updateRoles_viewer_isForbidden() throws Exception {
        mockMvc.perform(put("/api/admin/workflow-config/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROLES_BODY)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roleRepo);
        verifyNoInteractions(workflowConfigService);
    }

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void updateRoles_teamLead_isForbidden() throws Exception {
        // Workflow config is ADMIN-only — a TEAM_LEAD (planning role) must NOT pass.
        mockMvc.perform(put("/api/admin/workflow-config/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROLES_BODY)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roleRepo);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRoles_admin_isOk() throws Exception {
        ProjectConfigurationEntity config = new ProjectConfigurationEntity();
        config.setId(1L);
        config.setName("Default");
        config.setDefault(true);
        config.setCreatedAt(OffsetDateTime.now());
        when(configRepo.findByIsDefaultTrue()).thenReturn(Optional.of(config));
        when(roleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(roleRepo.findByConfigIdOrderBySortOrderAsc(1L)).thenReturn(List.of());

        List<WorkflowRoleDto> roles = List.of(
                new WorkflowRoleDto(null, "SA", "Analyst", "#blue", 1, false));

        mockMvc.perform(put("/api/admin/workflow-config/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roles))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(roleRepo).deleteByConfigId(1L);
        verify(workflowConfigService).clearCache();
    }
}

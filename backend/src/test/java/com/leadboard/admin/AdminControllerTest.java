package com.leadboard.admin;

import com.leadboard.auth.AppRole;
import com.leadboard.auth.SessionRepository;
import com.leadboard.auth.UserEntity;
import com.leadboard.auth.UserRepository;
import com.leadboard.chat.embedding.EmbeddingService;
import com.leadboard.config.AppProperties;
import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.ObservabilityMetrics;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamMemberRepository;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers the F84 code-review fix adding {@code @NotBlank} to {@link AdminController.UpdateRoleRequest#role()}.
 * Previously a missing/null {@code role} reached {@code AppRole.valueOf(null)}, which throws
 * {@code NullPointerException} (falling through to the generic 500 handler) instead of a clean 400.
 */
@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;
    @MockBean
    private TenantUserRepository tenantUserRepository;
    @MockBean
    private JiraIssueRepository jiraIssueRepository;
    @MockBean
    private TeamMemberRepository teamMemberRepository;
    @MockBean
    private WorkflowConfigService workflowConfigService;
    @MockBean
    private JiraClient jiraClient;
    @MockBean
    private EmbeddingService embeddingService;

    @MockBean
    private SessionRepository sessionRepository;
    @MockBean
    private AppProperties appProperties;
    @MockBean
    private JiraConfigResolver jiraConfigResolver;
    @MockBean
    private TenantRepository tenantRepository;
    @MockBean
    private ObservabilityMetrics observabilityMetrics;

    @Test
    void updateUserRole_missingRole_isBadRequest() throws Exception {
        mockMvc.perform(patch("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userRepository);
    }

    @Test
    void updateUserRole_blankRole_isBadRequest() throws Exception {
        mockMvc.perform(patch("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userRepository);
    }

    @Test
    void updateUserRole_validRole_updatesUser() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(2L);
        user.setAtlassianAccountId("acc-2");
        user.setAppRole(AppRole.MEMBER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        mockMvc.perform(patch("/api/admin/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEAM_LEAD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEAM_LEAD"));

        verify(userRepository).save(user);
    }
}

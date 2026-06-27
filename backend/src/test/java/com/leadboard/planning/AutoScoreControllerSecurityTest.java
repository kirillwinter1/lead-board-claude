package com.leadboard.planning;

import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for {@link AutoScoreController} mutating endpoints
 * (LAUNCH_PLAN L6 RBAC gap — representative "planning ops" controller).
 *
 * <p>Filters are enabled so method-level {@code @PreAuthorize} is enforced.
 * Recalculate operations require ADMIN / PROJECT_MANAGER / TEAM_LEAD; the
 * global recalculate is ADMIN-only. A VIEWER must be rejected before the
 * service is touched.</p>
 */
@WebMvcTest(AutoScoreController.class)
@AutoConfigureMockMvc
@Import(AutoScoreControllerSecurityTest.MethodSecurityConfig.class)
class AutoScoreControllerSecurityTest {

    /** Forces @PreAuthorize advice in this slice so role checks are exercised. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AutoScoreService autoScoreService;

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

    @BeforeEach
    void setUp() {
        // LeadBoardAuthenticationFilter reads the session cookie name; give it a
        // real Session so the (cookie-less) request leaves the @WithMockUser
        // SecurityContext intact and @PreAuthorize is the real gate under test.
        Mockito.lenient().when(appProperties.getSession()).thenReturn(new AppProperties.Session());
    }

    @Test
    void recalculateForTeam_unauthenticated_is4xx() throws Exception {
        mockMvc.perform(post("/api/planning/autoscore/teams/1/recalculate").with(csrf()))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(autoScoreService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void recalculateForTeam_viewer_isForbidden() throws Exception {
        mockMvc.perform(post("/api/planning/autoscore/teams/1/recalculate").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(autoScoreService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void recalculateAll_viewer_isForbidden() throws Exception {
        mockMvc.perform(post("/api/planning/autoscore/recalculate").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(autoScoreService);
    }

    @Test
    @WithMockUser(roles = "PROJECT_MANAGER")
    void recalculateAll_projectManager_isForbidden() throws Exception {
        // Global recalculate is ADMIN-only; a PM (planning role) must NOT pass.
        mockMvc.perform(post("/api/planning/autoscore/recalculate").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(autoScoreService);
    }

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void recalculateForTeam_teamLead_isOk() throws Exception {
        when(autoScoreService.recalculateForTeam(1L)).thenReturn(3);

        mockMvc.perform(post("/api/planning/autoscore/teams/1/recalculate").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recalculateAll_admin_isOk() throws Exception {
        when(autoScoreService.recalculateAll()).thenReturn(5);

        mockMvc.perform(post("/api/planning/autoscore/recalculate").with(csrf()))
                .andExpect(status().isOk());
    }
}

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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for {@link QuarterlyPlanningController} mutating endpoints.
 *
 * <p>Filters are enabled so method-level {@code @PreAuthorize} is enforced. The
 * F69 kanban board is primarily driven by TEAM_LEAD, so the epic-level mutations
 * it publishes (assign-to-quarter, boost) must allow ADMIN / PROJECT_MANAGER /
 * TEAM_LEAD. The F70 project-level desired-quarter remains a PM decision
 * (ADMIN / PROJECT_MANAGER only). Under-privileged and anonymous requests are
 * rejected before the service is touched.</p>
 */
@WebMvcTest(QuarterlyPlanningController.class)
@AutoConfigureMockMvc
@Import(QuarterlyPlanningControllerSecurityTest.MethodSecurityConfig.class)
class QuarterlyPlanningControllerSecurityTest {

    /** Forces @PreAuthorize advice in this slice so role checks are exercised. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuarterlyPlanningService planningService;

    @MockBean
    private com.leadboard.auth.AuthorizationService authorizationService;

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
    void assignEpicToQuarter_unauthenticated_is4xx() throws Exception {
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/quarter").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(planningService);
    }

    @Test
    void setEpicBoost_unauthenticated_is4xx() throws Exception {
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/boost").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boost\":10}"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(planningService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void assignEpicToQuarter_wrongRole_isForbidden() throws Exception {
        // VIEWER lacks ADMIN/PROJECT_MANAGER/TEAM_LEAD → @PreAuthorize denies with 403.
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/quarter").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planningService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void setEpicBoost_wrongRole_isForbidden() throws Exception {
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/boost").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boost\":10}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planningService);
    }

    // ==================== TEAM_LEAD on the F69 kanban board ====================
    // The team-lead view is the primary consumer of the planning board, so the
    // epic-level mutations it publishes (assign-to-quarter, boost) must be allowed
    // for TEAM_LEAD — not only ADMIN/PROJECT_MANAGER. The service is mocked, so
    // ok() with a null body (200) confirms the request passed authorization and
    // reached the controller.

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void assignEpicToQuarter_teamLead_isAllowed() throws Exception {
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/quarter").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void setEpicBoost_teamLead_isAllowed() throws Exception {
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/boost").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boost\":10}"))
                .andExpect(status().isOk());
    }

    // ==================== F70: desired-quarter (PM-level) ====================

    @Test
    void setProjectDesiredQuarter_unauthenticated_is4xx() throws Exception {
        // Anonymous → @PreAuthorize("isAuthenticated()") on the class denies the call.
        mockMvc.perform(post("/api/quarterly-planning/projects/PROJ-1/desired-quarter").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(planningService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void setProjectDesiredQuarter_wrongRole_isForbidden() throws Exception {
        // VIEWER lacks ADMIN/PROJECT_MANAGER → @PreAuthorize denies with 403.
        mockMvc.perform(post("/api/quarterly-planning/projects/PROJ-1/desired-quarter").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planningService);
    }

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void setProjectDesiredQuarter_teamLead_isForbidden() throws Exception {
        // desired-quarter is a PM-level decision (F70) — deliberately NOT broadened
        // to TEAM_LEAD even though the epic-level kanban mutations are.
        mockMvc.perform(post("/api/quarterly-planning/projects/PROJ-1/desired-quarter").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planningService);
    }
}

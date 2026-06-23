package com.leadboard.planning;

import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for {@link QuarterlyPlanningController} mutating endpoints.
 *
 * <p>Unlike {@link QuarterlyPlanningControllerTest} this test does NOT disable
 * security filters — it asserts that unauthenticated and under-privileged
 * requests are rejected with 4xx and never reach the service layer.</p>
 */
@WebMvcTest(QuarterlyPlanningController.class)
@AutoConfigureMockMvc
class QuarterlyPlanningControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuarterlyPlanningService planningService;

    @MockBean
    private com.leadboard.auth.AuthorizationService authorizationService;

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

    @Test
    void assignEpicToQuarter_unauthenticated_is4xx() throws Exception {
        // No @WithMockUser → request is anonymous. Both 401 (no auth) and 403
        // (auth-required preauthorize) are valid Spring outcomes here; we only
        // care that the call is rejected and the service is never invoked.
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(planningService);
    }

    @Test
    void setEpicBoost_unauthenticated_is4xx() throws Exception {
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/boost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boost\":10}"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(planningService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void assignEpicToQuarter_wrongRole_isForbidden() throws Exception {
        // VIEWER lacks ADMIN/PROJECT_MANAGER → @PreAuthorize denies with 403.
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planningService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void setEpicBoost_wrongRole_isForbidden() throws Exception {
        mockMvc.perform(post("/api/quarterly-planning/epics/EPIC-1/boost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boost\":10}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planningService);
    }

    // ==================== F70: desired-quarter ====================

    @Test
    void setProjectDesiredQuarter_unauthenticated_is4xx() throws Exception {
        // Anonymous → @PreAuthorize("isAuthenticated()") on the class denies the call.
        mockMvc.perform(post("/api/quarterly-planning/projects/PROJ-1/desired-quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(planningService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void setProjectDesiredQuarter_wrongRole_isForbidden() throws Exception {
        // VIEWER lacks ADMIN/PROJECT_MANAGER → @PreAuthorize denies with 403.
        mockMvc.perform(post("/api/quarterly-planning/projects/PROJ-1/desired-quarter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quarter\":\"2026Q2\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planningService);
    }
}

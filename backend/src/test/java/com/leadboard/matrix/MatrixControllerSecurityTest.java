package com.leadboard.matrix;

import com.leadboard.auth.AuthorizationService;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for {@link MatrixController} (F77).
 *
 * <p>Filters are enabled so method-level {@code @PreAuthorize} is enforced:
 * the GET is gated by {@code @authorizationService.canManageTeam}; triage requires
 * ADMIN / PROJECT_MANAGER / TEAM_LEAD. Under-privileged and anonymous requests must
 * be rejected before the service is touched.</p>
 */
@WebMvcTest(MatrixController.class)
@AutoConfigureMockMvc
@Import(MatrixControllerSecurityTest.MethodSecurityConfig.class)
class MatrixControllerSecurityTest {

    /** Forces @PreAuthorize advice in this slice so role checks are exercised. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
        /**
         * Context-aware expression handler so the GET endpoint's
         * {@code @authorizationService.canManageTeam(...)} bean reference resolves
         * inside the WebMvcTest slice (the default slice handler has no BeanResolver).
         */
        @Bean
        @Primary
        static MethodSecurityExpressionHandler methodSecurityExpressionHandler(ApplicationContext ctx) {
            DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
            handler.setApplicationContext(ctx);
            return handler;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatrixService matrixService;

    // Pin the bean name so the GET endpoint's @PreAuthorize SpEL bean reference
    // (@authorizationService) resolves; without an existing definition in this slice
    // @MockBean would otherwise register under a generated name.
    @MockBean(name = "authorizationService")
    private AuthorizationService authorizationService;

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
        Mockito.lenient().when(appProperties.getSession()).thenReturn(new AppProperties.Session());
    }

    private static final String TRIAGE_BODY = "{\"issueKey\":\"PROJ-1\",\"quadrant\":\"P1\"}";

    // ==================== GET /api/matrix ====================

    @Test
    void getMatrix_unauthenticated_is4xx() throws Exception {
        // canManageTeam mock returns false → @PreAuthorize denies.
        mockMvc.perform(get("/api/matrix").param("teamId", "1"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(matrixService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getMatrix_cannotManageTeam_isForbidden() throws Exception {
        when(authorizationService.canManageTeam(1L)).thenReturn(false);

        mockMvc.perform(get("/api/matrix").param("teamId", "1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(matrixService);
    }

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void getMatrix_canManageTeam_isOk() throws Exception {
        when(authorizationService.canManageTeam(1L)).thenReturn(true);
        when(matrixService.getMatrix(1L))
                .thenReturn(new MatrixViewDto(List.of(), List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/matrix").param("teamId", "1"))
                .andExpect(status().isOk());
    }

    // ==================== PUT /api/matrix/triage ====================

    @Test
    void triage_unauthenticated_is4xx() throws Exception {
        mockMvc.perform(put("/api/matrix/triage").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRIAGE_BODY))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(matrixService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void triage_viewer_isForbidden() throws Exception {
        mockMvc.perform(put("/api/matrix/triage").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRIAGE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(matrixService);
    }

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void triage_teamLead_isOk() throws Exception {
        when(matrixService.triage("PROJ-1", "P1"))
                .thenReturn(new MatrixCardDto("PROJ-1", "S", "Task", null, null, null, "To Do", "P1"));

        mockMvc.perform(put("/api/matrix/triage").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRIAGE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void triage_admin_isOk() throws Exception {
        when(matrixService.triage("PROJ-1", "P1"))
                .thenReturn(new MatrixCardDto("PROJ-1", "S", "Task", null, null, null, "To Do", "P1"));

        mockMvc.perform(put("/api/matrix/triage").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRIAGE_BODY))
                .andExpect(status().isOk());
    }
}

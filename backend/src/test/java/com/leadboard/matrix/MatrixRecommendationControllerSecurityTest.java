package com.leadboard.matrix;

import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.matrix.RecommendationDtos.RecommendationViewDto;
import com.leadboard.matrix.RecommendationDtos.ZeroBugPolicy;
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
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for {@link MatrixRecommendationController} (F78).
 *
 * <p>The recommendations GET is gated by {@code @authorizationService.canManageTeam}
 * (same gate as the matrix GET). Managers are allowed; under-privileged users are
 * rejected with 403.</p>
 */
@WebMvcTest(MatrixRecommendationController.class)
@AutoConfigureMockMvc
@Import(MatrixRecommendationControllerSecurityTest.MethodSecurityConfig.class)
class MatrixRecommendationControllerSecurityTest {

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
    private MatrixRecommendationService recommendationService;

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

    @Test
    @WithMockUser(roles = "TEAM_LEAD")
    void recommendations_allowedForManager() throws Exception {
        when(authorizationService.canManageTeam(anyLong())).thenReturn(true);
        when(recommendationService.getRecommendations(7L))
                .thenReturn(new RecommendationViewDto(new ZeroBugPolicy(0, List.of()), List.of()));

        mockMvc.perform(get("/api/matrix/recommendations").param("teamId", "7"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void recommendations_forbiddenForViewer() throws Exception {
        when(authorizationService.canManageTeam(anyLong())).thenReturn(false);

        mockMvc.perform(get("/api/matrix/recommendations").param("teamId", "7"))
                .andExpect(status().isForbidden());
    }
}

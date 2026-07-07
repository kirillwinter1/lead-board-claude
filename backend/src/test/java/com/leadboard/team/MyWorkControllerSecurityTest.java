package com.leadboard.team;

import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import com.leadboard.team.dto.MyWorkResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for {@link MyWorkController} (F88 "My Work").
 *
 * <p>Unlike {@link MyWorkControllerTest} this test does NOT disable the security
 * filters, so the class-level {@code @PreAuthorize("isAuthenticated()")} is
 * actually enforced. It proves anonymous callers are rejected before the
 * service layer is touched, while any authenticated user succeeds.</p>
 */
@WebMvcTest(MyWorkController.class)
@AutoConfigureMockMvc
@Import(MyWorkControllerSecurityTest.MethodSecurityConfig.class)
class MyWorkControllerSecurityTest {

    /** Forces @PreAuthorize advice in this slice so the isAuthenticated() check is exercised. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MyWorkService myWorkService;

    @MockBean
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
        // LeadBoardAuthenticationFilter reads the session cookie name; give it a
        // real Session so the (cookie-less) request leaves the @WithMockUser
        // SecurityContext intact and @PreAuthorize is the real gate under test.
        Mockito.lenient().when(appProperties.getSession()).thenReturn(new AppProperties.Session());
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        mockMvc.perform(get("/api/me/work").param("from", "2026-04-01").param("to", "2026-07-01"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(myWorkService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void authenticatedUserIsAllowed() throws Exception {
        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getAtlassianAccountId()).thenReturn("acc-1");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        MyWorkResponse empty = new MyWorkResponse(false, null, java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), null);
        when(myWorkService.getMyWork(anyString(), any(), any(), any())).thenReturn(empty);

        mockMvc.perform(get("/api/me/work").param("from", "2026-04-01").param("to", "2026-07-01"))
                .andExpect(status().isOk());
    }
}

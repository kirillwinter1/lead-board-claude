package com.leadboard.auth;

import com.leadboard.config.AppProperties;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantService;
import com.leadboard.tenant.TenantUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SECURITY_AUDIT.md §7 — OAuth login CSRF: {@code state} must be bound to the browser that
 * started the flow via a short-lived HttpOnly cookie, on top of the pre-existing server-side
 * (exists/not-expired) check in {@link OAuthService}. These tests exercise the controller-level
 * cookie binding: /authorize sets it, /callback requires it to match the query {@code state}.
 */
@WebMvcTest(OAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class OAuthControllerTest {

    private static final String STATE_COOKIE_NAME = "oauth_state";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuthService oauthService;

    @MockBean
    private AppProperties appProperties;

    @MockBean
    private TenantService tenantService;

    // Not used directly by OAuthController — required because @WebMvcTest also constructs
    // LeadBoardAuthenticationFilter (a registered Filter bean picked up by the web slice even
    // with addFilters=false, which only skips *registering* filters in MockMvc, not building
    // them as beans).
    @MockBean
    private SessionRepository sessionRepository;

    @MockBean
    private TenantUserRepository tenantUserRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private com.leadboard.config.ObservabilityMetrics observabilityMetrics;

    @BeforeEach
    void setUp() {
        AppProperties.Session sessionProps = new AppProperties.Session();
        sessionProps.setCookieSecure(true);
        when(appProperties.getSession()).thenReturn(sessionProps);
        when(appProperties.getFrontendUrl()).thenReturn("https://app.example.com");

        // Defensive: TenantContext is a static ThreadLocal — don't let another test class
        // leak a tenant into resolveTenantId()'s fallback lookup.
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("GET /oauth/atlassian/authorize")
    class AuthorizeTests {

        @Test
        @DisplayName("sets an HttpOnly state cookie whose value matches the state in the redirect URL")
        void setsStateCookieMatchingRedirectState() throws Exception {
            when(oauthService.createAuthorizationRequest(any()))
                    .thenReturn(new OAuthService.AuthorizationRequest(
                            "https://auth.atlassian.com/authorize?state=abc-123", "abc-123"));

            var result = mockMvc.perform(get("/oauth/atlassian/authorize"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("https://auth.atlassian.com/authorize?state=abc-123"))
                    .andReturn();

            jakarta.servlet.http.Cookie cookie = result.getResponse().getCookie(STATE_COOKIE_NAME);
            assertNotNull(cookie, "authorize must set a state-binding cookie");
            assertEquals("abc-123", cookie.getValue());
            assertTrue(cookie.isHttpOnly(), "state cookie must be HttpOnly");
            assertTrue(cookie.getSecure(), "state cookie Secure must follow the session cookie config");
        }
    }

    @Nested
    @DisplayName("GET /oauth/atlassian/callback")
    class CallbackTests {

        @Test
        @DisplayName("rejects callback when no state cookie was sent (attacker-initiated flow / login CSRF)")
        void rejectsMissingStateCookie() throws Exception {
            var result = mockMvc.perform(get("/oauth/atlassian/callback")
                            .param("code", "auth-code")
                            .param("state", "attacker-state"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            String redirectedUrl = result.getResponse().getRedirectedUrl();
            assertNotNull(redirectedUrl);
            assertTrue(redirectedUrl.contains("auth=error"));
            assertTrue(redirectedUrl.contains("reason=state_mismatch"));
            verify(oauthService, never()).handleCallback(anyString(), anyString());
        }

        @Test
        @DisplayName("rejects callback when the state cookie does not match the query state (login CSRF)")
        void rejectsMismatchedStateCookie() throws Exception {
            var result = mockMvc.perform(get("/oauth/atlassian/callback")
                            .param("code", "auth-code")
                            .param("state", "attacker-state")
                            .cookie(new MockCookie(STATE_COOKIE_NAME, "victim-state")))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            String redirectedUrl = result.getResponse().getRedirectedUrl();
            assertNotNull(redirectedUrl);
            assertTrue(redirectedUrl.contains("auth=error"));
            assertTrue(redirectedUrl.contains("reason=state_mismatch"));
            verify(oauthService, never()).handleCallback(anyString(), anyString());
        }

        @Test
        @DisplayName("proceeds to handleCallback and clears the cookie when it matches the query state")
        void proceedsWhenStateCookieMatches() throws Exception {
            when(oauthService.handleCallback("auth-code", "matching-state"))
                    .thenReturn(OAuthService.CallbackResult.success("session-id-1", null));

            var result = mockMvc.perform(get("/oauth/atlassian/callback")
                            .param("code", "auth-code")
                            .param("state", "matching-state")
                            .cookie(new MockCookie(STATE_COOKIE_NAME, "matching-state")))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            String redirectedUrl = result.getResponse().getRedirectedUrl();
            assertNotNull(redirectedUrl);
            assertTrue(redirectedUrl.contains("auth=success"));
            verify(oauthService).handleCallback("auth-code", "matching-state");

            jakarta.servlet.http.Cookie clearedCookie = result.getResponse().getCookie(STATE_COOKIE_NAME);
            assertNotNull(clearedCookie, "the one-time-use state cookie must be cleared after the callback");
            assertEquals(0, clearedCookie.getMaxAge());
        }

        @Test
        @DisplayName("still consumes/clears the state cookie even when the underlying handleCallback fails")
        void clearsStateCookieOnHandleCallbackFailure() throws Exception {
            when(oauthService.handleCallback("auth-code", "matching-state"))
                    .thenReturn(OAuthService.CallbackResult.failure("boom", null, null));

            var result = mockMvc.perform(get("/oauth/atlassian/callback")
                            .param("code", "auth-code")
                            .param("state", "matching-state")
                            .cookie(new MockCookie(STATE_COOKIE_NAME, "matching-state")))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            String redirectedUrl = result.getResponse().getRedirectedUrl();
            assertNotNull(redirectedUrl);
            assertTrue(redirectedUrl.contains("auth=error"));

            jakarta.servlet.http.Cookie clearedCookie = result.getResponse().getCookie(STATE_COOKIE_NAME);
            assertNotNull(clearedCookie);
            assertEquals(0, clearedCookie.getMaxAge());
        }
    }
}

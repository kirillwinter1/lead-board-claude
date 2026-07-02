package com.leadboard.auth;

import com.leadboard.config.AppProperties;
import com.leadboard.config.AtlassianOAuthProperties;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantJiraConfigEntity;
import com.leadboard.tenant.TenantJiraConfigRepository;
import com.leadboard.tenant.TenantService;
import com.leadboard.tenant.TenantUserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthServiceTest {

    @Mock
    private AtlassianOAuthProperties oauthProperties;

    @Mock
    private AppProperties appProperties;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthTokenRepository tokenRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private TenantService tenantService;

    @Mock
    private TenantJiraConfigRepository tenantJiraConfigRepository;

    private OAuthService oAuthService;

    @BeforeEach
    void setUp() {
        oAuthService = new OAuthService(oauthProperties, appProperties, userRepository, tokenRepository, sessionRepository,
                tenantService, tenantJiraConfigRepository);

        // Setup default properties
        when(oauthProperties.getAuthorizationUri()).thenReturn("https://auth.atlassian.com/authorize");
        when(oauthProperties.getClientId()).thenReturn("test-client-id");
        when(oauthProperties.getScopes()).thenReturn("read:jira-user read:jira-work");
        when(oauthProperties.getRedirectUri()).thenReturn("http://localhost:8080/oauth/callback");

        AppProperties.Session sessionProps = new AppProperties.Session();
        when(appProperties.getSession()).thenReturn(sessionProps);

        SecurityContextHolder.clearContext();
        // Defensive: TenantContext is a static ThreadLocal — make sure no tenant
        // leaks in from a previous test before we start (single-tenant mode by default).
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        // Defensive: never let a tenant set in one test leak into the next test
        // (or another test class) running on the same thread.
        TenantContext.clear();
    }

    // ==================== getAuthorizationUrl() Tests ====================

    @Nested
    @DisplayName("getAuthorizationUrl()")
    class GetAuthorizationUrlTests {

        @Test
        @DisplayName("should generate authorization URL with required parameters")
        void shouldGenerateAuthorizationUrl() {
            String url = oAuthService.getAuthorizationUrl(null);

            assertNotNull(url);
            assertTrue(url.contains("https://auth.atlassian.com/authorize"));
            assertTrue(url.contains("client_id=test-client-id"));
            assertTrue(url.contains("redirect_uri="));
            assertTrue(url.contains("response_type=code"));
            assertTrue(url.contains("state="));
        }

        @Test
        @DisplayName("should include audience parameter")
        void shouldIncludeAudience() {
            String url = oAuthService.getAuthorizationUrl(null);

            assertTrue(url.contains("audience=api.atlassian.com"));
        }

        @Test
        @DisplayName("should include prompt consent")
        void shouldIncludePromptConsent() {
            String url = oAuthService.getAuthorizationUrl(null);

            assertTrue(url.contains("prompt=consent"));
        }

        @Test
        @DisplayName("should generate unique states for concurrent flows")
        void shouldGenerateUniqueStates() {
            String url1 = oAuthService.getAuthorizationUrl(null);
            String url2 = oAuthService.getAuthorizationUrl(null);

            String state1 = extractParam(url1, "state");
            String state2 = extractParam(url2, "state");

            assertNotEquals(state1, state2);
        }

        private String extractParam(String url, String param) {
            int start = url.indexOf(param + "=") + param.length() + 1;
            int end = url.indexOf("&", start);
            return end > 0 ? url.substring(start, end) : url.substring(start);
        }
    }

    // ==================== handleCallback() Tests ====================

    @Nested
    @DisplayName("handleCallback()")
    class HandleCallbackTests {

        @Test
        @DisplayName("should reject invalid state")
        void shouldRejectInvalidState() {
            oAuthService.getAuthorizationUrl(null);

            OAuthService.CallbackResult result = oAuthService.handleCallback("code", "wrong-state");

            assertFalse(result.success());
            assertEquals("Invalid state parameter", result.error());
        }

        @Test
        @DisplayName("should reject when no state generated")
        void shouldRejectWhenNoState() {
            OAuthService.CallbackResult result = oAuthService.handleCallback("code", "any-state");

            assertFalse(result.success());
            assertEquals("Invalid state parameter", result.error());
        }
    }

    // ==================== applyMembershipGate() Tests (F82) ====================
    // Exercises the tenant-membership-vs-Jira-access gating logic directly (package-private
    // method), bypassing the real network calls in handleCallback() (exchangeCodeForTokens /
    // getUserInfo / accessible-resources all use a real WebClient with no test seam — see the
    // other handleCallback() tests above, which only cover the pre-network "invalid state" path).

    @Nested
    @DisplayName("applyMembershipGate() — F82 Jira access membership")
    class ApplyMembershipGateTests {

        private static final String TENANT_CLOUD_ID = "cloud-tenant-1";

        private TenantEntity tenant;
        private UserEntity user;

        @BeforeEach
        void setUpTenantAndUser() {
            tenant = new TenantEntity();
            tenant.setId(1L);
            tenant.setSlug("acme");
            tenant.setSchemaName("tenant_acme");

            user = new UserEntity();
            user.setId(42L);
            user.setAtlassianAccountId("acc-42");
            user.setEmail("user@acme.test");
        }

        private void mockTenantCloudId(String cloudId) {
            TenantJiraConfigEntity config = new TenantJiraConfigEntity();
            config.setJiraCloudId(cloudId);
            when(tenantJiraConfigRepository.findActive()).thenReturn(Optional.of(config));
        }

        @Test
        @DisplayName("bootstrap: first user of a brand-new tenant becomes ADMIN, no Jira check")
        void shouldBootstrapFirstUserAsAdminWithoutJiraCheck() {
            when(tenantService.findTenantUser(1L, 42L)).thenReturn(Optional.empty());
            when(tenantService.tenantHasUsers(1L)).thenReturn(false);

            OAuthService.MembershipGateResult result = oAuthService.applyMembershipGate(tenant, user, List.of());

            assertTrue(result.allowed());
            assertNull(result.errorCode());
            verify(tenantService).addUserToTenant(tenant, user, AppRole.ADMIN);
            verifyNoInteractions(tenantJiraConfigRepository);
        }

        @Test
        @DisplayName("join allowed: tenant's cloudId is in the user's accessible sites")
        void shouldAllowJoinWhenCloudIdMatches() {
            when(tenantService.findTenantUser(1L, 42L)).thenReturn(Optional.empty());
            when(tenantService.tenantHasUsers(1L)).thenReturn(true);
            mockTenantCloudId(TENANT_CLOUD_ID);

            OAuthService.MembershipGateResult result =
                    oAuthService.applyMembershipGate(tenant, user, List.of("other-cloud", TENANT_CLOUD_ID));

            assertTrue(result.allowed());
            verify(tenantService).addUserToTenant(tenant, user, AppRole.MEMBER);
        }

        @Test
        @DisplayName("join denied: tenant's cloudId is NOT in the user's accessible sites")
        void shouldDenyJoinWhenCloudIdDoesNotMatch() {
            when(tenantService.findTenantUser(1L, 42L)).thenReturn(Optional.empty());
            when(tenantService.tenantHasUsers(1L)).thenReturn(true);
            mockTenantCloudId(TENANT_CLOUD_ID);

            OAuthService.MembershipGateResult result =
                    oAuthService.applyMembershipGate(tenant, user, List.of("some-other-cloud"));

            assertFalse(result.allowed());
            assertEquals("jira_access_denied", result.errorCode());
            verify(tenantService, never()).addUserToTenant(any(), any(), any());
        }

        @Test
        @DisplayName("join denied: tenant has no configured Jira cloudId (auto-join closed)")
        void shouldDenyJoinWhenTenantHasNoCloudId() {
            when(tenantService.findTenantUser(1L, 42L)).thenReturn(Optional.empty());
            when(tenantService.tenantHasUsers(1L)).thenReturn(true);
            when(tenantJiraConfigRepository.findActive()).thenReturn(Optional.empty());

            OAuthService.MembershipGateResult result =
                    oAuthService.applyMembershipGate(tenant, user, List.of(TENANT_CLOUD_ID));

            assertFalse(result.allowed());
            assertEquals("jira_access_denied", result.errorCode());
            verify(tenantService, never()).addUserToTenant(any(), any(), any());
        }

        @Test
        @DisplayName("existing active member who lost Jira access is deactivated, login still allowed")
        void shouldDeactivateExistingMemberWhoLostAccess() {
            TenantUserEntity membership = new TenantUserEntity();
            membership.setActive(true);
            when(tenantService.findTenantUser(1L, 42L)).thenReturn(Optional.of(membership));
            mockTenantCloudId(TENANT_CLOUD_ID);

            OAuthService.MembershipGateResult result =
                    oAuthService.applyMembershipGate(tenant, user, List.of("some-other-cloud"));

            assertTrue(result.allowed(), "login flow proceeds; enforcement happens in the auth filter");
            verify(tenantService).deactivateMembership(membership, "jira_access_lost");
            verify(tenantService, never()).reactivateMembership(any());
        }

        @Test
        @DisplayName("existing deactivated member who regained access is reactivated")
        void shouldReactivateExistingMemberWhoRegainedAccess() {
            TenantUserEntity membership = new TenantUserEntity();
            membership.deactivate("jira_access_lost");
            when(tenantService.findTenantUser(1L, 42L)).thenReturn(Optional.of(membership));
            mockTenantCloudId(TENANT_CLOUD_ID);

            OAuthService.MembershipGateResult result =
                    oAuthService.applyMembershipGate(tenant, user, List.of(TENANT_CLOUD_ID));

            assertTrue(result.allowed());
            verify(tenantService).reactivateMembership(membership);
            verify(tenantService, never()).deactivateMembership(any(), any());
        }

        @Test
        @DisplayName("existing active member who still has access — no deactivate/reactivate")
        void shouldNotTouchActiveMemberWithAccess() {
            TenantUserEntity membership = new TenantUserEntity();
            membership.setActive(true);
            when(tenantService.findTenantUser(1L, 42L)).thenReturn(Optional.of(membership));
            mockTenantCloudId(TENANT_CLOUD_ID);

            OAuthService.MembershipGateResult result =
                    oAuthService.applyMembershipGate(tenant, user, List.of(TENANT_CLOUD_ID));

            assertTrue(result.allowed());
            verify(tenantService, never()).deactivateMembership(any(), any());
            verify(tenantService, never()).reactivateMembership(any());
        }

        @Test
        @DisplayName("existing member: tenant has no cloudId — cannot verify, membership untouched")
        void shouldNotTouchExistingMemberWhenTenantHasNoCloudId() {
            TenantUserEntity membership = new TenantUserEntity();
            membership.setActive(true);
            when(tenantService.findTenantUser(1L, 42L)).thenReturn(Optional.of(membership));
            when(tenantJiraConfigRepository.findActive()).thenReturn(Optional.empty());

            OAuthService.MembershipGateResult result =
                    oAuthService.applyMembershipGate(tenant, user, List.of(TENANT_CLOUD_ID));

            assertTrue(result.allowed());
            verify(tenantService, never()).deactivateMembership(any(), any());
            verify(tenantService, never()).reactivateMembership(any());
        }

        @Test
        @DisplayName("resolveTenantJiraCloudId restores TenantContext after switching to resolve the tenant's config")
        void shouldRestoreTenantContextAfterResolvingCloudId() {
            assertFalse(TenantContext.hasTenant());
            mockTenantCloudId(TENANT_CLOUD_ID);

            String cloudId = oAuthService.resolveTenantJiraCloudId(tenant);

            assertEquals(TENANT_CLOUD_ID, cloudId);
            assertFalse(TenantContext.hasTenant(), "must restore the (absent) tenant context after the lookup");
        }
    }

    // ==================== getValidAccessToken() Tests ====================

    @Nested
    @DisplayName("getValidAccessToken()")
    class GetValidAccessTokenTests {

        @Test
        @DisplayName("should return null when no token exists")
        void shouldReturnNullWhenNoToken() {
            when(tokenRepository.findLatestToken()).thenReturn(Optional.empty());

            String token = oAuthService.getValidAccessToken();

            assertNull(token);
        }

        @Test
        @DisplayName("should return access token when valid")
        void shouldReturnValidToken() {
            OAuthTokenEntity tokenEntity = createValidToken();

            when(tokenRepository.findLatestToken()).thenReturn(Optional.of(tokenEntity));

            String token = oAuthService.getValidAccessToken();

            assertEquals("valid-access-token", token);
        }

        @Test
        @DisplayName("single-tenant mode (no TenantContext) should use the global lookup, never the tenant-scoped one")
        void shouldUseGlobalLookupWhenNoTenantContext() {
            assertFalse(TenantContext.hasTenant());
            when(tokenRepository.findLatestToken()).thenReturn(Optional.of(createValidToken()));

            String token = oAuthService.getValidAccessToken();

            assertEquals("valid-access-token", token);
            verify(tokenRepository, never()).findLatestTokenForTenant(any());
        }

        @Test
        @DisplayName("multi-tenant mode should resolve the token scoped to the current tenant, not the global-latest one")
        void shouldResolveTenantScopedTokenWhenTenantContextSet() {
            TenantContext.setTenant(42L, "tenant_a");
            OAuthTokenEntity tenantToken = createValidToken();
            tenantToken.setAccessToken("tenant-42-token");
            when(tokenRepository.findLatestTokenForTenant(42L)).thenReturn(Optional.of(tenantToken));

            String token = oAuthService.getValidAccessToken();

            assertEquals("tenant-42-token", token);
            verify(tokenRepository).findLatestTokenForTenant(42L);
            verify(tokenRepository, never()).findLatestToken();
        }

        @Test
        @DisplayName("multi-tenant mode: no token for this tenant must return null, NOT another tenant's global-latest token")
        void shouldReturnNullNotAnotherTenantsTokenWhenTenantHasNoOwnToken() {
            TenantContext.setTenant(42L, "tenant_a");
            when(tokenRepository.findLatestTokenForTenant(42L)).thenReturn(Optional.empty());

            String token = oAuthService.getValidAccessToken();

            assertNull(token, "Should return null so callers (JiraClient/JiraWriteService) fall back to per-tenant Basic Auth "
                    + "instead of accidentally reusing another tenant's OAuth token");
            verify(tokenRepository, never()).findLatestToken();
        }

        @Test
        @DisplayName("distinct tenants resolve to their own distinct tokens, never each other's")
        void shouldIsolateTokensAcrossTenants() {
            OAuthTokenEntity tokenForTenantOne = createValidToken();
            tokenForTenantOne.setAccessToken("token-tenant-one");
            OAuthTokenEntity tokenForTenantTwo = createValidToken();
            tokenForTenantTwo.setAccessToken("token-tenant-two");

            when(tokenRepository.findLatestTokenForTenant(1L)).thenReturn(Optional.of(tokenForTenantOne));
            when(tokenRepository.findLatestTokenForTenant(2L)).thenReturn(Optional.of(tokenForTenantTwo));

            TenantContext.setTenant(1L, "tenant_one");
            assertEquals("token-tenant-one", oAuthService.getValidAccessToken());

            TenantContext.setTenant(2L, "tenant_two");
            assertEquals("token-tenant-two", oAuthService.getValidAccessToken());
        }
    }

    // ==================== getCloudIdForCurrentUser() Tests ====================

    @Nested
    @DisplayName("getCloudIdForCurrentUser()")
    class GetCloudIdTests {

        @Test
        @DisplayName("should return null when no token")
        void shouldReturnNullWhenNoToken() {
            when(tokenRepository.findLatestToken()).thenReturn(Optional.empty());

            String cloudId = oAuthService.getCloudIdForCurrentUser();

            assertNull(cloudId);
        }

        @Test
        @DisplayName("should return cloud ID from token")
        void shouldReturnCloudId() {
            OAuthTokenEntity tokenEntity = createValidToken();
            tokenEntity.setCloudId("cloud-123");

            when(tokenRepository.findLatestToken()).thenReturn(Optional.of(tokenEntity));

            String cloudId = oAuthService.getCloudIdForCurrentUser();

            assertEquals("cloud-123", cloudId);
        }

        @Test
        @DisplayName("multi-tenant mode should resolve cloudId scoped to the current tenant")
        void shouldResolveTenantScopedCloudId() {
            TenantContext.setTenant(7L, "tenant_seven");
            OAuthTokenEntity tenantToken = createValidToken();
            tenantToken.setCloudId("cloud-of-tenant-7");
            when(tokenRepository.findLatestTokenForTenant(7L)).thenReturn(Optional.of(tenantToken));

            String cloudId = oAuthService.getCloudIdForCurrentUser();

            assertEquals("cloud-of-tenant-7", cloudId);
            verify(tokenRepository, never()).findLatestToken();
        }

        @Test
        @DisplayName("multi-tenant mode: no token for this tenant must return null cloudId, NOT another tenant's cloudId")
        void shouldReturnNullNotAnotherTenantsCloudId() {
            TenantContext.setTenant(7L, "tenant_seven");
            when(tokenRepository.findLatestTokenForTenant(7L)).thenReturn(Optional.empty());

            String cloudId = oAuthService.getCloudIdForCurrentUser();

            assertNull(cloudId);
            verify(tokenRepository, never()).findLatestToken();
        }
    }

    // ==================== getAuthStatus() Tests ====================

    @Nested
    @DisplayName("getAuthStatus()")
    class GetAuthStatusTests {

        @Test
        @DisplayName("should return not authenticated when no SecurityContext")
        void shouldReturnNotAuthenticated() {
            OAuthService.AuthStatus status = oAuthService.getAuthStatus();

            assertFalse(status.authenticated());
            assertNull(status.user());
        }

        @Test
        @DisplayName("should return authenticated with user info from SecurityContext")
        void shouldReturnAuthenticatedWithUser() {
            UserEntity user = new UserEntity();
            user.setId(1L);
            user.setAtlassianAccountId("account-123");
            user.setDisplayName("John Doe");
            user.setEmail("john@test.com");
            user.setAvatarUrl("https://avatar.url");

            LeadBoardAuthentication auth = new LeadBoardAuthentication(user);
            SecurityContextHolder.getContext().setAuthentication(auth);

            OAuthService.AuthStatus status = oAuthService.getAuthStatus();

            assertTrue(status.authenticated());
            assertNotNull(status.user());
            assertEquals("John Doe", status.user().displayName());
            assertEquals("john@test.com", status.user().email());
        }
    }

    // ==================== logout() Tests ====================

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("should delete session by id")
        void shouldDeleteSessionById() {
            oAuthService.logout("session-123");

            verify(sessionRepository).deleteById("session-123");
        }

        @Test
        @DisplayName("should not delete when sessionId is null")
        void shouldNotDeleteWhenSessionIdNull() {
            oAuthService.logout(null);

            verify(sessionRepository, never()).deleteById(any());
        }
    }

    // ==================== Helper Methods ====================

    private OAuthTokenEntity createValidToken() {
        OAuthTokenEntity token = new OAuthTokenEntity();
        token.setAccessToken("valid-access-token");
        token.setRefreshToken("refresh-token");
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return token;
    }
}

package com.leadboard.auth;

import com.leadboard.config.AtlassianOAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthServiceTest {

    @Mock
    private AtlassianOAuthProperties oauthProperties;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthTokenRepository tokenRepository;

    private OAuthService oAuthService;

    @BeforeEach
    void setUp() {
        oAuthService = new OAuthService(oauthProperties, userRepository, tokenRepository);

        // Setup default properties
        when(oauthProperties.getAuthorizationUri()).thenReturn("https://auth.atlassian.com/authorize");
        when(oauthProperties.getClientId()).thenReturn("test-client-id");
        when(oauthProperties.getScopes()).thenReturn("read:jira-user read:jira-work");
        when(oauthProperties.getRedirectUri()).thenReturn("http://localhost:8080/oauth/callback");
    }

    // ==================== getAuthorizationUrl() Tests ====================

    @Nested
    @DisplayName("getAuthorizationUrl()")
    class GetAuthorizationUrlTests {

        @Test
        @DisplayName("should generate authorization URL with required parameters")
        void shouldGenerateAuthorizationUrl() {
            String url = oAuthService.getAuthorizationUrl();

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
            String url = oAuthService.getAuthorizationUrl();

            assertTrue(url.contains("audience=api.atlassian.com"));
        }

        @Test
        @DisplayName("should include prompt consent")
        void shouldIncludePromptConsent() {
            String url = oAuthService.getAuthorizationUrl();

            assertTrue(url.contains("prompt=consent"));
        }
    }

    // ==================== handleCallback() Tests ====================

    @Nested
    @DisplayName("handleCallback()")
    class HandleCallbackTests {

        @Test
        @DisplayName("should reject invalid state")
        void shouldRejectInvalidState() {
            // Generate a state first
            oAuthService.getAuthorizationUrl();

            OAuthService.AuthResult result = oAuthService.handleCallback("code", "wrong-state");

            assertFalse(result.success());
            assertEquals("Invalid state parameter", result.error());
        }

        @Test
        @DisplayName("should reject when no state generated")
        void shouldRejectWhenNoState() {
            OAuthService.AuthResult result = oAuthService.handleCallback("code", "any-state");

            assertFalse(result.success());
            assertEquals("Invalid state parameter", result.error());
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
    }

    // ==================== getAuthStatus() Tests ====================

    @Nested
    @DisplayName("getAuthStatus()")
    class GetAuthStatusTests {

        @Test
        @DisplayName("should return not authenticated when no token")
        void shouldReturnNotAuthenticated() {
            when(tokenRepository.findLatestToken()).thenReturn(Optional.empty());

            OAuthService.AuthStatus status = oAuthService.getAuthStatus();

            assertFalse(status.authenticated());
            assertNull(status.user());
        }

        @Test
        @DisplayName("should return authenticated with user info")
        void shouldReturnAuthenticatedWithUser() {
            UserEntity user = new UserEntity();
            user.setId(1L);
            user.setAtlassianAccountId("account-123");
            user.setDisplayName("John Doe");
            user.setEmail("john@test.com");
            user.setAvatarUrl("https://avatar.url");

            OAuthTokenEntity tokenEntity = createValidToken();
            tokenEntity.setUser(user);

            when(tokenRepository.findLatestToken()).thenReturn(Optional.of(tokenEntity));

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
        @DisplayName("should delete all tokens")
        void shouldDeleteAllTokens() {
            oAuthService.logout();

            verify(tokenRepository).deleteAll();
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

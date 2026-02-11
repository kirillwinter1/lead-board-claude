package com.leadboard.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leadboard.config.AppProperties;
import com.leadboard.config.AtlassianOAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);

    private final AtlassianOAuthProperties oauthProperties;
    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final OAuthTokenRepository tokenRepository;
    private final SessionRepository sessionRepository;
    private final WebClient webClient;

    // Support concurrent OAuth flows (multiple users logging in simultaneously)
    private final ConcurrentHashMap<String, Instant> pendingStates = new ConcurrentHashMap<>();

    public OAuthService(AtlassianOAuthProperties oauthProperties,
                        AppProperties appProperties,
                        UserRepository userRepository,
                        OAuthTokenRepository tokenRepository,
                        SessionRepository sessionRepository) {
        this.oauthProperties = oauthProperties;
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
        this.webClient = WebClient.builder().build();
    }

    public String getAuthorizationUrl() {
        String state = UUID.randomUUID().toString();
        pendingStates.put(state, Instant.now().plusSeconds(600)); // 10 min TTL

        // Cleanup expired states
        pendingStates.entrySet().removeIf(e -> e.getValue().isBefore(Instant.now()));

        return UriComponentsBuilder.fromUriString(oauthProperties.getAuthorizationUri())
                .queryParam("audience", "api.atlassian.com")
                .queryParam("client_id", oauthProperties.getClientId())
                .queryParam("scope", oauthProperties.getScopes())
                .queryParam("redirect_uri", oauthProperties.getRedirectUri())
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .queryParam("prompt", "consent")
                .build()
                .toUriString();
    }

    @Transactional
    public CallbackResult handleCallback(String code, String state) {
        // Verify and consume state
        Instant expiry = pendingStates.remove(state);
        if (expiry == null || expiry.isBefore(Instant.now())) {
            log.warn("Invalid or expired OAuth state: {}", state);
            return new CallbackResult(false, "Invalid state parameter", null);
        }

        try {
            // Exchange code for tokens
            TokenResponse tokenResponse = exchangeCodeForTokens(code);
            if (tokenResponse == null) {
                return new CallbackResult(false, "Failed to exchange code for tokens", null);
            }

            // Get user info
            UserInfo userInfo = getUserInfo(tokenResponse.accessToken);
            if (userInfo == null) {
                return new CallbackResult(false, "Failed to get user info", null);
            }

            // Get cloud ID for the site
            String cloudId = getCloudId(tokenResponse.accessToken);

            // Save or update user
            UserEntity user = userRepository.findByAtlassianAccountId(userInfo.accountId)
                    .orElseGet(() -> {
                        UserEntity newUser = new UserEntity();
                        newUser.setAtlassianAccountId(userInfo.accountId);
                        return newUser;
                    });

            user.setEmail(userInfo.email);
            user.setDisplayName(userInfo.name);
            user.setAvatarUrl(userInfo.picture);
            userRepository.save(user);

            // Save or update token
            OAuthTokenEntity token = tokenRepository.findByUserId(user.getId())
                    .orElseGet(() -> {
                        OAuthTokenEntity newToken = new OAuthTokenEntity();
                        newToken.setUser(user);
                        return newToken;
                    });

            token.setAccessToken(tokenResponse.accessToken);
            token.setRefreshToken(tokenResponse.refreshToken);
            token.setTokenType(tokenResponse.tokenType);
            token.setScope(tokenResponse.scope);
            token.setCloudId(cloudId);

            if (tokenResponse.expiresIn != null) {
                token.setExpiresAt(OffsetDateTime.now().plusSeconds(tokenResponse.expiresIn));
            }

            tokenRepository.save(token);

            // Create session
            SessionEntity session = new SessionEntity();
            session.setId(UUID.randomUUID().toString());
            session.setUser(user);
            session.setExpiresAt(OffsetDateTime.now().plusDays(appProperties.getSession().getMaxAgeDays()));
            sessionRepository.save(session);

            log.info("OAuth successful for user: {} ({})", user.getDisplayName(), user.getEmail());

            return new CallbackResult(true, null, session.getId());

        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            return new CallbackResult(false, e.getMessage(), null);
        }
    }

    private TokenResponse exchangeCodeForTokens(String code) {
        try {
            String responseBody = webClient.post()
                    .uri(oauthProperties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                            .with("client_id", oauthProperties.getClientId())
                            .with("client_secret", oauthProperties.getClientSecret())
                            .with("code", code)
                            .with("redirect_uri", oauthProperties.getRedirectUri()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Token response received");

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(responseBody, TokenResponse.class);
        } catch (Exception e) {
            log.error("Failed to exchange code for tokens", e);
            return null;
        }
    }

    @Transactional
    public String getValidAccessToken() {
        Optional<OAuthTokenEntity> tokenOpt = tokenRepository.findLatestToken();
        if (tokenOpt.isEmpty()) {
            return null;
        }

        OAuthTokenEntity token = tokenOpt.get();

        if (token.isExpired() && token.getRefreshToken() != null) {
            log.info("Token expired, refreshing...");
            if (!refreshToken(token)) {
                return null;
            }
        }

        return token.getAccessToken();
    }

    public String getCloudIdForCurrentUser() {
        return tokenRepository.findLatestToken()
                .map(OAuthTokenEntity::getCloudId)
                .orElse(null);
    }

    private boolean refreshToken(OAuthTokenEntity token) {
        try {
            TokenResponse response = webClient.post()
                    .uri(oauthProperties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                            .with("client_id", oauthProperties.getClientId())
                            .with("client_secret", oauthProperties.getClientSecret())
                            .with("refresh_token", token.getRefreshToken()))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (response != null && response.accessToken != null) {
                token.setAccessToken(response.accessToken);
                if (response.refreshToken != null) {
                    token.setRefreshToken(response.refreshToken);
                }
                if (response.expiresIn != null) {
                    token.setExpiresAt(OffsetDateTime.now().plusSeconds(response.expiresIn));
                }
                tokenRepository.save(token);
                log.info("Token refreshed successfully");
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to refresh token", e);
        }
        return false;
    }

    private UserInfo getUserInfo(String accessToken) {
        try {
            String responseBody = webClient.get()
                    .uri(oauthProperties.getUserInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("User info response received");

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(responseBody, UserInfo.class);
        } catch (Exception e) {
            log.error("Failed to get user info", e);
            return null;
        }
    }

    private String getCloudId(String accessToken) {
        try {
            String responseBody = webClient.get()
                    .uri(oauthProperties.getAccessibleResourcesUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Accessible resources response received");

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<AccessibleResource> resources = mapper.readValue(responseBody,
                    mapper.getTypeFactory().constructCollectionType(List.class, AccessibleResource.class));

            if (resources != null && !resources.isEmpty()) {
                log.info("Found cloud ID: {}", resources.get(0).id);
                return resources.get(0).id;
            }
        } catch (Exception e) {
            log.error("Failed to get accessible resources", e);
        }
        return null;
    }

    /**
     * Get a valid access token for a specific user by their Atlassian account ID.
     * Used by simulation to act on behalf of individual users.
     */
    @Transactional
    public TokenInfo getValidAccessTokenForUser(String atlassianAccountId) {
        Optional<OAuthTokenEntity> tokenOpt = tokenRepository.findByAtlassianAccountId(atlassianAccountId);
        if (tokenOpt.isEmpty()) {
            return null;
        }

        OAuthTokenEntity token = tokenOpt.get();

        if (token.isExpired() && token.getRefreshToken() != null) {
            log.info("Token expired for user {}, refreshing...", atlassianAccountId);
            if (!refreshToken(token)) {
                return null;
            }
        }

        return new TokenInfo(token.getAccessToken(), token.getCloudId());
    }

    public record TokenInfo(String accessToken, String cloudId) {}

    public AuthStatus getAuthStatus() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof LeadBoardAuthentication leadAuth) {
            UserEntity user = leadAuth.getUser();
            return new AuthStatus(true, new AuthenticatedUser(
                    user.getId(),
                    user.getAtlassianAccountId(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getAvatarUrl(),
                    user.getAppRole().name(),
                    user.getAppRole().getPermissions()
            ));
        }
        return new AuthStatus(false, null);
    }

    @Transactional
    public void logout(String sessionId) {
        if (sessionId != null) {
            sessionRepository.deleteById(sessionId);
            log.info("Session {} deleted", sessionId);
        }
    }

    // DTOs
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenResponse {
        @JsonProperty("access_token")
        public String accessToken;

        @JsonProperty("refresh_token")
        public String refreshToken;

        @JsonProperty("token_type")
        public String tokenType;

        @JsonProperty("expires_in")
        public Long expiresIn;

        @JsonProperty("scope")
        public String scope;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserInfo {
        @JsonProperty("account_id")
        public String accountId;

        @JsonProperty("email")
        public String email;

        @JsonProperty("name")
        public String name;

        @JsonProperty("picture")
        public String picture;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccessibleResource {
        @JsonProperty("id")
        public String id;

        @JsonProperty("name")
        public String name;

        @JsonProperty("url")
        public String url;
    }

    public record CallbackResult(boolean success, String error, String sessionId) {}

    public record AuthenticatedUser(
            Long id,
            String accountId,
            String displayName,
            String email,
            String avatarUrl,
            String role,
            java.util.Set<String> permissions
    ) {}

    public record AuthStatus(boolean authenticated, AuthenticatedUser user) {}
}

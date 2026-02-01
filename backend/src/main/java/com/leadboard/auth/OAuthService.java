package com.leadboard.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leadboard.config.AtlassianOAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);

    private final AtlassianOAuthProperties oauthProperties;
    private final UserRepository userRepository;
    private final OAuthTokenRepository tokenRepository;
    private final WebClient webClient;

    // Simple state storage (in production, use Redis or database)
    private String currentState;

    public OAuthService(AtlassianOAuthProperties oauthProperties,
                        UserRepository userRepository,
                        OAuthTokenRepository tokenRepository) {
        this.oauthProperties = oauthProperties;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.webClient = WebClient.builder().build();
    }

    public String getAuthorizationUrl() {
        currentState = UUID.randomUUID().toString();

        return UriComponentsBuilder.fromUriString(oauthProperties.getAuthorizationUri())
                .queryParam("audience", "api.atlassian.com")
                .queryParam("client_id", oauthProperties.getClientId())
                .queryParam("scope", oauthProperties.getScopes())
                .queryParam("redirect_uri", oauthProperties.getRedirectUri())
                .queryParam("state", currentState)
                .queryParam("response_type", "code")
                .queryParam("prompt", "consent")
                .build()
                .toUriString();
    }

    @Transactional
    public AuthResult handleCallback(String code, String state) {
        // Verify state
        if (currentState == null || !currentState.equals(state)) {
            log.warn("Invalid OAuth state. Expected: {}, Got: {}", currentState, state);
            return new AuthResult(false, "Invalid state parameter", null);
        }
        currentState = null;

        try {
            // Exchange code for tokens
            TokenResponse tokenResponse = exchangeCodeForTokens(code);
            if (tokenResponse == null) {
                return new AuthResult(false, "Failed to exchange code for tokens", null);
            }

            // Get user info
            UserInfo userInfo = getUserInfo(tokenResponse.accessToken);
            if (userInfo == null) {
                return new AuthResult(false, "Failed to get user info", null);
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

            log.info("OAuth successful for user: {} ({})", user.getDisplayName(), user.getEmail());

            return new AuthResult(true, null, new AuthenticatedUser(
                    user.getId(),
                    user.getAtlassianAccountId(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getAvatarUrl(),
                    user.getAppRole().name(),
                    user.getAppRole().getPermissions()
            ));

        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            return new AuthResult(false, e.getMessage(), null);
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

            log.info("Token response: {}", responseBody);

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

            log.info("User info response: {}", responseBody);

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

            log.info("Accessible resources response: {}", responseBody);

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

    public AuthStatus getAuthStatus() {
        Optional<OAuthTokenEntity> tokenOpt = tokenRepository.findLatestToken();
        if (tokenOpt.isEmpty()) {
            return new AuthStatus(false, null);
        }

        OAuthTokenEntity token = tokenOpt.get();
        UserEntity user = token.getUser();

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

    @Transactional
    public void logout() {
        tokenRepository.deleteAll();
        log.info("User logged out, all tokens deleted");
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

    public record AuthResult(boolean success, String error, AuthenticatedUser user) {}

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

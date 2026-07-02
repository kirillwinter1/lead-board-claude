package com.leadboard.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leadboard.config.AppProperties;
import com.leadboard.config.AtlassianOAuthProperties;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantJiraConfigEntity;
import com.leadboard.tenant.TenantJiraConfigRepository;
import com.leadboard.tenant.TenantService;
import com.leadboard.tenant.TenantUserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import io.netty.resolver.DefaultAddressResolverGroup;
import reactor.netty.http.client.HttpClient;

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
    private final TenantService tenantService;
    private final TenantJiraConfigRepository tenantJiraConfigRepository;
    private final WebClient webClient;

    // Support concurrent OAuth flows (multiple users logging in simultaneously)
    // State → (expiry, tenantId) for tenant-aware OAuth
    private final ConcurrentHashMap<String, OAuthState> pendingStates = new ConcurrentHashMap<>();

    // SECURITY_AUDIT.md §7: how long a `state` value stays valid, both server-side (this map)
    // and in the browser-bound state cookie OAuthController sets alongside it. Keep both in
    // sync — the cookie is what proves the callback is being followed by the same browser that
    // started the flow; this map is the pre-existing "does state exist / hasn't expired" layer.
    static final int STATE_TTL_SECONDS = 600;

    public OAuthService(AtlassianOAuthProperties oauthProperties,
                        AppProperties appProperties,
                        UserRepository userRepository,
                        OAuthTokenRepository tokenRepository,
                        SessionRepository sessionRepository,
                        TenantService tenantService,
                        TenantJiraConfigRepository tenantJiraConfigRepository) {
        this.oauthProperties = oauthProperties;
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
        this.tenantService = tenantService;
        this.tenantJiraConfigRepository = tenantJiraConfigRepository;
        // Use the JDK/OS DNS resolver instead of Netty's native UDP resolver, which
        // fails to resolve auth.atlassian.com on some networks (UnknownHostException)
        // and breaks the OAuth token exchange.
        HttpClient httpClient = HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private record OAuthState(Instant expiry, Long tenantId) {}

    /**
     * SECURITY_AUDIT.md §7: result of starting an OAuth flow — the Atlassian authorize URL
     * plus the raw {@code state} value, so the caller (OAuthController) can bind it to the
     * initiating browser via a short-lived HttpOnly state cookie. Without this, `state` is
     * only checked for existence/expiry server-side, which does not stop an attacker from
     * handing a victim a callback URL for a flow *the attacker* started (login CSRF).
     */
    public record AuthorizationRequest(String url, String state) {}

    public AuthorizationRequest createAuthorizationRequest(Long tenantId) {
        String state = registerState(tenantId);
        String url = buildAuthorizationUrl(state);
        return new AuthorizationRequest(url, state);
    }

    public String getAuthorizationUrl(Long tenantId) {
        return createAuthorizationRequest(tenantId).url();
    }

    private String registerState(Long tenantId) {
        String state = UUID.randomUUID().toString();
        if (tenantId == null) {
            tenantId = TenantContext.getCurrentTenantId();
        }
        pendingStates.put(state, new OAuthState(Instant.now().plusSeconds(STATE_TTL_SECONDS), tenantId));

        // Cleanup expired states
        pendingStates.entrySet().removeIf(e -> e.getValue().expiry().isBefore(Instant.now()));

        return state;
    }

    private String buildAuthorizationUrl(String state) {
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
        OAuthState oauthState = pendingStates.remove(state);
        if (oauthState == null || oauthState.expiry().isBefore(Instant.now())) {
            log.warn("Invalid or expired OAuth state: {}", state);
            return CallbackResult.failure("Invalid state parameter", null, null);
        }
        Long tenantId = oauthState.tenantId();

        try {
            // Exchange code for tokens
            TokenResponse tokenResponse = exchangeCodeForTokens(code);
            if (tokenResponse == null) {
                return CallbackResult.failure("Failed to exchange code for tokens", null, tenantId);
            }

            // Get user info
            UserInfo userInfo = getUserInfo(tokenResponse.accessToken);
            if (userInfo == null) {
                return CallbackResult.failure("Failed to get user info", null, tenantId);
            }

            // F82: fetch ALL Jira sites this account can access (not just the first) — needed
            // to gate/re-check tenant membership against Jira access below.
            List<String> accessibleCloudIds = getAccessibleCloudIds(tokenResponse.accessToken);
            String cloudId = accessibleCloudIds.isEmpty() ? null : accessibleCloudIds.get(0);

            // Save or update user
            boolean isFirstUser = userRepository.count() == 0;
            UserEntity user = userRepository.findByAtlassianAccountId(userInfo.accountId)
                    .orElseGet(() -> {
                        UserEntity newUser = new UserEntity();
                        newUser.setAtlassianAccountId(userInfo.accountId);
                        if (isFirstUser) {
                            newUser.setAppRole(AppRole.ADMIN);
                            log.info("First user registered — assigning ADMIN role");
                        }
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

            // F82: tenant membership is gated by Jira access (SECURITY_AUDIT.md §2 — was:
            // any authenticated Atlassian account could self-join any tenant via ?tenant=<slug>).
            if (tenantId != null) {
                Optional<TenantEntity> tenantOpt = tenantService.findById(tenantId);
                if (tenantOpt.isPresent()) {
                    TenantEntity tenant = tenantOpt.get();
                    MembershipGateResult gateResult = applyMembershipGate(tenant, user, accessibleCloudIds);
                    if (!gateResult.allowed()) {
                        return CallbackResult.failure(
                                "Jira access denied for this tenant", null, tenantId, gateResult.errorCode());
                    }
                }
            }

            // Create session with tenant_id
            SessionEntity session = new SessionEntity();
            session.setId(UUID.randomUUID().toString());
            session.setUser(user);
            session.setTenantId(tenantId);
            session.setExpiresAt(OffsetDateTime.now().plusDays(appProperties.getSession().getMaxAgeDays()));
            sessionRepository.save(session);

            log.info("OAuth successful for user: {} ({}) tenant: {}", user.getDisplayName(), user.getEmail(), tenantId);

            return CallbackResult.success(session.getId(), tenantId);

        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            return CallbackResult.failure(e.getMessage(), null, tenantId);
        }
    }

    /**
     * F82: gates/reconciles tenant membership against the user's current Jira site access.
     * See ai-ru/features/F82_JIRA_ACCESS_MEMBERSHIP.md and ai-ru/SECURITY_AUDIT.md §2.
     *
     * <ul>
     *   <li>First user of a brand-new tenant (bootstrap) — always ADMIN, no Jira config yet
     *       to check against.</li>
     *   <li>Not yet a member, tenant has a configured cloudId — join allowed only if that
     *       cloudId is in the user's accessible sites; otherwise the join is denied.</li>
     *   <li>Not yet a member, tenant has NO configured cloudId — auto-join is closed (cannot
     *       verify access), so we simply deny rather than silently admitting anyone.</li>
     *   <li>Already a member — deactivated if access was lost, reactivated if access is
     *       restored. Either way the login itself is allowed to proceed; enforcement of the
     *       {@code active} flag happens downstream (LeadBoardAuthenticationFilter /
     *       McpJwtContextFilter), exactly like "not a member" is handled today.</li>
     * </ul>
     *
     * Package-private (not private) so unit tests can exercise the branching logic directly,
     * without going through the network calls in {@link #handleCallback}.
     */
    MembershipGateResult applyMembershipGate(TenantEntity tenant, UserEntity user, List<String> accessibleCloudIds) {
        Optional<TenantUserEntity> existing = tenantService.findTenantUser(tenant.getId(), user.getId());

        if (existing.isEmpty()) {
            boolean tenantHasAnyUsers = tenantService.tenantHasUsers(tenant.getId());
            if (!tenantHasAnyUsers) {
                // Bootstrap: first user sets up a brand-new tenant — no Jira config yet.
                tenantService.addUserToTenant(tenant, user, AppRole.ADMIN);
                log.info("Added first user {} to tenant {} as ADMIN (bootstrap)", user.getEmail(), tenant.getSlug());
                return MembershipGateResult.allow();
            }

            String tenantCloudId = resolveTenantJiraCloudId(tenant);
            if (tenantCloudId == null || tenantCloudId.isBlank()) {
                log.warn("Tenant '{}' has no Jira cloudId configured — denying auto-join for user {}",
                        tenant.getSlug(), user.getEmail());
                return MembershipGateResult.deny("jira_access_denied");
            }
            if (!accessibleCloudIds.contains(tenantCloudId)) {
                log.warn("User {} lacks Jira access to tenant '{}' (cloudId {}) — denying join",
                        user.getEmail(), tenant.getSlug(), tenantCloudId);
                return MembershipGateResult.deny("jira_access_denied");
            }

            tenantService.addUserToTenant(tenant, user, AppRole.MEMBER);
            log.info("Added user {} to tenant {} with role MEMBER (Jira access verified)", user.getEmail(), tenant.getSlug());
            return MembershipGateResult.allow();
        }

        // Already a member — reconcile membership status against current Jira access.
        TenantUserEntity tenantUser = existing.get();
        String tenantCloudId = resolveTenantJiraCloudId(tenant);
        if (tenantCloudId == null || tenantCloudId.isBlank()) {
            log.debug("Tenant '{}' has no Jira cloudId configured — skipping access re-check for existing member {}",
                    tenant.getSlug(), user.getEmail());
            return MembershipGateResult.allow();
        }

        boolean hasAccess = accessibleCloudIds.contains(tenantCloudId);
        if (!hasAccess && tenantUser.isActive()) {
            tenantService.deactivateMembership(tenantUser, "jira_access_lost");
            log.warn("Deactivated membership: user {} lost Jira access to tenant '{}'", user.getEmail(), tenant.getSlug());
        } else if (hasAccess && !tenantUser.isActive()) {
            tenantService.reactivateMembership(tenantUser);
            log.info("Reactivated membership: user {} regained Jira access to tenant '{}'", user.getEmail(), tenant.getSlug());
        }

        return MembershipGateResult.allow();
    }

    /**
     * Resolves the tenant's configured Jira cloudId. {@code tenant_jira_config} lives per
     * tenant schema (no {@code tenant_id} column), so this temporarily switches
     * {@link TenantContext} to the given tenant, then restores whatever context was active
     * before (normally none — the OAuth callback is hit on the bare backend host, not a
     * tenant subdomain).
     */
    String resolveTenantJiraCloudId(TenantEntity tenant) {
        Long previousTenantId = TenantContext.getCurrentTenantId();
        String previousSchema = TenantContext.hasTenant() ? TenantContext.getCurrentSchema() : null;
        try {
            TenantContext.setTenant(tenant.getId(), tenant.getSchemaName());
            return tenantJiraConfigRepository.findActive()
                    .map(TenantJiraConfigEntity::getJiraCloudId)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to resolve Jira cloudId for tenant '{}': {}", tenant.getSlug(), e.getMessage());
            return null;
        } finally {
            if (previousTenantId != null) {
                TenantContext.setTenant(previousTenantId, previousSchema);
            } else {
                TenantContext.clear();
            }
        }
    }

    /** Outcome of {@link #applyMembershipGate}. */
    record MembershipGateResult(boolean allowed, String errorCode) {
        static MembershipGateResult allow() {
            return new MembershipGateResult(true, null);
        }

        static MembershipGateResult deny(String errorCode) {
            return new MembershipGateResult(false, errorCode);
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
        Optional<OAuthTokenEntity> tokenOpt = resolveLatestToken();
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
        return resolveLatestToken()
                .map(OAuthTokenEntity::getCloudId)
                .orElse(null);
    }

    /**
     * Resolves the most recently updated OAuth token, scoped to the current tenant
     * whenever a tenant context is active.
     *
     * SECURITY: in multi-tenant mode (TenantContext set — e.g. per-request via
     * TenantFilter, or per-tenant in TenantSyncScheduler's background sync loop),
     * we must NOT fall back to the globally-latest token across all tenants — that
     * would let tenant A's sync/chat/write use tenant B's Jira OAuth token and cloudId.
     * Only single-tenant deployments (no TenantContext, .env-driven JiraProperties)
     * use the unscoped global lookup.
     */
    private Optional<OAuthTokenEntity> resolveLatestToken() {
        if (TenantContext.hasTenant()) {
            return tokenRepository.findLatestTokenForTenant(TenantContext.getCurrentTenantId());
        }
        return tokenRepository.findLatestToken();
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
        List<String> ids = getAccessibleCloudIds(accessToken);
        if (!ids.isEmpty()) {
            log.info("Found cloud ID: {}", ids.get(0));
            return ids.get(0);
        }
        return null;
    }

    /**
     * F82: returns ALL Jira/Confluence site cloudIds this Atlassian account can access
     * (unlike {@link #getCloudId}, which only returns the first). Used to gate/re-check
     * tenant membership against Jira site access — see {@link #applyMembershipGate}.
     */
    public List<String> getAccessibleCloudIds(String accessToken) {
        List<AccessibleResource> resources = fetchAccessibleResources(accessToken);
        if (resources == null) {
            return List.of();
        }
        return resources.stream()
                .map(r -> r.id)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * F82: true if the given Atlassian account has access to the Jira site identified by
     * {@code tenantCloudId}.
     */
    public boolean userHasJiraAccess(String accessToken, String tenantCloudId) {
        if (tenantCloudId == null || tenantCloudId.isBlank()) {
            return false;
        }
        return getAccessibleCloudIds(accessToken).contains(tenantCloudId);
    }

    private List<AccessibleResource> fetchAccessibleResources(String accessToken) {
        try {
            String responseBody = webClient.get()
                    .uri(oauthProperties.getAccessibleResourcesUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Accessible resources response received");

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(responseBody,
                    mapper.getTypeFactory().constructCollectionType(List.class, AccessibleResource.class));
        } catch (Exception e) {
            log.error("Failed to get accessible resources", e);
            return null;
        }
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
            AppRole effectiveRole = leadAuth.getTenantRole();
            return new AuthStatus(true, new AuthenticatedUser(
                    user.getId(),
                    user.getAtlassianAccountId(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getAvatarUrl(),
                    effectiveRole.name(),
                    effectiveRole.getPermissions(),
                    leadAuth.getTenantId()
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

    public record CallbackResult(boolean success, String error, String sessionId, Long tenantId, String errorCode) {
        public static CallbackResult success(String sessionId, Long tenantId) {
            return new CallbackResult(true, null, sessionId, tenantId, null);
        }

        public static CallbackResult failure(String error, String sessionId, Long tenantId) {
            return new CallbackResult(false, error, sessionId, tenantId, null);
        }

        /** F82: failure with a machine-readable errorCode (e.g. "jira_access_denied") the
         * controller can surface to the frontend without leaking internal error details. */
        public static CallbackResult failure(String error, String sessionId, Long tenantId, String errorCode) {
            return new CallbackResult(false, error, sessionId, tenantId, errorCode);
        }
    }

    public record AuthenticatedUser(
            Long id,
            String accountId,
            String displayName,
            String email,
            String avatarUrl,
            String role,
            java.util.Set<String> permissions,
            Long tenantId
    ) {}

    public record AuthStatus(boolean authenticated, AuthenticatedUser user) {}
}

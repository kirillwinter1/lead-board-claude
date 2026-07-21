package com.leadboard.poker.websocket;

import com.leadboard.auth.SessionEntity;
import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Authenticates the WebSocket handshake via the session cookie and captures the
 * caller's identity + tenant into the WebSocket session attributes.
 *
 * WS message callbacks run on Tomcat WebSocket threads where neither the Spring
 * SecurityContext nor TenantContext is populated (BUG-174/175); everything the
 * handler needs must be resolved here, while the handshake HTTP request is
 * still available.
 */
@Component
public class PokerHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_ACCOUNT_ID = "poker.accountId";
    public static final String ATTR_DISPLAY_NAME = "poker.displayName";
    public static final String ATTR_TENANT_ID = "poker.tenantId";
    public static final String ATTR_TENANT_SCHEMA = "poker.tenantSchema";

    private static final Logger log = LoggerFactory.getLogger(PokerHandshakeInterceptor.class);

    private final SessionRepository sessionRepository;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final AppProperties appProperties;

    public PokerHandshakeInterceptor(SessionRepository sessionRepository,
                                     TenantRepository tenantRepository,
                                     TenantUserRepository tenantUserRepository,
                                     AppProperties appProperties) {
        this.sessionRepository = sessionRepository;
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.appProperties = appProperties;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String sessionId = extractSessionCookie(request);
        if (sessionId == null) {
            log.warn("Poker WS handshake rejected: no session cookie");
            return false;
        }

        Optional<SessionEntity> sessionOpt = sessionRepository.findValidSession(sessionId, OffsetDateTime.now());
        if (sessionOpt.isEmpty()) {
            log.warn("Poker WS handshake rejected: invalid or expired session");
            return false;
        }
        SessionEntity authSession = sessionOpt.get();

        // Tenant resolved by TenantFilter (subdomain) must match the auth session's tenant.
        Long contextTenantId = TenantContext.hasTenant() ? TenantContext.getCurrentTenantId() : null;
        Long sessionTenantId = authSession.getTenantId();
        if (contextTenantId != null && sessionTenantId != null && !contextTenantId.equals(sessionTenantId)) {
            log.warn("Poker WS handshake rejected: session tenant {} does not match request tenant {}",
                    sessionTenantId, contextTenantId);
            return false;
        }

        Long tenantId = sessionTenantId != null ? sessionTenantId : contextTenantId;
        if (tenantId == null) {
            // Poker tables live only in tenant schemas (V45) — no tenant, no poker.
            log.warn("Poker WS handshake rejected: no tenant for session");
            return false;
        }

        String schema = TenantContext.hasTenant() && tenantId.equals(contextTenantId)
                ? TenantContext.getCurrentSchema()
                : tenantRepository.findById(tenantId)
                        .filter(t -> t.isActive())
                        .map(t -> t.getSchemaName())
                        .orElse(null);
        if (schema == null) {
            log.warn("Poker WS handshake rejected: tenant {} not found or inactive", tenantId);
            return false;
        }

        // /ws/** is permitAll, so this handshake is the only auth point for poker sockets.
        // Enforce the same F82 contract as LeadBoardAuthenticationFilter: a user must have
        // an ACTIVE tenant_users membership — a deactivated (jira_access_lost) row, or no
        // membership at all (e.g. a null-tenant session reaching a tenant subdomain), is
        // denied exactly like a non-member.
        Long userId = authSession.getUser().getId();
        if (tenantUserRepository.findByTenantIdAndUserIdAndActiveTrue(tenantId, userId).isEmpty()) {
            log.warn("Poker WS handshake rejected: user {} is not an active member of tenant {}", userId, tenantId);
            return false;
        }

        attributes.put(ATTR_ACCOUNT_ID, authSession.getUser().getAtlassianAccountId());
        attributes.put(ATTR_DISPLAY_NAME, authSession.getUser().getDisplayName());
        attributes.put(ATTR_TENANT_ID, tenantId);
        attributes.put(ATTR_TENANT_SCHEMA, schema);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractSessionCookie(ServerHttpRequest request) {
        String cookieName = appProperties.getSession().getCookieName();
        List<String> cookieHeaders = request.getHeaders().get(HttpHeaders.COOKIE);
        if (cookieHeaders == null) return null;

        for (String header : cookieHeaders) {
            for (String pair : header.split(";")) {
                String trimmed = pair.trim();
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    String name = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1).trim();
                    if (cookieName.equals(name) && !value.isEmpty()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }
}

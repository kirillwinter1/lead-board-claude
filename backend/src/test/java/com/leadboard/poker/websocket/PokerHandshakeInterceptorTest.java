package com.leadboard.poker.websocket;

import com.leadboard.auth.SessionEntity;
import com.leadboard.auth.SessionRepository;
import com.leadboard.auth.UserEntity;
import com.leadboard.config.AppProperties;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug reproduction: /ws/** is permitAll, so this interceptor is the only
 * authentication point for poker WebSockets — yet it never consults
 * tenant_users. The F82 contract (LeadBoardAuthenticationFilter: a deactivated
 * membership row must be treated exactly like "not a member") is not enforced:
 * a user deactivated in the tenant keeps full poker access for the lifetime of
 * their session cookie.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PokerHandshakeInterceptor membership enforcement")
class PokerHandshakeInterceptorTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantUserRepository tenantUserRepository;

    private PokerHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new PokerHandshakeInterceptor(
                sessionRepository, tenantRepository, tenantUserRepository, new AppProperties());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("handshake must reject a user without an active tenant_users membership")
    void rejectsDeactivatedMember() {
        // The user's tenant_users row is deactivated (F82 "jira_access_lost") — the
        // world state the interceptor is obliged to check. Its current constructor has
        // no membership dependency at all, so it cannot reject and returns true.
        UserEntity user = new UserEntity();
        user.setId(42L);
        user.setAtlassianAccountId("acc-42");
        user.setDisplayName("Deactivated User");

        SessionEntity session = mock(SessionEntity.class);
        when(session.getUser()).thenReturn(user);
        when(session.getTenantId()).thenReturn(2L);

        when(sessionRepository.findValidSession(eq("sess-1"), any()))
                .thenReturn(Optional.of(session));

        TenantContext.setTenant(2L, "tenant_b");

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "LEAD_SESSION=sess-1");
        when(request.getHeaders()).thenReturn(headers);

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

        assertFalse(accepted,
                "a user without an active tenant_users membership must not pass the poker WS handshake (F82)");
    }
}

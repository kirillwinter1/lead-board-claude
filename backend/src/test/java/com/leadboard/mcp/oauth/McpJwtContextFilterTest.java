package com.leadboard.mcp.oauth;

import com.leadboard.auth.AppRole;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.auth.UserEntity;
import com.leadboard.auth.UserRepository;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserEntity;
import com.leadboard.tenant.TenantUserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * F82: McpJwtContextFilter must treat a deactivated tenant_users membership exactly like
 * "not a member" — same contract already covered for LeadBoardAuthenticationFilter.
 */
class McpJwtContextFilterTest {

    private UserRepository userRepository;
    private TenantRepository tenantRepository;
    private TenantUserRepository tenantUserRepository;
    private McpJwtContextFilter filter;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tenantRepository = mock(TenantRepository.class);
        tenantUserRepository = mock(TenantUserRepository.class);
        filter = new McpJwtContextFilter(userRepository, tenantRepository, tenantUserRepository);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private Jwt jwtFor(String accountId, String tenantId) {
        Jwt.Builder builder = Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("user_account_id", accountId);
        if (tenantId != null) {
            builder.claim("tenant_id", tenantId);
        }
        return builder.build();
    }

    @Test
    @DisplayName("active membership — authenticates and sets tenant context")
    void shouldAuthenticateActiveMembership() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(3L);
        user.setAtlassianAccountId("acc-42");
        when(userRepository.findByAtlassianAccountId("acc-42")).thenReturn(Optional.of(user));

        TenantEntity tenant = new TenantEntity();
        tenant.setId(1L);
        tenant.setSchemaName("tenant_acme");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        TenantUserEntity membership = new TenantUserEntity();
        membership.setAppRole(AppRole.MEMBER);
        membership.setActive(true);
        when(tenantUserRepository.findByTenantIdAndUserIdAndActiveTrue(1L, 3L)).thenReturn(Optional.of(membership));

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtFor("acc-42", "1")));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean[] chainSawContext = {false};
        FilterChain chain = (request, response) ->
                chainSawContext[0] = SecurityContextHolder.getContext().getAuthentication() instanceof LeadBoardAuthentication
                        && Long.valueOf(1L).equals(TenantContext.getCurrentTenantId());

        filter.doFilter(req, res, chain);

        assertTrue(chainSawContext[0], "tenant + LeadBoardAuthentication must be set inside the chain");
        assertNotEquals(401, res.getStatus());
    }

    @Test
    @DisplayName("F82: deactivated membership (Jira access lost) is rejected like a non-member")
    void shouldRejectDeactivatedMembership() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(3L);
        user.setAtlassianAccountId("acc-42");
        when(userRepository.findByAtlassianAccountId("acc-42")).thenReturn(Optional.of(user));

        TenantEntity tenant = new TenantEntity();
        tenant.setId(1L);
        tenant.setSchemaName("tenant_acme");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        // Membership row exists but is deactivated — active-aware lookup must not return it.
        when(tenantUserRepository.findByTenantIdAndUserIdAndActiveTrue(1L, 3L)).thenReturn(Optional.empty());

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtFor("acc-42", "1")));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("no JWT authentication present — rejected with 401")
    void shouldRejectWithoutJwt() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }
}

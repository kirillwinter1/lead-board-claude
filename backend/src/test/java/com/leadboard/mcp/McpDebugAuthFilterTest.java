package com.leadboard.mcp;

import com.leadboard.auth.AppRole;
import com.leadboard.auth.UserEntity;
import com.leadboard.auth.UserRepository;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantRepository;
import com.leadboard.tenant.TenantUserEntity;
import com.leadboard.tenant.TenantUserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpDebugAuthFilterTest {

    @Test
    void rejectsMissingToken() throws Exception {
        McpProperties props = new McpProperties();
        props.setEnabled(true);
        props.setDebugToken("secret");
        McpDebugAuthFilter filter = new McpDebugAuthFilter(props,
                mock(UserRepository.class), mock(TenantRepository.class), mock(TenantUserRepository.class));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsWrongToken() throws Exception {
        McpProperties props = new McpProperties();
        props.setEnabled(true);
        props.setDebugToken("secret");
        McpDebugAuthFilter filter = new McpDebugAuthFilter(props,
                mock(UserRepository.class), mock(TenantRepository.class), mock(TenantUserRepository.class));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader("Authorization", "Bearer wrong");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void setsAuthenticationAndTenantForValidToken() throws Exception {
        McpProperties props = new McpProperties();
        props.setEnabled(true);
        props.setDebugToken("secret");
        props.setDebugTenantSlug("test2");
        props.setDebugAccountId("acc-1");

        UserRepository users = mock(UserRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        TenantUserRepository tenantUsers = mock(TenantUserRepository.class);

        TenantEntity tenant = new TenantEntity();
        tenant.setId(7L);
        tenant.setSchemaName("tenant_test2");
        when(tenants.findBySlug("test2")).thenReturn(Optional.of(tenant));

        UserEntity user = new UserEntity();
        user.setId(3L);
        user.setAtlassianAccountId("acc-1");
        when(users.findByAtlassianAccountId("acc-1")).thenReturn(Optional.of(user));

        TenantUserEntity tu = new TenantUserEntity();
        tu.setAppRole(AppRole.TEAM_LEAD);
        when(tenantUsers.findByTenantIdAndUserId(7L, 3L)).thenReturn(Optional.of(tu));

        McpDebugAuthFilter filter = new McpDebugAuthFilter(props, users, tenants, tenantUsers);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader("Authorization", "Bearer secret");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean[] chainSawContext = {false};
        FilterChain chain = (request, response) -> {
            chainSawContext[0] = SecurityContextHolder.getContext().getAuthentication() != null
                    && Long.valueOf(7L).equals(TenantContext.getCurrentTenantId());
        };

        filter.doFilter(req, res, chain);

        assertTrue(chainSawContext[0], "tenant + auth must be set inside the chain");
        // cleared after the request
        assertNull(TenantContext.getCurrentTenantId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void skipsNonMcpPaths() throws Exception {
        McpProperties props = new McpProperties();
        props.setEnabled(true);
        props.setDebugToken("secret");
        McpDebugAuthFilter filter = new McpDebugAuthFilter(props,
                mock(UserRepository.class), mock(TenantRepository.class), mock(TenantUserRepository.class));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/board");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        // shouldNotFilter → chain proceeds, no 401
        verify(chain).doFilter(req, res);
        assertEquals(200, res.getStatus());
    }
}

package com.leadboard.auth;

import com.leadboard.config.AppProperties;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantUserEntity;
import com.leadboard.tenant.TenantUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadBoardAuthenticationFilterTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @Mock
    private FilterChain filterChain;

    private LeadBoardAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        filter = new LeadBoardAuthenticationFilter(sessionRepository, tenantUserRepository, appProperties);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        // TenantContext is a ThreadLocal — clear so cases don't leak into each
        // other or into other test classes running on the same thread.
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should authenticate user from valid session cookie")
    void shouldAuthenticateFromSessionCookie() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setAtlassianAccountId("acc-123");
        user.setDisplayName("John Doe");
        user.setEmail("john@test.com");

        SessionEntity session = new SessionEntity();
        session.setId("session-abc");
        session.setUser(user);
        session.setExpiresAt(OffsetDateTime.now().plusDays(30));

        when(sessionRepository.findValidSession(eq("session-abc"), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(session));

        request.setCookies(new Cookie("LEAD_SESSION", "session-abc"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        assertEquals("John Doe", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    @DisplayName("should not authenticate when no cookie present")
    void shouldNotAuthenticateWithoutCookie() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("should not authenticate when session expired")
    void shouldNotAuthenticateWithExpiredSession() throws Exception {
        when(sessionRepository.findValidSession(eq("session-expired"), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        request.setCookies(new Cookie("LEAD_SESSION", "session-expired"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("should not authenticate when session not found")
    void shouldNotAuthenticateWithInvalidSession() throws Exception {
        when(sessionRepository.findValidSession(eq("non-existent"), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        request.setCookies(new Cookie("LEAD_SESSION", "non-existent"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("should ignore unrelated cookies")
    void shouldIgnoreUnrelatedCookies() throws Exception {
        request.setCookies(new Cookie("OTHER_COOKIE", "some-value"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(sessionRepository, never()).findValidSession(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("BUG-94: should authenticate with per-tenant role, not global role")
    void shouldUsePerTenantRoleNotGlobalRole() throws Exception {
        TenantContext.setTenant(100L, "tenant_a");

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setAtlassianAccountId("acc-123");
        user.setDisplayName("John Doe");
        user.setEmail("john@test.com");
        user.setAppRole(AppRole.MEMBER); // global role is MEMBER

        SessionEntity session = new SessionEntity();
        session.setId("session-abc");
        session.setUser(user);
        session.setExpiresAt(OffsetDateTime.now().plusDays(30));

        when(sessionRepository.findValidSession(eq("session-abc"), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(session));

        TenantUserEntity tenantUser = new TenantUserEntity();
        tenantUser.setAppRole(AppRole.ADMIN); // per-tenant role is ADMIN
        when(tenantUserRepository.findByTenantIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(tenantUser));

        request.setCookies(new Cookie("LEAD_SESSION", "session-abc"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertInstanceOf(LeadBoardAuthentication.class, authentication);
        // Tenant role wins over global role
        assertEquals(AppRole.ADMIN, ((LeadBoardAuthentication) authentication).getRole());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    @DisplayName("BUG-95: should reject cross-tenant session reuse (not a member of current tenant)")
    void shouldRejectCrossTenantSessionReuse() throws Exception {
        TenantContext.setTenant(200L, "tenant_b");

        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setAtlassianAccountId("acc-777");
        user.setDisplayName("Jane Roe");
        user.setEmail("jane@test.com");
        user.setAppRole(AppRole.ADMIN); // global role would otherwise grant access

        SessionEntity session = new SessionEntity();
        session.setId("session-cross");
        session.setUser(user);
        session.setExpiresAt(OffsetDateTime.now().plusDays(30));

        when(sessionRepository.findValidSession(eq("session-cross"), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(session));

        // User is NOT a member of tenant 200 — no fallback to global role
        when(tenantUserRepository.findByTenantIdAndUserId(200L, 7L))
                .thenReturn(Optional.empty());

        request.setCookies(new Cookie("LEAD_SESSION", "session-cross"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("should fall back to global role when no tenant context")
    void shouldFallBackToGlobalRoleWithoutTenant() throws Exception {
        // No TenantContext set

        UserEntity user = new UserEntity();
        user.setId(3L);
        user.setAtlassianAccountId("acc-333");
        user.setDisplayName("Admin User");
        user.setEmail("admin@test.com");
        user.setAppRole(AppRole.ADMIN); // global role

        SessionEntity session = new SessionEntity();
        session.setId("session-global");
        session.setUser(user);
        session.setExpiresAt(OffsetDateTime.now().plusDays(30));

        when(sessionRepository.findValidSession(eq("session-global"), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(session));

        request.setCookies(new Cookie("LEAD_SESSION", "session-global"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertInstanceOf(LeadBoardAuthentication.class, authentication);
        assertEquals(AppRole.ADMIN, ((LeadBoardAuthentication) authentication).getRole());
        // Without a tenant we must never consult tenant_users
        verifyNoInteractions(tenantUserRepository);
    }
}

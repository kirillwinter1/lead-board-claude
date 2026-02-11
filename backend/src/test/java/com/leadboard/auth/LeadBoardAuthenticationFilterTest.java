package com.leadboard.auth;

import com.leadboard.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
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
    private FilterChain filterChain;

    private LeadBoardAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        filter = new LeadBoardAuthenticationFilter(sessionRepository, appProperties);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
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
}

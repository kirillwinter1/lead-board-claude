package com.leadboard.auth;

import com.leadboard.config.AppProperties;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantUserEntity;
import com.leadboard.tenant.TenantUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

/**
 * Filter that extracts user from session cookie and sets up SecurityContext.
 * Tenant-aware: if TenantContext has a tenant, loads per-tenant role from tenant_users.
 * BUG-94: Users not in tenant_users are denied access (no fallback to global role).
 */
@Component
public class LeadBoardAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LeadBoardAuthenticationFilter.class);

    private final SessionRepository sessionRepository;
    private final TenantUserRepository tenantUserRepository;
    private final AppProperties appProperties;

    public LeadBoardAuthenticationFilter(SessionRepository sessionRepository,
                                         TenantUserRepository tenantUserRepository,
                                         AppProperties appProperties) {
        this.sessionRepository = sessionRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String sessionId = extractSessionId(request);

        if (sessionId != null) {
            Optional<SessionEntity> sessionOpt = sessionRepository.findValidSession(sessionId, OffsetDateTime.now());

            if (sessionOpt.isPresent()) {
                SessionEntity session = sessionOpt.get();
                UserEntity user = session.getUser();
                if (user != null) {
                    Long tenantId = TenantContext.getCurrentTenantId();

                    if (tenantId != null) {
                        // BUG-94: Require tenant membership — do NOT fall back to global role
                        Optional<TenantUserEntity> tenantUserOpt =
                                tenantUserRepository.findByTenantIdAndUserId(tenantId, user.getId());
                        if (tenantUserOpt.isPresent()) {
                            AppRole tenantRole = tenantUserOpt.get().getAppRole();
                            LeadBoardAuthentication auth = new LeadBoardAuthentication(user, tenantId, tenantRole);
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        } else {
                            // User is not a member of this tenant — do not authenticate.
                            // They will get 401 on authenticated endpoints, but OAuth flow can still add them.
                            log.debug("User {} is not a member of tenant {}", user.getId(), tenantId);
                        }
                    } else {
                        // No tenant context — use global role (legacy / public schema mode)
                        AppRole globalRole = user.getAppRole();
                        LeadBoardAuthentication auth = new LeadBoardAuthentication(user, null, globalRole);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractSessionId(HttpServletRequest request) {
        String cookieName = appProperties.getSession().getCookieName();
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}

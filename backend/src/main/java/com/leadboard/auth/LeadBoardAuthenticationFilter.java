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

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

/**
 * Filter that extracts user from session cookie and sets up SecurityContext.
 * Now tenant-aware: if TenantContext has a tenant, loads per-tenant role from tenant_users.
 */
@Component
public class LeadBoardAuthenticationFilter extends OncePerRequestFilter {

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
                    AppRole tenantRole = user.getAppRole(); // fallback to global role

                    // If we have a tenant context, look up per-tenant role
                    if (tenantId != null) {
                        Optional<TenantUserEntity> tenantUserOpt =
                                tenantUserRepository.findByTenantIdAndUserId(tenantId, user.getId());
                        if (tenantUserOpt.isPresent()) {
                            tenantRole = tenantUserOpt.get().getAppRole();
                        }
                    }

                    LeadBoardAuthentication auth = new LeadBoardAuthentication(user, tenantId, tenantRole);
                    SecurityContextHolder.getContext().setAuthentication(auth);
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

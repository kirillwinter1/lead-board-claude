package com.leadboard.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * HTTP filter that resolves tenant from:
 * 1. Subdomain (acme.leadboard.app → slug "acme")
 * 2. X-Tenant-Slug header (for dev/localhost)
 *
 * Must run BEFORE LeadBoardAuthenticationFilter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-Slug";

    private final TenantRepository tenantRepository;

    public TenantFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String slug = resolveSlug(request);

            if (slug != null) {
                Optional<TenantEntity> tenantOpt = tenantRepository.findBySlug(slug);
                if (tenantOpt.isPresent()) {
                    TenantEntity tenant = tenantOpt.get();
                    if (tenant.isActive()) {
                        TenantContext.setTenant(tenant.getId(), tenant.getSchemaName());
                    } else {
                        log.warn("Tenant '{}' is inactive", slug);
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant is inactive");
                        return;
                    }
                } else {
                    // Unknown slug — continue without tenant context (public routes still work)
                    log.debug("Unknown tenant slug: '{}'", slug);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveSlug(HttpServletRequest request) {
        // 1. Check X-Tenant-Slug header (dev/localhost)
        String headerSlug = request.getHeader(TENANT_HEADER);
        if (headerSlug != null && !headerSlug.isBlank()) {
            return headerSlug.trim().toLowerCase();
        }

        // 2. Check subdomain
        String host = request.getServerName();
        if (host != null) {
            String[] parts = host.split("\\.");
            // acme.leadboard.app → parts = ["acme", "leadboard", "app"]
            if (parts.length >= 3) {
                String subdomain = parts[0];
                // Skip "www" and "api"
                if (!"www".equals(subdomain) && !"api".equals(subdomain)) {
                    return subdomain;
                }
            }
        }

        return null;
    }
}

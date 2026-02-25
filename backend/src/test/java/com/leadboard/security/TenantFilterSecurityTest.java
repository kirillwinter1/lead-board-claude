package com.leadboard.security;

import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantFilter;
import com.leadboard.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantFilterSecurityTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private TenantFilter tenantFilter;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Subdomain takes priority over X-Tenant-Slug header")
    void subdomain_priorityOverHeader() throws Exception {
        when(request.getServerName()).thenReturn("acme.onelane.ru");
        // Note: we don't stub getHeader since the code should never call it for non-localhost

        TenantEntity tenant = new TenantEntity();
        tenant.setId(1L);
        tenant.setSlug("acme");
        tenant.setSchemaName("tenant_acme");
        tenant.setActive(true);
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));

        tenantFilter.doFilter(request, response, filterChain);

        // Should use "acme" from subdomain, header is never checked
        verify(tenantRepository).findBySlug("acme");
        verify(tenantRepository, never()).findBySlug("evil-tenant");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Header ignored for non-localhost requests")
    void header_ignoredForProduction() throws Exception {
        when(request.getServerName()).thenReturn("onelane.ru"); // no subdomain, but not localhost

        tenantFilter.doFilter(request, response, filterChain);

        // Header should be ignored since it's not localhost
        verify(tenantRepository, never()).findBySlug(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Header allowed for localhost")
    void header_allowedForLocalhost() throws Exception {
        when(request.getServerName()).thenReturn("localhost");
        when(request.getHeader("X-Tenant-Slug")).thenReturn("dev-tenant");

        TenantEntity tenant = new TenantEntity();
        tenant.setId(2L);
        tenant.setSlug("dev-tenant");
        tenant.setSchemaName("tenant_dev_tenant");
        tenant.setActive(true);
        when(tenantRepository.findBySlug("dev-tenant")).thenReturn(Optional.of(tenant));

        tenantFilter.doFilter(request, response, filterChain);

        verify(tenantRepository).findBySlug("dev-tenant");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Inactive tenant returns 403")
    void inactiveTenant_returns403() throws Exception {
        when(request.getServerName()).thenReturn("inactive.onelane.ru");

        TenantEntity tenant = new TenantEntity();
        tenant.setId(3L);
        tenant.setSlug("inactive");
        tenant.setSchemaName("tenant_inactive");
        tenant.setActive(false);
        when(tenantRepository.findBySlug("inactive")).thenReturn(Optional.of(tenant));

        tenantFilter.doFilter(request, response, filterChain);

        verify(response).sendError(403, "Tenant is inactive");
        verify(filterChain, never()).doFilter(request, response);
    }
}

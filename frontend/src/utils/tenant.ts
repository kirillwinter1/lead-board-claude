/**
 * Tenant detection utilities for multi-tenancy.
 */

/**
 * Extract tenant slug from hostname.
 * acme.leadboard.app → "acme"
 * localhost → null (dev mode)
 */
export function getTenantSlug(): string | null {
    const host = window.location.hostname;

    // Development: check for explicit tenant header preference in localStorage
    if (host === 'localhost' || host === '127.0.0.1') {
        return localStorage.getItem('tenant_slug');
    }

    const parts = host.split('.');
    // subdomain.leadboard.app or subdomain.leadboard.example.com
    if (parts.length >= 3) {
        const subdomain = parts[0];
        if (subdomain !== 'www' && subdomain !== 'api') {
            return subdomain;
        }
    }

    return null;
}

/**
 * Check if we're on the main domain (no tenant context).
 * Used to show landing/registration page.
 */
export function isMainDomain(): boolean {
    const host = window.location.hostname;
    if (host === 'localhost' || host === '127.0.0.1') {
        return !localStorage.getItem('tenant_slug');
    }
    const parts = host.split('.');
    // leadboard.app (2 parts) or www.leadboard.app
    return parts.length <= 2 || parts[0] === 'www';
}

/**
 * Set tenant slug for localhost development.
 */
export function setDevTenantSlug(slug: string | null): void {
    if (slug) {
        localStorage.setItem('tenant_slug', slug);
    } else {
        localStorage.removeItem('tenant_slug');
    }
}

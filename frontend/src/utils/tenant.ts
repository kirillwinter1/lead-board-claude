/**
 * Tenant detection utilities for multi-tenancy.
 *
 * Priority: subdomain > localStorage.
 * On production subdomains are the source of truth (acme.onelane.ru).
 * localStorage is always used as fallback (and as primary when no subdomain).
 */

/**
 * Extract tenant slug.
 * 1) subdomain: acme.onelane.ru → "acme"
 * 2) localStorage fallback (set after registration)
 */
export function getTenantSlug(): string | null {
    const host = window.location.hostname;
    const parts = host.split('.');

    // subdomain.domain.tld (3+ parts, not www/api)
    if (parts.length >= 3) {
        const subdomain = parts[0];
        if (subdomain !== 'www' && subdomain !== 'api' && subdomain !== 'localhost') {
            return subdomain;
        }
    }

    // Fallback: localStorage (works everywhere)
    return localStorage.getItem('tenant_slug');
}

/**
 * Check if we're on the main domain (no tenant context).
 */
export function isMainDomain(): boolean {
    return !getTenantSlug();
}

/**
 * Store tenant slug (after registration or tenant switch).
 */
export function setTenantSlug(slug: string | null): void {
    if (slug) {
        localStorage.setItem('tenant_slug', slug);
    } else {
        localStorage.removeItem('tenant_slug');
    }
}

/**
 * @deprecated Use setTenantSlug instead
 */
export const setDevTenantSlug = setTenantSlug;

// auth.js: Authentication helpers for k6 perf tests

import { getSessionId } from '../config/env.js';

/**
 * Get HTTP headers for authenticated tenant requests.
 * @param {string} tenantSlug - e.g. 'perf-alpha'
 * @param {string} tenantName - e.g. 'alpha'
 * @param {number} userIndex - 1-based user index (1-10)
 * @returns {object} headers with Cookie and X-Tenant-Slug
 */
export function getTenantAuthHeaders(tenantSlug, tenantName, userIndex) {
    const sessionId = getSessionId(tenantName, userIndex);
    return {
        'Cookie': `LEAD_SESSION=${sessionId}`,
        'X-Tenant-Slug': tenantSlug,
        'Content-Type': 'application/json',
    };
}

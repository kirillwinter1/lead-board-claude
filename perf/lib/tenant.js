// tenant.js: User pool and VU context management

import { SharedArray } from 'k6/data';
import { TENANTS, TEAMS_PER_TENANT } from '../config/env.js';

/**
 * SharedArray of all VU contexts (300 total: 3 tenants × 100 users × rotated teams).
 * Each entry: { tenantSlug, tenantName, userIndex, teamIndex }
 */
export const userPool = new SharedArray('perf-user-pool', function () {
    const pool = [];
    for (const tenant of TENANTS) {
        for (let u = 1; u <= tenant.userCount; u++) {
            pool.push({
                tenantSlug: tenant.slug,
                tenantName: tenant.name,
                userIndex: u,
                teamIndex: ((u - 1) % TEAMS_PER_TENANT) + 1,
            });
        }
    }
    return pool;
});

/**
 * Get VU context for the current VU.
 * Distributes VUs across the user pool round-robin.
 * @returns {{ tenantSlug: string, tenantName: string, userIndex: number, teamIndex: number }}
 */
export function getVuContext() {
    const idx = (__VU - 1) % userPool.length;
    return userPool[idx];
}

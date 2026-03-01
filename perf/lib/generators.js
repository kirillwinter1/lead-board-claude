// generators.js: Random data generators for k6 perf tests

import { TEAMS_PER_TENANT, PROJECT_KEYS } from '../config/env.js';

/**
 * Random team index (1-based) for the tenant.
 */
export function randomTeamIndex() {
    return Math.floor(Math.random() * TEAMS_PER_TENANT) + 1;
}

/**
 * Random date range (from/to) within the last N days.
 * @param {number} daysBack - How many days back the range can start
 * @returns {{ from: string, to: string }} ISO date strings (YYYY-MM-DD)
 */
export function randomDateRange(daysBack = 90) {
    const now = new Date();
    const startOffset = Math.floor(Math.random() * daysBack);
    const rangeLength = Math.floor(Math.random() * 30) + 7; // 7-37 days

    const from = new Date(now);
    from.setDate(from.getDate() - startOffset - rangeLength);

    const to = new Date(now);
    to.setDate(to.getDate() - startOffset);

    return {
        from: from.toISOString().split('T')[0],
        to: to.toISOString().split('T')[0],
    };
}

/**
 * Random project key from the perf set.
 */
export function randomProjectKey() {
    return PROJECT_KEYS[Math.floor(Math.random() * PROJECT_KEYS.length)];
}

/**
 * Random think time between requests (1-3 seconds).
 */
export function thinkTime() {
    return Math.random() * 2 + 1; // 1-3s
}

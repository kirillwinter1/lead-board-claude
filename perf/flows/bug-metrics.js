// bug-metrics.js: Bug metrics flow

import { BASE_URL } from '../config/env.js';
import { getTenantAuthHeaders } from '../lib/auth.js';
import { apiGet } from '../lib/http.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

/**
 * Bug metrics flow: GET /api/metrics/bugs (all + filtered by team).
 */
export function bugMetricsFlow(tenantSlug, tenantName, userIndex, teamIndex) {
    const headers = getTenantAuthHeaders(tenantSlug, tenantName, userIndex);

    // 1. All teams
    apiGet(
        `${BASE_URL}/api/metrics/bugs`,
        headers,
        'bug_metrics',
        'bug_metrics_all'
    );

    sleep(thinkTime());

    // 2. Filtered by team
    apiGet(
        `${BASE_URL}/api/metrics/bugs?teamId=${teamIndex}`,
        headers,
        'bug_metrics',
        'bug_metrics_team'
    );
}

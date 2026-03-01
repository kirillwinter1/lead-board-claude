// metrics.js: Team metrics flow — throughput, lead-time, cycle-time, summary, dsr

import { BASE_URL } from '../config/env.js';
import { getTenantAuthHeaders } from '../lib/auth.js';
import { apiGet } from '../lib/http.js';
import { sleep } from 'k6';
import { thinkTime, randomDateRange } from '../lib/generators.js';

/**
 * Metrics flow: 5 key metric endpoints.
 */
export function metricsFlow(tenantSlug, tenantName, userIndex, teamIndex) {
    const headers = getTenantAuthHeaders(tenantSlug, tenantName, userIndex);
    const { from, to } = randomDateRange(90);
    const params = `teamId=${teamIndex}&from=${from}&to=${to}`;

    // 1. Throughput
    apiGet(
        `${BASE_URL}/api/metrics/throughput?${params}`,
        headers,
        'metrics',
        'metrics_throughput'
    );

    sleep(thinkTime());

    // 2. Lead time
    apiGet(
        `${BASE_URL}/api/metrics/lead-time?${params}`,
        headers,
        'metrics',
        'metrics_lead_time'
    );

    sleep(thinkTime());

    // 3. Cycle time
    apiGet(
        `${BASE_URL}/api/metrics/cycle-time?${params}`,
        headers,
        'metrics',
        'metrics_cycle_time'
    );

    sleep(thinkTime());

    // 4. Summary
    apiGet(
        `${BASE_URL}/api/metrics/summary?${params}`,
        headers,
        'metrics',
        'metrics_summary'
    );

    sleep(thinkTime());

    // 5. DSR
    apiGet(
        `${BASE_URL}/api/metrics/dsr?${params}`,
        headers,
        'metrics',
        'metrics_dsr'
    );
}

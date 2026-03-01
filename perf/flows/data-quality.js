// data-quality.js: Data quality flow — full scan + per team

import { BASE_URL } from '../config/env.js';
import { getTenantAuthHeaders } from '../lib/auth.js';
import { apiGet } from '../lib/http.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

/**
 * Data quality flow: GET /api/data-quality (all + filtered by team).
 */
export function dataQualityFlow(tenantSlug, tenantName, userIndex, teamIndex) {
    const headers = getTenantAuthHeaders(tenantSlug, tenantName, userIndex);

    // 1. All teams
    apiGet(
        `${BASE_URL}/api/data-quality`,
        headers,
        'data_quality',
        'data_quality_all'
    );

    sleep(thinkTime());

    // 2. Filtered by team
    apiGet(
        `${BASE_URL}/api/data-quality?teamId=${teamIndex}`,
        headers,
        'data_quality',
        'data_quality_team'
    );
}

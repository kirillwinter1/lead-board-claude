// timeline.js: Timeline flow — unified + retrospective + forecast + role-load

import { BASE_URL } from '../config/env.js';
import { getTenantAuthHeaders } from '../lib/auth.js';
import { apiGet } from '../lib/http.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

/**
 * Timeline flow: 4 planning endpoints that the timeline page loads in parallel.
 */
export function timelineFlow(tenantSlug, tenantName, userIndex, teamIndex) {
    const headers = getTenantAuthHeaders(tenantSlug, tenantName, userIndex);
    const teamParam = `teamId=${teamIndex}`;

    // 1. Unified planning
    apiGet(
        `${BASE_URL}/api/planning/unified?${teamParam}`,
        headers,
        'timeline',
        'timeline_unified'
    );

    sleep(thinkTime());

    // 2. Retrospective timeline
    apiGet(
        `${BASE_URL}/api/planning/retrospective?${teamParam}`,
        headers,
        'timeline',
        'timeline_retrospective'
    );

    sleep(thinkTime());

    // 3. Forecast
    apiGet(
        `${BASE_URL}/api/planning/forecast?${teamParam}`,
        headers,
        'timeline',
        'timeline_forecast'
    );

    sleep(thinkTime());

    // 4. Role load
    apiGet(
        `${BASE_URL}/api/planning/role-load?${teamParam}`,
        headers,
        'timeline',
        'timeline_role_load'
    );
}

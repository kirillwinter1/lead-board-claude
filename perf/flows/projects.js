// projects.js: Projects flow — list + timeline

import { BASE_URL } from '../config/env.js';
import { getTenantAuthHeaders } from '../lib/auth.js';
import { apiGet } from '../lib/http.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

/**
 * Projects flow: GET /api/projects + /api/projects/timeline.
 */
export function projectsFlow(tenantSlug, tenantName, userIndex) {
    const headers = getTenantAuthHeaders(tenantSlug, tenantName, userIndex);

    // 1. Projects list
    const listRes = apiGet(
        `${BASE_URL}/api/projects`,
        headers,
        'projects',
        'projects_list'
    );

    sleep(thinkTime());

    // 2. Projects timeline (Gantt)
    apiGet(
        `${BASE_URL}/api/projects/timeline`,
        headers,
        'projects',
        'projects_timeline'
    );

    sleep(thinkTime());

    // 3. Detail for a random project
    if (listRes.status === 200) {
        try {
            const projects = JSON.parse(listRes.body);
            if (Array.isArray(projects) && projects.length > 0) {
                const p = projects[Math.floor(Math.random() * projects.length)];
                const key = p.issueKey || p.key;
                if (key) {
                    apiGet(
                        `${BASE_URL}/api/projects/${key}`,
                        headers,
                        'projects',
                        'projects_detail'
                    );
                }
            }
        } catch (e) {
            // skip
        }
    }
}

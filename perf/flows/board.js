// board.js: Board flow — heaviest endpoint (triggers planning per team)

import { BASE_URL } from '../config/env.js';
import { getTenantAuthHeaders } from '../lib/auth.js';
import { apiGet } from '../lib/http.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

/**
 * Board flow: GET /api/board + score-breakdown for a random epic.
 * @param {string} tenantSlug - e.g. 'perf-alpha'
 * @param {string} tenantName - e.g. 'alpha'
 * @param {number} userIndex - 1-10
 * @param {number} teamIndex - 1-3 (team to filter by, or omit for all)
 */
export function boardFlow(tenantSlug, tenantName, userIndex, teamIndex) {
    const headers = getTenantAuthHeaders(tenantSlug, tenantName, userIndex);

    // 1. Get board (all teams)
    const boardRes = apiGet(
        `${BASE_URL}/api/board`,
        headers,
        'board',
        'board_all'
    );

    sleep(thinkTime());

    // 2. Get board filtered by team
    apiGet(
        `${BASE_URL}/api/board?teamIds=${teamIndex}`,
        headers,
        'board',
        'board_team'
    );

    sleep(thinkTime());

    // 3. Score breakdown for a random epic from the board response
    if (boardRes.status === 200) {
        try {
            const body = JSON.parse(boardRes.body);
            // Try to find an epic key from the response
            const epics = body.epics || body.items || [];
            if (epics.length > 0) {
                const randomEpic = epics[Math.floor(Math.random() * epics.length)];
                const epicKey = randomEpic.issueKey || randomEpic.key;
                if (epicKey) {
                    apiGet(
                        `${BASE_URL}/api/board/${epicKey}/score-breakdown`,
                        headers,
                        'board',
                        'board_score_breakdown'
                    );
                }
            }
        } catch (e) {
            // Response parsing failed, skip score-breakdown
        }
    }
}

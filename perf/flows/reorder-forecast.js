// reorder-forecast.js: Epic reorder + forecast recalculation flow
// Simulates: user drags epic to new position → forecast updates → board refreshes
//
// Chain: PUT /api/epics/{key}/order → GET /api/planning/unified → GET /api/board
// This is the heaviest write path: reorder shifts N epics in a transaction,
// then unified planning iterates all epics→stories→subtasks to recalculate dates.

import { BASE_URL } from '../config/env.js';
import { getTenantAuthHeaders } from '../lib/auth.js';
import { apiGet, apiPut } from '../lib/http.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

/**
 * Reorder + Forecast flow.
 * 1. GET /api/board?teamIds=N — load board, collect epic keys
 * 2. PUT /api/epics/{epicKey}/order — move random epic to random position
 * 3. GET /api/planning/unified?teamId=N — full forecast recalculation
 * 4. GET /api/planning/forecast?teamId=N — forecast view
 * 5. GET /api/board?teamIds=N — verify new order reflected
 *
 * @param {string} tenantSlug
 * @param {string} tenantName
 * @param {number} userIndex
 * @param {number} teamIndex
 */
export function reorderForecastFlow(tenantSlug, tenantName, userIndex, teamIndex) {
    const headers = getTenantAuthHeaders(tenantSlug, tenantName, userIndex);

    // 1. Load board to get epic keys for this team
    const boardRes = apiGet(
        `${BASE_URL}/api/board?teamIds=${teamIndex}`,
        headers,
        'reorder',
        'reorder_load_board'
    );

    if (boardRes.status !== 200) return;

    let epics;
    try {
        const body = JSON.parse(boardRes.body);
        epics = body.epics || body.items || [];
    } catch (e) {
        return;
    }

    if (epics.length < 2) return;

    // 2. Pick a random epic and move it to a random position
    const randomIdx = Math.floor(Math.random() * epics.length);
    const epic = epics[randomIdx];
    const epicKey = epic.issueKey || epic.key;
    if (!epicKey) return;

    // New position: random within [1, epics.length], different from current
    const currentPos = epic.manualOrder || (randomIdx + 1);
    let newPos;
    do {
        newPos = Math.floor(Math.random() * epics.length) + 1;
    } while (newPos === currentPos && epics.length > 1);

    apiPut(
        `${BASE_URL}/api/epics/${epicKey}/order`,
        { position: newPos },
        headers,
        'reorder',
        'reorder_move_epic'
    );

    sleep(0.2); // tiny pause — UI debounce

    // 3. Unified planning recalculation (heaviest call)
    apiGet(
        `${BASE_URL}/api/planning/unified?teamId=${teamIndex}`,
        headers,
        'forecast',
        'forecast_unified'
    );

    sleep(0.1);

    // 4. Forecast view
    apiGet(
        `${BASE_URL}/api/planning/forecast?teamId=${teamIndex}`,
        headers,
        'forecast',
        'forecast_view'
    );

    sleep(0.1);

    // 5. Board refresh — verify new order
    apiGet(
        `${BASE_URL}/api/board?teamIds=${teamIndex}`,
        headers,
        'reorder',
        'reorder_verify_board'
    );
}

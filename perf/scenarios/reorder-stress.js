// reorder-stress.js: Epic reorder + forecast recalculation stress test
//
// Focus: write contention on epic reorder (shifts in @Transactional),
// HikariCP pool saturation under concurrent reorder+forecast,
// UnifiedPlanningService CPU under parallel recalculations.
//
// 3 phases:
//   - Warm-up: 5 VUs, 30s — baseline latency
//   - Ramp:    5→50 VUs, 2 min — find degradation point
//   - Peak:    50 VUs sustained, 2 min — steady-state under contention
//   - Cool:    ramp down, 30s
//
// Total: ~5 min

import { reorderForecastFlow } from '../flows/reorder-forecast.js';
import { boardFlow } from '../flows/board.js';
import { getVuContext } from '../lib/tenant.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

export const options = {
    scenarios: {
        // Main: reorder + forecast chain
        reorder: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 5 },     // warm-up
                { duration: '1m',  target: 25 },    // ramp
                { duration: '1m',  target: 50 },    // ramp to peak
                { duration: '2m',  target: 50 },    // sustain peak
                { duration: '30s', target: 0 },     // cool down
            ],
            exec: 'reorderScenario',
        },
        // Background readers — simulate concurrent board viewers
        readers: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },
                { duration: '4m',  target: 10 },
                { duration: '30s', target: 0 },
            ],
            exec: 'readerScenario',
        },
    },
    thresholds: {
        // Reorder endpoint
        'api_duration_reorder': ['p(95)<1000', 'p(99)<3000'],
        // Forecast recalculation
        'api_duration_forecast': ['p(95)<2000', 'p(99)<5000'],
        // Overall
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<3000', 'p(99)<5000'],
    },
};

// Writers: reorder epics + trigger forecast recalc
export function reorderScenario() {
    const ctx = getVuContext();
    reorderForecastFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
    sleep(thinkTime());
}

// Readers: concurrent board loads (contend with writers on same data)
export function readerScenario() {
    const ctx = getVuContext();
    boardFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
    sleep(thinkTime());
}

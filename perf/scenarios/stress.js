// stress.js: 0→500 VUs, 3 min — find breaking point
// Only heaviest endpoints: Board + Timeline + Data Quality

import { boardFlow } from '../flows/board.js';
import { timelineFlow } from '../flows/timeline.js';
import { dataQualityFlow } from '../flows/data-quality.js';
import { getVuContext } from '../lib/tenant.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

export const options = {
    stages: [
        { duration: '30s', target: 50 },
        { duration: '30s', target: 150 },
        { duration: '30s', target: 300 },
        { duration: '30s', target: 500 },
        { duration: '30s', target: 500 },   // sustain
        { duration: '30s', target: 0 },      // ramp down
    ],
    thresholds: {
        // Relaxed thresholds for stress test — we expect degradation
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<3000'],
    },
};

const heavyFlows = ['board', 'timeline', 'data_quality'];

export default function () {
    const ctx = getVuContext();
    const flow = heavyFlows[Math.floor(Math.random() * heavyFlows.length)];

    switch (flow) {
        case 'board':
            boardFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
            break;
        case 'timeline':
            timelineFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
            break;
        case 'data_quality':
            dataQualityFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
            break;
    }

    sleep(thinkTime());
}

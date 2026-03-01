// soak.js: 100 VUs, 30 min constant — detect memory leaks, pool exhaustion

import { boardFlow } from '../flows/board.js';
import { timelineFlow } from '../flows/timeline.js';
import { metricsFlow } from '../flows/metrics.js';
import { getVuContext } from '../lib/tenant.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

export const options = {
    stages: [
        { duration: '1m', target: 100 },    // ramp up
        { duration: '28m', target: 100 },   // sustain
        { duration: '1m', target: 0 },       // ramp down
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000', 'p(99)<3000'],
        'api_duration_board': ['p(95)<1000'],
        'api_duration_timeline': ['p(95)<1500'],
        'api_duration_metrics': ['p(95)<600'],
    },
};

const soakFlows = ['board', 'metrics', 'timeline'];

export default function () {
    const ctx = getVuContext();
    const flow = soakFlows[Math.floor(Math.random() * soakFlows.length)];

    switch (flow) {
        case 'board':
            boardFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
            break;
        case 'metrics':
            metricsFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
            break;
        case 'timeline':
            timelineFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
            break;
    }

    sleep(thinkTime());
}

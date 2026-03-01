// smoke.js: 1 VU, 30s — sanity check that everything works

import { boardFlow } from '../flows/board.js';
import { metricsFlow } from '../flows/metrics.js';
import { getVuContext } from '../lib/tenant.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

export const options = {
    vus: 1,
    duration: '30s',
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<2000'],
    },
};

export default function () {
    const ctx = getVuContext();

    boardFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
    sleep(thinkTime());

    metricsFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
    sleep(thinkTime());
}

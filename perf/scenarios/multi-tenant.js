// multi-tenant.js: 3 parallel scenarios, 50 VUs per tenant

import { boardFlow } from '../flows/board.js';
import { dataQualityFlow } from '../flows/data-quality.js';
import { metricsFlow } from '../flows/metrics.js';
import { getTenantAuthHeaders } from '../lib/auth.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

export const options = {
    scenarios: {
        alpha: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '3m',  target: 50 },
                { duration: '30s', target: 0 },
            ],
            exec: 'tenantAlpha',
            tags: { tenant: 'alpha' },
        },
        beta: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '3m',  target: 50 },
                { duration: '30s', target: 0 },
            ],
            exec: 'tenantBeta',
            tags: { tenant: 'beta' },
        },
        gamma: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '3m',  target: 50 },
                { duration: '30s', target: 0 },
            ],
            exec: 'tenantGamma',
            tags: { tenant: 'gamma' },
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
        'http_req_duration{tenant:alpha}': ['p(95)<1000'],
        'http_req_duration{tenant:beta}': ['p(95)<1000'],
        'http_req_duration{tenant:gamma}': ['p(95)<1000'],
    },
};

function runTenantFlows(slug, name) {
    const userIndex = ((__VU - 1) % 10) + 1;
    const teamIndex = ((__VU - 1) % 3) + 1;

    const flows = ['board', 'metrics', 'data_quality'];
    const flow = flows[Math.floor(Math.random() * flows.length)];

    switch (flow) {
        case 'board':
            boardFlow(slug, name, userIndex, teamIndex);
            break;
        case 'metrics':
            metricsFlow(slug, name, userIndex, teamIndex);
            break;
        case 'data_quality':
            dataQualityFlow(slug, name, userIndex, teamIndex);
            break;
    }

    sleep(thinkTime());
}

export function tenantAlpha() {
    runTenantFlows('perf-alpha', 'alpha');
}

export function tenantBeta() {
    runTenantFlows('perf-beta', 'beta');
}

export function tenantGamma() {
    runTenantFlows('perf-gamma', 'gamma');
}

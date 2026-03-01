// load.js: 10→200 VUs, 5 min — sustained load with weighted flow selection
// Board 35%, Metrics 25%, Timeline 15%, Data Quality 10%, Projects 10%, Bug Metrics 5%

import { boardFlow } from '../flows/board.js';
import { timelineFlow } from '../flows/timeline.js';
import { metricsFlow } from '../flows/metrics.js';
import { dataQualityFlow } from '../flows/data-quality.js';
import { bugMetricsFlow } from '../flows/bug-metrics.js';
import { projectsFlow } from '../flows/projects.js';
import { getVuContext } from '../lib/tenant.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

export const options = {
    stages: [
        { duration: '30s', target: 10 },    // warm up
        { duration: '1m',  target: 50 },     // ramp up
        { duration: '1m',  target: 100 },    // increase
        { duration: '1m',  target: 200 },    // peak
        { duration: '1m',  target: 200 },    // sustain peak
        { duration: '30s', target: 0 },      // ramp down
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500', 'p(99)<2000'],
        'api_duration_board': ['p(95)<800', 'p(99)<3000'],
        'api_duration_timeline': ['p(95)<1000', 'p(99)<3000'],
        'api_duration_metrics': ['p(95)<400', 'p(99)<1500'],
        'api_duration_data_quality': ['p(95)<600', 'p(99)<2000'],
        'api_duration_bug_metrics': ['p(95)<300', 'p(99)<1000'],
        'api_duration_projects': ['p(95)<400', 'p(99)<1500'],
    },
};

// Weighted flow selection
const flows = [
    { weight: 35, name: 'board' },
    { weight: 25, name: 'metrics' },
    { weight: 15, name: 'timeline' },
    { weight: 10, name: 'data_quality' },
    { weight: 10, name: 'projects' },
    { weight: 5,  name: 'bug_metrics' },
];

function selectFlow() {
    const roll = Math.random() * 100;
    let cumulative = 0;
    for (const f of flows) {
        cumulative += f.weight;
        if (roll < cumulative) return f.name;
    }
    return 'board';
}

export default function () {
    const ctx = getVuContext();
    const flow = selectFlow();

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
        case 'data_quality':
            dataQualityFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
            break;
        case 'projects':
            projectsFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex);
            break;
        case 'bug_metrics':
            bugMetricsFlow(ctx.tenantSlug, ctx.tenantName, ctx.userIndex, ctx.teamIndex);
            break;
    }

    sleep(thinkTime());
}

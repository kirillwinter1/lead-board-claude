// env.js: Environment configuration for k6 perf tests

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Tenant configuration
export const TENANTS = [
    { slug: 'perf-alpha', name: 'alpha', userCount: 100 },
    { slug: 'perf-beta',  name: 'beta',  userCount: 100 },
    { slug: 'perf-gamma', name: 'gamma', userCount: 100 },
];

// Board loads issues from the first project key only (getProjectKey()),
// so only teams 1-50 (project PERF-A) are visible on the board.
// VUs must target teams within the visible range.
export const TEAMS_PER_TENANT = 50;
export const MEMBERS_PER_TEAM = 10;

// Project keys (F48 multi-project): 10 keys per tenant
export const PROJECT_KEYS = [
    'PERF-A', 'PERF-B', 'PERF-C', 'PERF-D', 'PERF-E',
    'PERF-F', 'PERF-G', 'PERF-H', 'PERF-I', 'PERF-J',
];

// Generate session ID for a tenant+user combination
export function getSessionId(tenantName, userIndex) {
    const paddedIndex = String(userIndex).padStart(3, '0');
    return `perf-session-${tenantName}-u${paddedIndex}`;
}

// Global thresholds
export const THRESHOLDS = {
    global: { p95: 500, p99: 2000, errorRate: 0.01 },
    board: { p95: 800, p99: 3000, errorRate: 0.01 },
    timeline: { p95: 1000, p99: 3000, errorRate: 0.01 },
    metrics: { p95: 400, p99: 1500, errorRate: 0.01 },
    dataQuality: { p95: 600, p99: 2000, errorRate: 0.01 },
    bugMetrics: { p95: 300, p99: 1000, errorRate: 0.01 },
    projects: { p95: 400, p99: 1500, errorRate: 0.01 },
};

// http.js: HTTP helpers with custom metrics and checks

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// Pre-declare all custom metrics in init context (k6 requirement)
const groupTrends = {
    board: new Trend('api_duration_board', true),
    timeline: new Trend('api_duration_timeline', true),
    metrics: new Trend('api_duration_metrics', true),
    data_quality: new Trend('api_duration_data_quality', true),
    bug_metrics: new Trend('api_duration_bug_metrics', true),
    projects: new Trend('api_duration_projects', true),
    reorder: new Trend('api_duration_reorder', true),
    forecast: new Trend('api_duration_forecast', true),
};
const groupErrors = new Counter('api_errors');

/**
 * Perform a GET request with metrics tracking.
 * @param {string} url - Full URL
 * @param {object} headers - Request headers
 * @param {string} group - Metric group name (e.g. 'board', 'timeline')
 * @param {string} [name] - Optional display name for the request
 * @returns {object} k6 response
 */
export function apiGet(url, headers, group, name) {
    const params = {
        headers,
        tags: { group, name: name || group },
    };

    const res = http.get(url, params);

    // Track duration per group
    if (groupTrends[group]) {
        groupTrends[group].add(res.timings.duration);
    }

    // Check for success
    const ok = check(res, {
        [`${name || group} status 200`]: (r) => r.status === 200,
    });

    if (!ok) {
        groupErrors.add(1, { group });
    }

    return res;
}

/**
 * Perform a POST request with metrics tracking.
 * @param {string} url - Full URL
 * @param {string|object} body - Request body
 * @param {object} headers - Request headers
 * @param {string} group - Metric group name
 * @param {string} [name] - Optional display name
 * @returns {object} k6 response
 */
export function apiPost(url, body, headers, group, name) {
    const params = {
        headers,
        tags: { group, name: name || group },
    };

    const payload = typeof body === 'string' ? body : JSON.stringify(body);
    const res = http.post(url, payload, params);

    if (groupTrends[group]) {
        groupTrends[group].add(res.timings.duration);
    }

    const ok = check(res, {
        [`${name || group} status 2xx`]: (r) => r.status >= 200 && r.status < 300,
    });

    if (!ok) {
        groupErrors.add(1, { group });
    }

    return res;
}

/**
 * Perform a PUT request with metrics tracking.
 * @param {string} url - Full URL
 * @param {string|object} body - Request body
 * @param {object} headers - Request headers
 * @param {string} group - Metric group name
 * @param {string} [name] - Optional display name
 * @returns {object} k6 response
 */
export function apiPut(url, body, headers, group, name) {
    const params = {
        headers,
        tags: { group, name: name || group },
    };

    const payload = typeof body === 'string' ? body : JSON.stringify(body);
    const res = http.put(url, payload, params);

    if (groupTrends[group]) {
        groupTrends[group].add(res.timings.duration);
    }

    const ok = check(res, {
        [`${name || group} status 2xx`]: (r) => r.status >= 200 && r.status < 300,
    });

    if (!ok) {
        groupErrors.add(1, { group });
    }

    return res;
}

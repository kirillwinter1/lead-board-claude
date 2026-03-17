# QA Report: F66 TeamMetricsPage Redesign — CTO Dashboard
**Date:** 2026-03-10
**Tester:** Claude QA Agent
**Version:** v0.66.0 (frontend shows v0.67.0)

## Summary
- **Overall Status:** PASS WITH ISSUES
- Unit tests: 923 total, 850+ passed, 73 failed (all Testcontainers/E2E — pre-existing Docker infra issue)
- All @WebMvcTest controller tests: PASS (6/6 test classes)
- All metrics service tests: PASS (77 tests including new ones)
- Frontend build: PASS (no TS errors)
- API tests: 1/3 new endpoints work (delivery-health OK, data-status and executive-summary 500)
- Visual: Page renders correctly, 3 bugs found

## Bugs Found

### BUG-101: Executive Summary KPI cards not rendering (HIGH)
- **Severity:** HIGH
- **Steps:** Navigate to /metrics → Look at Executive Summary section
- **Expected:** 6 KPI cards (Throughput, Cycle Time, Lead Time, Predictability, Utilization, Blocked/Aging) next to DeliveryHealthBadge
- **Actual:** Only DeliveryHealthBadge visible. KPI cards area is blank/empty. Console shows `GET /api/metrics/executive-summary?teamId=1&from=...&to=... 500`
- **Root cause:** `getExecutiveSummary()` in TeamMetricsService passes `dsrService` and `velocityService` as method params from controller. The method calls `calculateThroughput()` which calls `metricsRepository.getThroughputByWeek()` — fails with 500 on the local environment. However, the same native queries work fine for the `summary` endpoint that uses the same `calculateThroughput()` method via Vite proxy.
- **Note:** The `data-status` endpoint also returns 500 when called directly via curl to port 8080, but the DataStatusBar renders correctly on the frontend (via Vite proxy on 5173). This suggests the 500 for `executive-summary` may be a real bug in the service method, not just a tenant routing issue.

### BUG-102: Inconsistent trend thresholds between KPI cards and assignee metrics (MEDIUM)
- **Severity:** MEDIUM
- **Location:** `TeamMetricsService.java`
- **Details:** `calculateTrend()` (line 458) uses 10% threshold for UP/DOWN, but `buildKpiCard()` (line 443) uses 5% threshold. This means a 7% change shows as "UP" in KPI cards but "STABLE" in assignee trend column.
- **Fix:** Standardize on one threshold (recommend 5%)

### BUG-103: Russian text in Role Load alerts (LOW)
- **Severity:** LOW (pre-existing)
- **Location:** RoleLoadBlock component
- **Details:** Alert messages are in Russian: "SA недозагружены: 5.3%", "Дисбаланс нагрузки: DEV (49%) vs SA (5%)". All other new F66 components use English.
- **Note:** Pre-existing issue from F65, not introduced by F66. But the new recommendations below ("Capacity available for reallocation", "Rebalance assignments across roles") are in English — creating a language mix on the same component.

### BUG-104: Redundant unused `getExtendedMetricsByAssignee()` query method (LOW)
- **Severity:** LOW
- **Location:** `MetricsQueryRepository.java`, lines 101-153
- **Details:** The V1 method `getExtendedMetricsByAssignee()` is no longer called anywhere — replaced by `getExtendedMetricsByAssigneeV2()`. Dead code should be removed.

## Visual Review

### What Works Well
- **3-section layout** (Executive Summary / Diagnostics / Drilldown) renders correctly with collapsible sections
- **DateRangePicker** pills (30d/90d/180d/1y) work — URL updates with `?preset=30d`, all charts/tables reload
- **DataStatusBar** shows sync time, issue count, coverage percentage correctly
- **AlertStrip** renders 3 alert pills with correct severity colors, expand/collapse works
- **DeliveryHealthBadge** shows circular score (F 30 / F 41), 4 dimension bars visible
- **Time in Status Table** replaces old SVG chart — sortable columns, heatmap cell coloring works
- **AssigneeTable** shows delta columns (+11, +5, +1), search input present, DSR legend visible
- **Role Load** recommendations section renders at bottom ("Capacity available for reallocation")
- **Drilldown** section collapsed by default, expands to show DSR Breakdown, Velocity, Worklog Timeline, Burndown
- **Section collapse state** persists (localStorage)
- **URL state preservation** — teamId and preset sync to URL params

### Visual Issues
- Executive Summary section has large empty space where KPI cards should be (only HealthBadge on left)
- "Selected for Development" status truncated to "SELECTED FOR ..." in Time in Status table (long status names)
- Health dimension labels truncated: "Spee" instead of "Speed", "Capa" instead of "Capacity" — needs wider display

## Code Review Issues

### Frontend
| # | Severity | Issue | File |
|---|----------|-------|------|
| 1 | HIGH | Missing `onAlerts` in useEffect dependency array | DeliveryHealthBadge.tsx:38 |
| 2 | MEDIUM | Hardcoded colors (#0052CC, #DFE1E6, #FAFBFC) instead of constants | DateRangePicker.tsx, DataStatusBar.tsx |
| 3 | MEDIUM | Missing loading state in DataStatusBar | DataStatusBar.tsx |
| 4 | LOW | Alert pills use array index as React key | AlertStrip.tsx:19 |
| 5 | LOW | Missing aria-label/aria-expanded on MetricsSection, DateRangePicker buttons | Multiple files |

### Backend
| # | Severity | Issue | File |
|---|----------|-------|------|
| 1 | HIGH | Inconsistent trend thresholds (10% vs 5%) | TeamMetricsService.java:443 vs 458 |
| 2 | MEDIUM | NPE risk if `getStoryPipelineStatuses()` returns null | TeamMetricsService.java:160 |
| 3 | MEDIUM | `percentile()` edge case: `lower >= size` when p=1.0 | TeamMetricsService.java:541 |
| 4 | LOW | Dead code: `getExtendedMetricsByAssignee()` V1 method | MetricsQueryRepository.java:101-153 |

## Test Coverage

### Covered
- DeliveryHealthService: 4 tests (allGood, overloaded, noData, gradeBoundaries)
- TeamMetricsService: getDataStatus (2 tests), calculateByAssignee outlier detection (1 test)
- All controller endpoints have @WebMvcTest coverage

### Gaps
- No test for `getExecutiveSummary()` — this method is complex (calls 5 sub-services) and is the one failing at runtime
- No test for edge case: `calculateByAssignee()` with empty previous period data
- No frontend tests for any new components (ExecutiveSummaryRow, MetricsSection, TimeInStatusTable, etc.)
- No test for `percentile()` with p=1.0 boundary

## Recommendations

1. **P0:** Fix `getExecutiveSummary()` — add unit test with mocked services, debug the 500 error
2. **P1:** Add `onAlerts` to useEffect dependency array in DeliveryHealthBadge
3. **P1:** Standardize trend threshold to 5% across both methods
4. **P2:** Replace hardcoded colors with constants from `colors.ts`
5. **P2:** Add loading state to DataStatusBar
6. **P2:** Widen health dimension labels or use full names
7. **P3:** Remove dead V1 query method
8. **P3:** Add aria attributes to interactive elements

## Screenshots
- `ai-ru/testing/screenshots/f66_metrics_login.png` — Initial page load (auth redirect, then full page)
- `ai-ru/testing/screenshots/f66_metrics_full.png` — Full page with all sections expanded
- `ai-ru/testing/screenshots/f66_alert_expanded.png` — Alert strip with expanded "Low Predictability" alert

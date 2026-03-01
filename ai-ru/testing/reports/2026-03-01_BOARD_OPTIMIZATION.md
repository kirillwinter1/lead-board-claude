# QA Report: Board Endpoint Optimization (N+1 Fix, Cache, DQ Decoupling)
**Date:** 2026-03-01
**Tester:** Claude QA Agent

## Summary
- **Overall status: PASS WITH ISSUES**
- Unit tests: 785 passed, 0 failed
- API tests: 18 passed, 0 failed
- Performance: p50 132ms (no DQ), p50 384ms (with DQ) on 61K issues — **massive improvement**
- Bugs found: 2 Medium, 2 Low

## Changes Tested

6 optimizations for `GET /api/board`:
1. **HikariCP pool size:** 10 → 30 (`application.yml`)
2. **BoardService N+1 fix:** pre-built `subtasksByParent` map, eliminated per-story DB queries
3. **O(n^2) hierarchy fix:** `childrenByParentKey` index, eliminated per-epic nested loop
4. **UnifiedPlanningService N+1 fix:** batch `findByParentKeyIn()` for all stories/subtasks
5. **TTL cache (60s):** `ConcurrentHashMap<Long, CachedPlan>` with invalidation on reorder/sync
6. **DQ decoupling:** `includeDQ` parameter (default=false), frontend sends `includeDQ=true`

## Performance Results

| Metric | Before (Run #1) | After (Measured) | Target |
|--------|-----------------|------------------|--------|
| Board p50 (no DQ) | 30,000ms (timeout) | **132ms** | 500-2000ms |
| Board p50 (with DQ) | 30,000ms (timeout) | **384ms** | 500-2000ms |
| First request (cold) | 30,000ms+ | **201ms** | N/A |
| Cached request | N/A | **132ms** | N/A |
| 5 concurrent requests | Timeout/errors | **~200ms each** | <3000ms |
| Error rate | 84.4% | **0%** | <10% |

## API Test Results

| # | Test | Result | Details |
|---|------|--------|---------|
| 1 | Board no DQ (perf-alpha, 61K issues) | PASS | 200ms, 100 epics, no alerts |
| 2 | Board with DQ | PASS | 549ms, 648 alerts across levels |
| 3 | Cache hit (2nd request) | PASS | 186ms |
| 4 | Cache hit (3rd request) | PASS | 132ms |
| 5 | Team filter | PASS | 117ms, 20 epics for teamId=1 |
| 6 | Query filter | PASS | 146ms, correct filtering |
| 7 | Status filter | PASS | 144ms, 40 epics "In Progress" |
| 8 | Pagination (page=1, size=10) | PASS | 128ms, 10 items |
| 9 | No auth | PASS | 401 |
| 10 | Invalid session | PASS | 401 |
| 11 | Negative page | PASS | 200 (graceful) |
| 12 | Cross-tenant isolation | PASS | 401 (session from other tenant) |
| 13 | 5 concurrent requests | PASS | All ~200ms, no errors |
| 14 | Response structure validation | PASS | All fields present, correct types |
| 15 | DQ alerts validation | PASS | Epic/Story/Subtask alerts present |
| 16 | 10 sequential (no DQ) avg | PASS | **132ms avg** |
| 17 | 10 sequential (with DQ) avg | PASS | **384ms avg** |
| 18 | perf-beta tenant | PASS | 153ms, 100 epics |

## Auto-Test Review

### BoardServiceTest (23 tests)
- **Quality: Good**
- Covers: basic scenarios, hierarchy, progress, filtering, sorting, pagination, DQ alerts, team mapping, error handling
- `@DisplayName` on all tests
- Properly updated for 6-arg `getBoard()` signature
- DQ test correctly uses `includeDQ=true`
- **Gap:** No test for `includeDQ=false` explicitly verifying zero alerts (covered by other tests implicitly)
- **Gap:** No test for `mapToNode()` with pre-built `subtasksByParent` map verification

### UnifiedPlanningServiceTest (8 tests)
- **Quality: Good**
- Covers: basic planning, parallel stories, day splitting, no estimate warning, dependencies, role transitions, no capacity, done stories
- Properly updated with `findByParentKeyIn` mocks
- **Gap:** No test for cache behavior (hit, miss, expiration, invalidation)
- **Gap:** No test for `invalidatePlanCache()` / `invalidateAllPlanCaches()`

### IssueOrderServiceTest (20 tests)
- **Quality: Good**
- Covers: epic reorder, story reorder, assign order, normalize orders
- Updated with `UnifiedPlanningService` mock
- **Gap:** No verification that `invalidatePlanCache()` is called after reorder

### BoardControllerTest (5 tests)
- **Quality: Adequate**
- Updated for 6-arg mock matchers
- **Gap:** No test for `includeDQ=true` parameter

## Bugs Found

### Medium

**BUG-136: Cache not invalidated when team planning config changes**
- **Location:** `TeamService.updatePlanningConfig()` (line 214)
- **Description:** When risk buffer, grade coefficients, WIP limits, or story duration config is updated via `PUT /api/teams/{id}/planning-config`, the `UnifiedPlanningService.planCache` is not invalidated. Cached plans continue using stale config for up to 60 seconds.
- **Impact:** Users change planning parameters but board shows old forecast for up to 1 minute.
- **Fix:** Add `unifiedPlanningService.invalidatePlanCache(teamId)` call in `TeamService.updatePlanningConfig()`.

**BUG-137: Cache not invalidated when team members change**
- **Location:** `TeamService` — member add/update/deactivate methods (lines 138, 164, 178)
- **Description:** When team members are added, updated (role/grade/hours), or deactivated, the plan cache is not invalidated. Plans use stale member data for up to 60 seconds.
- **Impact:** Adding/removing a developer doesn't immediately affect planning forecast.
- **Fix:** Add `unifiedPlanningService.invalidatePlanCache(teamId)` calls in member mutation methods.

### Low

**BUG-138: Unbounded ConcurrentHashMap growth potential**
- **Location:** `UnifiedPlanningService.planCache` (line 53)
- **Description:** The `planCache` ConcurrentHashMap grows without bound. While expired entries are replaced on access, entries for deleted/deactivated teams are never evicted. In a multi-tenant SaaS with many tenants, each with multiple teams, this could accumulate over time.
- **Impact:** Minimal — each entry is a `UnifiedPlanningResult` (planning data for one team). With realistic team counts (<100 per tenant), memory impact is negligible. Self-healing on restart.
- **Recommendation:** Consider adding a `cleanExpiredEntries()` method called periodically, or use Caffeine cache with TTL.

**BUG-139: Frontend always requests DQ (negates optimization benefit)**
- **Location:** `frontend/src/hooks/useBoardData.ts` (line 17)
- **Description:** `useBoardData` always sends `includeDQ: true`, meaning every board page load incurs the DQ overhead (384ms vs 132ms). The optimization of making DQ optional is only useful if the frontend conditionally enables it.
- **Impact:** Board is still 3x slower than it could be. The DQ decoupling infrastructure works, but the frontend doesn't take advantage of it.
- **Recommendation:** Load board without DQ first (fast render), then lazy-load DQ alerts separately. Or only request DQ when the Data Quality panel/column is visible.

## Code Review Notes

### Positive
- N+1 elimination pattern is clean and consistent: pre-build map in one query, then O(1) lookup
- `findByParentKeyIn()` already existed in repository — good reuse
- Cache implementation is simple and effective (ConcurrentHashMap + TTL record)
- DQ decoupling is backwards-compatible (default false, frontend opts in)
- All 785 tests pass, proper mock updates for changed signatures
- `LENIENT` strictness on tests avoids brittle mock failures

### Concerns
- SQL IN clauses with 15K+ keys (story subtask batch): works for PostgreSQL, but could be an issue if migrating to MySQL (64K parameter limit). Consider chunking for safety.
- `calculatePlanUncached()` loads allStories via `findByParentKeyIn(epicKeys)` and then loads subtasks via `findByParentKeyIn(allStoryKeys)`. This is 2 queries per cache miss — good, but the allStories result is filtered to `isStoryOrBug()` only. Non-story/non-bug children are loaded but discarded.

## Test Coverage Gaps

1. **Cache behavior tests:** No unit tests for cache hit/miss/expiration/invalidation in UnifiedPlanningServiceTest
2. **Cache invalidation verification:** IssueOrderServiceTest doesn't verify `invalidatePlanCache()` is called
3. **DQ parameter test in controller:** BoardControllerTest doesn't test `includeDQ=true` parameter
4. **Large dataset test:** No test for IN clause behavior with many keys

## Recommendations

1. **Fix BUG-136/137** — Add cache invalidation to TeamService (quick fix, high value)
2. **Fix BUG-139** — Lazy-load DQ in frontend to fully realize the 3x speedup
3. **Add cache tests** — At minimum test cache hit, miss, and invalidation
4. **Consider Caffeine** — Replace ConcurrentHashMap with Caffeine cache for automatic eviction, max size, and TTL support
5. **Run perf test** — Execute `./run.sh reorder` to get official Run #2 numbers

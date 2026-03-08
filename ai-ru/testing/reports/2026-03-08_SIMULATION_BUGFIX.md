# QA Report: Simulation Bugfix (BUG-76/77/78/80/81/85/88)
**Date:** 2026-03-08
**Tester:** Claude QA Agent
**Scope:** SimulationService, SimulationController, SimulationScheduler, SimulationNotFoundException, T10 migration — bugfix deployment

## Summary
- **Overall Status:** FAIL — found 2 new bugs in the fix itself
- **Backend unit tests:** 31 tests ALL PASS (4 test classes)
- **API tests:** 15 checks — 12 PASS, 2 BUG, 1 NOTE
- **Production deployment:** OK, app healthy after container recreation

## Bugs Fixed (verified)

| Original Bug | Fix | API Test Result |
|---|---|---|
| BUG-76 (TOCTOU race) | Partial unique index `idx_sim_logs_running` + `saveAndFlush` | PASS — DB rejects duplicate RUNNING |
| BUG-77 (Scheduler no TenantContext) | TenantRepository iteration + TenantContext.set/clear | PASS — code review verified |
| BUG-78 (null teamId → 500) | `IllegalArgumentException` + `@ExceptionHandler` → 400 | PASS — returns `{"error":"teamId is required"}` |
| BUG-80 (fromJson null → NPE) | Returns empty list / default SimulationSummary | PASS — code review verified |
| BUG-81 (toJson silent "[]") | Throws RuntimeException | PASS — code review verified |
| BUG-85 (partial date range ignored) | Throws IllegalArgumentException if only from or to | PASS — returns 400 |
| BUG-88 (permanent RUNNING lock) | `recoverStuckSimulations()` before each run | **FAIL** — see BUG-94 |
| BUG-89 (no warning for empty teamIds) | `log.warn()` when teamIds empty | PASS — code review verified |

## New Bugs Found

### Critical (1)

| Bug ID | Description | File | Status |
|---|---|---|---|
| BUG-94 | **`recoverStuckSimulations()` doesn't work within `@Transactional runSimulation()`.** Hibernate flushes INSERTs before UPDATEs. The recovery `save()` (UPDATE status='FAILED') is queued but not flushed. The subsequent `saveAndFlush()` (INSERT new RUNNING) flushes both — INSERT first, then UPDATE. INSERT fails because the old RUNNING row still exists (unique index). Transaction rolls back entirely. **Result: stuck RUNNING row is never recovered by API calls.** | `SimulationService.java:72` | OPEN |

**Root Cause:** Hibernate flush order is: 1) INSERTs, 2) UPDATEs, 3) DELETEs. Within a single transaction, the recovery UPDATE hasn't reached the DB when the new INSERT is attempted.

**Fix Required:** Run `recoverStuckSimulations()` in a separate transaction (e.g., `@Transactional(propagation = REQUIRES_NEW)` on a separate method, or use `TransactionTemplate`). This ensures the UPDATE commits to DB before the INSERT attempt.

### High (1)

| Bug ID | Description | File | Status |
|---|---|---|---|
| BUG-95 | **GET /api/simulation/logs/{id} returns 500 instead of 404 for nonexistent ID.** `SimulationNotFoundException` has `@ResponseStatus(HttpStatus.NOT_FOUND)` but `GlobalExceptionHandler` has a catch-all `Exception.class` handler that intercepts it first, returning 500 with generic error message. | `SimulationService.java:137`, `GlobalExceptionHandler.java:68` | OPEN |

**Fix Required:** Add `SimulationNotFoundException` handler in `GlobalExceptionHandler` or `SimulationController` `@ExceptionHandler`.

## API Testing Details

| # | Test | HTTP | Expected | Actual | Result |
|---|---|---|---|---|---|
| 1 | GET /status (auth + tenant) | 200 | `{"running":false}` | `{"running":false}` | PASS |
| 2 | GET /status (no auth) | 401 | Unauthorized | 401 | PASS |
| 3 | GET /logs?teamId=1 | 200 | Array of logs | 24 logs returned | PASS |
| 4 | GET /logs?teamId=9999 | 200 | Empty array | `[]` | PASS |
| 5 | GET /logs?teamId=1&from=2026-01-01 (only from) | 400 | Error message | `{"error":"Both 'from' and 'to' dates are required..."}` | PASS |
| 6 | GET /logs/17 (existing) | 200 | Log details | Full log with actions | PASS |
| 7 | GET /logs/99999 (nonexistent) | 404 | Not found | **500** "internal error" | **BUG-95** |
| 8 | POST /dry-run `{"date":"2026-03-08"}` (null teamId) | 400 | Error | `{"error":"teamId is required"}` | PASS |
| 9 | POST /dry-run `{}` (empty body) | 400 | Error | `{"error":"teamId is required"}` | PASS |
| 10 | POST /dry-run (no auth) | 401 | Unauthorized | 401 | PASS |
| 11 | POST /dry-run Sunday `{"teamId":1,"date":"2026-03-08"}` | 200 | 0 actions | 0 actions, COMPLETED | PASS |
| 12 | POST /dry-run workday `{"teamId":1,"date":"2026-03-06"}` | 200 | Actions planned | 200, actions planned | PASS |
| 13 | POST /dry-run with stuck RUNNING row (15min old) | 200 | Recovery + success | **409** "already running" | **BUG-94** |
| 14 | GET /status without tenant header | 500 | Error | 500 (simulation_logs not in public schema) | NOTE — expected in multi-tenant |
| 15 | Unique index: 2nd INSERT RUNNING same team | error | Rejected | `duplicate key violation` | PASS |

## Container Recreation Test

| Test | Result |
|---|---|
| `docker compose down && up` | PASS — app starts, health OK |
| Tenant migration T10 applied | PASS — `idx_sim_logs_running` exists |
| No simulation errors on startup | PASS — no @PostConstruct crash |
| Stuck RUNNING recovery after restart | **FAIL** — recovery only runs on next API call, and BUG-94 prevents it |

## Test Coverage Analysis

### Existing Tests (31 total — ALL PASS)

| Class | Tests | Notes |
|---|---|---|
| SimulationServiceTest | 7 | New: nullTeamId, concurrentGuard (DB index) |
| SimulationPlannerTest | 9 | Good coverage of planning logic |
| SimulationExecutorTest | 10 | Transition, worklog, assign, multi-step |
| SimulationDeviationTest | 6 | Daily/speed deviation, rounding |

### Test Gaps

1. **No test for `recoverStuckSimulations()`** — the critical method has zero tests
2. **No test for `getLogs()` partial date range** — BUG-85 fix untested
3. **No test for `getLog()` with nonexistent ID** — BUG-95 untested
4. **No `@DisplayName` on any test** (BUG-93 from original report, still open)
5. **SimulationController still has 0 tests** (BUG-92 from original report)
6. **SimulationScheduler still has 0 tests** — TenantContext logic unverified by tests

## Code Review Findings

### Good
- Spring-managed `ObjectMapper` instead of manual creation (fixed)
- Proper `try/catch` in error save path (BUG-88 partial fix)
- `@ExceptionHandler` for `IllegalArgumentException` and `IllegalStateException` in controller
- Tenant-aware scheduler matches `TenantSyncScheduler` pattern

### Concerns
1. **Comment says "V50" but migration is T10** — `SimulationService.java:77` still says "partial unique index (V50)"
2. **Double recovery call**: `runForTeams()` in scheduler calls `recoverStuckSimulations()`, AND `runSimulation()` also calls it — redundant when called from scheduler
3. **`recoverStuckSimulations()` is public** but only called internally — should be package-private or moved to a dedicated recovery service

## Recommendations

### P0 (Critical — blocks "works after container recreation")
1. **Fix BUG-94**: Run `recoverStuckSimulations()` in a separate transaction:
   ```java
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public void recoverStuckSimulations() { ... }
   ```
   Or extract to a separate `@Service` class (since Spring proxied `@Transactional` doesn't work on self-calls).

### P1 (High)
2. **Fix BUG-95**: Add to `GlobalExceptionHandler`:
   ```java
   @ExceptionHandler(SimulationNotFoundException.class)
   public ResponseEntity<Map<String, String>> handleSimNotFound(SimulationNotFoundException ex) {
       return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
   }
   ```

### P2 (Medium)
3. Add unit tests for `recoverStuckSimulations()`, `getLogs()` partial date, `getLog()` not found
4. Fix stale comment "V50" → "T10" in `SimulationService.java`
5. Remove double recovery call (keep only in scheduler's `runForTeams`)

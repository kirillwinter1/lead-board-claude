# QA Report: Simulation (F28)
**Date:** 2026-02-25
**Tester:** Claude QA Agent
**Scope:** SimulationService, SimulationPlanner, SimulationExecutor, SimulationDeviation, SimulationScheduler, SimulationController (13 files, ~1300 LOC main + 810 LOC tests)

## Summary
- **Overall Status:** FAIL — found Critical and High bugs
- **Backend unit tests:** 25 tests ALL PASS (4 test classes)
- **Frontend:** No frontend component (backend-only module)
- **API tests:** 23 checks — 14 PASS, 5 BUG, 4 NOTE
- **Code review:** 13 main files, 4 test files

## Bugs Found

### Critical (2)

| Bug ID | Description | File | Status |
|--------|------------|------|--------|
| BUG-76 | **TOCTOU race condition in concurrent simulation guard.** `existsByStatus("RUNNING")` check followed by `save(entity)` with status "RUNNING" — two threads can pass the check simultaneously. No DB-level locking (SELECT FOR UPDATE / advisory lock). Default READ_COMMITTED isolation allows interleaving. | `SimulationService.java:41-52` | OPEN |
| BUG-77 | **SimulationScheduler ignores TenantContext.** Iterates `properties.getTeamIds()` and calls `runSimulation()` without setting tenant schema. All DB queries run against default/public schema instead of tenant-specific schema. In multi-tenant deployment, simulation reads/writes wrong tenant data. | `SimulationScheduler.java:26-36` | OPEN |

### High (4)

| Bug ID | Description | File | Status |
|--------|------------|------|--------|
| BUG-78 | **POST /dry-run and /run return 500 on null/missing teamId.** No input validation on `SimulationRunRequest`. Missing teamId causes NPE in planner. Expected: 400 Bad Request. | `SimulationController.java:26`, `SimulationRunRequest.java` | OPEN |
| BUG-79 | **POST /dry-run with non-existent teamId (9999) returns 500.** `calculatePlan(teamId)` throws exception for unknown team. Expected: 404 Not Found or 400 with message. | `SimulationController.java:26-29` | OPEN |
| BUG-80 | **fromJson() returns null on corrupt JSON, callers don't check → NPE.** If `simulation_logs.actions` or `summary` JSONB is corrupted, `fromJson()` returns null. `toDto()` passes null to `SimulationLogDto` constructor. Any log read (GET /logs, GET /logs/{id}) will 500 NPE on corrupt row. | `SimulationService.java:117-122, 149-166` | OPEN |
| BUG-81 | **toJson() swallows serialization errors, saves "[]".** If serialization fails (e.g., circular reference), actions saved as "[]" and status is "COMPLETED". Summary also saved as "[]" which is invalid SimulationSummary JSON (array not object). Simulation data silently lost. | `SimulationService.java:140-146` | OPEN |

### Medium (7)

| Bug ID | Description | File | Status |
|--------|------------|------|--------|
| BUG-82 | **N+1 query problem.** `findByParentKey()` called per story per phase in `processPhase()`, `planCatchUpTransitions()`, and `planStoryTransition()`. For 20 epics x 5 stories x 3 phases: ~700 individual SELECT queries per simulation run. | `SimulationPlanner.java:144,256,300` | OPEN |
| BUG-83 | **No duplicate guard for same team+date.** Multiple dry-runs for the same team and date create new log entries each time. No deduplication check. Observed: 3 logs created from 3 dry-run calls (id=1,5,7). | `SimulationService.java:40-90` | OPEN |
| BUG-84 | **Systematic over-logging due to 0.5h rounding minimum.** When remaining work is <0.5h (e.g., 0.1h), worklog rounds UP to 0.5h minimum. Over many simulation runs, this inflates actual effort metrics in Jira. | `SimulationPlanner.java:420-422` | OPEN |
| BUG-85 | **getLogs() partial date filtering silently ignored.** Providing only `from` without `to` (or vice versa) silently returns ALL logs unfiltered. Expected: 400 error or default the missing bound. | `SimulationController.java:40-45` | OPEN |
| BUG-86 | **getLogs() has no pagination.** Returns all logs with full JSONB actions/summary payloads. Long-running simulation history can cause memory pressure and slow responses. | `SimulationService.java:103-111` | OPEN |
| BUG-87 | **Deviation probability config not validated.** If `onTrackChance + earlyChance + delayChance + severeDelayChance != 1.0`, distribution is silently biased. Values that sum to less than 1.0 always fall through to severe delay. | `SimulationDeviation.java:47-59` | OPEN |
| BUG-88 | **Permanent "RUNNING" lock on DB failure during error handling.** If the catch block's `logRepository.save()` also fails (DB connection lost), status stays "RUNNING" forever. All subsequent simulations rejected with "Another simulation is already running". Requires manual DB fix. | `SimulationService.java:82-90` | OPEN |

### Low (5)

| Bug ID | Description | File | Status |
|--------|------------|------|--------|
| BUG-89 | **No warning log when scheduler enabled without teamIds.** `simulation.enabled=true` with empty `teamIds` silently does nothing. Admin has no indication of misconfiguration. | `SimulationScheduler.java:29-36`, `SimulationProperties.java:14` | OPEN |
| BUG-90 | **Fixed 100ms rate limit without adaptive backoff.** No response to Jira 429 rate limit headers. Failed requests not retried. For 100+ actions, adds 10+ seconds of artificial delay. | `SimulationExecutor.java:43` | OPEN |
| BUG-91 | **Hardcoded "Epic" fallback in getEpicTypeNames().** `getEpicTypeNames().stream().findFirst().orElse("Epic")` violates project rule against hardcoded type names. | `SimulationPlanner.java:339` | OPEN |
| BUG-92 | **0 tests for SimulationController and SimulationScheduler.** No @WebMvcTest for 5 API endpoints. RBAC (`@PreAuthorize("hasRole('ADMIN')")`) enforcement not verified. | All test files | OPEN |
| BUG-93 | **No @DisplayName on any of 25 tests.** All 4 test classes lack readable test names for CI reports. | All test files | OPEN |

---

## API Testing Details

### Simulation Endpoints (23 tests)

| # | Test | HTTP | Result |
|---|------|------|--------|
| 1 | GET /status | 200 | PASS — `{"running":false}` |
| 2 | GET /status no auth | 401 | PASS — auth enforced |
| 3 | GET /logs (no params) | 400 | PASS — teamId required |
| 4 | GET /logs?teamId=1 | 200 | PASS — `[]` (empty) |
| 5 | GET /logs?teamId=9999 | 200 | PASS — `[]` (no error, just empty) |
| 6 | GET /logs date range | 200 | PASS — `[]` |
| 7 | GET /logs only from (no to) | 200 | **BUG-85** — silently ignores from param |
| 8 | GET /logs/{id} non-existent | 400 | NOTE — 400 instead of 404 |
| 9 | GET /logs/{id} invalid (abc) | 400 | PASS — type mismatch |
| 10 | POST /dry-run (happy) | 200 | PASS — 7 actions planned |
| 11 | POST /dry-run no auth | 401 | PASS — auth enforced |
| 12 | POST /dry-run no teamId | 500 | **BUG-78** — NPE, should be 400 |
| 13 | POST /dry-run teamId=9999 | 500 | **BUG-79** — should be 404 |
| 14 | POST /dry-run empty body | 500 | **BUG-78** — NPE, should be 400 |
| 15 | POST /dry-run Sunday | 200 | PASS — 0 actions (non-workday) |
| 16 | POST /dry-run bad date | 400 | PASS — Jackson rejects |
| 17 | POST /dry-run teamId=-1 | 500 | **BUG-78** — should be 400 |
| 18 | GET /logs/1 (after dry-run) | 200 | PASS — id=1, 7 actions |
| 19 | GET /logs?teamId=1 (count) | 200 | PASS — 2 logs (workday + Sunday) |
| 20 | POST /dry-run duplicate | 200 | **BUG-83** — no dedup, id=7 created |
| 21 | POST /run (real) | SKIP | SKIPPED — would modify Jira data |
| 22 | GET /logs inverted range | 200 | NOTE — silently returns `[]` |
| 23 | Check assigneeAccountId | 200 | NOTE — null for 6/7 actions (only ASSIGN has it) |

---

## Dry-Run Action Analysis

Dry-run for team 1, date 2026-02-25 produced 7 actions:

| # | Type | Issue | Assignee | Details |
|---|------|-------|----------|---------|
| 1 | WORKLOG | LB-99 (Тестирование) | Александр | 5.5h, QA phase |
| 2 | TRANSITION | LB-99 | Александр | В работе → Готово (work completed) |
| 3 | WORKLOG | LB-248 (Разработка) | Kirill Reshetov | 5.5h, DEV phase (20h remaining) |
| 4 | ASSIGN | LB-229 (Аналитика) | Елисей | SA phase starting |
| 5 | TRANSITION | LB-229 | Елисей | Новое → Проверка (SA active) |
| 6 | WORKLOG | LB-229 | Елисей | 7.5h, SA phase (8h remaining) |
| 7 | TRANSITION | LB-96 (История) | — | Analysis Review → Готово (all subtasks done) |

**Observations:**
- Actions look logically correct: worklog before completion, assign before transition
- Non-workday (Sunday) correctly produces 0 actions
- `assigneeAccountId` is null on 6/7 actions — only ASSIGN populates it. This means the executor would use system/BasicAuth credentials for WORKLOG and TRANSITION actions (not per-user OAuth)

---

## Test Coverage Gaps

### Backend

| Class | Tests | Coverage | Gap |
|-------|-------|----------|-----|
| SimulationService | 6 | ~60% | getLog, getLogs, toJson error, fromJson error |
| SimulationPlanner | 9 | ~50% | planDatabaseCatchUp, planEpicTransition, worklog capping, null assignee, member not found |
| SimulationExecutor | 10 | ~70% | InterruptedException, empty transitions list, worklog/assign errors, category-based match |
| SimulationDeviation | 6 | ~80% | Custom config, zero/negative baseHours |
| SimulationController | 0 | **0%** | ALL 5 endpoints untested |
| SimulationScheduler | 0 | **0%** | Scheduled execution untested |
| SimulationSummary | 0 | 0% | fromActions() untested |

### Test Quality Issues

1. **All tests use `assertEquals`/`assertTrue`** instead of AssertJ `assertThat()` — less readable, worse error messages
2. **No `@DisplayName`** on any of 25 test methods
3. **`Strictness.LENIENT`** used everywhere, hiding dead stubs
4. **SimulationDeviationTest.rollSpeedDeviation** — probabilistic test with 10000 iterations, inherently flaky (should use seeded Random)
5. **SimulationExecutorTest.execute_multipleActions_processedInOrder** — asserts count but NOT actual order
6. **Deviation mock bypassed** — planner tests always return base value, never testing deviation-adjusted hours

---

## Code Review Findings (recommendations, not bugs)

1. **SimulationService creates own ObjectMapper** instead of using Spring-managed bean. May miss global Jackson config (modules, serializers).
2. **Status fallback to English** in `getDoneStatusName()`/`getInProgressStatusName()` — returns "Done"/"In Progress" when no status mapped. Won't match Russian workflows "Готово"/"В работе".
3. **Non-deterministic phase iteration** if `story.phases()` returns HashMap. Phase processing order matters for action sequencing.
4. **POST /run is synchronous** — large simulations (100+ actions) can timeout. Should return 202 Accepted + job ID.
5. **SimulationRunRequest has `Long teamId`** (nullable). Should use `long teamId` (primitive) with `@NotNull`.

---

## Recommendations

### P0 (Critical)
1. **Fix TOCTOU race condition** — use `SELECT ... FOR UPDATE SKIP LOCKED` or PostgreSQL advisory lock for the concurrent guard
2. **Add TenantContext to SimulationScheduler** — iterate tenant_users/tenants like TenantSyncScheduler does

### P1 (High)
3. **Add input validation** — `@Valid` on SimulationRunRequest, `@NotNull Long teamId`, handle non-existent team with 404
4. **Fix null safety in fromJson/toJson** — return Optional or throw, don't return null/silent "[]"
5. **Add controller tests** — @WebMvcTest for all 5 endpoints (validation, auth, routing)

### P2 (Medium)
6. **Fix N+1 queries** — batch load subtasks per epic, cache in-memory during planning
7. **Add pagination** to getLogs endpoint
8. **Validate deviation probabilities** — check sum == 1.0 at startup, fail fast
9. **Add stuck RUNNING recovery** — @PostConstruct check like SyncService already does
10. **Add duplicate guard** — check if log exists for team+date before creating new one

### P3 (Low)
11. **Add @DisplayName** to all test methods
12. **Add warning log** when scheduler has no teamIds
13. **Switch to AssertJ** assertions
14. **Add adaptive rate limiting** — respond to Jira 429 headers

# QA Report: F70 Customer-Driven Quarter Planning

**Date:** 2026-05-16
**Tester:** Claude QA Agent
**Branch:** feature/f70-customer-quarter-planning
**Version:** 0.70.0
**Spec:** [ai-ru/features/F70_CUSTOMER_QUARTER_PLANNING.md](../../features/F70_CUSTOMER_QUARTER_PLANNING.md)

## Summary

- **Overall:** PASS WITH ISSUES
- **Unit tests:** 68 F70-specific tests passed, 0 failed
- **API tests:** 19/19 smoke scenarios passed
- **Visual UI:** Partial (logged-out screenshot only — see "Limitations" below)
- **Bugs fixed:** 0 (no Critical/High found)
- **Frontend build:** PASS (v0.70.0, 2389 modules, 0 errors)
- **Backend health:** OK at v0.70.0

## Unit Test Breakdown (F70)

| Suite | Tests | Pass | Fail |
|---|---|---|---|
| `QuarterlyPlanningServiceTest` | 42 | 42 | 0 |
| `QuarterlyPlanningControllerTest` | 14 | 14 | 0 |
| `QuarterlyPlanningControllerSecurityTest` | 6 | 6 | 0 |
| `ProjectLabelPersistenceServiceTest` | 3 | 3 | 0 |
| `EpicLabelPersistenceServiceTest` | 3 | 3 | 0 |
| **Total** | **68** | **68** | **0** |

Spec expected ~22 new F70 tests — actual count is higher (matches spec's "Service +14, Controller +5, ControllerSecurity +2, ProjectLabelPersistenceService +3, regression +1 = 25 new", plus pre-existing).

> Note: full `./gradlew test` shows pre-existing failures in `*ComponentTest*` classes (Board, Forecast, Metrics, Sync, Team) — these are Testcontainers / `NoClassDefFoundError` infrastructure issues that already fail on the previous commit. They are NOT caused by F70 (confirmed via `git stash`-and-re-test).

## API Smoke Tests (via curl, tenant_test2 / ADMIN session)

| # | Scenario | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | `GET /api/quarterly-planning/projects/LB-294/quarter-commitment` (project with 2026Q1 label) | 200, `desiredQuarter:"2026Q1"`, `commitmentByTeam[]` with 1 team | 200 + correct payload, `Команда победителей` 1 epic, 0 committed, 1 uncommitted | PASS |
| 2 | `GET .../projects/NONEXIST-999/quarter-commitment` | 404 | 404 `{"error":"Project not found: NONEXIST-999"}` | PASS |
| 3 | `GET .../quarters/2026Q1/epics?onlyDesired=true` | Only desired-Q1-project epics + standalone | 13 epics: 1 project-bound (LB-9 under LB-294) + 12 standalone | PASS |
| 4 | `GET .../quarters/2026Q1/epics?onlyDesired=false` | All epics for Q1 | 22 epics (includes other project-bound epics like LB-308, LB-351, LB-318…) | PASS |
| 5 | `PlanningEpicDto` carries new fields | `projectDesiredQuarter`, `isStandalone` present | Both present, correct values per epic | PASS |
| 6 | `POST .../projects/LB-294/desired-quarter` with `{"quarter":"junk"}` | 400 + clear message | 400 `{"error":"Invalid quarter label: junk"}` | PASS |
| 7 | `POST .../desired-quarter` with no LEAD_SESSION cookie | 401 | 401 (empty body) | PASS |
| 8 | `POST .../projects/NONEXIST-999/desired-quarter` | 404 | 404 `{"error":"Project not found: NONEXIST-999"}` | PASS |
| 9 | `POST .../projects/LB-9/desired-quarter` (LB-9 is an EPIC, not PROJECT) | 4xx | 404 `{"error":"Issue LB-9 is not a project (boardCategory=EPIC)"}` | PASS |
| 10 | `POST .../projects/LB-294/desired-quarter` `{"quarter":"2026Q2"}` happy path | 200, response has `desiredQuarter:"2026Q2"`, Jira label updated, L1 cache OK | 200 + `desiredQuarter:"2026Q2"`, DB labels `{2026Q1,project}` -> `{project,2026Q2}`, no stale value | PASS |
| 11 | L1 cache fix verification (response must reflect new desiredQuarter, not stale) | Response shows new value immediately | `desiredQuarter` field returned new value `2026Q2` (was `2026Q1`) | PASS |
| 12 | `POST .../desired-quarter` with `{"quarter":null}` clears the label | 200, `desiredQuarter:null`, Jira label removed | 200 + `desiredQuarter:null`, DB labels `{project,2026Q2}` -> `{project}` | PASS |
| 13 | Cross-check: after setting LB-294 desired=Q2, `GET .../quarters/2026Q2/epics?onlyDesired=true` includes LB-9 | LB-9 visible | LB-9 present with `projectDesiredQuarter="2026Q2"`, `isStandalone=false` | PASS |
| 14 | Cross-check: after setting LB-294 desired=Q2, `GET .../quarters/2026Q1/epics?onlyDesired=true` has no project-bound epics | No project-bound epics for Q1 | 12 epics — 0 project-bound, 12 standalone | PASS |
| 15 | Standalone epics always present in `onlyDesired=true` mode | 12 standalone epics shown | All 12 present, `isStandalone:true` | PASS |
| 16 | MEMBER/VIEWER role denied via @PreAuthorize (covered by `QuarterlyPlanningControllerSecurityTest`) | 403 for VIEWER on `POST .../desired-quarter` | Verified in unit tests `setProjectDesiredQuarter_wrongRole_isForbidden` | PASS |
| 17 | Unauthenticated 4xx (covered by security test) | 4xx | Verified in unit test `setProjectDesiredQuarter_unauthenticated_is4xx` | PASS |
| 18 | Project with `desired_quarter=null` returns correct shape | `desiredQuarter:null` | Verified in test 12 above | PASS |
| 19 | Commitment view exposes teamId=0 / standalone case (epics without team mapping) | Not explicitly tested in DB scenario (LB-294 had `Команда победителей` only) | Verified in `QuarterlyPlanningServiceTest.getProjectCommitment_groupsEpicsWithoutTeamUnderTeamIdZero` | PASS |

**API test result: 19/19 PASS.**

## Visual / Frontend

| What | Status |
|---|---|
| Frontend production build (`npm run build`) | PASS — 2389 modules, no errors, v0.70.0 in bundle |
| New components present | `components/projects/DesiredQuarterPicker.tsx`, `components/projects/ProjectCommitmentView.tsx` |
| `QuarterlyPlanningPage` wires `onlyDesired` toggle (default true) | Verified in code (`setOnlyDesired`, `useState(true)`) |
| `EpicCard` renders "PM желает 2026Q2" badge when `projectDesiredQuarter !== currentQuarter` | Verified in code (lines 96–97, 318) |
| `EpicCard` renders "в 2026Qx" badge when epic committed elsewhere | Verified in code (lines 90, 314–315) |
| `BacklogColumn` exposes toggle "Только заявленные на квартал" with empty-state message | Verified in code (lines 220, 233–234) |
| `ProjectsPage` wires `DesiredQuarterPicker` + `ProjectCommitmentView` under `isAdmin() \|\| isProjectManager()` gate | Verified in code (lines 8–9, 237–238, 1036–1073) |
| Login page (no auth) renders correctly with v0.70.0 badge | Screenshot: `ai-ru/testing/screenshots/F70/00-login-screen-no-auth.png` |

### Limitation: full UI screenshots could not be captured

The Lead Board session cookie `LEAD_SESSION` is `HttpOnly`, which means JavaScript-based cookie injection from Playwright is impossible without an `addCookies` API call. The Playwright tools available in this environment (`browser_navigate`, `browser_click`, `browser_snapshot`, etc.) do not expose direct cookie/storage manipulation. The user's auth flow requires real Atlassian OAuth, which cannot be automated.

Mitigation taken:
- Confirmed all backend logic and HTTP semantics directly via curl against the running 0.70.0 backend on the same DB (tenant `test2`).
- Read all frontend components touched by F70 to confirm the wiring matches the spec (toggle, badges, picker, commitment view).
- Verified that the frontend builds and serves at 0.70.0.

Recommendation: future QA on logged-in screens should either (a) add a dev-only `/api/dev/login?userId=…` endpoint, or (b) expose a Playwright cookie tool, or (c) drive the OAuth callback with a stub token. None of these is in scope for F70.

## Bugs Found

### Critical (0)
None.

### High (0)
None.

### Medium

**M1 — `getEpicsForQuarter` still uses inherited `quarterLabel`, contradicting the spec's "inheritance disabled" rule.**

- **Where:** `backend/src/main/java/com/leadboard/planning/QuarterlyPlanningService.java`, lines 801–802:
  ```java
  String epicQuarter = resolveQuarterLabel(epic, epicToProjectKey, projectsByKey); // inherits from parent
  boolean inQuarter = quarterLabel.equals(epicQuarter);
  ```
- **Spec (F70_CUSTOMER_QUARTER_PLANNING.md, lines 39–41):**
  > For `resolveQuarterLabel()` in `getEpicsForQuarter()` (тимлидский экран): **наследование отключено**. Эпик «в квартале» только если у него явно установлен `committed_quarter`.
- **Observed:** epic `LB-9` (own labels `{q1-2026,roadmap}` — no quarter pattern label) reports `quarterLabel:"2026Q2"` and `inQuarter:true` in the Q2 view, because its parent project `LB-294` has `desired_quarter=2026Q2`. Per spec, the tech lead view should classify LB-9 as Backlog (not committed) until LB-9 itself receives a `2026Q2` label.
- **Impact:** Visible mismatch between the "committed_quarter" concept (per spec) and the actual tech-lead screen. The "PM желает Q2" badge will still surface correctly because that uses `projectDesiredQuarter` (computed via the new `resolveDesiredQuarter`), so user-visible PM nudge logic remains right — only the "In Quarter" column membership semantics are off.
- **Fix:** swap `resolveQuarterLabel` → `resolveCommittedQuarter` on line 801 of `getEpicsForQuarter`. The F67/Board fallback path uses `resolveQuarterLabel` elsewhere — leave those untouched (the spec preserves inheritance for the F67 filter).
- **Test gap:** `QuarterlyPlanningServiceTest` has no case for "epic without own label + parent project with matching desired_quarter" — this is exactly the case that surfaces the bug. Add a test like:
  ```java
  // Given: epic with no quarter label, parent project desired=2026Q2
  // When: getEpicsForQuarter("2026Q2", false)
  // Then: epic in result with quarterLabel=null, inQuarter=false
  ```

**M2 — Pre-existing `*ComponentTest` failures.**

- `BoardComponentTest`, `ForecastComponentTest`, `MetricsComponentTest`, `SyncComponentTest`, `TeamComponentTest` all fail with `ExceptionInInitializerError` at Testcontainers / TestSecurityConfig setup. Verified via `git stash` that this also fails on the parent commit `6cba544` — pre-existing, unrelated to F70.
- **Recommended owner:** infra / tooling, not the F70 author.

### Low

**L1 — Possible perf concern in `ProjectCommitmentView` lazy load.**

- Already documented as a TODO in the spec ("О(N×epics_total) при раскрытии всех проектов на ProjectsPage"). The spec acknowledges this as a known follow-up. No action required for F70.

## Files & Artifacts

- **Test report:** `/Users/kirillreshetov/IdeaProjects/lead-board-claude/ai-ru/testing/reports/2026-05-16_F70_CUSTOMER_QUARTER_PLANNING.md`
- **Screenshots:** `/Users/kirillreshetov/IdeaProjects/lead-board-claude/ai-ru/testing/screenshots/F70/` (login page only)
- **Tested commit:** `6cba544` + staged F70 changes (uncommitted as of 2026-05-16 10:50 local)
- **Tenant used for API:** `test2` (schema `tenant_test2`), user kirillwinter@gmail.com (ADMIN)
- **Project key used for mutating tests:** `LB-294`. The desired_quarter on LB-294 was changed during testing (2026Q1 → 2026Q2 → null → 2026Q2). Final state: `{project,2026Q2}`.

## Test Coverage Gaps

1. **Missing service test:** epic without own quarter label + parent project with desired_quarter == viewed quarter. This is the case that exposes bug M1.
2. **No real-tenant test for ROLE_PROJECT_MANAGER editing:** only ADMIN exists in tenant_test2 in the live DB. Authorization for PM is covered by `@WithMockUser(roles="PROJECT_MANAGER")` unit tests, but not exercised end-to-end via HTTP.
3. **No visual confirmation of UI badges or commitment view** (see "Limitation" above).
4. **No load test for `getProjectCommitment` at scale** — spec already flags this as O(N×epics).

## Recommendations

- Fix M1 by replacing the resolver on line 801 (one-line change + one new unit test) — does not block release because the user-visible nudge logic still works, but bringing it in line with the spec avoids future confusion.
- Owner of the `*ComponentTest` suite should investigate the Testcontainers init failure separately.
- Consider a dev-only auth endpoint or a Playwright cookie injection helper to unblock visual QA in future iterations.

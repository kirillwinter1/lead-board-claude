# QA Report: F84 — Data Quality Auto-Fix

**Date:** 2026-07-02
**Tester:** Claude QA Agent
**Branch/worktree:** feat/f84-dq-autofix (`.claude/worktrees/f84-dq-autofix`), v0.84.0
**Tenant under test:** tenant_test2 (user Kirill Reshetov, app_role ADMIN)

## Summary
- **Overall: PASS** (feature works end-to-end; no Critical/High code bugs)
- Unit tests: **95 passed, 0 failed** (`com.leadboard.quality.*` + `com.leadboard.quality.fix.*`); JiraWriteService fallback: 9 passed
- API tests: **13/13 passed** (1 main report + 7 previews + 5 negative)
- Visual: 8 screenshots, **0 console errors, 0 page errors**
- Bugs fixed via sub-agents: **0** (none needed)
- Environment blocker resolved (local DB, not F84 code): **1** (missing `team_id_manual` column)
- Nothing was applied to real Jira data. No `POST /fix` was executed.

## Environment blocker (resolved locally — NOT an F84 code defect)
On startup, tenant Flyway migrations failed for **all** tenants with a **checksum mismatch on migration version 9** (`T9__jira_projects_table`, applied `-52374126` vs resolved `-1662743804`). Flyway aborts validation on T9, so T10–T14 never ran — meaning F84's **T14 `team_id_manual`** column was never added. Result: `column jie1_0.team_id_manual does not exist` → **`GET /api/data-quality` and `/api/board` both returned HTTP 500.**

- Root cause is **pre-existing local-DB drift** (this dev DB was hand-patched earlier — T10–T13 columns exist though history stops at T9; matches the known "missing changelog author" memory note). T9 was **not** modified by the F84 branch (last touched in F63/F64), so this is not introduced by F84.
- The **T14 migration DDL itself is correct.**
- Resolution: applied `ALTER TABLE tenant_test2.jira_issues ADD COLUMN IF NOT EXISTS team_id_manual BOOLEAN NOT NULL DEFAULT FALSE;` to the **local dev DB only** (as sanctioned by the task note). After this, all endpoints returned 200.
- **Deploy risk (see Medium below):** in any environment whose tenant Flyway history has a checksum mismatch on an earlier migration, T14 will silently not apply and the whole board + DQ page will 500.

## API Testing (all PASS)

### GET /api/data-quality
- 200; 80 issues with violations; `summary` totals present (12 errors, 68 warnings).
- Every violation carries `fixable`. **9 distinct fixable rules present in local data** (+ RICE handled on FE):
  CHILD_IN_PROGRESS_EPIC_NOT, EPIC_DONE_OPEN_CHILDREN, EPIC_NO_DUE_DATE, IN_PROGRESS_NO_ASSIGNEE, RICE_MISSING_ASSESSMENT, STORY_FULLY_LOGGED_NOT_DONE, STORY_TODO_BUT_HAS_WORK, SUBTASK_DONE_NO_TIME_LOGGED, SUBTASK_IN_PROGRESS_STORY_NOT.

### GET /api/data-quality/fix-preview — 7 fixTypes verified
| Issue | Rule | fixType | Shape verified |
|-------|------|---------|----------------|
| LB-313 | EPIC_NO_DUE_DATE | DUE_DATE | changes (Due date — → selected); input `dueDate` type=date required; authMode OAUTH |
| LB-659 | IN_PROGRESS_NO_ASSIGNEE | ASSIGNEE_SELECT | select `accountId` with 3 member options; changes Assignee Unassigned → selected |
| LB-658 | SUBTASK_DONE_NO_TIME_LOGGED | WORKLOG | changes Time logged 0h → 8h; no inputs |
| LB-313 | EPIC_DONE_OPEN_CHILDREN | TRANSITION | **risky=true** + warning ("closes 3 issue(s)…") + **affectedIssues** [LB-315,316,314] + 3 status changes → Done (Готово) |
| LB-431 | STORY_TODO_BUT_HAS_WORK | TRANSITION | change Status Новое → Test Review (target from workflow config) |
| LB-659 | SUBTASK_IN_PROGRESS_STORY_NOT | TRANSITION | change on parent LB-657 |
| LB-302 | RICE_MISSING_ASSESSMENT | RICE_FORM | **authMode LOCAL**, no backend handler (FE RICE form) |

`applicable`, `notApplicableReason`, `risky`, `warning`, `affectedIssues`, `inputs[].options`, and `authMode` all serialize correctly. `authMode=OAUTH` because the session user has an OAuth token.

### Negative cases (all PASS)
| Case | Expected | Actual |
|------|----------|--------|
| unknown rule (`BOGUS_RULE`) | 400 | **400** ("No enum constant …") |
| non-fixable rule (`EPIC_NO_DESCRIPTION`) | 400 | **400** ("Rule is not fixable") |
| unknown issue (`LB-999999`) | 404/400 | **400** ("Issue not found") |
| missing params | 400 | **400** |
| unauthenticated (no cookie) GET | 401 | **401** |
| unauthenticated POST /fix | 401 | **401** |

RBAC: `@PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')")` on `/fix-preview` and `/fix`. A **VIEWER→403** path was not directly exercised (only an ADMIN session exists in the local DB) — see Coverage Gaps.

## Visual Testing (Playwright, 1440x900, LEAD_SESSION cookie + `test2.localhost` subdomain, 0 console errors)
- `f84_dq_page.png` — DQ page: category chips, severity filters, counts, per-issue rows.
- `f84_dq_expanded.png` — LB-313 expanded; **Fix** button present on fixable violation rows.
- `f84_fix_duedate_date_input.png` — "Set a due date": change row + empty date input; **Apply disabled** until filled (verified programmatically `isDisabled()==true`).
- `f84_fix_assignee_select.png` — "Assign an active team member": member select; **Apply disabled** while select empty.
- `f84_fix_risky_warning.png` — "Close all open children": 3 status-change rows + **red warning box** + affected issues LB-315/316/314.
- `f84_fix_rice_form.png` — RICE_MISSING_ASSESSMENT opens the embedded **RICE form** (REACH/IMPACT scoring).
- `f84_board.png` — Board loads cleanly.
- `f84_board_alert_tooltip.png` — Board alert tooltip shows **human labels** ("Epic without due date", "Time logged on wrong epic status", "Epic without description"), **not enum codes**. Confirmed backend serializes `label` via `@JsonProperty("label")` → `rule.getLabel()`, and `AlertIcon` renders `alert.label || alert.rule`.

All screenshots under `ai-ru/testing/screenshots/f84_*.png`.

## Bugs Found & Fixed
### Critical / High
None (no F84 code defects). The only Critical-symptom (500 on board/DQ) was environment DB drift, resolved at the DB layer — see Environment blocker above.

## Remaining Issues
### Medium
- **Deploy risk:** F84 adds a `NOT NULL` column (`team_id_manual`) referenced in every board/DQ query. If T14 fails to apply in a tenant (e.g. a pre-existing Flyway checksum mismatch on an earlier migration, as seen locally on T9), the entire Board and Data Quality pages 500. Recommend verifying tenant Flyway histories are clean before deploying F84, and/or making the column addition resilient (Flyway `repair` step in the tenant migration runner, or `IF NOT EXISTS`/nullable-with-default).

### Low
- `fix-preview` with an unknown rule returns a 400 whose message leaks the internal enum FQN: `No enum constant com.leadboard.quality.DataQualityRule.BOGUS_RULE`. Cosmetic info-leak; prefer a generic "Unknown rule: X".
- Pre-existing global `v0.84.0` version badge (Layout.tsx, `position:fixed`) overlaps the right edge of the Board ALERTS column and the DQ table on tall pages. Not an F84 regression.

## Test Coverage Gaps
- **VIEWER→403** not exercised end-to-end (no VIEWER session in local DB); RBAC verified only by annotation + 401 for anonymous.
- **`POST /fix` not executed** by design (safety: writes go to real Jira). Preview paths fully covered; apply handlers covered only by unit tests (`FixHandlersTest`, `FixServiceTest`). No EPIC_NO_TEAM violation currently exists in local data, so the sanctioned LOCAL apply path was not exercised live.
- `SUBTASK_WORK_NO_ESTIMATE`, `TIME_LOGGED_NOT_IN_SUBTASK`, `BUG_NO_PRIORITY`, `CHILD_DUE_AFTER_EPIC`, `STORY_DONE_OPEN_CHILDREN`, `TEAM_FIELD_UNMAPPED`, `EPIC_NO_TEAM` handlers exist but had no live violations to preview in the current dataset (unit-tested only).

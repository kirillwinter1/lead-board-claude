# QA Report: F64 Multi-Project Support
**Date:** 2026-03-06
**Tester:** Claude QA Agent

## Summary
- Overall status: **PASS WITH ISSUES**
- Unit tests: 152 passed, 0 failed (F64-related)
- API tests: 6 passed, 1 pre-existing failure (sync endpoints 500 — no jira_sync_state in public schema)
- Frontend build: Clean
- Visual: No issues

## Test Results

### API Endpoints Tested
| Endpoint | Method | Result | Notes |
|----------|--------|--------|-------|
| `/api/config/projects` | GET | PASS | Returns active project keys, respects active/syncEnabled flags |
| `/api/admin/jira-projects` | GET | PASS | Lists all projects with full details |
| `/api/admin/jira-projects` | POST | PASS | Creates project, uppercases key, validates blank/duplicate |
| `/api/admin/jira-projects/{id}` | PUT | PASS | Updates displayName, active, syncEnabled |
| `/api/admin/jira-projects/{id}` | DELETE | PASS | Returns 204, project removed |
| `/api/admin/jira-projects` | GET (no auth) | PASS | Returns 401 |
| `/api/sync/projects` | GET | FAIL (500) | Pre-existing: jira_sync_state table missing in public schema |
| `/api/board` | GET | PASS | Returns correct structure with projectKey field |

### Unit Tests
- `JiraProjectServiceTest` — 8/8 passed (seed, list, create, update, delete, case-insensitive duplicate)
- `BoardServiceTest` — 16/16 passed (with project-aware workflow mocks)
- `BoardServiceSearchTest` — 8/8 passed (with project-aware workflow mocks)
- `SyncServiceTest` — passed
- `AutoScoreCalculatorTest`, `DataQualityServiceTest`, `IssueOrderServiceTest`, `EpicServiceTest` — all passed

### Visual Testing
- Settings page: Jira Projects section renders correctly — table with Key, Name (editable), Active/Sync checkboxes, Issues count, Last Sync, Remove button
- Board page: Project filter correctly hidden when only 1 project (LB)
- Board page: Filter bar clean, no visual regressions
- Version displayed: v0.64.0

## Bugs Found

### High
None

### Medium
1. **[WorkflowConfigService.java:544,552,620,637,654,671] Several methods not yet project-aware** — `determinePhase()`, `getEpicStatusScoreWeight()`, `getStoryStatusSortOrder()`, `getStoryStatusColor()`, `getStoryStatusScoreWeight()` still use only global merged maps without per-project fallback. If two projects have conflicting status-to-role or status-score mappings, these methods return wrong results for one project.
   - Impact: Planning scores, status sort order, status colors could be wrong for secondary project
   - Recommendation: Add per-project overloads (same pattern as `categorizeByBoardCategory`)

2. **[useBoardFilters.ts] Project filter not synced to URL** — Team filter syncs `selectedTeams` back to `?teamId=X` in URL, but `selectedProjects` only reads from `?project=` on init (lines 36-46). Changing the project filter does not update the URL, so filter state is lost on page refresh.
   - Impact: UX inconsistency — team filter persists in URL, project filter doesn't

### Low
3. **[SettingsPage.tsx:100] Silent error swallowing in fetchProjects** — `catch {}` hides all errors including network/server failures. User gets no feedback if project list fails to load.
   - Recommendation: At minimum log to console; ideally show toast

4. **[SyncController.java + SyncService.java] /api/sync/projects returns 500** — Pre-existing issue: `jira_sync_state` table doesn't exist in public schema, only in tenant schemas. Not caused by F64 but affects the new per-project sync status feature.

## Good Practices
- Auto-seeding from `jira_sync_state` ensures zero-config migration
- Graceful fallback chain (DB -> .env) maintains backward compatibility
- Project filter only renders when 2+ projects available — clean UX
- `create()` normalizes key to uppercase BEFORE uniqueness check (fixed in review)
- `@PreAuthorize("hasRole('ADMIN')")` on all admin endpoints including new `/api/sync/projects` (fixed in review)
- Safe type casting in `JiraProjectController.update()` using `instanceof` pattern matching (fixed in review)
- Per-project workflow resolution with global fallback — correct two-tier lookup
- Delete button has `confirm()` dialog — not accidental
- Add Project button disabled when input is empty

## Test Coverage Gaps
- No integration test for multi-project board rendering (2+ projects with different workflows)
- No test for `WorkflowConfigService.loadConfiguration()` populating per-project maps
- No test for `determinePhase()` with conflicting per-project configs
- No frontend tests for project filter behavior (show/hide logic, URL init)

## Recommendations
1. Add unit test for `WorkflowConfigService` per-project resolution (project A returns EPIC, project B returns STORY for same type name)
2. Add per-project overloads to remaining global-only methods (`determinePhase`, score/sort/color methods)
3. Sync `selectedProjects` to URL params like team filter does
4. Fix pre-existing sync status 500 for public schema deployments

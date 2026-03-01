# QA Report: F48 Per-Project Workflow Configuration
**Дата:** 2026-03-01
**Тестировщик:** Claude QA Agent
**Версия:** 0.48.0

## Summary
- Общий статус: **PASS WITH ISSUES**
- Backend unit tests: 785+ passed, 0 failed
- Frontend tests: 235 passed, 1 failed (pre-existing Layout.test.tsx)
- API tests: 10 passed, 0 failed
- Bugs found: 0 Critical, 2 High, 3 Medium, 3 Low

## API Testing Results

| # | Test | Result |
|---|------|--------|
| 1 | GET /projects (tenant context) | PASS — returns project list |
| 2 | GET /workflow-config (backward compat) | PASS — returns merged config |
| 3 | GET /workflow-config?projectKey=LB | PASS — returns project config |
| 4 | GET /workflow-config?projectKey=NONEXISTENT | PASS — returns 404 |
| 5 | GET /workflow-config?projectKey= (empty) | PASS — returns 404 |
| 6 | GET /projects without auth | PASS — returns 401 |
| 7 | POST /auto-detect?projectKey=LB | PASS — 200, correct counts |
| 8 | GET /roles?projectKey=LB | PASS — returns 3 roles |
| 9 | GET /statuses?projectKey=LB | PASS — returns 44 statuses |
| 10 | SQL injection in projectKey | PASS — returns 404, no leak |

## Bugs Found

### High

**BUG-F48-1: Race condition on initial config load (WorkflowConfigPage.tsx:620-643)**
- **Описание:** Two `loadConfig()` calls can overlap: first fires immediately (line 634), then `getProjectConfigs().then()` sets `selectedProjectKey` which triggers the second `useEffect` (line 638-643) calling `loadConfig()` again. The second call has no `AbortController`, so two concurrent requests race to update state.
- **Шаги:** 1) Open Workflow Config page with multi-project tenant. 2) The initial effect fires `loadConfig(null)` AND `getProjectConfigs()` in parallel. 3) `getProjectConfigs` resolves and calls `setSelectedProjectKey("PROJ1")`. 4) Second `useEffect` fires `loadConfig()` — but the first one may still be in flight. 
- **Ожидаемый результат:** Config loads once for the correct project.
- **Фактический результат:** Two concurrent `loadConfig()` calls — the second may overwrite the first or the first may overwrite the second, depending on timing.
- **Severity:** High — can show wrong project's config momentarily.

**BUG-F48-2: `validate()` and `getStatusIssueCounts()` don't accept projectKey (workflowConfig.ts:122,131)**
- **Описание:** All other API methods in `workflowConfigApi` accept optional `projectKey` parameter, but `validate()` and `getStatusIssueCounts()` always hit the global endpoint. In multi-project scenarios, validation runs against the default config regardless of which project is selected.
- **Ожидаемый результат:** `validate(projectKey)` and `getStatusIssueCounts(projectKey)` should pass projectKey.
- **Фактический результат:** Always operates on default config.
- **Severity:** High — validation results may be incorrect for non-default projects.

### Medium

**BUG-F48-3: Thread safety gap during loadConfiguration() (WorkflowConfigService.java:165-233)**
- **Описание:** `loadConfiguration()` is `synchronized`, but ConcurrentHashMap caches are cleared sequentially (lines 165-172). Between `typeToCategory.clear()` and the repopulation loop completing, concurrent readers via `categorizeIssueType()` etc. see empty maps and return null. The `ensureLoaded()` method acquires the same lock, preventing re-entry, but other methods (like `isEpic()`, `isDone()`) that call `ensureLoaded()` will block until reload completes. During the brief window between `clear()` and repopulation, a concurrent thread that already passed `ensureLoaded()` but hasn't yet read from the map could get null.
- **Severity:** Medium — very narrow timing window, most likely scenario is blocking rather than null reads.

**BUG-F48-4: `getProjectConfigs()` error silently swallowed (WorkflowConfigPage.tsx:633)**
- **Описание:** `.catch(() => {})` on the `getProjectConfigs()` call means any API error (network, 500, auth) is completely silent. The project selector won't appear, and the user has no indication of what went wrong.
- **Severity:** Medium — user sees no project tabs without error feedback.

**BUG-F48-5: No `AbortController` on second `loadConfig()` call (WorkflowConfigPage.tsx:641)**
- **Описание:** The `useEffect` at line 638-643 calls `loadConfig()` without passing a signal. If the component unmounts or `selectedProjectKey` changes again quickly, the stale request completes and updates state on the old/unmounted component.
- **Severity:** Medium — potential stale state or React warnings.

### Low

**BUG-F48-6: `wizardSave()` has no rollback on partial failure (WorkflowConfigPage.tsx:970)**
- **Описание:** Four sequential API calls (updateRoles, updateIssueTypes, updateStatuses, updateLinkTypes). If call #2 fails, call #1 has already persisted. The user sees a generic error but the config is in a partial state.
- **Severity:** Low — user can retry wizard, and the partial state is not destructive.

**BUG-F48-7: Pre-existing: `computeWeightFromLevel` division by zero (MappingAutoDetectService.java:643)**
- **Описание:** When `maxLevel == 2`, the formula `(level - 1) / (maxLevel - 2)` divides by zero. Guard only handles `maxLevel <= 1`.
- **Severity:** Low — pre-existing, unlikely to trigger (requires exactly 2 statuses in a category where neither is "new" nor "done").

**BUG-F48-8: Pre-existing: Layout.test.tsx OAuth test fails**
- **Описание:** `should redirect to OAuth on login click` fails in jsdom because `window.location.hostname` behavior differs from browser.
- **Severity:** Low — pre-existing, not related to F48.

## Test Coverage Gaps

1. **No test for merged loading with 2+ configs** — `PerProjectWorkflowConfigTest` tests auto-detect isolation but not the merged reading path in `WorkflowConfigService.loadConfiguration()`
2. **No test for concurrent auto-detect** on the same project (race condition)
3. **No test for `autoDetectForProject` error handling** (Jira API failure mid-detect)
4. **No test for BUG → STORY fallback** in score weight calculations
5. **No test for `isConfigEmptyForProject` when only issueTypeRepo has data** (only roles path tested)
6. **No test for `getOrCreateConfigIdForProject` idempotency** (calling twice for same project)

## Visual Review
- Visual testing blocked: localhost doesn't provide tenant subdomain, so browser requests lack tenant context → redirects to landing page. Would need subdomain-based testing (e.g., `test1.localhost:5173`).

## Backward Compatibility
- API without `?projectKey` behaves identically to pre-F48 — VERIFIED
- Single-project tenants: config loading unchanged — VERIFIED (test2 tenant with 1 project)
- Auth enforcement (401 without session) — VERIFIED
- SQL injection resistance — VERIFIED (parameterized queries via Spring Data JPA)

## Recommendations

1. **Fix BUG-F48-1/F48-5:** Restructure initial load: call `getProjectConfigs()` first, THEN `loadConfig(selectedProject)` after project is determined. Remove the separate `useEffect` for `selectedProjectKey` or add proper AbortController.
2. **Fix BUG-F48-2:** Add `projectKey` parameter to `validate()` and `getStatusIssueCounts()`.
3. **Add integration test** for merged loading: 2 configs with overlapping types → verify first-wins.
4. **Add test** for `getOrCreateConfigIdForProject` idempotency.
5. **Consider atomic map swap** in `loadConfiguration()` instead of clear+repopulate: build new maps, then swap references atomically.

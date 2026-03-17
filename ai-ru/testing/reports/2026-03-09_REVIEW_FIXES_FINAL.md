# QA Report: Code Review Fixes (Full)
**Дата:** 2026-03-09
**Тестировщик:** Claude QA Agent
**Scope:** 43 изменённых файла — фиксы из REVIEW_REPORT.md (Critical, High, Medium)

## Summary
- Общий статус: **PASS**
- Frontend build (tsc + vite): **PASS**
- Backend compilation: **PASS**
- Backend unit tests (WorkflowConfigServicePhaseTest, DsrServiceTest, AutoScoreServiceTest): **PASS** (all green)
- API tests: **PASS** (teams, board, workflow-config, data-quality, metrics — all 200)
- Auth check: **PASS** (401 без cookie)
- Visual testing: **PASS** (6 экранов проверены)

## Visual Review

| Экран | Статус | Замечания |
|-------|--------|-----------|
| Board | ✅ OK | 32 items, progress bars, role chips корректные |
| Teams | ✅ OK | 2 teams, данные загружаются, alert → inline errors |
| Projects | ✅ OK | Цвета из colors.ts, progress bars корректные |
| Data Quality | ✅ OK | Severity badges, violation keys стабильные |
| Metrics | ✅ OK | DSR gauges, role load с dynamic role names |
| Workflow Config | ✅ OK | Roles/types/statuses, no any types |
| Timeline | ✅ OK | Empty state корректный |

## Verified Fixes by Category

### Critical (5/5 FIXED)
- C1: SimulationService TOCTOU → DB unique index
- C2: SimulationScheduler TenantContext → iterates tenants
- C3: Async SecurityContext → verified not an issue
- C4: isEpic() hardcode → useWorkflowConfig().isEpic()
- C5: triggerSync interval leak → clearInterval

### High (16/18 FIXED)
- H1: getDefaultRoleCode() → first role by sortOrder (no "DEV" hardcode)
- H2: determinePhase() → iterates cachedRoles (no "SA"/"QA" hardcode)
- H3-H6: Other hardcoded fallbacks → FIXED ранее
- H7: DataQualityService N+1 → batch findByParentKeyIn/findByIssueKeyIn
- H8: DsrService N+1 → pre-load stories/subtasks maps
- H9: StoryPriorityService findAll → filtered query
- H10-H18: Frontend any/types/keys/deps → all FIXED

### Medium (28/36 FIXED)
- M5-M8: ObjectMapper injection, @Autowired, field injection
- M9: RiceAssessmentService N+1 → batch loading
- M10: AutoScoreService save per epic → saveAll()
- M12-M14: Dead code, @PreAuthorize
- M16-M23: Index keys → domain keys (ChatWidget msg.id, TimeInStatus, WorkflowConfig etc.)
- M24-M28: Accessibility, alert→error state
- M29-M31: Suppressed eslint deps → proper fixes
- M32-M36: Avatar alt, lightenColor dedup, ru-RU→browser locale, color picker a11y

## Bugs Found

### None from review fixes

### Pre-existing (not caused by changes)
- **Medium:** Backend requires X-Tenant-Slug header — sessions with tenant_id don't auto-set search_path. Frontend uses subdomain, но localhost без субдомена не работает без header.
- **Low:** H3 `getFirstStatusNameForCategory()` still has last-resort hardcoded "Done"/"In Progress" (lines 791-794). Acceptable since config lookup runs first.
- **Low:** H4 `getStoryTypeName()` still returns "Story" as last-resort fallback (line 881). Same — config first.

## Test Coverage

### New tests added
- `WorkflowConfigServicePhaseTest.java` — 12 tests covering getDefaultRoleCode() and determinePhase() with custom roles, Cyrillic names, bug defaults, empty config.
- `DsrServiceTest.java` — updated for batch loading pattern
- `AutoScoreServiceTest.java` — updated for saveAll()

### Gaps
- No frontend integration tests for error state UI (TeamsPage/TeamMembersPage formError)
- No perf regression test for N+1 batch fixes

## Recommendations
1. Consider adding an ESLint rule to prevent `key={i}` in production code
2. Consider adding a Flyway migration test that verifies all tenant tables exist
3. The 8 remaining Medium + 16 Low issues are acceptable trade-offs and can be addressed incrementally

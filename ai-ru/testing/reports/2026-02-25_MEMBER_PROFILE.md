# QA Report: Member Profile (F30, S8)
**Date:** 2026-02-25
**Tester:** Claude QA Agent
**Scope:** MemberProfileService, MemberProfilePage, TeamController profile endpoint (~800 LOC backend + 527 LOC frontend)

## Summary
- **Overall Status:** PASS WITH ISSUES
- **Backend unit tests:** 9/9 PASS (MemberProfileServiceTest)
- **Frontend tests:** 236/236 PASS (but 0 tests for MemberProfilePage)
- **API tests:** 14 checks — 12 PASS, 1 BUG, 1 NOTE
- **Visual:** Limited (tenant/data mismatch prevents screenshot)
- **Code review:** 2 High, 5 Medium, 2 Low

## Bugs Found

### High (2)

| Bug ID | Description | File | Status |
|--------|------------|------|--------|
| BUG-108 | **Inverted date range (from > to) returns 200 with empty data instead of 400.** `GET /profile?from=2026-03-01&to=2026-01-01` returns 200 OK with all empty arrays. No validation that from <= to. Users who accidentally swap dates see empty data with no error message. | `TeamController.java:155-161`, `MemberProfileService.java:49` | OPEN |
| BUG-109 | **Silent error swallowing in 3 useEffect callbacks.** `competencyApi.getMember().catch(() => {})`, `competencyApi.getComponents().catch(() => {})`, `teamsApi.getUpcomingAbsences().catch(() => {})` -- if any of these APIs fail, sections silently don't appear. Also `handleCompetencyChange` catch block at line 182: `catch { /* silent */ }`. User loses competency changes without notification. | `MemberProfilePage.tsx:168-170, 182` | OPEN |

### Medium (5)

| Bug ID | Description | File | Status |
|--------|------------|------|--------|
| BUG-110 | **No AbortController for profile fetch.** `loadProfile()` in useCallback has no cancellation. Fast period switching can cause stale data or state updates on unmounted component. | `MemberProfilePage.tsx:148-160` | OPEN |
| BUG-111 | **DSR=0.0 for completed tasks with 0 spent hours.** Tasks LB-239, LB-230 have 20h estimate but 0h spent, marked as done. DSR shows 0.0 instead of null. This skews the average DSR (summary.avgDsr=0.32 instead of the real ratio from tasks with actual work). The calculateDsr method only checks `originalEstimateSeconds == 0` for null, not `timeSpentSeconds == 0`. | `MemberProfileService.java:271-278` | OPEN |
| BUG-112 | **Hardcoded status-badge CSS classes.** `<span className="status-badge in-progress">` and `<span className="status-badge todo">` -- uses local CSS instead of StatusBadge component from design system. Also doesn't use StatusStylesContext colors. | `MemberProfilePage.tsx:498, 514` | OPEN |
| BUG-113 | **No `@PreAuthorize` on profile endpoint.** `GET /teams/{teamId}/members/{memberId}/profile` has no role restriction. Any authenticated user (including VIEWER) can view any member's profile including DSR metrics, cycle time, and utilization. This may or may not be intended -- profile data could be sensitive in some organizations. | `TeamController.java:155-161` | OPEN |
| BUG-114 | **0 frontend tests for MemberProfilePage.** 527 LOC component with business logic (DSR classification, hours formatting, trend chart SVG) has zero test coverage. | `frontend/src/pages/` | OPEN |

### Low (2)

| Bug ID | Description | File | Status |
|--------|------------|------|--------|
| BUG-115 | **`catch (e: any)` TypeScript any type.** Line 155 uses `any` type in catch block instead of proper error typing. | `MemberProfilePage.tsx:155` | OPEN |
| BUG-116 | **N+1 query pattern in resolveEpicInfo.** For each completed/active/upcoming task, `issueRepository.findByIssueKey()` is called to resolve parent and grandparent. With in-memory cache it's better than raw N+1, but each new parent/grandparent key triggers a DB query. For 20+ tasks from different stories, this could be 10+ individual queries. | `MemberProfileService.java:154-180` | OPEN |

---

## API Testing Details

### Profile Endpoint (14 tests)

| # | Test | HTTP | Result |
|---|------|------|--------|
| T1 | GET /profile happy path (team 1, member 1) | 200 | PASS -- 4 completed, 3 active, 3 upcoming |
| T2 | GET /profile no auth | 401 | PASS |
| T3 | GET /profile non-existent team (999) | 404 | PASS -- "Team not found: 999" |
| T4 | GET /profile non-existent member (999) | 404 | PASS -- "Team member not found: 999" |
| T5 | GET /profile missing from/to params | 400 | PASS -- Bad Request |
| T6 | GET /profile inverted date range (from > to) | 200 | **BUG-108** -- returns empty response, should be 400 |
| T7 | GET /profile missing 'to' param | 400 | PASS -- Bad Request |
| T8 | GET /profile invalid date format (abc) | 400 | PASS -- parse error |
| T9 | GET /profile huge date range (1 year) | 200 | PASS -- 4 tasks, 8 weekly trend |
| T10 | GET /profile cross-team member (member 4 from team 1) | 404 | PASS -- rejects |
| T11 | Full response analysis | 200 | PASS -- all sections populated correctly |
| T12 | Task categorization check | 200 | PASS -- "В работе" mapped to active, "Новое" mapped to upcoming |
| T13 | DSR for tasks with 0 spent hours | 200 | NOTE -- DSR=0.0 instead of null for tasks with 20h estimate, 0h spent |
| T14 | Member from team 2 | 200 | PASS -- correct member, team, summary |

---

## Business Logic Analysis

### DSR Calculation

| Issue | Estimate | Spent | DSR | Status |
|-------|----------|-------|-----|--------|
| LB-114 | 6h | 6.3h | 1.06 | Correct |
| LB-102 | 12h | 12.3h | 1.03 | Correct |
| LB-239 | 20h | 0h | 0.0 | Questionable -- should be null (task marked done without work logged) |
| LB-230 | 20h | 0h | 0.0 | Questionable -- same issue |

### Summary Metrics (team 1, member 1, 2026-01-01 to 2026-02-25)

| Metric | Value | Analysis |
|--------|-------|----------|
| completedCount | 4 | Correct |
| totalEstimateH | 58.0 (6+12+20+20) | Correct |
| totalSpentH | 18.6 (6.3+12.3+0+0) | Correct |
| avgDsr | 0.32 (18600s/57600s) | Mathematically correct, but misleading because 2 tasks have 0h spent |
| avgCycleTimeDays | 12.8 | Correct -- only tasks with startedAt and doneAt counted |
| utilization | 10% | Correct -- (18.6h / (workdays * 6h/day)), low because many tasks have 0h logged |

### Weekly Trend

- 8 weeks shown (correct)
- Week 19 Jan: 2 tasks, 0h logged (tasks completed without work logs)
- Week 16 Feb: 2 tasks, 18.6h logged, DSR=1.04
- Remaining weeks: empty (correct)

---

## Backend Test Review (MemberProfileServiceTest -- 9 tests)

| Test | Coverage | Quality |
|------|----------|---------|
| getMemberProfile_memberInfo | Member info fields | Good |
| getMemberProfile_completedTasks_dsrCalculation | DSR = spent/estimate | Good -- verifies 0.75 |
| getMemberProfile_activeAndUpcomingFiltering | Status categorization | Good -- tests IN_PROGRESS + NEW |
| getMemberProfile_weeklyTrend | 8 weeks, has data | Weak -- only checks count, not specific week content |
| getMemberProfile_summary | completedCount, DSR, cycle time | Good -- verifies calculations |
| getMemberProfile_emptyData | Empty lists, zero summary | Good |
| getMemberProfile_teamNotFound | TeamNotFoundException | Good |
| getMemberProfile_memberNotFound | TeamMemberNotFoundException | Good |
| getMemberProfile_epicInfoResolution | subtask to story to epic chain | Good |

### Test Quality Issues

1. No `@DisplayName` on any of 9 tests
2. Uses `assertEquals`/`assertTrue` instead of AssertJ `assertThat()`
3. No test for inverted date range validation
4. No test for DSR with 0 spent hours (current behavior returns 0.0)
5. No test for `null` parentKey (orphan subtask)
6. No controller-level test for HTTP contract (validation, status codes)

---

## Frontend Code Review

### MemberProfilePage.tsx (527 LOC)

**Issues found:**

1. **Silent error swallowing (HIGH)** -- 4 catch blocks silently ignore errors:
   - Line 168: `competencyApi.getMember().catch(() => {})`
   - Line 169: `competencyApi.getComponents().catch(() => {})`
   - Line 170: `teamsApi.getUpcomingAbsences().catch(() => {})`
   - Line 182: `catch { /* silent */ }`

2. **No AbortController (MEDIUM)** -- `loadProfile()` at lines 148-160 doesn't use AbortController. Rapid period changes can cause race conditions.

3. **TypeScript `any` (LOW)** -- Line 155: `catch (e: any)` instead of `catch (e: unknown)`

4. **Hardcoded status badges (MEDIUM)** -- Lines 498, 514 use `.status-badge.in-progress` / `.status-badge.todo` CSS classes instead of `StatusBadge` component from design system. Colors don't come from `StatusStylesContext`.

5. **Hardcoded DSR threshold colors (LOW)** -- Functions `getDsrClass()` and `getDsrStatClass()` use hardcoded thresholds (1.0, 1.2, 1.15) -- not configurable.

6. **Hardcoded chart colors (LOW)** -- TrendChart uses hardcoded colors (#0052cc, #c1c7d0, #ebecf0) -- consistent with Atlassian design but not from theme.

7. **No aria-labels (MEDIUM)** -- SVG chart elements, date inputs, and competency rating stars have no accessibility labels.

### Design System Compliance

| Rule | Status |
|------|--------|
| Issue icons via getIssueIcon() | N/A (no issue icons used) |
| Status colors from StatusStylesContext | **FAIL** -- hardcoded CSS classes |
| Team colors via team.color | N/A (team name shown without color) |
| Role colors from getRoleColor() | N/A (role shown as text badge) |
| Reuse StatusBadge | **FAIL** -- custom .status-badge CSS |

---

## Recommendations

### P1 (High)
1. **Fix BUG-108:** Add `from <= to` validation in `getMemberProfile()` -- return 400 for inverted date ranges
2. **Fix BUG-109:** Add error state for competency/absence sections -- show "Ошибка загрузки" instead of silent hide

### P2 (Medium)
3. **Fix BUG-110:** Add AbortController to `loadProfile()`
4. **Fix BUG-111:** Return DSR=null when `timeSpentSeconds == 0` (completed without work logged)
5. **Fix BUG-112:** Replace custom `.status-badge` with `StatusBadge` component
6. **Fix BUG-114:** Write frontend tests for MemberProfilePage

### P3 (Low)
7. Fix TypeScript `any` type (BUG-115)
8. Add `@DisplayName` to backend tests
9. Add aria-labels to interactive elements
10. Consider `@PreAuthorize` on profile endpoint if DSR/utilization data is sensitive (BUG-113)

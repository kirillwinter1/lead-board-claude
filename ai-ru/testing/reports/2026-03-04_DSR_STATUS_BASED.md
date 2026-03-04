# QA Report: DSR Calendar-Based → Status-Based

**Дата:** 2026-03-04
**Тестировщик:** Claude QA Agent
**Scope:** DSR formula change from `startedAt`-based to status changelog-based calculation

## Summary

- **Общий статус:** PASS WITH ISSUES
- **Unit tests:** 10 passed, 0 failed (DsrServiceTest)
- **Related tests:** ALL passed (MemberProfileServiceTest, TeamMetricsControllerTest)
- **API tests:** Skipped — backend running old code, requires restart
- **Code review bugs:** 5 found (1 High, 3 Medium, 1 Low)

## Changed Files

| Файл | Изменение |
|------|-----------|
| `EpicDsr.java` | Removed `startedAt`, `inProgress`, `calendarWorkingDays`. Added `inProgressWorkdays` |
| `JiraIssueRepository.java` | Removed `startedAt IS NOT NULL` filter, changed sort to `issueKey` |
| `DsrService.java` | Full rewrite: changelog-based IN_PROGRESS period calculation |
| `DsrServiceTest.java` | 10 tests rewritten with changelog mocks |
| `metrics.ts` | Updated `EpicDsr` interface |
| `DsrBreakdownChart.tsx` | Removed LIVE badge, Started line, `formatDate()` |

## Bugs Found

### BUG-158 (High): Historical data regression — completed epics without changelog silently excluded

**Описание:** Completed epics that have no status changelog entries (e.g., completed before changelog tracking was enabled, or imported without history) will be **silently excluded** from DSR results. Previously, any epic with `startedAt IS NOT NULL` and `doneAt` was included.

**Шаги:** Query DSR for a period containing historically completed epics that predate status changelog import.

**Ожидаемый результат:** Epic appears in DSR with workdays calculated from some fallback.

**Фактический результат:** `calculateInProgressWorkdays` returns 0 → `calculateEpicDsr` returns null → epic excluded. DSR chart may show significantly fewer epics than before.

**Impact:** Teams with long history may see DSR metrics "disappear" for older epics. The `totalEpics` count will decrease, affecting `avgDsrActual` and `onTimeRate`.

**Recommendation:** Add a fallback in `calculateInProgressWorkdays`: if changelog is empty AND `epic.getDoneAt() != null`, use `epic.getStartedAt()` → `epic.getDoneAt()` as a single period. This preserves backward compatibility.

---

### BUG-159 (Medium): knowledge_base.md stale DSR documentation

**Описание:** `backend/src/main/resources/chat/knowledge_base.md` still describes the old calendar-based DSR approach:
- Line 142: "Начало: started_at эпика (первый переход в работу), fallback: jira_created_at"
- Line 143: "Конец: для Done — MAX(done_at подзадач), для In Progress — сегодня (live)"
- Line 164: "Колонки: Эпик, Статус, Оценка(д), **Календарь(д)**, Пауза(д), Эффект.(д), DSR"
- Lines 437-464: Full DSR formula section describes `started_at`, subtask endpoint, calendar working days

**Impact:** AI chat assistant will give incorrect answers about DSR calculation.

---

### BUG-160 (Medium): F32 feature spec outdated

**Описание:** `ai-ru/features/F32_DSR_V2_PAUSE_FLAG.md` describes old approach:
- Line 17: `working_days — рабочие дни от epic.started_at до endDate`
- Line 43: `EpicDsr.java — +3 поля: inProgress, calendarWorkingDays, flaggedDays, effectiveWorkingDays`
- Line 45: `JiraIssueRepository.java — +query findEpicsForDsr() (startedAt != null, completed OR in-progress)`

**Impact:** Developer confusion — spec doesn't match implementation.

---

### BUG-161 (Medium): Unused `issueType` parameter in calculateInProgressWorkdays

**Описание:** `DsrService.calculateInProgressWorkdays(String issueKey, String issueType)` accepts `issueType` parameter but never uses it (line 171). Dead parameter adds confusion.

**Файл:** `DsrService.java:171`

---

### BUG-162 (Low): N+1 query in changelog loading

**Описание:** `calculateInProgressWorkdays()` is called per epic, each executing `statusChangelogRepository.findByIssueKeyOrderByTransitionedAtAsc(issueKey)` — one query per epic. For teams with 20+ epics, this creates 20+ extra DB queries per DSR request.

**Impact:** Performance degradation proportional to epic count. Previously, DSR had N+1 for stories/subtasks; now adds N for changelog.

**Recommendation:** Batch-load changelogs using `findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List<String> issueKeys)` (already exists in StatusChangelogRepository) and group by issueKey in memory.

---

## Logic Review

### Correct behaviors verified:
- IN_PROGRESS → Blocked → IN_PROGRESS creates 2 separate periods (correct sum)
- IN_PROGRESS → Planned rollback (LB-1 case) counts only the IN_PROGRESS period
- Open period (still in progress) uses `LocalDate.now()` as end
- Flagged days calculated only within IN_PROGRESS periods (not total)
- Zero IN_PROGRESS days → epic excluded (correct — no work = no DSR)
- `effectiveWorkingDays = max(inProgressWorkdays - flaggedDays, 1)` prevents division issues

### Edge cases handled:
- First changelog entry with `fromStatus=null` — correctly detected as "entering IN_PROGRESS"
- Consecutive "enter IN_PROGRESS" entries — second ignored (periodStart already set)
- Exit without enter — `periodStart != null` guard prevents crash

### Frontend changes:
- `EpicDsr` interface correctly updated — `startedAt`, `inProgress`, `calendarWorkingDays` removed, `inProgressWorkdays` added
- `ChartRow` mapping cleaned up — no references to removed fields
- Tooltip correctly simplified — no LIVE badge, no Started line
- `formatDate()` removed (was only used for Started line)
- No broken references in other consumers (DsrGauge, AssigneeTable, TeamMetricsPage, MemberProfilePage)

## Test Coverage Assessment

### Covered:
- Happy path (changelog → workdays → DSR)
- Status rollback (LB-1 case)
- Multiple IN_PROGRESS periods
- Zero days exclusion
- Flagged days in periods
- Subtask estimate calculation
- No estimate → null DSR
- Open period (still in progress)
- Mixed completed + in-progress
- Direct `calculateInProgressWorkdays` test

### Missing (recommended):
- **P0:** Completed epic with empty changelog (BUG-158 scenario)
- **P1:** Same-day enter+exit period (countWorkdays returns 0 or 1?)
- **P1:** Flagged days exceeding IN_PROGRESS days (edge case)
- **P2:** Concurrent IN_PROGRESS sub-statuses (e.g., "Developing" and "Testing" both mapped as IN_PROGRESS)

## Regression Check

| Consumer | Status | Notes |
|----------|--------|-------|
| DsrService → DsrResponse | OK | Record fields match |
| TeamMetricsController | OK | Only passes through DsrResponse |
| MemberProfileService | OK | Has own `calculateDsr()` for subtasks — completely independent |
| ChatToolExecutor | OK | No direct EpicDsr references |
| DsrGauge (frontend) | OK | Uses DsrResponse fields only |
| DsrBreakdownChart (frontend) | OK | Updated, no stale refs |
| AssigneeTable (frontend) | OK | Uses personalDsr from AssigneeMetrics |
| TeamMetricsPage (frontend) | OK | No stale refs |
| MemberProfilePage (frontend) | OK | Own DSR calculation |

## Recommendations

1. **Fix BUG-158 (P0):** Add fallback for epics with empty changelog — use `startedAt`/`doneAt` as legacy period to prevent data regression
2. **Fix BUG-159 (P1):** Update `knowledge_base.md` DSR sections to describe status-based approach
3. **Fix BUG-161 (P2):** Remove unused `issueType` parameter
4. **Consider BUG-162 (P3):** Batch-load changelogs for performance
5. **Add test:** Completed epic with no changelog entries

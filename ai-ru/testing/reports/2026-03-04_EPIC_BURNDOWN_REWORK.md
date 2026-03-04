# QA Report: Epic Burndown — Plan from Snapshot, Actual from Worklogs
**Дата:** 2026-03-04
**Тестировщик:** Claude QA Agent

## Summary
- Общий статус: **PASS WITH ISSUES**
- Unit tests: 0 (нет тестов для EpicBurndownService)
- API tests: 5 passed, 0 failed
- Visual: 1 issue found
- Code review: 3 bugs found

## Scope of Changes
Рефакторинг Epic Burndown:
- **План (пунктир)**: из forecast snapshot (ступенчатое сгорание по forecastEnd сторей). Fallback на линейную кривую.
- **Факт (сплошная)**: из worklogs (кумулятивное списание времени). Ранее строилась по датам завершения сторей.
- Новое поле `planEstimateDays` в API.
- Frontend: показ Plan vs Current estimate, если отличаются.

## Files Changed
- `EpicBurndownService.java` — полностью переписан
- `EpicBurndownResponse.java` — добавлено `planEstimateDays`
- `ForecastSnapshotRepository.java` — `findClosestOnOrBefore()`
- `ForecastSnapshotService.java` — `getUnifiedPlanningFromClosestSnapshot()`
- `IssueWorklogRepository.java` — `findDailyTimeSpentByIssueKeys()`
- `metrics.ts` — `planEstimateDays` в TypeScript interface
- `EpicBurndownChart.tsx` — условное отображение Plan/Current estimate

## Bugs Found

### BUG-A (Medium): Worklogs before epic start date are silently dropped
**Файл:** `EpicBurndownService.java:255-269`
**Описание:** Метод `buildActualLineFromWorklogs()` итерирует только от `start` до `end`. Worklogs, зафиксированные до `startDate`, полностью игнорируются.
**Воспроизведение:** Эпик LB-95 имеет startDate=2026-01-25, но worklogs за 2026-01-23 (3.7 чел-дн) существуют. Фактическая линия начинается с 16.8, хотя должна начинаться с 16.8 - 3.7 = 13.1.
**Ожидаемое:** Worklogs ДО startDate должны учитываться в начальном значении cumulative.
**Фактическое:** Потеряно 3.7 чел-дн списанного времени.
**Fix:** Перед итерацией, суммировать worklogs до startDate в начальное значение cumulative.

### BUG-B (Low): Unused import `java.util.stream.Collectors`
**Файл:** `EpicBurndownService.java:22`
**Описание:** Import `java.util.stream.Collectors` не используется нигде в файле.
**Ожидаемое:** Удалить неиспользуемый импорт.

### BUG-C (Low): Chart type should be `stepAfter` for plan line when from snapshot
**Файл:** `EpicBurndownChart.tsx:194`
**Описание:** Линия плана всегда рендерится как `type="monotone"` (гладкая кривая). Когда план строится из снэпшота, данные ступенчатые (шаг вниз при завершении стори), но Recharts сглаживает кривую, скрывая ступенчатую природу.
**Ожидаемое:** Если `planEstimateDays != null` (= данные из снэпшота), использовать `type="stepAfter"` для плана.
**Фактическое:** Всегда `type="monotone"`.
**Примечание:** Не критично, т.к. на текущих данных нет снэпшотов (fallback на линейный план). Станет видно при появлении снэпшотов.

## API Tests

| # | Test | Result | Details |
|---|------|--------|---------|
| 1 | GET /epics-for-burndown?teamId=1 | PASS | Returns 6 epics, correct structure |
| 2 | GET /epic-burndown?epicKey=LB-95 | PASS | 39 points, actual line has 10 unique values (worklogs work) |
| 3 | GET /epic-burndown?epicKey=LB-292 (0 stories) | PASS | Empty response, totalStories=0 |
| 4 | GET /epic-burndown?epicKey=FAKE-999 | PASS | 400 "Epic not found" |
| 5 | GET /epic-burndown (no param) | PASS | 400 "Missing required parameter" |
| 6 | GET /epic-burndown (no auth) | PASS | 401 |

## Business Logic Validation

| Check | Result |
|-------|--------|
| Plan line fallback (no snapshot) → linear | PASS: planEstimateDays=null, linear descent |
| Actual line Y-start = totalEstimateDays | PASS: 16.8 = 16.8 |
| Ideal/actual date ranges match | PASS: both 2026-01-25 to 2026-03-04, 39 points |
| Empty epic (no stories) → empty response | PASS |
| No estimates → empty lines | PASS (handled at totalEstimateDays ≤ 0) |
| Non-existent epic → 400 | PASS |
| NullPointerException safety | PASS: teamId null → graceful null return |

## Visual Review
- Layout: Burndown section renders at bottom of metrics page
- Empty state: "No burndown data available" — correct
- Epic selector: Works, shows epic key + truncated summary
- Info section: Shows Stories count and Estimate (Plan/Current not visible since no snapshots)
- Chart: Cannot visually test chart rendering (default epic has 0 stories)

## Test Coverage Gaps
- **CRITICAL: No unit tests for EpicBurndownService** — zero coverage for 305-line service
- No tests for `findClosestOnOrBefore` repository method
- No tests for `findDailyTimeSpentByIssueKeys` repository method
- No tests for snapshot deserialization + epic matching
- No tests for stepped plan line construction
- No tests for worklog aggregation edge cases

## Recommendations
1. **Fix BUG-A**: Account for pre-start worklogs in cumulative initial value
2. **Write unit tests**: At minimum cover:
   - `buildPlanLineFromSnapshot` — stepped curve with known story dates
   - `buildActualLineFromWorklogs` — cumulative subtraction
   - `buildLinearPlanLine` — fallback
   - Edge cases: no stories, no estimates, no worklogs, no snapshot
3. Remove unused import (BUG-B)
4. Consider `stepAfter` chart type for snapshot-based plan (BUG-C)

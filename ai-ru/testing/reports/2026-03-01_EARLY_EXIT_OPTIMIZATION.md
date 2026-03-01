# QA Report: Early Exit Planning Optimization
**Дата:** 2026-03-01
**Тестировщик:** Claude QA Agent

## Summary
- Общий статус: **PASS WITH ISSUES**
- Unit tests: 11 passed, 0 failed (UnifiedPlanningServiceTest)
- Related tests: all planning/project/simulation/forecast unit tests pass
- API tests: Не выполнены (backend не запущен)
- Visual: Не применимо (backend-only change)
- Bugs found: 3 (1 High, 2 Medium)

## Scope of Changes

| Файл | Изменение |
|------|-----------|
| `UnifiedPlanningResult.java` | +`boolean approximate` field в `PlannedEpic` record |
| `UnifiedPlanningService.java` | +`EARLY_EXIT_WORKDAYS=130`, `roleCapacityPerDay`, early exit loop, `planEpicFast()`, `planStoryFast()` |
| `UnifiedPlanningServiceTest.java` | +3 новых теста |
| 4 test files | Updated PlannedEpic constructors (+`, false`) |

## Bugs Found

### BUG-EE1 (High): Fast-mode stories start from LocalDate.now() instead of chaining from previous epic

**Описание:** `planStoryFast()` всегда использует `LocalDate.now()` как `earliestStart` (строка 1206). В нормальном режиме `planStory()` тоже начинает с `LocalDate.now()`, но `AssigneeSchedule` глобально отслеживает занятость — story не может начаться раньше, чем assignee освободится. В fast-mode нет AssigneeSchedule, поэтому все fast-mode epics начинаются "с сегодня", создавая ложное перекрытие.

**Параметр `epicStartDate`** передаётся в `planEpicFast()`, но **никогда не используется** для планирования stories.

**Шаги:** 3 эпика, первый на 6 месяцев. Второй и третий в fast-mode оба начнутся "с сегодня", а не после первого.

**Ожидаемый результат:** Fast-mode stories должны начинаться от `epicStartDate` (= endDate предыдущего эпика), а не от `LocalDate.now()`.

**Фактический результат:** Все fast-mode эпики начинаются с текущей даты, перекрывая первый точно спланированный эпик.

**Файл:** `UnifiedPlanningService.java:1206`

---

### BUG-EE2 (Medium): planEpicFast rough estimates path passes empty assigneeSchedules

**Описание:** Строка 1020-1021: `planEpicByRoughEstimates(epic, Map.of(), ...)` — передаёт пустую map assigneeSchedules. Метод `planPhase()` внутри не найдёт assignees и вернёт `noCapacity` для всех ролей. Результат: rough-estimate эпик в fast-mode получит `null` startDate и endDate.

**Ожидаемый результат:** Rough-estimate эпики в fast-mode должны использовать математический расчёт (как `planStoryFast`) или хотя бы иметь корректные даты.

**Фактический результат:** Все роли получают `noCapacity`, эпик без дат.

**Файл:** `UnifiedPlanningService.java:1020-1021`

---

### BUG-EE3 (Medium): Fast-mode epics don't add warnings to globalWarnings

**Описание:** В нормальном `planEpic()` предупреждения NO_ESTIMATE и FLAGGED добавляются в `globalWarnings` (строки 323, 339). В `planEpicFast()` globalWarnings не передаётся и не используется:
- Flagged stories просто пропускаются без предупреждения (строка 1085-1087)
- NO_ESTIMATE warnings создаются на уровне story, но не в global list

**Ожидаемый результат:** Fast-mode эпики должны добавлять те же предупреждения в globalWarnings.

**Фактический результат:** Предупреждения теряются для fast-mode эпиков.

**Файл:** `UnifiedPlanningService.java:1061-1158` (весь цикл stories в planEpicFast)

---

## Code Review Findings

### Positive
1. `@JsonIgnoreProperties(ignoreUnknown = true)` на PlannedEpic — старые снэпшоты безопасно десериализуются с `approximate=false`
2. Primitive `boolean` (не `Boolean`) — Jackson корректно дефолтит в `false`
3. `roleCapacityPerDay` правильно учитывает grade-adjusted effective hours
4. Dependency tracking (`storyEndDates`) корректно шарится между normal и fast mode
5. `planStoryFast` корректно обрабатывает `noCapacity` case
6. Math: `ceil(hours/capacity)` — правильная формула для workdays estimation

### Concerns
1. **No frontend type update** — `frontend/src/api/forecast.ts:PlannedEpicDto` не содержит `approximate` field. Не критично (Jackson сериализует, JS просто игнорирует), но frontend не может показать approximate indicator.
2. **Horizon date = 130 workdays from now** — фиксированный порог. При холодном старте с большой командой первый эпик может оказаться быстрым, и switch не сработает вовсе. Это OK — оптимизация "наилучшего случая".
3. **Grade coefficients учтены** в capacity, но **competency scoring** (per-story) нет. Документировано как known limitation.

## Test Coverage Analysis

### Existing Tests (8 tests) — all pass
- `testBasicPlanning_SingleEpicSingleStory` — basic happy path
- `testParallelStoriesForSameRole` — parallel SA work
- `testDaySplitting` — day split across stories
- `testStoryWithoutEstimate_ShowsWarning` — no estimate warning
- `testDependencies_BlockedStoryWaitsForBlocker` — dependency chain
- `testRoleTransitionBetweenEpics` — cross-epic role transition
- `testNoCapacity_ShowsWarning` — missing role capacity
- `testDoneStoriesIncludedInPlannedStories` — done stories in result

### New Tests (3 tests) — all pass
- `earlyExit_switchesToFastMode_whenEpicEndsBeyondHorizon` — verifies approximate flag
- `planStoryFast_calculatesCorrectDuration` — math verification (48h/8h = 6 days)
- `earlyExit_respectsDependencies` — dependency tracking in fast mode

### Coverage Gaps
1. **No test for rough-estimate epic in fast mode** — would expose BUG-EE2
2. **No test verifying fast-mode epic start dates chain correctly** — would expose BUG-EE1
3. **No test for fast-mode flagged story warnings** — would expose BUG-EE3
4. **No test for all-epics-within-horizon** (no fast mode triggered) — regression safety
5. **No test for exact boundary** (epic ending exactly at horizon date)
6. **No test for team with no members** (empty assigneeSchedules → empty roleCapacityPerDay)
7. **No test for mixed roles** (e.g., story with SA+DEV where only DEV capacity exists)

## Recommendations

1. **Fix BUG-EE1 first** — это самый критичный баг. `planStoryFast()` должен использовать `max(epicStartDate, LocalDate.now())` как baseline вместо `LocalDate.now()`
2. **Fix BUG-EE2** — либо сделать математический расчёт для rough estimates в fast mode (аналог `planStoryFast` логики), либо передать реальные assigneeSchedules
3. **Fix BUG-EE3** — передать `globalWarnings` в `planEpicFast()` и добавлять FLAGGED/NO_ESTIMATE warnings
4. Добавить TypeScript interface `approximate?: boolean` в `forecast.ts` для будущего UI
5. Добавить тесты на boundary cases из "Coverage Gaps"

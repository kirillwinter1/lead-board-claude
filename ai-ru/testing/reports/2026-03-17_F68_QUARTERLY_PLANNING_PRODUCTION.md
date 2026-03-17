# QA Report: F68 Quarterly Planning Production UI
**Дата:** 2026-03-17
**Тестировщик:** Claude QA Agent

## Summary
- **Общий статус:** PASS WITH ISSUES
- Unit tests: 14 passed, 0 failed (6 new for F68)
- API tests: 6 passed, 0 failed (2 new endpoints + edge cases)
- Visual: 3 tabs screenshotted, layout correct, 0 critical visual bugs
- Frontend code review: 0 critical, 2 low issues

## Bugs Found

### Medium

**BUG-M1: Not-added projects show `risk=high` incorrectly**
- **Шаги:** Open `/quarterly-planning`, look at projects with status "Not added" (e.g., LB-334, LB-301, LB-317)
- **Ожидаемый результат:** Risk should be "low" for projects not in the quarter (nothing to plan → no risk)
- **Фактический результат:** Risk shows "High" in the detail panel because `roughCoverage=0 < 50` triggers the high-risk rule
- **Причина:** В `QuarterlyPlanningService.getProjectsOverview()` формула риска не учитывает `inQuarter=false`
- **Рекомендация:** Добавить `if (!inQuarter) risk = "low"` перед вычислением

### Low

**BUG-L1: No AbortController for data loading**
- **Описание:** `loadData()` в `QuarterlyPlanningLivePage` не использует AbortController. При быстром переключении квартала может произойти race condition — ответ от предыдущего запроса перезапишет данные нового
- **Влияние:** Низкое — Promise.all обычно быстро возвращается, и setState идемпотентен для последнего вызова
- **Рекомендация:** Добавить AbortController или ref-based guard как в старом `QuarterlyPlanningLivePage`

**BUG-L2: Table rows lack keyboard accessibility**
- **Описание:** Строки таблиц (Projects, Teams) кликабельны мышкой но не навигируемы клавиатурой (нет `role="button"`, `tabIndex={0}`, `onKeyDown`)
- **Влияние:** Низкое — accessibility concern, не влияет на функционал
- **Рекомендация:** Добавить `role="button" tabIndex={0} onKeyDown={handleKeyboard}` на `<tr>` элементы

## API Test Results

| Endpoint | Test | Status |
|----------|------|--------|
| `GET /projects-overview?quarter=2026Q1` | Happy path | PASS — 2 projects in quarter, correct statuses |
| `GET /teams-overview?quarter=2026Q1` | Happy path | PASS — 2 teams, correct capacity/demand/gap |
| `GET /projects-overview?quarter=2099Q4` | Empty quarter | PASS — inQuarterCount=0, valid structure |
| `GET /teams-overview?quarter=2099Q4` | Empty quarter | PASS — all teams returned, demandDays=0 |
| `GET /projects-overview` | Missing param | PASS — 400 error |
| `GET /projects-overview` | No auth | PASS — 401 |

## Business Logic Verification

| Check | Status | Details |
|-------|--------|---------|
| Planning status: ready | OK | 100% rough + 100% team → ready (verified in unit test) |
| Planning status: blocked | OK | rough < 60% → blocked (LB-294: 33%, LB-293: 0%) |
| Planning status: not-added | OK | No quarter label → not-added |
| Demand calculation | OK | null when rough < 100%, correct sum with 20% buffer |
| Forecast label | OK | "Xd demand" or "Demand unavailable" |
| Team capacity math | OK | sum(capacityByRole) == capacityDays ✓ |
| Team demand math | OK | sum(demandByRole) == demandDays ✓ |
| Utilization formula | OK | demand/capacity * 100, verified ✓ |
| Quarter inheritance | OK | Epics inherit from project quarter label |
| Summary counts | OK | inQuarterCount=2, readyCount=0, blockedCount=2 — all correct |

## Visual Review

- **Projects tab** (`f68_projects_tab.png`): Hero, step cards, summary metrics, table + detail panel — all correct. Filter chips work. TeamBadge colors correct.
- **Readiness tab** (`f68_readiness_tab.png`): Issue cards (4 without rough, 0 without team, 0 partial) — correct. Readiness table clear.
- **Teams tab** (`f68_teams_tab.png`): Team table, role bars (SA/DEV/QA), impacting projects — all render correctly.
- **Prototype** (`?mock=1`): Still functional, shows mock data with "Prototype mode" kicker.

## Test Coverage Gaps

1. **Нет теста для смены квартала** — проверить что данные корректно перезагружаются при выборе другого квартала
2. **Нет теста для Boost editing** — click boost → input → save → refresh
3. **Нет frontend unit тестов** — компонент не покрыт vitest/RTL тестами
4. **Нет теста для проекта без эпиков** — epicCount=0 с quarter label

## Recommendations

1. **Fix BUG-M1** — risk="low" для not-added проектов
2. Добавить AbortController (BUG-L1) для предотвращения race conditions
3. Рассмотреть добавление keyboard navigation для table rows (BUG-L2)
4. Добавить frontend unit тест для основного flow (render → load → display)

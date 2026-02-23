# QA Report: AutoScore / Planning
**Дата:** 2026-02-23
**Тестировщик:** Claude QA Agent

## Summary
- Общий статус: **PASS WITH ISSUES**
- Backend tests: ALL PASSED (planning + forecast + integration)
- API tests: 15/17 PASS, 1 BUG (500), 1 NOTE (negative days)
- Visual: Timeline + Board OK
- Test quality: significant coverage gaps in UnifiedPlanningServiceTest

## Scope

Модуль AutoScore / Planning — ядро бизнес-логики:
- AutoScoreCalculator (7 факторов epic, 9 факторов story)
- UnifiedPlanningService (pipeline SA→DEV→QA, 946 LOC)
- ForecastService (epic-level forecast)
- StoryForecastService (story-level forecast)
- Dependencies, RoleLoad, Retrospective, WIP Snapshots

### Файлы (17 сервисов, 5 контроллеров, 13+ тест-файлов)

---

## Unit Tests

### Backend: ALL PASSED
```
BUILD SUCCESSFUL — com.leadboard.planning.*, com.leadboard.forecast.*, integration tests
```

### Test Quality Review

| Файл | Тестов | @DisplayName | Assertion style | Edge cases | Quality |
|------|--------|-------------|-----------------|------------|---------|
| AutoScoreCalculatorTest | 47 | None | JUnit5 only | Good (nulls, boundaries) | B+ |
| UnifiedPlanningServiceTest | **7** | None | JUnit5 only | **Weak** | **D** |
| ForecastServiceTest | 12 | None | JUnit5 only | Moderate | B- |
| StoryAutoScoreServiceTest | ~20 | None | JUnit5 only | Moderate | B |
| StoryForecastServiceTest | ~15 | None | JUnit5 only | Moderate | B |
| IssueOrderServiceTest | ~20 | None | JUnit5 only | Good | B |
| StoryDependencyServiceTest | ~12 | None | JUnit5 only | Good (cycles) | B |
| ForecastControllerTest | ~10 | None | JUnit5 only | Basic | C+ |

---

## Bugs Found

### BUG-49: Critical — `forecast?teamId=999` → 500 Internal Server Error
- **Endpoint:** `GET /api/planning/forecast?teamId=999`
- **Ожидаемое:** 200 с пустым списком epics или 404
- **Фактическое:** 500 Internal Server Error
- **Причина:** Вероятно NullPointerException при отсутствии команды
- **Severity:** High — любой пользователь может вызвать 500

### BUG-50: Medium — `wip-history?days=-1` принимается (200 OK)
- **Endpoint:** `GET /api/planning/wip-history?teamId=1&days=-1`
- **Ожидаемое:** 400 Bad Request (отрицательный период)
- **Фактическое:** 200 с `from > to` (`"from":"2026-02-24","to":"2026-02-23"`) и пустыми данными
- **Severity:** Medium — нет валидации, инвертированный диапазон дат

### BUG-51: Medium — `wip-history?days=0` принимается (200 OK)
- **Endpoint:** `GET /api/planning/wip-history?teamId=1&days=0`
- **Ожидаемое:** 400 или хотя бы `from == to`
- **Фактическое:** 200 с `from > to` (`"from":"2026-02-23","to":"2026-02-23"`) — бессмысленный запрос
- **Note:** Менее критично чем days=-1, но всё равно нет валидации

### BUG-52: Medium — `unified` возвращает `expectedDone=null` и пустые `phaseSchedule` для ВСЕХ эпиков
- **Endpoint:** `GET /api/planning/unified?teamId=1`
- **Ожидаемое:** expectedDone и phaseSchedule заполнены (как в `/api/planning/forecast`)
- **Фактическое:** Все 6 эпиков: `expectedDone=null`, `phases=[]`
- **Контекст:** `forecast` endpoint возвращает корректные данные (expectedDone, phaseSchedule)
- **Severity:** Medium — unified endpoint используется фронтом, данные неполные (возможно by design — unified возвращает raw plan, а forecast добавляет даты?)

### BUG-53: Low — 0 тестов используют @DisplayName (все 13+ файлов)
- **Severity:** Low — влияет на читаемость CI-отчётов

### BUG-54: Medium — UnifiedPlanningServiceTest покрывает только 7 сценариев для сервиса в 946 LOC
- **Критические непокрытые ветви:**
  - `planEpicByRoughEstimates` — ни одного теста
  - Done stories (skip + progress accumulation) — не тестируется
  - Flagged stories (FLAGGED warning) — не тестируется
  - Absence dates blocking (`absenceService.getTeamAbsenceDates`) — не тестируется
  - Grade coefficients (JUNIOR 1.5, SENIOR 0.8) — всегда MIDDLE
  - WIP limits — не тестируются
  - Competency scores — всегда neutral (3.0)
- **Severity:** Medium — ядро бизнес-логики с минимальным покрытием

### BUG-55: Low — Тест `testDaySplitting` не проверяет day-splitting
- **Файл:** `UnifiedPlanningServiceTest.java`
- **Проблема:** Тест проверяет только что assignee не null, но НЕ проверяет что задача корректно разбивается на дни
- **Severity:** Low — тест даёт ложное ощущение покрытия

### BUG-56: Low — `alignmentBoost_factorAppearsInCalculateFactors` — trivial test
- **Файл:** `AutoScoreCalculatorTest.java`
- **Проблема:** Проверяет только `containsKey("alignmentBoost")`, не проверяет значение
- **Severity:** Low

### BUG-57: Low — ForecastServiceTest: test name contradicts assertion
- **Файл:** `ForecastServiceTest.java`
- **Тест:** `setsMediumConfidenceWhenMultipleNoCapacityWarnings` → asserts `Confidence.LOW`
- **Проблема:** Название говорит MEDIUM, assertion проверяет LOW

---

## API Testing Results

| # | Endpoint | Params | Expected | Actual | Status |
|---|----------|--------|----------|--------|--------|
| 1 | GET /api/planning/forecast | teamId=1 | 200 | 200 (6 epics) | PASS |
| 2 | GET /api/planning/forecast | teamId=2 | 200 | 200 (5 epics) | PASS |
| 3 | GET /api/planning/forecast | teamId=999 | 200/404 | **500** | **FAIL** |
| 4 | GET /api/planning/unified | teamId=1 | 200 | 200 (6 epics) | PASS |
| 5 | GET /api/planning/unified | teamId=2 | 200 | 200 | PASS |
| 6 | GET /api/planning/role-load | teamId=1 | 200 | 200 (3 roles) | PASS |
| 7 | GET /api/planning/retrospective | teamId=1 | 200 | 200 | PASS |
| 8 | GET /api/planning/wip-history | days=30 | 200 | 200 | PASS |
| 9 | GET /api/planning/wip-history | days=-1 | 400 | **200** | **NOTE** |
| 10 | GET /api/planning/wip-history | days=0 | 400 | **200** | **NOTE** |
| 11 | GET /api/planning/autoscore/epics/LB-1 | | 200 | 200 (score=29.56) | PASS |
| 12 | GET /api/planning/autoscore/epics/NONEXIST | | 404 | 404 | PASS |
| 13 | GET /api/planning/autoscore/teams/1/epics | | 200 | 200 (7 epics) | PASS |
| 14 | GET /api/planning/epics/LB-95/story-forecast | teamId=1 | 200 | 200 | PASS |
| 15 | GET /api/planning/epics/LB-95/stories | | 200 | 200 | PASS |
| 16 | GET /api/planning/epics/NONEXIST/stories | | 200 | 200 (empty []) | PASS |
| 17 | GET /api/planning/forecast | no teamId | 400 | 400 | PASS |
| 18 | GET /api/planning/forecast | no auth | 401 | 401 | PASS |

---

## Business Logic Verification

### AutoScore Factors (LB-1, score=29.56)
```
status:         15    (ЗАПЛАНИРОВАНО — correct per formula)
progress:       0.37  (low progress — reasonable)
dueDate:        0     (no due date — correct)
priority:       10    (Medium — correct)
size:           2.33  (small epic — reasonable)
age:            1.86  (age factor — reasonable)
riceBoost:      0     (no RICE assessment)
alignmentBoost: 0     (no alignment data)
flagged:        0     (not flagged)
TOTAL:          29.56 ✓
```
Sum: 15 + 0.37 + 0 + 10 + 2.33 + 1.86 + 0 + 0 + 0 = **29.56** ✓

### Forecast Pipeline (Team 1)
```
LB-95:  QA only     → Expected Done 2 мар   (3.3 work days)
LB-202: DEV+QA      → Expected Done 10 мар  (DEV 5.4d + QA 4.5d, overlap)
LB-1:   SA+DEV+QA   → Expected Done 20 мар  (full pipeline)
LB-201: SA+DEV+QA   → Expected Done 7 апр   (after LB-1 frees SA)
LB-9:   SA+DEV+QA   → Expected Done 4 июн   (large epic, 12+14.4+13.2 days)
LB-292: no estimate  → Expected Done null    (correct — no data)
```
Pipeline порядок: SA→DEV→QA с корректным перекрытием фаз. ✓

### Role Capacity (Team 1)
```
DEV: 6.00 h/day
SA:  4.67 h/day
QA:  6.25 h/day
```
Числа реалистичны для команды из нескольких человек. ✓

---

## Visual Review

### Timeline (team 2 — Красивые)
- Gantt-диаграмма отображается корректно
- Фазы SA/DEV/QA цветами различимы (синий/фиолетовый/зелёный)
- Прогресс-бары на эпиках
- Статусные бейджи (Developing, Запланировано, E2E Testing)
- Легенда SA|DEV|QA|Сегодня|Due Date
- "Нет активных сторей" для LB-205 — корректный empty state

### Board (team 1 — Команда победителей)
- AutoScore отображается в Priority column
- Expected Done даты корректны (2 мар, 10 мар, 20 мар, 7 апр, 4 июн)
- Role-Based Progress бары (SA/DEV/QA) с процентами
- Alerts column с числовыми индикаторами
- Рекомендации порядка (стрелки ↑↓)

---

## Test Coverage Gaps (Priority Order)

### P0 — Critical
1. **UnifiedPlanningService**: 7 тестов на 946 LOC. Rough estimates, done/flagged stories, absences, grade coefficients, WIP limits — всё без тестов
2. **forecast?teamId=999 → 500**: нет обработки несуществующей команды

### P1 — High
3. **AutoScoreCalculator**: DB-driven weight path (`getStatusScoreWeight` non-zero) не тестируется — в продакшне это PRIMARY path
4. **No @DisplayName** в 13+ файлах — CI-отчёты нечитаемы
5. **wip-history negative days**: нет валидации

### P2 — Medium
6. **ForecastService**: confidence MEDIUM vs LOW test name mismatch
7. **testDaySplitting**: не проверяет day-splitting (ложное покрытие)
8. **No error/exception tests** во всех 3 основных тестах

---

## Recommendations

1. **Исправить BUG-49** (500 на teamId=999) — добавить try-catch или проверку существования команды в ForecastService
2. **Добавить валидацию days >= 1** в wip-history endpoint
3. **Расширить UnifiedPlanningServiceTest** минимум до 15-20 тестов: rough estimates, done stories, flagged, absences, grade coefficients
4. **Добавить тест для DB-driven status weight** в AutoScoreCalculatorTest (mock возвращает non-zero)
5. **Добавить @DisplayName** ко всем тестам (можно автоматизировать)
6. **Миграция на AssertJ** для BigDecimal сравнений (eliminates scale-sensitivity)

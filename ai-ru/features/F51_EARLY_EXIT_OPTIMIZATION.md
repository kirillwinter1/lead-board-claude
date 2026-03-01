# F51: Early Exit Planning Optimization

**Версия:** 0.51.0
**Дата:** 2026-03-01
**Статус:** ✅ Готово

## Проблема

После серии perf-тестов (k6, F50) главный bottleneck сместился с DB-запросов на сам алгоритм `calculatePlan()`. Cold start = 5-8 секунд на одну команду (20 эпиков / 300 stories / 900 subtasks).

Причина — day-by-day симуляция в `AssigneeSchedule`: для каждой story алгоритм перебирает всех assignees роли, для каждого симулирует аллокацию по дням. При 300 stories × 3 roles × 5 assignees × до 365 дней = ~550K операций с датами.

При этом первые эпики (ближайшие по времени) важны точно, а дальние (>6 месяцев) — нет: их прогноз всё равно будет неточным.

## Решение

**Early Exit** — когда накопленный timeline превышает порог (~130 рабочих дней ≈ 6 месяцев), оставшиеся эпики переключаются на быструю математическую экстраполяцию без day-by-day итерации.

Ближние эпики планируются точно (с учётом assignees, competency, day-splitting), дальние — приблизительно (`ceil(hours / teamCapacity)`). Результат помечается флагом `approximate = true`.

## Архитектура

```
calculatePlanUncached()
  │
  ├─ Pre-compute roleCapacityPerDay: {SA: 16h/day, DEV: 24h/day, QA: 8h/day}
  ├─ horizonDate = addWorkdays(today, 130)
  │
  ├─ for each epic:
  │    ├─ if !useFastMode:
  │    │    ├─ planEpic()          ← точный алгоритм (day-by-day, assignees, competency)
  │    │    └─ if endDate > horizon → useFastMode = true
  │    └─ if useFastMode:
  │         └─ planEpicFast()      ← быстрый расчёт (math only)
  │              └─ planStoryFast() ← ceil(hours / capacity) per role
```

### Сложность

| Метод | Сложность | Описание |
|-------|-----------|----------|
| `planStory()` | O(roles × assignees × days) | Day-by-day simulation, competency scoring, assignee selection |
| `planStoryFast()` | O(roles) | `ceil(hours / capacity)`, no assignee, no day iteration |

### Что НЕ делает fast mode

- Не назначает конкретных assignees (`PhaseSchedule.assignee = null`)
- Не учитывает competency scoring (используется средняя capacity команды)
- Не модифицирует `AssigneeSchedule` state (часы не резервируются)
- Не поддерживает day-splitting между stories

## Изменения

### Backend

**DTO:**
- `PlannedEpic` — новое поле `boolean approximate` (последнее в record). Для точных эпиков = `false`, для fast-mode = `true`. Jackson: primitive `boolean` дефолтит в `false` при десериализации старых снэпшотов.

**UnifiedPlanningService — новые константы и поля:**
- `EARLY_EXIT_WORKDAYS = 130` (~6 месяцев рабочих дней)
- `roleCapacityPerDay` — pre-computed map суммарной capacity команды по ролям

**UnifiedPlanningService — модифицированный цикл:**
- `calculatePlanUncached()` — early exit логика: `useFastMode`, `horizonDate`, `currentPlanDate`
- Первый эпик, чей `endDate > horizonDate`, переключает `useFastMode = true`
- Все последующие эпики планируются через `planEpicFast()`

**UnifiedPlanningService — новые методы:**

| Метод | Описание |
|-------|----------|
| `planEpicFast()` | Fast-estimation эпика: обрабатывает done/flagged/empty stories как обычно, active stories через `planStoryFast()`. Добавляет warnings в `globalWarnings`. |
| `planStoryFast()` | Математический расчёт: `ceil(bufferedHours / roleCapacity)` workdays per phase. Учитывает dependencies (`storyEndDates`). Использует `epicStartDate` как baseline. |
| `planRoughEstimateFast()` | Fast-estimation для эпиков с rough estimates (без stories). Математический расчёт вместо `planPhase()` с пустыми assignees. |

### Frontend

Изменений нет. Поле `approximate` сериализуется в JSON, но frontend его пока не использует. `@JsonIgnoreProperties(ignoreUnknown = true)` обеспечивает обратную совместимость.

### Тесты

**UnifiedPlanningServiceTest** — 3 новых теста:

| Тест | Что проверяет |
|------|---------------|
| `earlyExit_switchesToFastMode_whenEpicEndsBeyondHorizon` | Первый эпик precise, последующие approximate |
| `planStoryFast_calculatesCorrectDuration` | Математика: 48h / 8h/day = 6 workdays |
| `earlyExit_respectsDependencies` | Dependency tracking корректен в fast mode |

**Обновлённые тесты** — 4 файла, все `PlannedEpic` конструкторы получили параметр `approximate = false`:
- `ForecastServiceTest.java` (3 места)
- `ProjectServiceTest.java` (4 места)
- `ProjectAlignmentServiceTest.java` (1 место)
- `SimulationPlannerTest.java` (1 место)

## Ключевые файлы

| Файл | Описание |
|------|----------|
| `planning/UnifiedPlanningService.java` | Early exit логика, planEpicFast, planStoryFast, planRoughEstimateFast |
| `planning/dto/UnifiedPlanningResult.java` | `boolean approximate` в PlannedEpic |
| `planning/UnifiedPlanningServiceTest.java` | 3 новых теста |

## Обратная совместимость

- **Snapshot десериализация:** `@JsonIgnoreProperties(ignoreUnknown = true)` + primitive `boolean` = старые снэпшоты без `approximate` десериализуются с `false`
- **API:** Новое поле `approximate` добавляется в JSON-ответ, не ломает существующих клиентов
- **Поведение:** Эпики в пределах горизонта (130 рабочих дней) планируются точно так же, как раньше. Изменения затрагивают только дальние эпики.

## Ожидаемые результаты

| Метрика | До | После |
|---------|-----|-------|
| calculatePlan cold start | 5-8s | ~1-2s |
| Board cold (1 VU) | 7,926ms | ~2-3s |
| Board warm (cache) | 120ms | 120ms (без изменений) |

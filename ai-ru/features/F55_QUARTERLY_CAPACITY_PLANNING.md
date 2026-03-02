# F55 Quarterly Capacity-Based Planning with Project Priority

**Статус: ✅ Реализовано (v0.55.0)**

## Описание

Квартальное планирование ёмкости с приоритизацией проектов. Позволяет PM и тимлидам:
- Видеть загрузку команд по кварталам
- Приоритизировать проекты через RICE + ручной boost
- Видеть когда ёмкость команды превышена (overcommit)
- Двойной вид: «по командам» и «по проектам»

## Архитектура

### Backend

**Миграция V47:** `labels TEXT[]`, `manual_boost INTEGER` в `jira_issues`. GIN-индекс на labels.

**QuarterRange** — утилитарный record для работы с кварталами:
- `of("2026Q2")` → start/end даты
- `labelForDate(date)` → метка квартала
- `currentQuarterLabel()` → текущий квартал

**QuarterlyPlanningService:**
- `getTeamCapacity(teamId, quarter)` — ёмкость команды (рабочие дни × часы × грейд-коэффициент − отсутствия)
- `getTeamDemand(teamId, quarter)` — спрос: rough estimates × risk buffer, группировка по проектам
- `getSummary(quarter)` — сводка по всем командам (capacity/demand/utilization%)
- `getProjectView(projectKey, quarter)` — проект через все команды
- `updateProjectBoost(projectKey, boost)` — ручной boost приоритета [-50..+50]

**Priority Score** = RICE normalizedScore + manualBoost, clamped [0..150].

**Quarter label inheritance:** Если эпик без метки квартала, наследует от родительского проекта.

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/quarterly-planning/capacity?teamId=N&quarter=2026Q2` | Ёмкость команды |
| GET | `/api/quarterly-planning/demand?teamId=N&quarter=2026Q2` | Спрос + проекты |
| GET | `/api/quarterly-planning/summary?quarter=2026Q2` | Сводка по всем командам |
| GET | `/api/quarterly-planning/project-view?projectKey=X&quarter=2026Q2` | Вид проекта |
| GET | `/api/quarterly-planning/quarters` | Доступные кварталы |
| PUT | `/api/quarterly-planning/projects/{key}/boost` | Установить boost (ADMIN/PM) |

### Frontend

**QuarterlyPlanningPage** — новая страница с двумя табами:
- **By Teams**: выбор команды → MetricCard (Capacity/Demand/Utilization/Overcommit) → Recharts BarChart (capacity vs demand по ролям) → список проектов (сворачиваемый) с эпиками
- **By Projects**: выбор проекта → карточки команд с capacity-барами и списком эпиков

**Reused components:** MetricCard, StatusBadge, TeamBadge, RiceScoreBadge, WorkflowConfigContext.getRoleColor()

**Navigation:** "Planning" tab после "Project Timeline"

## Данные

- **Labels** — синхронизируются из Jira (поле `labels`), read-only в нашем UI
- **Manual boost** — наш локальный параметр, сохраняется при синке
- **Ёмкость** — рассчитывается динамически из team members + absences + work calendar
- **Спрос** — rough estimates × (1 + riskBuffer)
- **Кварталы** — определяются из labels в формате `YYYYQn`

## Тесты

- **QuarterRangeTest** — 7 тестов (parse Q1-Q4, invalid format, labelForDate, currentQuarterLabel)
- **QuarterlyPlanningServiceTest** — 8 тестов (capacity basic, absences, priority score computation, clamping, quarter label inheritance, capacity fit cutoff, unassigned epics, empty quarter)

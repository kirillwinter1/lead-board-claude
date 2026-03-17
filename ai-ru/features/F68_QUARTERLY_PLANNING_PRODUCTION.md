# F68: Quarterly Planning Production UI

**Версия:** 0.68.0
**Дата:** 2026-03-16

## Описание

Страница Quarterly Planning переведена с прототипа на реальные данные. Ранее страница содержала только режим с mock-данными (`?mock=1`) и заглушку. Теперь основной режим (`QuarterlyPlanningLivePage`) загружает данные из двух новых backend-эндпоинтов и отображает актуальную картину квартального планирования: состав квартала, готовность проектов к планированию и нагрузку команд.

Страница сохраняет прототипный дизайн (CSS из `QuarterlyPlanningPrototypePage.css`) — live-версия использует те же CSS-классы, что и prototype.

## Архитектурное решение

Принятая структура: квартальное планирование — трёхуровневая воронка:
1. **Projects** — какие проекты включены в квартал (по Jira label вида `2026Q2`)
2. **Readiness** — у каких проектов заполнены rough estimates и team mapping
3. **Teams** — только после readiness capacity vs demand имеет смысл

Добавить/убрать квартальный label у проекта можно только в Jira (требуется Jira Write API). В live-версии кнопка Action убрана; добавлен hint "Add/remove labels directly in Jira".

## Изменения

### Backend

- `QuarterlyPlanningService.getProjectsOverview(quarter)` — новый метод. Загружает все проекты, резолвит quarter label, вычисляет покрытие rough estimates и team mapping, статус планирования (`ready` / `partial` / `blocked` / `not-added`), риск, forecast label, список блокеров на проект
- `QuarterlyPlanningService.getTeamsOverview(quarter)` — новый метод. Для каждой команды: capacity (из F55), demand (из эпиков квартала), gap, utilization, overloaded epics, risk, список проектов-источников нагрузки
- `QuarterlyPlanningController`: добавлены два endpoint'а — `GET /api/quarterly-planning/projects-overview` и `GET /api/quarterly-planning/teams-overview`
- Новые DTO:
  - `QuarterlyProjectOverviewDto` — проект с readiness-метриками и списком эпиков
  - `QuarterlyTeamOverviewDto` — команда с capacity/demand/gap по ролям
  - `QuarterlyProjectsResponse` — конверт с summary-счётчиками и списком проектов

### Frontend

- `QuarterlyPlanningPage` — полная замена: вместо `<QuarterlyPlanningPrototypePage />` по умолчанию рендерится `QuarterlyPlanningLivePage`. Прототип доступен через `?mock=1`
- `QuarterlyPlanningLivePage` — новый компонент:
  - Загрузка доступных кварталов + автовыбор текущего (`YYYY Q{N}`)
  - Параллельная загрузка `projects-overview` и `teams-overview`
  - 3 таба: Projects, Readiness, Teams
  - 4 summary-карточки: Projects in quarter, Ready to plan, Epics coverage, Teams involved
  - Step-cards с подсветкой активного шага
- `ProjectsTab` — таблица проектов с фильтр-чипами (All / In quarter / Needs planning work / Not added) + detail-панель с checklist, blockers, списком эпиков и редактируемым Boost
- `ReadinessTab` — issue-карточки (epics without rough estimates, without team mapping, partially ready) + readiness-таблица
- `TeamsTab` — таблица команд capacity/demand/gap + detail-панель с role bars и списком проектов
- `api/quarterlyPlanning.ts` — добавлены `getProjectsOverview()` и `getTeamsOverview()`, новые типы `QuarterlyProjectOverviewDto`, `QuarterlyProjectsResponse`, `QuarterlyTeamOverviewDto`, `EpicOverviewDto`, `TeamRef`, `ProjectRefDto`

## API Endpoints

- `GET /api/quarterly-planning/projects-overview?quarter={q}` — список всех проектов с readiness-метриками, статусом планирования и сводкой по кварталу
- `GET /api/quarterly-planning/teams-overview?quarter={q}` — список команд с capacity/demand/gap/risk по квартальным проектам

## Ограничения

- **Добавление/удаление квартального label** требует Jira Write API (scope `write:jira-work`). В текущей реализации кнопка Action не реализована — пользователь перенаправляется в Jira для управления labels напрямую. Это принятое ограничение, а не баг.
- Страница не требует новых миграций — данные читаются из существующих таблиц (`jira_issues`, `teams`, `project_configurations`, `rice_assessments`).

## Конфигурация

Не требуется. Новые endpoints доступны без дополнительной настройки.

# F35: Projects (Sync + UI + Progress)

**Дата:** 2026-02-16 (Stage 1-2), 2026-02-17 (Stage 3)
**Статус:** Реализована (Stage 1 + Stage 2 + Stage 3)
**Бэклог:** BF5 Projects

## Описание

Добавление типа задачи "Project" (Проект) в иерархию OneLane:
`PROJECT → EPIC → STORY → SUBTASK`

Проекты синхронизируются из Jira как обычные issue. Связь Project→Epic конфигурируется (parent или issueLink).

## Изменения

### Backend

#### База данных (V33)
- `project_configurations`: добавлены колонки `epic_link_type` (VARCHAR, default 'parent') и `epic_link_name` (VARCHAR)
- `jira_issues`: добавлена колонка `child_epic_keys` (TEXT[]) — для хранения связей в режиме issuelink

#### BoardCategory enum
- Добавлен `PROJECT` перед EPIC

#### WorkflowConfigService
- Новые поля: `projectTypeNames`, `epicLinkType`, `epicLinkName`
- `isProject()`, `getProjectTypeNames()`, `getEpicLinkType()`, `getEpicLinkName()`
- Fallback в `categorizeIssueType()`: "project"/"проект" → PROJECT (до проверки epic)
- `loadConfiguration()`: заполняет projectTypeNames и epicLinkType/epicLinkName из конфига

#### MappingAutoDetectService
- `detectBoardCategory()`: "project"/"проект" → PROJECT (до epic)

#### MappingValidationService
- PROJECT — опциональный (warning, не error)

#### SyncService
- В `saveOrUpdateIssue()`: для PROJECT-задач в режиме issuelink парсит issueLinks и сохраняет childEpicKeys

#### ProjectService + ProjectController (новые)
- `GET /api/projects` → List<ProjectDto>
- `GET /api/projects/{issueKey}` → ProjectDetailDto
- Поддержка двух режимов: parent и issuelink

#### DTO
- `ProjectDto` (issueKey, summary, status, assigneeDisplayName, childEpicCount)
- `ProjectDetailDto` (+ epics: List<ChildEpicDto>)
- `ChildEpicDto` (issueKey, summary, status, teamName)

#### Config API
- `WorkflowConfigResponse`: добавлены `epicLinkType`, `epicLinkName`
- `ProjectConfigUpdateRequest`: добавлены `epicLinkType`, `epicLinkName`

### Frontend

#### API
- `frontend/src/api/projects.ts` — клиент для /api/projects

#### WorkflowConfigPage
- `BOARD_CATEGORIES`: добавлен 'PROJECT'
- `suggestIssueTypes()`: эвристика "project"/"проект" → PROJECT
- Фильтры статусов: добавлен PROJECT
- UI для настройки epicLinkType/epicLinkName (в табе Issue Types, при наличии PROJECT)

#### WorkflowConfigContext
- `isProject()` helper

#### ProjectsPage (новая)
- Список проектов (карточки): key, summary, status, assignee, кол-во эпиков
- Раскрытие: таблица дочерних эпиков (key, summary, status, команда)

#### Навигация
- Route `/board/projects` → ProjectsPage
- Таб "Projects" в навигации (после Teams)

### Тесты

- `ProjectServiceTest`: 3 теста (listProjects, parentMode, issueLinkMode)
- `MappingAutoDetectServiceTest`: тест detectBoardCategory для PROJECT

## Конфигурация

### Epic Link Type

| Режим | Описание |
|-------|----------|
| `parent` (default) | Эпики привязаны через Jira parent field (Project = parent of Epic) |
| `issuelink` | Эпики привязаны через Jira issue links (указать имя связи) |

Настройка через Workflow Config → Issue Types → "Project → Epic Link Mode".

## API

### GET /api/projects
Возвращает список проектов с количеством дочерних эпиков.

### GET /api/projects/{issueKey}
Возвращает проект с деталями и списком дочерних эпиков.

---

## Stage 2: Progress + Expected Done + Board Badge

**Дата:** 2026-02-16

### Изменения

#### Backend — DTOs
- `ProjectDto`: +`completedEpicCount` (int), +`progressPercent` (int), +`expectedDone` (LocalDate)
- `ChildEpicDto`: +`estimateSeconds` (Long), +`loggedSeconds` (Long), +`progressPercent` (Integer), +`expectedDone` (LocalDate), +`dueDate` (LocalDate)
- `ProjectDetailDto`: +`completedEpicCount`, +`progressPercent`, +`expectedDone`

#### Backend — ProjectService
- Зависимости: `UnifiedPlanningService`, `WorkflowConfigService`
- `buildEpicPlanningMap()`: собирает teamId из эпиков, вызывает `calculatePlan()` для каждой команды, возвращает `Map<String, PlannedEpic>`
- `countCompletedEpics()`: через `workflowConfigService.isDone(status, issueType)`
- `computeExpectedDone()`: max(endDate) из planning для не-done эпиков, fallback на dueDate
- `listProjects()` и `getProjectWithEpics()` обогащены прогрессом и прогнозами

#### Backend — Board Badge
- `BoardNode`: +`parentProjectKey` (String)
- `BoardService.getBoard()`: reverse lookup epic→project через parent mode и issuelink mode, проставляет `parentProjectKey` на epic BoardNode

#### Frontend — API
- `projects.ts`: интерфейсы расширены новыми полями
- `board/types.ts`: `BoardNode` +`parentProjectKey: string | null`

#### Frontend — ProjectsPage
- Progress bar на карточке проекта (completedEpics/totalEpics)
- Дата expectedDone на карточке
- StatusBadge с динамическими цветами (через StatusStylesProvider)
- Таблица эпиков: колонки Progress (мини-бар + % + logged/estimate) и Expected Done

#### Frontend — BoardRow
- Бейдж `parentProjectKey` на epic-строках Board (#DEEBFF / #0747A6, 10px font)

### Тесты
- `ProjectServiceTest`: 5 тестов
  - `listProjects_includesProgressAndExpectedDone`
  - `getProjectWithEpics_enrichesEpicProgress`
  - `listProjects_handlesEmptyEpicList`
  - `listProjects_handlesPlanningServiceFailure` (graceful degradation → fallback на dueDate)
  - `getProjectWithEpics_issueLinkMode`

---

## Stage 3: RICE Integration + AutoScore Boost

**Дата:** 2026-02-17
**Реализовано в рамках:** [F36 RICE Scoring](F36_RICE_SCORING.md) (BF4)

Stage 3 BF5 полностью покрыт фичей BF4 (RICE Scoring), которая реализовала:

### Что доставлено

- **RICE оценка проектов:** Единая `rice_assessments` таблица для проектов и эпиков (вместо отдельной `project_overlays`)
- **RICE → AutoScore boost:** Новый фактор `riceBoost` (до +15 баллов) в `AutoScoreCalculator`
- **Наследование Project→Epic:** Эпик внутри проекта наследует RICE Score родителя
- **RiceForm на ProjectsPage:** Форма оценки в развёрнутом виде проекта
- **RiceScoreBadge:** Бейдж со score на карточках проектов
- **Data Quality:** Правило RICE_MISSING для проектов без оценки
- **Effort auto:** Автоматический расчёт из реальных оценок subtask'ов

### Архитектурное отклонение от спеки BF5

| Спека BF5 | Реализация (BF4) | Оценка |
|-----------|-------------------|--------|
| Отдельная таблица `project_overlays` | Единая `rice_assessments` | Лучше — нет дублирования |
| Простые поля (reach=integer, impact=enum) | Шаблонный движок с подкритериями | Лучше — гибче |
| `ProjectRiceService` | `RiceAssessmentService` (универсальный) | Ок — нет дублирования |
| `PUT /api/projects/{key}/rice` | `POST /api/rice/assessments` | Ок — единый endpoint |

---

## Stage 4: Cross-team Alignment

**Дата:** 2026-02-17

### Описание

Обнаружение отстающих эпиков, AutoScore alignment boost, рекомендации для PM.

### Backend

#### ProjectRecommendation (DTO)
- `RecommendationType` enum: `EPIC_LAGGING`, `ALL_EPICS_DONE`, `EPIC_NO_FORECAST`, `RICE_NOT_FILLED`
- `ProjectRecommendation` record: type, severity, message, epicKey, teamName, delayDays

#### ChildEpicDto
- +`delayDays` (Integer, nullable): отставание от средней даты проекта в днях

#### ProjectService
- `findChildEpics()`, `buildEpicPlanningMap()`: private → package-private
- `computeAverageExpectedDone()`: усреднение endDate non-done эпиков (null если < 2)
- `getProjectWithEpics()`: вычисляет delayDays для каждого эпика

#### ProjectAlignmentService (новый)
- `getRecommendations(projectKey)`: генерирует список рекомендаций
  - `ALL_EPICS_DONE` — все эпики завершены
  - `EPIC_NO_FORECAST` — эпик без прогнозной даты
  - `EPIC_LAGGING` — эпик отстаёт > 2 дней от средней
  - `RICE_NOT_FILLED` — проект без RICE-оценки
- `preloadAlignmentData(epics)`: batch preload для AutoScore

#### AutoScoreCalculator
- Новый фактор `alignmentBoost`: до +10 баллов (1 балл/день отставания)
- `preloadAlignmentData()` / `clearAlignmentData()`: batch preload

#### AutoScoreService
- `recalculateAll()` и `recalculateForTeam()`: preload alignment data

#### ProjectController
- `GET /api/projects/{issueKey}/recommendations` → List<ProjectRecommendation>

### Frontend

#### API (`projects.ts`)
- +`delayDays: number | null` в `ChildEpicDto`
- +`ProjectRecommendation` interface
- +`getRecommendations()` в `projectsApi`

#### ProjectsPage
- Таблица эпиков: новая колонка "Align" (⚠ +Nd / ✓)
- Блок рекомендаций (жёлтый фон) под таблицей эпиков
- Параллельная загрузка detail + recommendations

### Тесты
- `ProjectAlignmentServiceTest`: 9 тестов
- `AutoScoreCalculatorTest`: +4 теста (alignmentBoost)
- `ProjectServiceTest`: +2 теста (delayDays)

---

## Stage 5a: PROJECT_MANAGER Role

**Дата:** 2026-02-17

### Описание

Роль PROJECT_MANAGER — менеджер проектов. Видит board/timeline/projects, управляет RICE-оценками, участвует в покере. Не имеет доступа к admin/sync/team management/priorities.

### Backend

#### Миграция V37
- Расширение CHECK constraint: `app_role IN ('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD', 'MEMBER', 'VIEWER')`

#### AppRole enum
- Добавлен `PROJECT_MANAGER` между ADMIN и TEAM_LEAD
- Permissions: `projects:manage`, `board:view`, `poker:participate`

#### AuthorizationService
- `isProjectManager()`: проверка роли PROJECT_MANAGER
- `canManageProjects()`: ADMIN || PROJECT_MANAGER

#### ProjectController (@PreAuthorize)
- `GET /api/projects` — без ограничений
- `GET /api/projects/{key}` — без ограничений
- `GET /api/projects/{key}/recommendations` — ADMIN, PROJECT_MANAGER, TEAM_LEAD

#### RiceController (@PreAuthorize)
- GET endpoints — без ограничений (read-only)
- `POST /api/rice/templates`, `PUT /api/rice/templates/{id}` — ADMIN, PROJECT_MANAGER, TEAM_LEAD
- `POST /api/rice/assessments` — ADMIN, PROJECT_MANAGER, TEAM_LEAD

### Frontend

#### SettingsPage
- `ROLES` array: добавлен `PROJECT_MANAGER`
- Permissions table: колонка "PM" + строка "Manage Projects/RICE"

---

## Stage 5b: Project Timeline (Gantt)

**Дата:** 2026-02-17

### Описание

Gantt-style визуальный таймлайн проектов. Проект = группа с summary bar, эпики = бары с фазовыми сегментами по ролям (SA/DEV/QA). Штриховка для rough estimates, текст с оставшимися днями. Отдельный таб "Project Timeline".

### Backend

#### DTOs
- `ProjectTimelineDto` (issueKey, summary, status, progressPercent, riceNormalizedScore, epics)
- `EpicTimelineDto` (epicKey, summary, status, teamName, startDate, endDate, progressPercent, isRoughEstimate, roughEstimates, phaseAggregation, roleProgress, flagged)
- `EpicTimelineDto.PhaseAggregationInfo` (hours, startDate, endDate)
- `EpicTimelineDto.PhaseProgressInfo` (estimateSeconds, loggedSeconds, completed)

#### ProjectService
- `getTimelineData()`: загружает проекты, эпики, planning data, RICE scores, маппит PlannedEpic → EpicTimelineDto
- `mapToEpicTimeline()`: конвертация PlannedEpic с phaseAggregation и roleProgress

#### ProjectController
- `GET /api/projects/timeline` → List<ProjectTimelineDto> (без @PreAuthorize)

### Frontend

#### API (`projects.ts`)
- Интерфейсы: PhaseAggregationInfo, PhaseProgressInfo, EpicTimelineDto, ProjectTimelineDto
- `projectsApi.getTimeline()`

#### ProjectTimelinePage (новая)
- Gantt container: labels panel (left, fixed 300px) + chart panel (right, scrollable)
- Project rows: expand/collapse chevron + summary bar (progress)
- Epic rows: phase segments colored by role (from WorkflowConfigContext)
- Rough estimate epics: hatched pattern (repeating-linear-gradient 135deg)
- Bar text: remaining days per role (e.g. "SA:10/DEV:5d")
- Zoom: week (120px, default) / month (100px)
- Today line (red vertical)
- Auto-scroll to today on load
- Expand/Collapse All button
- Legend: role colors + today + rough estimate

#### Навигация
- Route: `/board/project-timeline` → ProjectTimelinePage
- Tab: "Project Timeline" после "Projects"

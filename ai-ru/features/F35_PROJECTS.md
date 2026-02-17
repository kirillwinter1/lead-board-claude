# F35: Projects (Sync + UI + Progress)

**Дата:** 2026-02-16
**Статус:** Реализована (Stage 1 + Stage 2)
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

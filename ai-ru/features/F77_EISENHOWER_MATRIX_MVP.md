# F77. Eisenhower Matrix — MVP (Backlog Triage + ручной triage)

**Версия:** 0.77.0 | **Дата:** 2026-06-27 | **Полная спека:** [BF10](../backlog/BF10_EISENHOWER_MATRIX.md)

MVP-срез BF10: экран матрицы 2×2 для orphan-задач + ручной triage через
drag-and-drop. Остальное из BF10 (авто-предложение, статистика/пороги, создание
subtasks в Jira, рекомендации автопланера, автомиграция) — последующие фазы, НЕ в
этом MVP.

## Источник задач (orphan)
Issues команды, у которых: `parent_key IS NULL` AND `board_category = 'STORY'`
(Story/Task/Bug, не subtask и не epic) AND статус не «done»
(`WorkflowConfigService.isDone(status, issueType, projectKey)` == false).
Subtask — не самостоятельная задача, в матрице не участвует.

## Данные
Tenant-миграция **T12** (`db/tenant/`):
```sql
ALTER TABLE jira_issues ADD COLUMN eisenhower_quadrant VARCHAR(10);
CREATE INDEX idx_jira_issues_quadrant ON jira_issues(team_id, eisenhower_quadrant)
  WHERE parent_key IS NULL;
```
Значения: `P1`/`P2`/`P3`/`P4`/`NULL` (нераспределённая). Колонка переживает sync
(sync делает upsert по другим полям, eisenhower_quadrant не трогает).

## Backend (`com.leadboard.matrix`)
- `MatrixService`
  - `getMatrix(teamId)` → orphan-задачи команды, отфильтрованы по «не done»
    (через WorkflowConfigService), сгруппированы по `eisenhower_quadrant`
    (NULL → `unassigned`).
  - `triage(issueKey, quadrant)` → проставить/снять `eisenhower_quadrant` (валидация:
    quadrant ∈ {P1,P2,P3,P4,null}; задача должна быть orphan текущей команды).
- `MatrixController` (`/api/matrix`)
  - `GET /api/matrix?teamId=` → `MatrixViewDto { p1[], p2[], p3[], p4[], unassigned[] }`.
  - `PUT /api/matrix/triage` `{ issueKey, quadrant }`.
  - `@PreAuthorize("@authorizationService.canManageTeam(#teamId)")` для GET;
    для triage — TeamLead+ (`hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')`).
- `MatrixCardDto`: issueKey, summary, issueType, priority, estimateHours
  (originalEstimateSeconds/3600), assigneeDisplayName, status, quadrant.
- Тесты: MatrixService (orphan-фильтр, группировка, triage-валидация),
  MatrixControllerSecurityTest (VIEWER → 403, TEAM_LEAD → 200).

## Frontend
- Роут `/matrix` + nav-таб **«Matrix»** (Layout).
- `MatrixPage`: селектор команды (`SingleSelectDropdown`, как в Metrics) + сетка 2×2
  + ряд «Нераспределённые».
- `matrix/MatrixQuadrant.tsx` ×4 (drop-зоны: П1 важно+срочно, П2 важно+не срочно,
  П3 не важно+срочно, П4 не важно+не срочно) + `matrix/MatrixUnassigned.tsx`.
- `matrix/MatrixCard.tsx` (draggable): иконка типа (`getIssueIcon` + категория),
  ключ→Jira-ссылка, summary, иконка приоритета, estimate (`Nч`), assignee.
- **dnd-kit** (`@dnd-kit/core`): перетаскивание карточки между зонами → оптимистичное
  обновление + `PUT /api/matrix/triage`; откат при ошибке.
- `api/matrixApi.ts`. Тесты (рендер квадрантов, dnd → triage-вызов) + визуал.

## Design System
Иконки типа — `getIssueIcon(type, getIssueTypeIconUrl(type), getIssueTypeCategory(type))`;
иконка приоритета — `getPriorityIconUrl`; статус — `StatusBadge` при показе; цвета
квадрантов — добавить в `constants/colors` (без хардкода в компонентах).

## НЕ в MVP
Авто-предложение квадранта, статистика/пороги/алерты, создание subtasks в Jira
(заблокировано проблемой Jira-полей — см. Planning Poker pause), рекомендации
автопланера, автомиграция, детальная панель, history/audit.

## Границы
`MatrixService` (orphan-запрос + группировка + triage) ← `MatrixController` (REST)
← `matrixApi` ← `MatrixPage` (презентационные зоны/карточки) + dnd-хук. Каждый юнит
тестируется независимо.

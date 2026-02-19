# F39 — Unified Style Refactoring

**Дата:** 2026-02-19
**Версия:** 0.39.0

## Цель

Привести весь проект к единому визуальному стилю: иконки типов задач из Jira, цвета статусов из WorkflowConfig, цвета команд везде где отображается team name.

## Что сделано

### Backend

- **ChildEpicDto** — добавлено поле `teamColor`
- **EpicTimelineDto** — добавлено поле `teamColor`
- **ProjectService** — `loadTeamColors()` для передачи цветов в DTO
- **V40 миграция** — `description` в `jira_issues`
- **V41 миграция** — `assignee_avatar_url` в `jira_issues`
- **SyncService** — синк description и assigneeAvatarUrl из Jira

### Frontend

#### Статусы: StatusStylesContext вместо hardcoded палитр
- **ProjectTimelinePage** — убрана константа `STATUS_COLORS`, добавлен `StatusStylesProvider` + `getStatusStyles()`
- **TimelinePage** — убрана константа `STATUS_COLORS`, добавлен `StatusStylesProvider`, компоненты `EpicLabel`/`StoryBars` используют `useStatusStyles()`

#### Иконки типов задач: getIssueIcon() из helpers.ts
- **TimelinePage** — убраны локальные импорты `storyIcon`, `bugIcon`, `subtaskIcon` и функция `getIssueTypeIcon()`, заменено на `getIssueIcon()` из `helpers.ts`
- **ProjectTimelinePage** — убран `import epicIcon`, заменено на `getIssueIcon()`
- **DataQualityPage** — иконка всегда рендерится с fallback через `getIssueIcon()`

#### Цвета команд
- **ProjectsPage** — `TeamBadge` вместо plain text для teamName
- **ProjectTimelinePage** — цветные лейблы эпиков (borderLeft + tinted background), цветная рамка на Gantt барах, цвет команды в тултипе

#### CSS
- **TimelinePage.css** — убраны hardcoded `.legend-phase-sa/dev/qa::before` → динамические через `getRoleColor()`
- **ProjectTimelinePage.css** — стили для цветных team лейблов и баров

## Файлы

### Backend (изменённые)
- `backend/build.gradle.kts` — version 0.39.0
- `backend/src/main/java/com/leadboard/project/ChildEpicDto.java`
- `backend/src/main/java/com/leadboard/project/EpicTimelineDto.java`
- `backend/src/main/java/com/leadboard/project/ProjectService.java`
- `backend/src/main/java/com/leadboard/project/ProjectDetailDto.java`
- `backend/src/main/java/com/leadboard/project/ProjectDto.java`
- `backend/src/main/java/com/leadboard/project/ProjectTimelineDto.java`
- `backend/src/main/java/com/leadboard/jira/JiraIssue.java`
- `backend/src/main/java/com/leadboard/sync/JiraIssueEntity.java`
- `backend/src/main/java/com/leadboard/sync/SyncService.java`
- `backend/src/test/java/com/leadboard/project/ProjectServiceTest.java`
- `backend/src/main/resources/db/migration/V40__add_description_to_jira_issues.sql`
- `backend/src/main/resources/db/migration/V41__add_assignee_avatar_url.sql`

### Frontend (изменённые)
- `frontend/package.json` — version 0.39.0
- `frontend/src/api/projects.ts`
- `frontend/src/pages/ProjectTimelinePage.tsx`
- `frontend/src/pages/ProjectTimelinePage.css`
- `frontend/src/pages/TimelinePage.tsx`
- `frontend/src/pages/TimelinePage.css`
- `frontend/src/pages/ProjectsPage.tsx`
- `frontend/src/pages/DataQualityPage.tsx`
- `frontend/src/components/Modal.tsx`

## Правила Design System (закреплённые)

1. **Иконки типов задач** — `getIssueIcon(type, getIssueTypeIconUrl(type))`. Запрещены локальные импорты иконок.
2. **Цвета статусов** — `StatusStylesContext` / `StatusBadge`. Запрещены hardcoded `STATUS_COLORS`.
3. **Цвета команд** — `TeamBadge` или `team.color`. Команда = её цвет везде.
4. **Цвета фаз** — `getRoleColor(code)` из `WorkflowConfigContext`. Запрещены hardcoded фазовые цвета.

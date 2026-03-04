# F57: Jira Worklog Sync + Per-Day Role Coloring on Timeline

## Статус: ✅ ЗАВЕРШЕНА (v0.57.0)

## Описание

Синхронизация Jira worklogs и отображение факта работы по дням на Timeline. Вместо сплошных фазовых блоков (SA→DEV→QA) ретроспективные стори показывают per-day сегменты: серый фон = нет работы, цвет роли = есть worklog в этот день.

## Визуальный результат

```
До (сплошные блоки):  ████SA████████DEV████████QA████░░░░░░░
После (факт по дням): ░░█░░░██░█░░░░███░░░░█░█░░░░░░░░░░░░
                         SA  SA DEV   DEV  QA
```

## Backend

### БД
- **V48__worklog_sync.sql** + **T8__worklog_sync.sql**: таблица `issue_worklogs`
  - `id`, `issue_key` (FK → jira_issues), `worklog_id`, `author_account_id`
  - `time_spent_seconds`, `started_date` (DATE), `role_code`
  - Unique constraint: `(issue_key, worklog_id)`
  - Индексы: `issue_key`, `started_date`

### Jira API
- **JiraWorklogResponse** — DTO для `/rest/api/3/issue/{key}/worklog`
- **JiraClient.fetchIssueWorklogs()** — OAuth + BasicAuth, пагинация

### Entity + Repository
- **IssueWorklogEntity** — маппинг таблицы
- **IssueWorklogRepository** — CRUD + агрегирующий native query (GROUP BY issue_key, started_date, role_code)

### Импорт
- **WorklogImportService**:
  - `importWorklogsForIssuesAsync(keys)` — после синка для subtask'ов
  - `importAllWorklogsAsync(projectKey)` — полный импорт всех subtask'ов
  - `importWorklogsForIssue(key)` — идемпотентно (delete+insert)
  - Role resolution: subtask.workflowRole (primary) → team_members.role (fallback)

### Интеграция
- **SyncService**: хук после changelog import — фильтрует subtask'и и запускает worklog import
- **SyncController**: `POST /api/sync/import-worklogs` — ручной импорт (ADMIN)

### Обогащение данных
- **RetrospectiveResult.WorklogDay** — `record(date, roleCode, timeSpentSeconds)`
- **RetroStory.worklogDays** — nullable список WorklogDay
- **RetrospectiveTimelineService**: batch-load worklogs для subtask'ов, агрегация по parent story → date → role

## Frontend

### Типы
- **WorklogDay** interface в `forecast.ts`
- **RetroStory.worklogDays** — `WorklogDay[] | null`

### Timeline рендеринг
- `mergeHybridEpics` пробрасывает `_worklogDays` на каждую story
- **StoryBar**: новая функция `renderWorklogSegments()`
  - Каждый день = div, цвет = доминирующая роль (по max timeSpentSeconds)
  - Дни без worklogs = серый фон (#e5e7eb)
- Логика переключения:
  - `_source === 'forecast'` → текущие полосатые блоки (без изменений)
  - `_source === 'retro'/'hybrid'` + есть worklogDays → per-day сегменты
  - `_source === 'retro'/'hybrid'` + нет worklogDays → fallback на фазовые блоки

## Тесты

- **WorklogImportServiceTest** (6 тестов): happy path, role fallback, no worklogs, idempotency, date parsing, role priority
- **RetrospectiveTimelineServiceTest** (+2 теста): worklogDays populated, worklogDays null

## API

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/api/sync/import-worklogs` | Ручной импорт worklogs (ADMIN) |

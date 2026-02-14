# F30: Member Profile Page

## Статус: ✅ Done (2026-02-14)

## Описание

Страница профиля участника команды с реальными данными из Jira: завершённые, активные и предстоящие задачи, недельный тренд DSR, сводные метрики.

## Что реализовано

### Backend

- **MemberProfileService** — агрегация данных профиля:
  - Завершённые задачи (subtask'и с timeSpent > 0 за период)
  - Активные задачи (IN_PROGRESS subtask'и по assignee)
  - Предстоящие задачи (NEW/PLANNED subtask'и в эпиках команды)
  - Недельный тренд DSR (estimate vs spent по неделям)
  - Summary: avgDsr, avgCycleTimeDays, utilization, totalSpentH, totalEstimateH
- **MemberProfileServiceTest** — 264 строки, покрытие основных сценариев

### Frontend

- **MemberProfilePage** — переписана с мок-данных на реальный API:
  - Фильтр по периоду (from/to)
  - Таблицы: Completed Tasks, Active Tasks, Upcoming Tasks
  - Тренд-чарт DSR по неделям
  - Summary cards (задачи, DSR, время цикла, утилизация)

## API

```
GET /api/teams/{teamId}/members/{memberId}/profile?from=2026-01-01&to=2026-02-14
```

### Response

```json
{
  "member": { "id", "displayName", "role", "grade", "hoursPerDay", "teamName", "teamId" },
  "completedTasks": [{ "key", "summary", "epicKey", "epicSummary", "estimateH", "spentH", "dsr", "doneDate" }],
  "activeTasks": [{ "key", "summary", "epicKey", "epicSummary", "estimateH", "spentH", "status" }],
  "upcomingTasks": [{ "key", "summary", "epicKey", "epicSummary", "estimateH", "spentH", "status" }],
  "weeklyTrend": [{ "week", "weekStart", "dsr", "tasksCompleted", "hoursLogged" }],
  "summary": { "completedCount", "avgDsr", "avgCycleTimeDays", "utilization", "totalSpentH", "totalEstimateH" }
}
```

## Файлы

### Новые
- `backend/src/main/java/com/leadboard/team/MemberProfileService.java`
- `backend/src/main/java/com/leadboard/team/dto/MemberProfileResponse.java`
- `backend/src/test/java/com/leadboard/team/MemberProfileServiceTest.java`

### Модифицированные
- `backend/src/main/java/com/leadboard/team/TeamController.java` — новый endpoint
- `frontend/src/api/teams.ts` — `getMemberProfile()` API клиент
- `frontend/src/pages/MemberProfilePage.tsx` — переписана на реальные данные

## Также в этом коммите

- **MappingAutoDetectService** — автодетект маппингов из Jira metadata
- **StatusMapping.color** — поле цвета для статусов (V27 миграция)
- **WorkflowConfigPage** — pipeline view для статусов с drag-and-drop карточками, color picker

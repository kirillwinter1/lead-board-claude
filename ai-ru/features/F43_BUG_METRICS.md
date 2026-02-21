# F43: Bug Metrics Dashboard

## Статус: ✅ Реализовано (v0.43.0)

## Описание

Отдельная страница с метриками по багам: количество открытых, SLA compliance, среднее время решения, распределение по приоритетам, список открытых багов с SLA-статусом.

## Что реализовано

### Backend

**DTO:** `BugMetricsResponse` (record) с вложенными `PriorityMetrics` и `OpenBugDto`.

**Сервис:** `BugMetricsService.getBugMetrics(Long teamId)`:
- Загрузка всех BUG-задач (опционально по teamId)
- Разделение на open/resolved через `WorkflowConfigService.isDone()`
- Карточки: openBugs, staleBugs (через `BugSlaService.checkStale()`), avgResolutionHours, slaCompliancePercent
- Группировка по приоритету с SLA-метриками
- Список открытых багов с сортировкой по приоритету + возрасту

**Контроллер:** `GET /api/metrics/bugs?teamId=N` (teamId опциональный)

### Frontend

**Страница:** `BugMetricsPage` (`/board/bug-metrics`)
- Фильтр по команде (select)
- 4 карточки MetricCard: Open Bugs, SLA Compliance %, Avg Resolution, Stale Bugs
- Таблица по приоритетам (цветные точки, open/resolved/avg/SLA limit/compliance)
- Таблица открытых багов (ссылка на Jira, summary, priority, StatusBadge, age, SLA badge)

**Навигация:** Таб "Bugs" после "Data Quality"

### Переиспользуемые компоненты
- `MetricCard` — карточки метрик
- `StatusBadge` — статусы в таблице багов
- `BugSlaService` — checkSlaBreach, getResolutionTimeHours, checkStale
- `WorkflowConfigService` — isDone()

## API

### GET /api/metrics/bugs

**Параметры:**
| Параметр | Тип | Обязательный | Описание |
|----------|-----|-------------|----------|
| teamId | Long | Нет | ID команды для фильтрации |

**Ответ:**
```json
{
  "openBugs": 5,
  "resolvedBugs": 12,
  "staleBugs": 1,
  "avgResolutionHours": 48,
  "slaCompliancePercent": 83.3,
  "byPriority": [
    {
      "priority": "High",
      "openCount": 2,
      "resolvedCount": 5,
      "avgResolutionHours": 36,
      "slaLimitHours": 72,
      "slaCompliancePercent": 80.0
    }
  ],
  "openBugList": [
    {
      "issueKey": "PROJ-123",
      "summary": "Bug summary",
      "priority": "High",
      "status": "In Progress",
      "ageDays": 3,
      "ageHours": 72,
      "slaBreach": false,
      "jiraUrl": "https://jira.example.com/browse/PROJ-123"
    }
  ]
}
```

## Тесты

`BugMetricsServiceTest` — 7 тестов:
- Пустой список → все нули
- Микс open/resolved → правильные счётчики
- SLA breach/compliance → правильные проценты
- Группировка по приоритету
- Фильтр по teamId
- Stale bugs подсчёт
- Сортировка open bug list

## Файлы

### Новые
- `backend/.../metrics/dto/BugMetricsResponse.java`
- `backend/.../metrics/service/BugMetricsService.java`
- `backend/.../metrics/controller/BugMetricsController.java`
- `backend/.../metrics/service/BugMetricsServiceTest.java` (тест)
- `frontend/src/pages/BugMetricsPage.tsx`
- `frontend/src/pages/BugMetricsPage.css`

### Изменённые
- `frontend/src/api/metrics.ts` — типы + fetchBugMetrics
- `frontend/src/App.tsx` — route
- `frontend/src/components/Layout.tsx` — NavLink "Bugs"
- `backend/build.gradle.kts` — 0.43.0
- `frontend/package.json` — 0.43.0

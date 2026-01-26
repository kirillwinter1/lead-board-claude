# F22: Team Metrics (Командные метрики)

## Цель

Реализовать страницу командных метрик для тимлида с ключевыми показателями эффективности:
- **Throughput** — сколько задач выпущено за период
- **Lead Time** — время от создания до завершения
- **Cycle Time (LTC)** — время от начала работы до завершения
- **Time in Status** — сколько задача провела в каждом статусе
- **Детализация по исполнителям** — кто сколько закрыл

## Метрики

### Throughput
Количество завершённых задач за период.
```
Throughput = COUNT(issues) WHERE done_at BETWEEN from AND to
```

### Lead Time
Время от создания задачи до её завершения (в днях).
```
Lead Time = done_at - jira_created_at
```

### Cycle Time
Время от начала работы до завершения (в днях).
```
Cycle Time = done_at - started_at
(если started_at null, используется jira_created_at)
```

### Time in Status
Среднее время нахождения задачи в каждом статусе.
Рассчитывается на основе `status_changelog`.

## Структура БД

### Миграция V16: добавление `done_at`
```sql
ALTER TABLE jira_issues ADD COLUMN done_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_jira_issues_done_at ON jira_issues(done_at);
CREATE INDEX idx_jira_issues_team_done ON jira_issues(team_id, done_at) WHERE done_at IS NOT NULL;
CREATE INDEX idx_jira_issues_assignee_done ON jira_issues(assignee_account_id, done_at)
    WHERE done_at IS NOT NULL AND assignee_account_id IS NOT NULL;
```

### Миграция V17: таблица `status_changelog`
```sql
CREATE TABLE status_changelog (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL,
    issue_id VARCHAR(50) NOT NULL,
    from_status VARCHAR(100),
    to_status VARCHAR(100) NOT NULL,
    transitioned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    time_in_previous_status_seconds BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_changelog_issue FOREIGN KEY (issue_key)
        REFERENCES jira_issues(issue_key) ON DELETE CASCADE
);
```

## Backend API

### Endpoints

| Endpoint | Описание |
|----------|----------|
| `GET /api/metrics/summary` | Все метрики вместе |
| `GET /api/metrics/throughput` | Throughput с группировкой по периодам |
| `GET /api/metrics/lead-time` | Lead Time статистика |
| `GET /api/metrics/cycle-time` | Cycle Time статистика |
| `GET /api/metrics/time-in-status` | Время в статусах |
| `GET /api/metrics/by-assignee` | Метрики по исполнителям |

### Параметры запросов

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `teamId` | Long | Да | ID команды |
| `from` | LocalDate | Да | Начало периода |
| `to` | LocalDate | Да | Конец периода |
| `issueType` | String | Нет | Фильтр по типу (Epic/Story/Sub-task) |
| `epicKey` | String | Нет | Фильтр по эпику |
| `assigneeAccountId` | String | Нет | Фильтр по исполнителю |

### Пример ответа `/api/metrics/summary`
```json
{
  "from": "2024-01-01",
  "to": "2024-01-31",
  "teamId": 1,
  "throughput": {
    "totalEpics": 2,
    "totalStories": 15,
    "totalSubtasks": 45,
    "total": 62,
    "byPeriod": [
      {
        "periodStart": "2024-01-01",
        "periodEnd": "2024-01-07",
        "epics": 1,
        "stories": 5,
        "subtasks": 12,
        "total": 18
      }
    ]
  },
  "leadTime": {
    "avgDays": 5.5,
    "medianDays": 4.0,
    "p90Days": 12.0,
    "minDays": 1.0,
    "maxDays": 20.0,
    "sampleSize": 62
  },
  "cycleTime": {
    "avgDays": 3.5,
    "medianDays": 2.5,
    "p90Days": 8.0,
    "minDays": 0.5,
    "maxDays": 15.0,
    "sampleSize": 62
  },
  "timeInStatuses": [
    {
      "status": "In Progress",
      "avgHours": 24.5,
      "medianHours": 20.0,
      "transitionsCount": 50
    }
  ],
  "byAssignee": [
    {
      "accountId": "123",
      "displayName": "John Doe",
      "issuesClosed": 10,
      "avgLeadTimeDays": 5.5,
      "avgCycleTimeDays": 3.2
    }
  ]
}
```

## Frontend

### Страница Team Metrics (`/metrics`)

**Фильтры:**
- Team — выбор команды
- Period — 7/14/30/60/90 дней
- Issue Type — All/Epics/Stories/Sub-tasks

**Компоненты:**
1. **Summary Cards** — 4 карточки с основными метриками:
   - Throughput (общее количество)
   - Avg Lead Time (средний lead time)
   - Avg Cycle Time (средний cycle time)
   - Sample Size (выборка)

2. **Throughput Chart** — stacked bar chart по периодам (неделям)
   - Цвета: Epic (фиолетовый), Story (синий), Subtask (голубой)

3. **Time in Status Chart** — horizontal bar chart
   - Показывает среднее время в каждом статусе

4. **Assignee Table** — таблица с метриками по исполнителям
   - Name, Issues Closed, Avg Lead Time, Avg Cycle Time
   - Сортировка по Issues Closed

5. **WIP History** — существующий компонент с историей WIP

## Интеграция с SyncService

При синхронизации задач из Jira:
1. Детектируется изменение статуса
2. Создаётся запись в `status_changelog`
3. Обновляется `done_at` при переходе в Done статус
4. При reopening задачи `done_at` очищается

## Структура файлов

### Backend
```
backend/src/main/java/com/leadboard/metrics/
├── entity/
│   └── StatusChangelogEntity.java
├── repository/
│   ├── StatusChangelogRepository.java
│   └── MetricsQueryRepository.java
├── service/
│   ├── StatusChangelogService.java
│   └── TeamMetricsService.java
├── controller/
│   └── TeamMetricsController.java
└── dto/
    ├── ThroughputResponse.java
    ├── PeriodThroughput.java
    ├── LeadTimeResponse.java
    ├── CycleTimeResponse.java
    ├── TimeInStatusResponse.java
    ├── AssigneeMetrics.java
    └── TeamMetricsSummary.java
```

### Frontend
```
frontend/src/
├── api/
│   └── metrics.ts
├── components/
│   └── metrics/
│       ├── MetricCard.tsx
│       ├── ThroughputChart.tsx
│       ├── TimeInStatusChart.tsx
│       └── AssigneeTable.tsx
└── pages/
    └── TeamMetricsPage.tsx (обновлён)
```

## Тестовое покрытие

### Unit tests
- `StatusChangelogServiceTest.java` — 5 тестов
- `TeamMetricsServiceTest.java` — 8 тестов
- `TeamMetricsControllerTest.java` — 5 тестов

### Покрываемые сценарии
- Запись status changelog при смене статуса
- Игнорирование одинаковых статусов
- Установка done_at при переходе в Done
- Очистка done_at при reopening
- Расчёт throughput с группировкой
- Расчёт lead time / cycle time статистики
- Агрегация по исполнителям
- Конвертация времени в часы

## Статус

✅ **Готово** — 2026-01-26

### Реализовано
- [x] Миграции V16, V17
- [x] StatusChangelogEntity + Repository
- [x] StatusChangelogService
- [x] Интеграция с SyncService
- [x] TeamMetricsService с расчётами
- [x] TeamMetricsController + DTOs
- [x] Frontend API client
- [x] Frontend компоненты (MetricCard, charts, table)
- [x] Обновление TeamMetricsPage
- [x] CSS стили
- [x] Unit tests (backend)
- [x] Документация

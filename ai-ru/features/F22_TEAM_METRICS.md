# F22 + F24: Team Metrics

> Объединяет F22_TEAM_METRICS.md (базовые метрики) и F24_TEAM_METRICS_V2.md (DSR, Forecast Accuracy)

## Обзор

Страница `/metrics` — командные метрики для тимлида с ключевыми показателями эффективности.

## Метрики

### Throughput
Количество завершённых задач за период.
```
Throughput = COUNT(issues) WHERE done_at BETWEEN from AND to
```

### Lead Time
Время от создания до завершения (в днях).
```
Lead Time = done_at - jira_created_at
```

### Cycle Time
Время от начала работы до завершения (в днях).
```
Cycle Time = done_at - started_at
```

### DSR (Delivery Speed Ratio)
Коэффициент точности оценок.
```
DSR = фактические рабочие дни в Developing / (original_estimate / 8h)
```
Цветовая индикация: зелёный (≤1.1), жёлтый (1.1-1.5), красный (>1.5).

### Forecast Accuracy
Сравнение plan vs fact для завершённых эпиков.
- `actualStart` — из StatusChangelog (первый переход в Developing)
- `actualEnd` — из StatusChangelog (последний переход в Done)
- Рабочие дни через WorkCalendarService
- Колонка "Оценка": initialEstimateHours vs developingEstimateHours

### Time in Status
Среднее время в каждом статусе (из `status_changelog`).

## Структура БД

### Миграция V16: `done_at` в jira_issues
```sql
ALTER TABLE jira_issues ADD COLUMN done_at TIMESTAMP WITH TIME ZONE;
```

### Миграция V17: `status_changelog`
```sql
CREATE TABLE status_changelog (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL,
    issue_id VARCHAR(50) NOT NULL,
    from_status VARCHAR(100),
    to_status VARCHAR(100) NOT NULL,
    transitioned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    time_in_previous_status_seconds BIGINT,
    CONSTRAINT fk_changelog_issue FOREIGN KEY (issue_key) REFERENCES jira_issues(issue_key) ON DELETE CASCADE
);
```

## API

| Endpoint | Описание |
|----------|----------|
| `GET /api/metrics/summary` | Все метрики |
| `GET /api/metrics/throughput` | Throughput с группировкой |
| `GET /api/metrics/lead-time` | Lead Time статистика |
| `GET /api/metrics/cycle-time` | Cycle Time статистика |
| `GET /api/metrics/time-in-status` | Время в статусах |
| `GET /api/metrics/by-assignee` | По исполнителям |
| `GET /api/metrics/dsr` | DSR по эпикам |
| `GET /api/metrics/forecast-accuracy` | Forecast vs actual |

### Параметры
- `teamId` (обяз.) — ID команды
- `from`, `to` (обяз.) — период
- `issueType`, `epicKey`, `assigneeAccountId` — фильтры

## Frontend

### Summary Cards (gauge-карточки)
- DSR команды — среднее с цветовой зоной
- DSR Forecast — точность прогноза
- Throughput — закрыто задач
- On-Time Delivery — % вовремя

### Компоненты
- `MetricCard.tsx` — карточка метрики
- `ThroughputChart.tsx` — stacked bar по неделям
- `DsrGauge.tsx` — gauge с цветовыми зонами
- `ForecastAccuracyChart.tsx` — таблица plan vs fact с estimate change
- `TimeInStatusChart.tsx` — horizontal bar
- `AssigneeTable.tsx` — таблица по исполнителям

## Файлы

### Backend
```
metrics/
├── controller/TeamMetricsController.java (8 endpoints)
├── service/
│   ├── TeamMetricsService.java — throughput, lead/cycle time
│   ├── ForecastAccuracyService.java — forecast vs actual
│   ├── DsrService.java — DSR расчёт
│   └── StatusChangelogService.java — status transitions
├── entity/StatusChangelogEntity.java
├── repository/StatusChangelogRepository.java, MetricsQueryRepository.java
└── dto/ — 11 DTOs
```

### Frontend
```
components/metrics/ — MetricCard, ThroughputChart, DsrGauge, ForecastAccuracyChart, etc.
pages/TeamMetricsPage.tsx
api/metrics.ts
```

## Тестовое покрытие
- StatusChangelogServiceTest — 5 тестов
- TeamMetricsServiceTest — 8 тестов
- TeamMetricsControllerTest — 5 тестов

## Запланированные улучшения (из F24)
- [ ] Epic Burndown Chart (ideal vs actual line)
- [ ] Scatter plot для Forecast Accuracy
- [ ] Throughput Trend с moving average
- [ ] Team Velocity Chart (logged vs capacity)
- [ ] Assignee Table: DSR персональный, velocity, trend

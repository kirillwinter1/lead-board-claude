# F24: Team Metrics v2

**Статус:** ✅ Завершено (2026-02-04)

## Цель

Расширить страницу `/metrics` пятью визуализациями для более глубокого анализа производительности команды:

1. **Scatter Plot для Forecast Accuracy** — точечный график plan vs fact
2. **Throughput с Moving Average** — тренд throughput + скользящее среднее
3. **Assignee Table Extended** — расширенная таблица с DSR, velocity, trend
4. **Team Velocity Chart** — logged hours vs capacity по неделям
5. **Epic Burndown Chart** — burndown для выбранного эпика

## Реализованные компоненты

### Backend

**Новые DTO:**
- `VelocityResponse` — данные о velocity команды
- `EpicBurndownResponse` — данные для burndown эпика

**Новые сервисы:**
- `VelocityService` — расчёт velocity (logged vs capacity)
- `EpicBurndownService` — расчёт burndown (ideal vs actual)

**Изменения в существующих DTO:**
- `ThroughputResponse` — добавлено поле `movingAverage: List<BigDecimal>`
- `AssigneeMetrics` — добавлены поля `personalDsr`, `velocityPercent`, `trend`

**Новые endpoints:**
- `GET /api/metrics/velocity?teamId=&from=&to=` — velocity данные
- `GET /api/metrics/epic-burndown?epicKey=` — burndown эпика
- `GET /api/metrics/epics-for-burndown?teamId=` — список эпиков для выбора

### Frontend

**Новые компоненты:**
- `ForecastScatterPlot` — scatter plot с X=plannedDays, Y=actualDays
- `VelocityChart` — bar chart capacity vs logged
- `EpicBurndownChart` — line chart с селектором эпика

**Обновлённые компоненты:**
- `ThroughputChart` — добавлена SVG линия MA
- `AssigneeTable` — добавлены колонки DSR, Velocity, Trend

## API

### GET /api/metrics/velocity

```json
{
  "teamId": 1,
  "from": "2026-01-01",
  "to": "2026-01-31",
  "totalCapacityHours": 640.0,
  "totalLoggedHours": 580.0,
  "utilizationPercent": 90.6,
  "byWeek": [
    {
      "weekStart": "2026-01-01",
      "capacityHours": 160.0,
      "loggedHours": 145.0,
      "utilizationPercent": 90.6
    }
  ]
}
```

### GET /api/metrics/epic-burndown

```json
{
  "epicKey": "PROJ-123",
  "summary": "Epic summary",
  "startDate": "2026-01-01",
  "endDate": "2026-01-31",
  "totalEstimateHours": 200,
  "idealLine": [
    {"date": "2026-01-01", "remainingHours": 200},
    {"date": "2026-01-31", "remainingHours": 0}
  ],
  "actualLine": [
    {"date": "2026-01-01", "remainingHours": 200},
    {"date": "2026-01-15", "remainingHours": 100}
  ]
}
```

## Визуализации

### 1. Scatter Plot (Forecast Accuracy)

- **X:** Плановые дни (из прогноза)
- **Y:** Фактические дни
- **Диагональ y=x:** Идеальная точность
- **Цвета точек:**
  - Зелёный: |diff| ≤ 2 дней (точный прогноз)
  - Жёлтый: |diff| 3-5 дней (приемлемо)
  - Красный: |diff| > 5 дней (нужен анализ)

### 2. Throughput + MA

- Stacked bar chart (epics/stories/subtasks)
- Красная линия: 4-недельное скользящее среднее
- MA показывает тренд независимо от флуктуаций

### 3. Assignee Table Extended

| Колонка | Описание |
|---------|----------|
| DSR | Personal Delivery Speed Ratio (time_spent / estimate) |
| Velocity | Процент использования оценки |
| Trend | UP/DOWN/STABLE по сравнению с предыдущим периодом |

**Цветовая индикация:**
- DSR: ≤1.0 зелёный, ≤1.2 жёлтый, >1.2 красный
- Velocity: 90-110% зелёный
- Trend: ↑ зелёный, ↓ красный, = серый

### 4. Velocity Chart

- Paired bars: capacity (серый) vs logged (синий)
- Summary cards: Total Capacity, Total Logged, Utilization %
- Группировка по неделям

### 5. Epic Burndown

- Selector для выбора эпика
- Dashed line: Ideal (linear от estimate до 0)
- Solid line: Actual (на основе time_spent)
- Учёт рабочих дней (WorkCalendarService)

## Файлы

### Backend
- `metrics/dto/VelocityResponse.java`
- `metrics/dto/EpicBurndownResponse.java`
- `metrics/service/VelocityService.java`
- `metrics/service/EpicBurndownService.java`
- `metrics/controller/TeamMetricsController.java` (расширен)
- `metrics/dto/ThroughputResponse.java` (расширен)
- `metrics/dto/AssigneeMetrics.java` (расширен)
- `metrics/service/TeamMetricsService.java` (расширен)
- `metrics/repository/MetricsQueryRepository.java` (новый запрос)

### Frontend
- `components/metrics/ForecastScatterPlot.tsx`
- `components/metrics/ForecastScatterPlot.css`
- `components/metrics/VelocityChart.tsx`
- `components/metrics/VelocityChart.css`
- `components/metrics/EpicBurndownChart.tsx`
- `components/metrics/EpicBurndownChart.css`
- `components/metrics/ThroughputChart.tsx` (расширен)
- `components/metrics/AssigneeTable.tsx` (расширен)
- `api/metrics.ts` (новые типы и функции)
- `pages/TeamMetricsPage.tsx` (интеграция)

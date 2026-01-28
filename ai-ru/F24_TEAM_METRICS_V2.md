# F24. Team Metrics v2 — LTC, Burndown, Forecast Integration

## Цель
Переделать страницу метрик команды с фокусом на Forecast как главную фичу. Показать эффективность команды через сравнение план vs факт.

## Страница /metrics — Новая структура

### Фильтры (без изменений)
- Team selector
- Period (7/14/30/60/90 дней)
- Epic selector (для burndown и детализации)

### Графики (порядок реализации)

---

### 1. Summary Cards (переработка)

Текущие карточки: Throughput, Avg Lead Time, Avg Cycle Time, Sample Size

**Новые карточки:**

| Карточка | Описание | Формула |
|----------|----------|---------|
| **LTC команды** | Средний Lead Time Coefficient | avg(фактические рабочие дни в Developing / original estimate в днях) |
| **LTC Forecast** | Средний коэфф. точности прогноза | avg(фактические рабочие дни в Developing / forecast длительность) |
| **Throughput** | Закрыто задач за период | count(done_at в периоде) |
| **On-Time Delivery** | % эпиков закрытых вовремя по forecast | count(actualEnd <= forecastEnd) / total |

Цветовая индикация LTC:
- Зелёный: LTC ≤ 1.1 (в рамках оценки)
- Жёлтый: 1.1 < LTC ≤ 1.5 (небольшое превышение)
- Красный: LTC > 1.5 (сильное превышение)

---

### 2. LTC Chart — Lead Time Coefficient по эпикам

**Тип:** Горизонтальная bar chart

**Ось X:** LTC значение (0 ... 3+)
**Ось Y:** Эпики (ключ + название)

**Каждый бар:**
- Цвет по LTC (зелёный/жёлтый/красный)
- Подпись: LTC значение, фактические дни / оценка дней
- Вертикальная линия на LTC = 1.0 (идеал)

**Расчёт LTC Actual для эпика:**
```
1. Найти все дни, когда эпик был в статусе "Developing"
   (из status_changelog: transition to "Developing" → transition from "Developing")
2. Вычесть выходные и праздники (производственный календарь РФ)
3. Получить original estimate (сумма original_estimate_seconds всех subtasks)
4. LTC = рабочие_дни_в_developing / (original_estimate_seconds / 8 / 3600)
```

**Расчёт LTC Forecast для эпика:**
```
1. Те же рабочие дни в Developing (факт)
2. Из forecast_snapshots: forecastDuration = planned_end - planned_start (рабочие дни)
3. LTC Forecast = рабочие_дни_в_developing / forecastDuration
```

**Backend endpoint:**
```
GET /api/metrics/ltc?teamId=1&from=2026-01-01&to=2026-01-28
Response:
{
  "avgLtcActual": 1.3,
  "avgLtcForecast": 1.1,
  "epics": [
    {
      "epicKey": "LB-216",
      "summary": "...",
      "actualWorkingDays": 15,
      "estimateDays": 10,
      "forecastDays": 14,
      "ltcActual": 1.5,
      "ltcForecast": 1.07,
      "status": "Done"
    }
  ]
}
```

---

### 3. Epic Burndown Chart

**Тип:** Line chart (по одному эпику, выбирается в фильтре)

**Ось X:** Даты (от начала эпика до конца/сегодня)
**Ось Y:** Часы (оставшиеся)

**Линии:**
1. **Ideal (Forecast)** — прямая от total estimate до 0, от forecast start до forecast end
2. **Actual** — total estimate минус нарастающий time_spent, по датам worklogs

**Данные:**
- Total estimate = сумма original_estimate_seconds всех subtasks эпика
- Actual remaining на дату D = total_estimate - sum(time_spent до D)
- Time spent берётся из `time_spent_seconds` с группировкой по дням (из Jira worklogs если есть, или из done_at)

**Визуализация:**
- Область между линиями закрашена (зелёный если факт лучше плана, красный если хуже)
- Вертикальная линия "Сегодня"
- Точка пересечения ideal с осью X = forecast end date
- Если actual выше ideal → отставание

**Backend endpoint:**
```
GET /api/metrics/burndown?epicKey=LB-216&teamId=1
Response:
{
  "epicKey": "LB-216",
  "summary": "...",
  "totalEstimateHours": 120,
  "forecastStart": "2026-01-10",
  "forecastEnd": "2026-02-05",
  "idealLine": [
    {"date": "2026-01-10", "remainingHours": 120},
    {"date": "2026-01-11", "remainingHours": 115.4},
    ...
  ],
  "actualLine": [
    {"date": "2026-01-10", "remainingHours": 120},
    {"date": "2026-01-11", "remainingHours": 118},
    ...
  ]
}
```

---

### 4. Forecast Accuracy Chart (переработка)

**Текущая:** таблица с planned vs actual dates

**Новая:** Scatter plot + таблица

**Scatter plot:**
- Ось X: Planned duration (forecast дни)
- Ось Y: Actual duration (рабочие дни)
- Диагональ = идеал (planned == actual)
- Точки выше диагонали = опоздание
- Точки ниже = раннее завершение
- Цвет точки по severity отклонения

**Таблица (остаётся)** с добавлением LTC колонки

---

### 5. Throughput Trend (переработка)

**Текущий:** Stacked bar по неделям

**Новый:** Добавить линию тренда (moving average 4 недели) + показать forecast plan (сколько задач планировалось закрыть по forecast)

---

### 6. Team Velocity Chart (новый)

**Тип:** Bar chart по неделям

**Бары:**
- Logged hours (факт) — синий
- Expected hours (capacity × рабочие дни) — серый outline

**Показывает:** команда логирует больше/меньше чем capacity

---

### 7. Assignee Table (переработка)

Добавить колонки:
- LTC (персональный)
- Velocity (logged / capacity)
- Trend (↑↓→)

---

## Зависимости

- Производственный календарь РФ (есть в ForecastService)
- status_changelog (есть, V17)
- forecast_snapshots (есть, V18)
- original_estimate_seconds, time_spent_seconds (есть в jira_issues)

## Порядок реализации

1. Summary Cards (LTC + LTC Forecast) + backend LTC endpoint
2. LTC Chart (horizontal bars)
3. Epic Burndown Chart + backend endpoint
4. Forecast Accuracy переработка (scatter plot)
5. Throughput Trend с forecast line
6. Team Velocity Chart
7. Assignee Table переработка

# F14: Timeline/Gantt

> Объединяет F14_TIMELINE_GANTT.md и F14_TIMELINE_IMPROVEMENTS.md

## Обзор

Страница `/timeline` — Gantt-диаграмма для визуализации плана работ по эпикам.

## Реализованный функционал

### Gantt-диаграмма
- Горизонтальные бары для эпиков (единый бар с progress overlay)
- Раскрытие epic → фазы SA/DEV/QA по клику
- Цвет бара по статусу: зелёный (on track), жёлтый (at risk), красный (late), серый (no due date)
- Opacity по уровню уверенности (HIGH=1.0, MEDIUM=0.7, LOW=0.4)

### Индикаторы
- **Today** — синяя вертикальная линия
- **Due Date** — красная вертикальная линия
- **No Resource** — красные полосатые бары с пульсацией

### Zoom уровни
- Day / Week / Month

### Summary Panel
- "5 epics · 3 on track · 2 at risk"

### Auto-scroll to Today

### Tooltip с деталями
- Expected Done, прогресс, confidence, фазы SA/DEV/QA

## Конвейерная модель (Pipeline)

Старая модель (последовательная):
```
SA:  [=========]
DEV:            [==================]
QA:                                 [=========]
```

Новая модель (конвейерная): DEV начинает после первой стори SA, QA после первой стори DEV:
```
SA:  [===]
DEV:   [==================]
QA:       [=========]
```

### StoryDuration (настройка в planning config)

| Параметр | Default | Влияние |
|----------|---------|---------|
| SA (дней) | 2 | Меньше → больше параллелизм |
| DEV (дней) | 2 | |
| QA (дней) | 2 | |

## Цветовая схема

| Статус | Цвет | Условие |
|--------|------|---------|
| On track | #22c55e | delta <= 0 |
| At risk | #eab308 | delta 1-5 дней |
| Late | #ef4444 | delta > 5 дней |
| No due date | #6b7280 | dueDate = null |

## Компоненты (Frontend)

```
TimelinePage
├── SummaryPanel
├── Controls (Team, Zoom, Legend)
└── GanttContainer
    ├── GanttLabels → LabelRow → PhaseLabels (expanded)
    └── GanttChart → GanttBody → GanttRow → PhaseRow (expanded)
```

## API

`GET /api/planning/forecast` — response включает `phaseSchedule` для каждого эпика:

```json
{
  "phaseSchedule": {
    "sa": { "startDate": "2026-01-26", "endDate": "2026-01-27", "workDays": 1.8, "noCapacity": false },
    "dev": { "startDate": "2026-01-29", "endDate": "2026-02-20", "workDays": 12.4, "noCapacity": false },
    "qa": { "startDate": "2026-02-04", "endDate": "2026-02-13", "workDays": 3.5, "noCapacity": false }
  }
}
```

## Запланированные улучшения

| Улучшение | Приоритет |
|-----------|-----------|
| Quarter markers (Q1/Q2/Q3/Q4) | P2 |
| Status filter (In Progress / Backlog / All) | P2 |
| Keyboard navigation | P2 |
| Click to Board | P2 |
| Capacity utilization bar | P3 |
| Export PNG/PDF | P3 |
| Multi-team view | P3 |
| Expand to stories | P4 |
| Drag & drop reorder | P4 |
| Dependencies | P4 |

## Файлы

**Backend:** `ForecastService.java`, `PlanningConfigDto.java`, `EpicForecast.java`
**Frontend:** `TimelinePage.tsx`, `App.css`, `forecast.ts`

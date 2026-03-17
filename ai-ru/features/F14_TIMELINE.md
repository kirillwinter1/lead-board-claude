# F14: Timeline/Gantt

> Объединяет F14_TIMELINE_GANTT.md и F14_TIMELINE_IMPROVEMENTS.md

## Обзор

Timeline больше не живёт как отдельная самостоятельная вкладка. Основной сценарий:
- корневая страница `/` открывает unified workspace `Board | Timeline`
- переключение между режимами идёт через query-параметр `view=timeline`
- legacy route `/timeline` сохранён как redirect на `/?view=timeline`

Timeline использует тот же контекст эпиков и те же бизнес-фильтры, что и Board.

## Реализованный функционал

### Unified Board/Timeline workspace
- Переключатель `Board | Timeline` находится внутри страницы Board
- Переключение бесшовное: без отдельного nav-tab и без смены логики фильтров
- Верхняя навигация содержит только таб `Board`
- Timeline в embedded-режиме рендерится внутри той же страницы и не дублирует board-фильтры

### Общие фильтры с Board
- Timeline использует те же фильтры, что и Board: `search`, `team`, `status`, `project`, `Hide NEW`, `Hide DONE`
- Отрисовываются только те эпики, которые уже прошли board-фильтрацию
- Для построения таймлайна должна быть выбрана ровно одна команда
- Если команда не выбрана или выбрано несколько команд, вместо бесконечной загрузки показывается empty state

### Собственные controls Timeline
- `Scale`: Day / Week / Month
- `Date`: live data или historical snapshot
- Легенда ролей и индикаторов (`Actual`, `Forecast`, `Today`, `Due Date`)

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
├── Shared Board FilterPanel
├── ViewToggle (Board | Timeline)
├── TimelineControls (Scale, Date, Legend)
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
**Frontend:** `TimelinePage.tsx`, `BoardPage.tsx`, `Layout.tsx`, `App.tsx`, `forecast.ts`

# F14. Timeline/Gantt — План улучшений

## Текущий статус

### Реализовано (v1)
- [x] Базовая Gantt-диаграмма с фазами SA/DEV/QA
- [x] Zoom уровни (Day/Week/Month)
- [x] Today и Due Date индикаторы
- [x] No Resource предупреждения
- [x] Выбор команды

### Реализовано (v2 — текущая сессия)
- [x] Summary panel — "5 epics · 3 on track · 2 at risk"
- [x] Единый бар на epic (вместо 3 отдельных)
- [x] Цвет по статусу: зелёный/жёлтый/красный/серый
- [x] Progress overlay на баре
- [x] Раскрытие epic → SA/DEV/QA фазы по клику
- [x] Умный tooltip с деталями
- [x] Auto-scroll to Today
- [x] Упрощённая легенда

---

## Запланированные улучшения

### Приоритет 1: Quick Wins

| Улучшение | Статус | Описание |
|-----------|--------|----------|
| Summary panel | ✅ Done | Количество epics, on track, at risk |
| Risk highlighting | ✅ Done | Цвет бара по статусу |
| Progress overlay | ✅ Done | Затемнённая часть = прогресс |
| Auto-scroll to Today | ✅ Done | При загрузке scroll к сегодня |
| Tooltip с деталями | ✅ Done | Expected, progress, confidence, фазы |

### Приоритет 2: UX улучшения

| Улучшение | Статус | Описание |
|-----------|--------|----------|
| Quarter markers | ⏳ | Вертикальные линии Q1/Q2/Q3/Q4 |
| Status filter | ⏳ | Показывать In Progress / Backlog / All |
| Date range filter | ⏳ | Ближайший месяц/квартал |
| Keyboard navigation | ⏳ | ↑↓ между epics, Enter для раскрытия |
| Click to Board | ⏳ | Клик открывает epic в Board |

### Приоритет 3: Аналитика

| Улучшение | Статус | Описание |
|-----------|--------|----------|
| Capacity utilization bar | ⏳ | Загрузка SA/DEV/QA в % |
| Export to PNG/PDF | ⏳ | Для презентаций |
| Multi-team view | ⏳ | Все команды на одной диаграмме |

### Приоритет 4: Расширенные возможности

| Улучшение | Статус | Описание |
|-----------|--------|----------|
| Expand to stories | ⏳ | Раскрыть epic → stories внутри |
| Drag & drop reorder | ⏳ | Перетаскивание для смены приоритета |
| Dependencies | ⏳ | Связи между эпиками (blocked by) |

---

## Технические заметки

### Структура компонентов (v2)

```
TimelinePage
├── SummaryPanel          # Статистика epics
├── Controls              # Team, Zoom, Legend
└── GanttContainer
    ├── GanttLabels       # Левая панель с epic names
    │   └── LabelRow      # Epic key + expand icon
    │       └── PhaseLabels  # SA/DEV/QA tags (expanded)
    └── GanttChart
        └── GanttBody
            └── GanttRow     # Unified bar
                └── PhaseRow # SA/DEV/QA bars (expanded)
```

### Цветовая схема

| Статус | Цвет | Условие |
|--------|------|---------|
| On track | `#22c55e` (green) | delta <= 0 |
| At risk | `#eab308` (yellow) | delta 1-5 дней |
| Late | `#ef4444` (red) | delta > 5 дней |
| No due date | `#6b7280` (gray) | dueDate = null |

### Tooltip позиционирование

- Fixed positioning (не absolute)
- Появляется над баром
- Задержка 300ms перед показом
- z-index: 9999

---

## История изменений

| Дата | Изменение |
|------|-----------|
| 2026-01-24 | v2: Progressive disclosure, unified bar, tooltips |
| 2026-01-23 | v1: Базовая реализация с SA/DEV/QA барами |

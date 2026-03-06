# F59 — Filter UX Redesign

**Статус:** ✅ Реализовано
**Дата:** 2026-03-06
**Версия:** 0.59.0

## Описание

Визуальный редизайн фильтров и выпадающих списков в стиле Linear. Единая библиотека компонентов для всех страниц.

## Новые компоненты

| Компонент | Файл | Описание |
|-----------|------|----------|
| `FilterBar` | `components/FilterBar.tsx` | Контейнер фильтров (серый фон, тень, скруглённые углы) |
| `FilterChips` | `components/FilterChips.tsx` | Чипсы активных фильтров |
| `SearchInput` | `components/SearchInput.tsx` | Поиск с AI-подсказками из реальных эпиков |
| `SingleSelectDropdown` | `components/SingleSelectDropdown.tsx` | Единичный выбор (замена нативных `<select>`) |
| `MultiSelectDropdown` | `components/MultiSelectDropdown.tsx` | Множественный выбор (обновлён) |

## Визуальные изменения

### Pill-shaped элементы
- Все триггеры dropdown: `border-radius: 20px`, `border: 1.5px solid #e9ecef`
- Toggle-кнопки (Hide NEW/DONE): pill shape
- Чипсы фильтров: `border-radius: 20px`
- Поле поиска: pill shape

### Тонированные фоны
- FilterBar: `background: #f8f9fa` (серый тинт)
- Chips row: `background: #f1f3f5`
- Выбранный триггер: `background: #e7f1ff; border-color: #b3d4ff; color: #0052cc`

### Глубокие тени на меню
- `box-shadow: 0 8px 30px rgba(0,0,0,0.12)`
- `border-radius: 12px`
- Items с `border-radius: 6px; margin: 0 6px`

### Чекмарки вместо чекбоксов
- MultiSelectDropdown: убран `<input type="checkbox">`, SVG чекмарк справа (`#0052cc`)
- SingleSelectDropdown: SVG чекмарк для выбранного элемента

### CSS-класс `has-selection`
- Триггер подсвечивается голубым когда есть выбранные элементы

## Замена нативных `<select>` → SingleSelectDropdown

| Страница | Selects |
|----------|---------|
| TimelinePage | Команда (с цветом), Масштаб, Дата |
| ProjectTimelinePage | PM, Масштаб |
| TeamMetricsPage | Period (WIP History) |
| EpicBurndownChart | Epic selector |
| QuarterlyPlanningPage | Quarter |

**Не затронуты:** формы и настройки (WorkflowConfig, Settings, BugSla, AbsenceModal, RiceForm, Poker).

## Поиск — AI Search UX

- Ширина: 300px (было 160px)
- Placeholder: `"AI search: key, text, or meaning..."`
- При фокусе на пустом поле — dropdown с реальными названиями эпиков как подсказки
- Клик по подсказке вставляет текст в поиск
- Badge `AI`/`TXT` показывает режим поиска

## Timeline — тултипы у курсора

Все тултипы на Timeline (EpicLabel, StoryBar, RoughEstimateBar) теперь следуют за курсором через `onMouseMove` + `clientX/clientY` вместо привязки к центру элемента.

## Страницы с обновлёнными фильтрами (6)

1. **BoardPage** — FilterBar + FilterChips + SearchInput + MultiSelectDropdown
2. **DataQualityPage** — FilterBar + MultiSelectDropdown
3. **BugMetricsPage** — FilterBar + SingleSelectDropdown
4. **ProjectsPage** — FilterBar + MultiSelectDropdown + SearchInput
5. **QuarterlyPlanningPage** — FilterBar + SingleSelectDropdown
6. **WorkflowConfigPage** — FilterBar + SingleSelectDropdown

## Файлы

### Новые
- `frontend/src/components/FilterBar.tsx` + `.css`
- `frontend/src/components/FilterChips.tsx` + `.css`
- `frontend/src/components/SearchInput.tsx`
- `frontend/src/components/SingleSelectDropdown.tsx`

### Изменённые
- `frontend/src/components/MultiSelectDropdown.tsx` + `.css`
- `frontend/src/components/board/FilterPanel.tsx`
- `frontend/src/pages/BoardPage.tsx`
- `frontend/src/pages/TimelinePage.tsx`
- `frontend/src/pages/ProjectTimelinePage.tsx`
- `frontend/src/pages/TeamMetricsPage.tsx`
- `frontend/src/pages/QuarterlyPlanningPage.tsx`
- `frontend/src/pages/DataQualityPage.tsx`
- `frontend/src/pages/BugMetricsPage.tsx`
- `frontend/src/pages/ProjectsPage.tsx`
- `frontend/src/pages/WorkflowConfigPage.tsx`
- `frontend/src/components/metrics/EpicBurndownChart.tsx`

# F61 — Projects UX Polish

**Статус:** Done
**Версия:** 0.62.0
**Дата:** 2026-03-06

## Что сделано

### Unified Projects Page (List + Gantt)
- Объединены две страницы (`/projects` + `/project-timeline`) в одну с переключателем List/Gantt
- Удалён отдельный маршрут `/project-timeline` и `ProjectTimelinePage.tsx`
- Извлечён компонент `ProjectGanttView.tsx` из старой страницы
- `ViewToggle` — кнопочный переключатель вместо навигации (без перезагрузки)
- Оба датасета загружаются параллельно при монтировании
- Все фильтры (search, PM, status, team) сохраняются в URL params и работают в обоих видах

### Shared Controls
- Sort dropdown виден в обоих видах (List + Gantt), сортировка применяется к таймлайну
- Collapse/Expand all работает в обоих видах (List переведён на multi-expand)
- Zoom перенесён в trailing area (правая сторона, только Gantt)

### Smart AI Search
- Короткие запросы (<3 символов): локальный поиск по ключу + summary
- Запросы >= 3 символов: debounced запрос к `/api/board/search` (семантический/substring)
- Найденные epic keys матчатся на родительские проекты
- Показывает loading indicator, AI/TXT badge, search hints из названий проектов
- Placeholder: "AI search: key, text, or meaning..."

### Gantt UX
- Epic rows: убраны тяжёлые TeamBadge + StatusBadge, заменены на компактную цветную точку команды
- Project tooltip: добавлены Status (badge), RICE score, Quarter label
- Project key — кликабельная ссылка в Jira

### Filter Chip Contrast Fix
- CSS: `border: none` -> `border: 1px solid transparent` (inline borderColor теперь работает)
- Текст чипа всегда тёмный (#495057), цвет показывается через точку и фон
- Убран цветной override на кнопке удаления

### Backend
- `ProjectTimelineDto`: добавлено поле `quarterLabel` из `JiraIssueEntity.getQuarterLabel()`

## Файлы

### Frontend
- `src/pages/ProjectsPage.tsx` — unified page (rewritten)
- `src/components/ProjectGanttView.tsx` — extracted Gantt component (new)
- `src/components/ViewToggle.tsx` — button-based toggle (new)
- `src/pages/ProjectTimelinePage.tsx` — deleted
- `src/pages/ProjectTimelinePage.css` — updated (team dot, link styles)
- `src/components/FilterBar.css` — chips separator
- `src/components/FilterChips.css` — border fix
- `src/components/FilterChips.tsx` — contrast fix
- `src/App.tsx` — removed /project-timeline route
- `src/components/Layout.tsx` — simplified nav
- `src/api/projects.ts` — added quarterLabel to ProjectTimelineDto

### Backend
- `ProjectTimelineDto.java` — added quarterLabel field
- `ProjectService.java` — pass quarterLabel from entity

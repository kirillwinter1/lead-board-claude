# F67: Quarter Label & Filter on Board and Projects

**Версия:** 0.67.0
**Дата:** 2026-03-10

## Описание

Добавлен quarter label (напр. "2026Q2") на Board и Projects с фильтрацией. Quarter labels берутся из Jira labels, совпадающих с паттерном `\d{4}Q[1-4]`. Эпики наследуют quarter от родительского PROJECT issue.

## Изменения

### Backend
- `BoardNode`: новое поле `quarterLabel`
- `BoardService`: резолвинг quarter label для эпиков с наследованием от parent project (аналогично `QuarterlyPlanningService.resolveQuarterLabel`)
- `ProjectDto`: добавлено поле `quarterLabel`
- `ProjectService.listProjects()`: передаёт `getQuarterLabel()` в DTO

### Frontend
- `BoardNode` interface: добавлено `quarterLabel`
- `ProjectDto` interface: добавлено `quarterLabel`
- `useBoardFilters`: добавлены `selectedQuarters`, `availableQuarters`, `handleQuarterToggle`, фильтрация по quarter
- `FilterPanel`: опциональный Quarter dropdown (MultiSelectDropdown) с поддержкой `__NO_QUARTER__` sentinel
- `BoardPage`: проброс quarter filter props в FilterPanel
- `BoardRow`: зелёный badge с quarter label на epic-строках
- `ProjectsPage`: Quarter filter dropdown, chips, фильтрация list и Gantt view, URL persistence (`?quarter=`)
- `MultiSelectDropdown`: новый проп `renderOption` для кастомного отображения опций

### Тесты
- `BoardServiceQuarterLabelTest`: 4 теста — прямой label, наследование, null, приоритет собственного label

## Файлы

| Файл | Изменение |
|------|-----------|
| `backend/.../board/BoardNode.java` | +quarterLabel field |
| `backend/.../board/BoardService.java` | +resolveQuarterLabel, quarter label resolution |
| `backend/.../project/ProjectDto.java` | +quarterLabel to record |
| `backend/.../project/ProjectService.java` | Pass quarterLabel to DTO |
| `frontend/src/components/board/types.ts` | +quarterLabel to BoardNode |
| `frontend/src/api/projects.ts` | +quarterLabel to ProjectDto |
| `frontend/src/hooks/useBoardFilters.ts` | +quarter filter state & logic |
| `frontend/src/components/board/FilterPanel.tsx` | +Quarter dropdown |
| `frontend/src/pages/BoardPage.tsx` | Wire quarter filter |
| `frontend/src/components/board/BoardRow.tsx` | +quarter badge |
| `frontend/src/pages/ProjectsPage.tsx` | +quarter filter, chips, URL |
| `frontend/src/components/MultiSelectDropdown.tsx` | +renderOption prop |
| `backend/.../board/BoardServiceQuarterLabelTest.java` | 4 unit tests |

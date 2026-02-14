# F31: Dynamic Status Colors on Board

## Статус: ✅ Done (2026-02-14)

## Описание

Цвета статусов на главной Board подтягиваются из Workflow Configuration (поле `color` в `status_mappings`) вместо хардкоженных CSS-классов. Также улучшен UX color picker на странице Workflow Configuration.

## Что реализовано

### Backend

- **Public endpoint** `GET /api/config/workflow/status-styles`:
  - Возвращает `Map<statusName, {color, statusCategory}>` для всех статусов
  - Агрегирует из `status_mappings`, дедупликация по `jiraStatusName`
  - Публичный (без авторизации), покрыт правилом `/api/config/workflow/**` в SecurityConfig

### Frontend

- **StatusStylesContext** — React Context для передачи стилей статусов без prop drilling
- **StatusBadge** — обновлён:
  - Принимает опциональный проп `color`
  - Читает стили из контекста по имени статуса
  - Если цвет найден — inline `backgroundColor` + авто-контраст текста (W3C luminance)
  - Если нет — fallback на CSS-классы (`.status-badge.in-progress` и т.д.)
- **BoardPage** — загружает `getStatusStyles()` при mount, оборачивает в `StatusStylesProvider`

### Color Picker UX

- **StatusColorPicker** — `position: fixed` вместо `absolute` (не обрезается `overflow` контейнером pipeline)
- Закрытие по клику вне picker'а через `mousedown` listener (вместо backdrop-элемента)
- Выбор цвета не закрывает dropdown — можно перебирать варианты
- Добавлены 3 новых цвета: Pink (#FCE7F3), Mint (#D1FAE5), Peach (#FED7AA)
- Grid расширен до 7 колонок (всего 13 цветов в 2 ряда)

### Story AutoScore Fix

- `StoryAutoScoreService` использует `score_weight` вместо `sort_order` для расчёта веса статуса
- `sort_order` — для порядка в pipeline, `score_weight` — для приоритетного скоринга
- Добавлен метод `WorkflowConfigService.getStoryStatusScoreWeight()`

## API

```
GET /api/config/workflow/status-styles
```

### Response

```json
{
  "Готово": { "color": "#E3FCEF", "statusCategory": "DONE" },
  "Новое": { "color": "#DFE1E6", "statusCategory": "NEW" },
  "Dev Review": { "color": "#DEEBFF", "statusCategory": "IN_PROGRESS" },
  ...
}
```

## Файлы

### Новые
- `frontend/src/components/board/StatusStylesContext.tsx`

### Модифицированные
- `backend/src/main/java/com/leadboard/config/controller/PublicConfigController.java` — status-styles endpoint
- `backend/src/main/java/com/leadboard/config/service/WorkflowConfigService.java` — `getStoryStatusScoreWeight()`
- `backend/src/main/java/com/leadboard/planning/StoryAutoScoreService.java` — score_weight вместо sort_order
- `frontend/src/api/board.ts` — `StatusStyle`, `getStatusStyles()`
- `frontend/src/components/board/StatusBadge.tsx` — dynamic colors
- `frontend/src/pages/BoardPage.tsx` — StatusStylesProvider
- `frontend/src/pages/WorkflowConfigPage.tsx` — StatusColorPicker improvements
- `frontend/src/pages/WorkflowConfigPage.css` — fixed dropdown, 7-column grid

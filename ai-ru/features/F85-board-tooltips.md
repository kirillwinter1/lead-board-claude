# F85 — Board Tooltips

**Версия:** 0.85.0
**Дата:** 2026-07-02

## Суть

Два hover-tooltip'а на строке доски (вкладка Board):
- **Эпик** (наведение на ключ/название): полное название + описание — того, что не помещается/не показано на строке.
- **Проект** (наведение на синий бэйдж проекта): название, прогресс (% и done/total эпиков), дедлайн, команды (бэйджи с цветами).

## Реализация

- Lazy-load по наведению (debounce 300мс, кэш, портал) — обёртка `HoverInfoCard<T>` (`frontend/src/components/HoverInfoCard.tsx`), тонкие обёртки `EpicTooltip`/`ProjectTooltip` (`frontend/src/components/board/`).
- Эпик: новый endpoint `GET /api/epics/{epicKey}/detail` → `EpicDetailDto{issueKey, summary, description}` (`EpicService.getEpicDetail`).
- Проект: существующий `GET /api/projects/{key}` (`ProjectDetailDto`); команды выводятся на фронте из `epics[].teamName/teamColor`.

## Changes

### Backend
- `EpicDetailDto` — новый DTO `{issueKey, summary, description}`
- `EpicService.getEpicDetail(epicKey)` — загружает эпик, проверяет тип через `WorkflowConfigService.isEpic()`, возвращает `EpicDetailDto`; бросает 404 если не найден или не является эпиком
- `EpicController` — новый endpoint `GET /api/epics/{epicKey}/detail`
- Тесты: `EpicServiceTest.GetEpicDetail` — успех / 404 / не-эпик

### Frontend
- `HoverInfoCard<T>` (`frontend/src/components/HoverInfoCard.tsx`) — универсальная lazy-load обёртка с debounce 300мс, in-memory кэшем и порталом
- `EpicTooltip` (`frontend/src/components/board/EpicTooltip.tsx`) — тонкая обёртка для тултипа эпика (ключ + summary + description)
- `ProjectTooltip` (`frontend/src/components/board/ProjectTooltip.tsx`) — тонкая обёртка для тултипа проекта (прогресс, дедлайн, команды через `TeamBadge`)
- `BoardPage.tsx` — подключены `EpicTooltip` на ячейку ключа/названия эпика и `ProjectTooltip` на бэйдж проекта

## API Endpoints
- `GET /api/epics/{epicKey}/detail` — возвращает `EpicDetailDto{issueKey, summary, description}`

## Design System
- Команды в тултипе проекта — только `TeamBadge`
- Проверка типа эпика — `WorkflowConfigService.isEpic()`

## Configuration
None

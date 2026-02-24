# F45 Гибридный таймлайн

**Статус:** ✅ Реализована (v0.45.0)

## Описание

Единый гибридный вид таймлайна, объединяющий ретроспективные (фактические) и прогнозные данные.

- **Слева от линии "сегодня"** — фактические даты из StatusChangelog (сплошные бары)
- **Справа от линии "сегодня"** — прогнозные даты из UnifiedPlanningService (полосатые бары)
- **Пересекающие "сегодня"** — гибридные бары (сплошная часть + полосатая)

## Что изменилось

### Убрано
- Переключатель режимов "Прогноз / Ретроспектива"
- Баннер ретроспективного режима
- Фильтрация завершённых стори (isDone) — теперь они отображаются

### Добавлено
- Загрузка 3 API параллельно: `getUnifiedPlanning()`, `getRetrospective()`, `getForecast()`
- Функция `mergeHybridEpics()` — мерж данных из forecast и retro
- Визуальное различие: сплошные бары (факт) vs полосатые бары (прогноз)
- Завершённые стори теперь видны на Gantt с их реальными датами
- Баги (BUG категория) поддерживаются в RetrospectiveTimelineService
- Легенда: "Факт" и "Прогноз" маркеры

### Backend
- `UnifiedPlanningService`: завершённые стори включены в `plannedStories` (placeholder с null-датами)
- `UnifiedPlanningService`: isEmpty стори теперь накапливают epicTotalEstimate
- `RetrospectiveTimelineService`: поддержка категории BUG (`findByBoardCategoryInAndTeamId`)
- `JiraIssueRepository`: новый метод `findByBoardCategoryInAndTeamId(List, Long)`

### Frontend
- `TimelinePage.tsx`: убран mode toggle, загрузка 3 API, `mergeHybridEpics()`, гибридные бары
- `TimelinePage.css`: стили `.phase-bar-forecast` (полосатый паттерн), легенда

## Логика мержа

Для каждого эпика и стори:
1. Если есть ретро-данные — используем фактические даты для завершённых фаз
2. Если фаза активна — гибрид: retro startDate + forecast endDate
3. Если нет ретро — чистый прогноз (полосатые бары)

Поле `_source` на каждой стори: `'retro'` | `'forecast'` | `'hybrid'`

## API

Используются существующие API (без новых эндпоинтов):
- `GET /api/planning/unified?teamId=N`
- `GET /api/planning/retrospective?teamId=N`
- `GET /api/planning/forecast?teamId=N`

## Тесты

- `UnifiedPlanningServiceTest.testDoneStoriesIncludedInPlannedStories` — проверка что завершённые стори появляются в результате планирования

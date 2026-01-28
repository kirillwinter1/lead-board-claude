# F18: Data Quality

## Цель
Система проверки качества данных для выявления проблем в эпиках, историях и подзадачах.

## Уровни серьёзности

| Уровень | Цвет | Влияние |
|---------|------|---------|
| ERROR | Красный | Блокирует планирование |
| WARNING | Жёлтый | Проблема, не блокирует |
| INFO | Серый | Рекомендация |

## Правила проверки (17 правил)

| Правило | Severity | Применяется к |
|---------|----------|---------------|
| `TIME_LOGGED_WRONG_EPIC_STATUS` | WARNING | Epic |
| `TIME_LOGGED_NOT_IN_SUBTASK` | ERROR | Epic/Story/Bug |
| `CHILD_IN_PROGRESS_EPIC_NOT` | ERROR | Story/Subtask |
| `SUBTASK_IN_PROGRESS_STORY_NOT` | ERROR | Subtask |
| `EPIC_NO_ESTIMATE` | WARNING | Epic |
| `SUBTASK_NO_ESTIMATE` | WARNING | Subtask |
| `SUBTASK_WORK_NO_ESTIMATE` | ERROR | Subtask |
| `SUBTASK_OVERRUN` | WARNING | Subtask |
| `EPIC_NO_TEAM` | ERROR | Epic |
| `EPIC_TEAM_NO_MEMBERS` | WARNING | Epic |
| `EPIC_NO_DUE_DATE` | INFO | Epic |
| `EPIC_OVERDUE` | ERROR | Epic |
| `EPIC_FORECAST_LATE` | WARNING | Epic |
| `EPIC_DONE_OPEN_CHILDREN` | ERROR | Epic |
| `STORY_DONE_OPEN_CHILDREN` | ERROR | Story |
| `EPIC_IN_PROGRESS_NO_STORIES` | WARNING | Epic |
| `STORY_IN_PROGRESS_NO_SUBTASKS` | WARNING | Story |

## Интеграция с планированием
- Эпики с ERROR не получают Expected Done
- Фильтрация по planning-allowed статусам

## API
```
GET /api/data-quality?teamId=1
```
Возвращает summary (counts by severity/rule) + violations по issue.

## UI
- Страница `/data-quality` с таблицей нарушений и фильтрами
- Alert badges в колонке ALERTS на Board
- Тултип с деталями нарушений (иконки severity)

## Файлы
- `quality/DataQualityService.java` (444 LOC)
- `quality/DataQualityRule.java`, `DataQualityViolation.java`, `DataQualitySeverity.java`
- `quality/DataQualityController.java` (200 LOC)

## Тесты
25+ unit-тестов: все правила для Epic, Story, Subtask + blocking errors.

# F18: Data Quality

## Цель
Система проверки качества данных для выявления проблем в эпиках, историях и подзадачах.

## Уровни серьёзности

| Уровень | Цвет | Влияние |
|---------|------|---------|
| ERROR | Красный | Блокирует планирование |
| WARNING | Жёлтый | Проблема, не блокирует |
| INFO | Серый | Рекомендация |

## Правила проверки (38 правил, с F83 — категории + 9 новых правил)

> Начиная с [F83](F83_DQ_CATEGORIES_AND_RULES.md) каждое правило имеет категорию (`DataQualityCategory`) и человекочитаемый `label`, которые отдаются API (`GET /api/data-quality`) и используются на фронтенде для фильтрации/группировки. Правила, добавленные в F83, отмечены **F83**.

| Правило | Категория | Severity | Применяется к |
|---------|-----------|----------|---------------|
| `TIME_LOGGED_WRONG_EPIC_STATUS` | Time Logging | WARNING | Epic |
| `TIME_LOGGED_NOT_IN_SUBTASK` | Time Logging | ERROR | Epic/Story/Bug |
| `SUBTASK_TIME_LOGGED_WHILE_EPIC_FLAGGED` | Time Logging | WARNING | Subtask |
| `CHILD_IN_PROGRESS_EPIC_NOT` | Status Consistency | ERROR | Story/Subtask |
| `SUBTASK_IN_PROGRESS_STORY_NOT` | Status Consistency | ERROR | Subtask |
| `SUBTASK_TIME_LOGGED_BUT_TODO` | Status Consistency | ERROR | Subtask |
| `SUBTASK_DONE_NO_TIME_LOGGED` | Status Consistency | WARNING | Subtask |
| `STORY_TODO_BUT_HAS_WORK` | Status Consistency | WARNING | Story/Bug |
| `STORY_FULLY_LOGGED_NOT_DONE` | Status Consistency | WARNING | Story/Bug |
| `EPIC_NO_ESTIMATE` | Estimates | WARNING | Epic |
| `SUBTASK_NO_ESTIMATE` | Estimates | WARNING | Subtask |
| `SUBTASK_WORK_NO_ESTIMATE` | Estimates | ERROR | Subtask |
| `SUBTASK_OVERRUN` | Estimates | WARNING | Subtask |
| `STORY_NO_SUBTASK_ESTIMATES` | Estimates | WARNING | Story/Bug |
| `SUBTASK_ESTIMATE_TOO_BIG` **F83** | Estimates | INFO | Subtask (оценка >40ч) |
| `EPIC_NO_TEAM` | Team | ERROR | Epic |
| `EPIC_TEAM_NO_MEMBERS` | Team | WARNING | Epic |
| `TEAM_FIELD_UNMAPPED` **F83** | Team | WARNING | Epic |
| `IN_PROGRESS_NO_ASSIGNEE` **F83** | Assignee | ERROR | Subtask |
| `ASSIGNEE_NOT_IN_TEAM` **F83** | Assignee | WARNING | Subtask |
| `EPIC_NO_DUE_DATE` | Due Dates | INFO | Epic |
| `EPIC_OVERDUE` | Due Dates | ERROR | Epic |
| `EPIC_FORECAST_LATE` | Due Dates | WARNING | Epic |
| `CHILD_DUE_AFTER_EPIC` **F83** | Due Dates | WARNING | Story/Bug |
| `EPIC_DONE_OPEN_CHILDREN` | Hierarchy | ERROR | Epic |
| `STORY_DONE_OPEN_CHILDREN` | Hierarchy | ERROR | Story/Bug |
| `EPIC_IN_PROGRESS_NO_STORIES` | Hierarchy | WARNING | Epic |
| `STORY_IN_PROGRESS_NO_SUBTASKS` | Hierarchy | WARNING | Story/Bug |
| `STORY_BLOCKED_BY_MISSING` | Dependencies | ERROR | Story/Bug |
| `STORY_CIRCULAR_DEPENDENCY` | Dependencies | ERROR | Story/Bug |
| `STORY_BLOCKED_NO_PROGRESS` | Dependencies | WARNING | Story/Bug |
| `BUG_STALE` | Staleness | WARNING | Bug |
| `IN_PROGRESS_TOO_LONG` **F83** | Staleness | WARNING | Epic/Story/Bug/Subtask |
| `EPIC_FLAGGED_TOO_LONG` **F83** | Staleness | WARNING | Epic |
| `RICE_MISSING_ASSESSMENT` | RICE | WARNING | Epic |
| `BUG_SLA_BREACH` | Bug SLA | ERROR | Bug |
| `BUG_NO_PRIORITY` **F83** | Bug SLA | WARNING | Bug |
| `EPIC_NO_DESCRIPTION` **F83** | Content | INFO | Epic |

Полное описание каждого нового правила и точные условия срабатывания — см. [F83](F83_DQ_CATEGORIES_AND_RULES.md).

## Интеграция с планированием
- Эпики с ERROR не получают Expected Done
- Фильтрация по planning-allowed статусам

## API
```
GET /api/data-quality?teamId=1
```
Возвращает `summary` (counts by severity/rule/category), `violations` по issue (каждое нарушение — `rule`/`label`/`category`/`categoryLabel`/`severity`/`message`) и `rules` — каталог всех 38 известных правил (для построения фильтров без сканирования нарушений; см. F83).

## UI
- Страница `/data-quality` с таблицей нарушений и фильтрами (Team, Category, Rule, Severity)
- Строка кликабельных чипов по категориям (счётчик нарушений на категорию)
- Alert badges в колонке ALERTS на Board
- Тултип с деталями нарушений (иконки severity + бейдж категории)

## Файлы
- `quality/DataQualityService.java` (775 LOC)
- `quality/DataQualityRule.java`, `DataQualityCategory.java` (F83), `DataQualityViolation.java`, `DataQualitySeverity.java`
- `quality/DataQualityController.java` (223 LOC)

## Тесты
63 unit-теста: все правила для Epic, Story, Bug, Subtask + blocking errors (см. `DataQualityServiceTest`).

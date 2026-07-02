# F83: Data Quality — категории правил + новые правила

**Version:** 0.83.0
**Date:** 2026-07-02

## Summary
Категории правил Data Quality стали "гражданами первого класса": новый enum `DataQualityCategory` (12 категорий с человекочитаемыми лейблами) и человекочитаемый `label` теперь хранятся в самом `DataQualityRule` на бэкенде, а не в отдельной map на фронтенде (заодно исправлен неверный лейбл `STORY_FULLY_LOGGED_NOT_DONE`). Добавлено 9 новых правил — итого **38 правил** (было 29). Также исправлен расчёт `STORY_BLOCKED_NO_PROGRESS`: окно "заблокирован без прогресса" теперь считается от `max(story.created, blocker.created)` по каждому блокеру, а не только от даты создания истории.

## Changes

### Backend
- Новый enum `com.leadboard.quality.DataQualityCategory` — 12 категорий: `TIME_LOGGING` (Time Logging), `STATUS_CONSISTENCY` (Status Consistency), `ESTIMATES` (Estimates), `TEAM` (Team), `ASSIGNEE` (Assignee), `DUE_DATES` (Due Dates), `HIERARCHY` (Hierarchy), `DEPENDENCIES` (Dependencies), `STALENESS` (Staleness), `RICE` (RICE), `BUG_SLA` (Bug SLA), `CONTENT` (Content).
- `DataQualityRule` расширен: каждое значение enum теперь несёт `category` + человекочитаемый `label` (лейблы перенесены с фронтенда на бэкенд; попутно исправлен неверный лейбл `STORY_FULLY_LOGGED_NOT_DONE` — раньше он ошибочно звучал как про "epic", хотя правило применяется к Story/Bug).
- 9 новых правил (см. таблицу ниже): `IN_PROGRESS_NO_ASSIGNEE`, `ASSIGNEE_NOT_IN_TEAM`, `IN_PROGRESS_TOO_LONG` (переиспользует `StatusAgeService` из F79, считается один раз на отчёт в `DataQualityController`), `EPIC_FLAGGED_TOO_LONG`, `TEAM_FIELD_UNMAPPED`, `CHILD_DUE_AFTER_EPIC`, `SUBTASK_ESTIMATE_TOO_BIG`, `BUG_NO_PRIORITY`, `EPIC_NO_DESCRIPTION`.
- `BUG_STALE` перенесён в категорию `STALENESS` (раньше был вместе с Bug SLA правилами).
- Fix: `STORY_BLOCKED_NO_PROGRESS` — окно 30 дней теперь считается от `max(story.created, blocker.created)` по каждому блокеру, а не от даты создания истории (старая история со свежим блокером больше не помечается мгновенно).
- Сознательно НЕ добавлено: `STORY_NO_EPIC` — истории без эпика являются легитимной работой по техдолгу.
- `DataQualityController`: `StatusAgeService.compute()` вызывается один раз на весь отчёт (батчево по всем issues), результат передаётся в `checkEpic`/`checkStory`/`checkBug`/`checkSubtask` как `StatusAge`, чтобы правило `IN_PROGRESS_TOO_LONG` не пересчитывало age по одному issue.
- DTO: `IssueViolations.ViolationDto` получил поля `label`, `category`, `categoryLabel`; `DataQualityResponse.Summary` получил `byCategory`; новая запись `DataQualityResponse.RuleInfo` (`name`/`label`/`category`/`categoryLabel`/`severity`) — каталог всех известных правил (не только сработавших), нужен фронтенду для построения фильтров.
- Никаких новых миграций БД — все новые правила используют существующие поля (`teamFieldValue`, `flagChangelog`, `dueDate`, `originalEstimateSeconds`, `priority`, `description`, `assigneeAccountId`) и существующий `StatusAgeService` (F79).

### Frontend
- `DataQualityPage`: локальная map `ruleLabels` удалена целиком — все лейблы правил и категорий приходят из API (`response.rules`, каталог всех правил).
- Новый фильтр `Category` (dropdown, `SingleSelectDropdown`) — сужает список правил в фильтре `Rule` до правил выбранной категории; сброс `ruleFilter`, если он не принадлежит выбранной категории.
- Строка кликабельных чипов "категория N" (`category-summary-row` / `category-chip`) под карточками Summary — построена из `summary.byCategory`, отсортирована по убыванию количества; клик по чипу — toggle фильтра категории.
- Чип категории в `FilterBar` при активном фильтре Category.
- В раскрытой строке нарушения (`ViolationRow`) добавлен бейдж категории (`violation-category`) рядом с `SeverityBadge` и текстом правила.
- Новые CSS-классы в `DataQualityPage.css`: `.category-summary-row`, `.category-chip`, `.category-chip-count`, `.category-chip.active`, `.violation-category`.

## API Endpoints
- `GET /api/data-quality?teamId={id}` — без изменений сигнатуры, но расширенный response:
  - `violations[].violations[]` (`ViolationDto`) теперь содержит `label`, `category`, `categoryLabel` в дополнение к `rule`/`severity`/`message`.
  - `summary.byCategory` — количество нарушений по категориям (аналогично существовавшим `byRule`/`bySeverity`).
  - `rules` — каталог всех 38 известных правил (`name`, `label`, `category`, `categoryLabel`, `severity`), не зависит от того, сработало правило в текущем отчёте или нет — фронтенд строит фильтры без сканирования (возможно пустого) списка нарушений.

## Rules Catalog (38 rules)

Правила, добавленные в F83, отмечены **NEW**.

| Правило | Категория | Severity | Применяется к |
|---|---|---|---|
| `TIME_LOGGED_WRONG_EPIC_STATUS` | Time Logging | WARNING | Epic |
| `TIME_LOGGED_NOT_IN_SUBTASK` | Time Logging | ERROR | Epic/Story/Bug |
| `SUBTASK_TIME_LOGGED_WHILE_EPIC_FLAGGED` | Time Logging | WARNING | Subtask |
| `CHILD_IN_PROGRESS_EPIC_NOT` | Status Consistency | ERROR | Story/Subtask |
| `SUBTASK_IN_PROGRESS_STORY_NOT` | Status Consistency | ERROR | Subtask |
| `SUBTASK_TIME_LOGGED_BUT_TODO` | Status Consistency | ERROR | Subtask |
| `SUBTASK_DONE_NO_TIME_LOGGED` | Status Consistency | WARNING | Subtask |
| `STORY_TODO_BUT_HAS_WORK` | Status Consistency | WARNING | Story/Bug |
| `STORY_FULLY_LOGGED_NOT_DONE` | Status Consistency | WARNING | Story/Bug (лейбл исправлен в F83) |
| `EPIC_NO_ESTIMATE` | Estimates | WARNING | Epic |
| `SUBTASK_NO_ESTIMATE` | Estimates | WARNING | Subtask |
| `SUBTASK_WORK_NO_ESTIMATE` | Estimates | ERROR | Subtask |
| `SUBTASK_OVERRUN` | Estimates | WARNING | Subtask |
| `STORY_NO_SUBTASK_ESTIMATES` | Estimates | WARNING | Story/Bug |
| `SUBTASK_ESTIMATE_TOO_BIG` **NEW** | Estimates | INFO | Subtask — оценка >40ч, подсказка разбить на подзадачи; исключены Done |
| `EPIC_NO_TEAM` | Team | ERROR | Epic |
| `EPIC_TEAM_NO_MEMBERS` | Team | WARNING | Epic |
| `TEAM_FIELD_UNMAPPED` **NEW** | Team | WARNING | Epic — значение поля команды из Jira не смаппено ни на одну команду; срабатывает вместе с `EPIC_NO_TEAM` как подсказка первопричины |
| `IN_PROGRESS_NO_ASSIGNEE` **NEW** | Assignee | ERROR | Subtask — в работе без исполнителя |
| `ASSIGNEE_NOT_IN_TEAM` **NEW** | Assignee | WARNING | Subtask — исполнитель не является активным участником команды эпика |
| `EPIC_NO_DUE_DATE` | Due Dates | INFO | Epic |
| `EPIC_OVERDUE` | Due Dates | ERROR | Epic |
| `EPIC_FORECAST_LATE` | Due Dates | WARNING | Epic |
| `CHILD_DUE_AFTER_EPIC` **NEW** | Due Dates | WARNING | Story/Bug — дедлайн истории позже дедлайна эпика |
| `EPIC_DONE_OPEN_CHILDREN` | Hierarchy | ERROR | Epic |
| `STORY_DONE_OPEN_CHILDREN` | Hierarchy | ERROR | Story/Bug |
| `EPIC_IN_PROGRESS_NO_STORIES` | Hierarchy | WARNING | Epic |
| `STORY_IN_PROGRESS_NO_SUBTASKS` | Hierarchy | WARNING | Story/Bug |
| `STORY_BLOCKED_BY_MISSING` | Dependencies | ERROR | Story/Bug |
| `STORY_CIRCULAR_DEPENDENCY` | Dependencies | ERROR | Story/Bug |
| `STORY_BLOCKED_NO_PROGRESS` | Dependencies | WARNING | Story/Bug — fix в F83: 30 дней считаются от `max(story.created, blocker.created)` по блокеру |
| `BUG_STALE` | Staleness | WARNING | Bug — перенесён в Staleness в F83 |
| `IN_PROGRESS_TOO_LONG` **NEW** | Staleness | WARNING | Epic/Story/Bug/Subtask — issue в текущем активном статусе дольше CRITICAL-порога `StatusAgeService` (F79) |
| `EPIC_FLAGGED_TOO_LONG` **NEW** | Staleness | WARNING | Epic — флаг (пауза) дольше 14 дней (`flag_changelog`, открытая запись); Done-эпики исключены |
| `RICE_MISSING_ASSESSMENT` | RICE | WARNING | Epic |
| `BUG_SLA_BREACH` | Bug SLA | ERROR | Bug |
| `BUG_NO_PRIORITY` **NEW** | Bug SLA | WARNING | Bug — без приоритета SLA не может быть применён |
| `EPIC_NO_DESCRIPTION` **NEW** | Content | INFO | Epic — эпик в статусе Planned+ без описания |

## Configuration
None — новые правила используют существующие поля Jira-issue и существующие сервисы (`StatusAgeService` из F79, `FlagChangelogRepository`, `TeamMemberRepository`). Миграций БД нет.

## Files
- `backend/src/main/java/com/leadboard/quality/DataQualityCategory.java` (новый файл, 31 LOC) — enum категорий
- `backend/src/main/java/com/leadboard/quality/DataQualityRule.java` (324 LOC) — 38 правил, каждое с `category` + `label`
- `backend/src/main/java/com/leadboard/quality/DataQualityService.java` (775 LOC) — логика всех правил
- `backend/src/main/java/com/leadboard/quality/DataQualityController.java` (223 LOC) — сборка отчёта, каталог правил (`buildRuleCatalog()`), батчевый `StatusAgeService.compute()`
- `backend/src/main/java/com/leadboard/quality/dto/DataQualityResponse.java`, `dto/IssueViolations.java` — расширенные DTO (`label`/`category`/`categoryLabel`, `byCategory`, `RuleInfo`)
- `backend/src/test/java/com/leadboard/quality/DataQualityServiceTest.java` — 63 теста (было ~25)
- `frontend/src/pages/DataQualityPage.tsx`, `DataQualityPage.css`, `DataQualityPage.test.tsx` — фильтр Category, чипы категорий, бейдж категории в раскрытой строке

## Tests
- 63 unit-теста в `DataQualityServiceTest` (JUnit5 + Mockito): каждое из 9 новых правил покрыто позитивным и негативным сценарием (включая Done-исключения для `EPIC_FLAGGED_TOO_LONG`/`SUBTASK_ESTIMATE_TOO_BIG`), плюс регрессионный тест на новый расчёт окна `STORY_BLOCKED_NO_PROGRESS` (блокер создан позже истории).
- Frontend: `DataQualityPage.test.tsx` обновлён под API-driven лейблы/категории (без локальной `ruleLabels` map) и под новый фильтр Category.

## Related
- Опирается на `StatusAgeService` из [F79](F79_STATUS_AGE.md) (переиспользует CRITICAL-порог для `IN_PROGRESS_TOO_LONG`).
- Дополняет [F18](F18_DATA_QUALITY.md) (Data Quality MVP) — см. обновлённую таблицу правил в F18.

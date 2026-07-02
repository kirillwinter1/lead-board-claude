# F84: Data Quality Auto-Fix

**Version:** 0.84.0
**Date:** 2026-07-02

## Summary
На странице `/data-quality` у 19 из 38 правил (см. [F18](F18_DATA_QUALITY.md)/[F83](F83_DQ_CATEGORIES_AND_RULES.md)) появилась кнопка **Fix** (доступна ролям ADMIN/PROJECT_MANAGER/TEAM_LEAD). Клик открывает модалку с точным превью изменений («LB-42: Backlog → Developing»), нужными полями ввода и — для рискованных массовых фиксов — красным предупреждением со списком затрагиваемых задач. После Apply изменение уходит в Jira (OAuth-токен пользователя, либо fallback на сервисный BasicAuth), затем затронутые issue пересинхронизируются поштучно и отчёт перезапрашивается.

Заодно алерты на Board (`AlertIcon`) перестали хардкодить человекочитаемые лейблы правил — теперь используют `label`, который бэкенд уже отдаёт с F83.

## Changes

### Backend
- Новый пакет `com.leadboard.quality.fix`:
  - `FixHandler` — интерфейс стратегии фикса одного правила (`preview()` — read-only превью, `apply()` — само изменение, `local()` — true для чисто локальных фиксов без записи в Jira).
  - `FixService` — реестр `DataQualityRule → FixHandler` (собирается автоматически из всех Spring-бинов `FixHandler`; дубликат хендлера для одного правила — `IllegalStateException` при старте). Перед `preview`/`apply` перепроверяет нарушение на свежих данных через `FixSupport.stillViolated()`; `apply()` на несуществующее уже нарушение бросает `FixConflictException` (409). После успешного нелокального фикса вызывает `SyncService.syncSingleIssue()` для каждого изменённого issue (сбои синка — не фейлят фикс, только логируются).
  - `FixSupport` — общая инфраструктура для хендлеров: `jiraWrite()`/`jira()` фасады, `targetStatusName()` (статус по `StatusCategory` через `WorkflowConfigService`, без хардкода), `openChildren()`/`openSubtasks()`/`resolveEpicOf()` (обход иерархии), `memberOptions()`/`isActiveMember()` (участники команды эпика), `teamsWithBlankJiraValue()`, `runRule()`/`stillViolated()` (точечный перезапуск `DataQualityService.checkEpic/Story/Bug/Subtask` на одном issue), парсинг параметров (`stringParam`/`doubleParam`/`dateParam`).
  - DTO: `FixPreview` (`fixType`, `title`, `applicable`/`notApplicableReason`, `risky`/`warning`, `authMode` OAUTH|BASIC|LOCAL, `changes`, `affectedIssues`, `inputs`, `choices`, с builder'ом), `FixChange` (`issueKey`/`summary`/`field`/`from`/`to`/`local`), `FixInput` (select/date/number, с фабриками), `FixChoice` (id/label/changes/inputs — взаимоисключающие варианты фикса), `FixRequest`/`FixResult`.
  - `FixConflictException` — на 409, если нарушение уже исчезло между preview и apply (гонка/параллельный фикс).
  - 18 хендлеров в `com.leadboard.quality.fix.handlers` — по одному на правило (см. таблицу ниже); `RICE_MISSING_ASSESSMENT` считается fixable, но бэкенд-хендлера не имеет — фронтенд открывает существующую `RiceForm` напрямую (fixType `RICE_FORM`, authMode `LOCAL`).
- `DataQualityController`:
  - `GET /api/data-quality` — `ViolationDto` теперь несёт `fixable` (из `FixService.isFixable(rule)`).
  - `GET /api/data-quality/fix-preview?issueKey&rule` и `POST /api/data-quality/fix` — новые эндпоинты, `@PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')")`.
  - Локальные `@ExceptionHandler`: `IllegalArgumentException`/`IllegalStateException` → 400, `FixConflictException` → 409, `JiraClientException` → 502.
- `JiraWriteService`: `hasUserCreds()` + fallback-варианты `transitionWithFallback()`, `assignWithFallback()`, `logWorkWithFallback()` — используют OAuth-токен текущего пользователя, если он есть, иначе сервисный BasicAuth из `JiraClient` (фиксы должны работать и для пользователей без подключённого Atlassian OAuth).
- `JiraClient`: новые методы `updateDueDate()`, `updatePriority()`, `addWorklogAt()` (worklog с явным `started`, в отличие от `addWorklog()` не форсирует 09:00 — нужно для переноса ворклогов с сохранением даты), `deleteWorklog()` (`?adjustEstimate=leave&notifyUsers=false`); все — OAuth-или-Basic fallback как остальные write-методы.
- `SyncService.syncSingleIssue(issueKey)` — точечная пересинхронизация одного issue после фикса (валидация ключа regex `^[A-Z][A-Z0-9]+-\d+$` против JQL-инъекции, `JQL: key = ISSUE-KEY`, upsert как в обычном sync). При апдейте `teamId` теперь учитывается `team_id_manual`: если Jira резолвит команду — она побеждает и флаг сбрасывается; если Jira ничего не резолвит и флаг стоит — ручная команда сохраняется; иначе (флага нет) — как раньше, `null`.
- `JiraIssueEntity` + миграция `db/tenant/T14__jira_issue_team_manual.sql` — новая колонка `team_id_manual BOOLEAN NOT NULL DEFAULT FALSE`: фикс `EPIC_NO_TEAM` назначает команду только локально (без записи в поле команды Jira), и без этого флага следующий sync тут же обнулял бы `teamId`.
- `board`: `AlertIcon.tsx` (frontend) перестал хардкодить карту лейблов правил — типы алертов (`types.ts`) получили поле с человекочитаемым лейблом, приходящим из того же каталога правил, что и Data Quality (F83).

### Frontend
- `frontend/src/api/dataQuality.ts` — новый модуль: типы `FixChange`/`FixInput`/`FixChoice`/`FixPreview`/`ApplyFixRequest`/`ApplyFixResult` + `getFixPreview()` (`GET /fix-preview`) и `applyFix()` (`POST /fix`).
- `frontend/src/components/quality/FixModal.tsx` (+ `.css`) — модалка фикса:
  - Для `RICE_MISSING_ASSESSMENT` рендерит существующую `RiceForm` напрямую (без запроса превью).
  - Иначе грузит `FixPreview`, рендерит: radio-группу `choices` (если есть), список `ChangeRow` (превью `field: from → to`, бейдж "local" для нелокальных-в-Jira изменений), инпуты (`select` через `SingleSelectDropdown`, `date`/`number` — нативные), красный `fix-warning-box` с `warning` + `affectedIssues` при `risky`, подсказку "Changes will be applied by the service account" при `authMode === 'BASIC'`.
  - `not applicable` (нарушение уже исправлено/условие не выполнено) — информационная панель + кнопка Close, которая тоже триггерит refetch отчёта.
  - `apply()`: 409 от сервера трактуется как "уже исправлено" (успешное закрытие с сообщением, не ошибка), остальные ошибки — красный `fix-error` с текстом от бэкенда.
- `frontend/src/pages/DataQualityPage.tsx`: `useAuth().hasRole(...)` проверяет `FIX_ROLES = ['ADMIN','PROJECT_MANAGER','TEAM_LEAD']` (дублирует backend `@PreAuthorize`, единственная точка правды — сам guard на бэкенде). Кнопка **Fix** в раскрытой строке нарушения — только если `violation.fixable && canFix`. Открытие `FixModal`, `onApplied` закрывает модалку и вызывает `fetchData()` (полный refetch отчёта — самый простой способ отразить каскадные изменения, например закрытие нескольких issue разом).
- `frontend/src/components/board/AlertIcon.tsx` — локальная хардкод-карта лейблов алертов удалена; `types.ts` расширен полем-лейблом, приходящим из бэкенд-каталога правил (тот же источник, что и Data Quality таблица).

## Fixes by Group

### Group A — one-click (5 правил, без ввода)
| Правило | Что делает Fix |
|---|---|
| `CHILD_IN_PROGRESS_EPIC_NOT` | Переводит **эпик** в In Progress |
| `STORY_TODO_BUT_HAS_WORK` | Переводит **историю** в In Progress |
| `SUBTASK_IN_PROGRESS_STORY_NOT` | Переводит родительскую **историю** в In Progress |
| `SUBTASK_TIME_LOGGED_BUT_TODO` | Переводит **подзадачу** в In Progress |
| `SUBTASK_DONE_NO_TIME_LOGGED` | Логирует ворклог = original estimate подзадачи (не применим, если оценки нет) |

### Group B — с вводом (10 правил, включая RICE)
| Правило | fixType | Ввод / выбор |
|---|---|---|
| `EPIC_NO_TEAM` | `TEAM_SELECT` | Выбор команды **только локально** (`authMode=LOCAL`); ставит `team_id_manual=true`, чтобы sync не затёр |
| `TEAM_FIELD_UNMAPPED` | `TEAM_SELECT` | Привязка значения поля команды Jira к существующей команде без Jira-маппинга (`TeamService.updateTeam` → `jiraTeamValue`, `linkIssuesToTeam`); только локально |
| `IN_PROGRESS_NO_ASSIGNEE` | `ASSIGNEE_SELECT` | Выбор исполнителя из активных участников команды эпика |
| `ASSIGNEE_NOT_IN_TEAM` | `CHOICE` | **reassign** (переназначить на активного участника) или **addToTeam** (добавить текущего исполнителя в команду локально); участник валидируется на активность |
| `SUBTASK_NO_ESTIMATE` / `SUBTASK_WORK_NO_ESTIMATE` | `ESTIMATE` | Число часов (шаг 0.5ч) → `JiraClient.updateEstimate` |
| `EPIC_NO_DUE_DATE` | `DUE_DATE` | Дата дедлайна эпика |
| `CHILD_DUE_AFTER_EPIC` | `CHOICE` | **moveStory** (подтянуть дедлайн истории к эпику) или **moveEpic** (отодвинуть дедлайн эпика к истории); дата редактируема, по умолчанию — дата второй стороны |
| `BUG_NO_PRIORITY` | `PRIORITY` | Приоритет из списка Jira (`JiraMetadataService.getPriorities()`, fallback на дефолтный список Highest…Lowest) |
| `RICE_MISSING_ASSESSMENT` | `RICE_FORM` | Открывает существующую `RiceForm` (без backend-хендлера, `authMode=LOCAL`) |

### Group C — рискованные (4 правила, красное предупреждение + список задач)
| Правило | Что делает Fix | Риск |
|---|---|---|
| `EPIC_DONE_OPEN_CHILDREN` | Закрывает **все** открытые истории эпика и их открытые подзадачи, снизу вверх (сначала подзадачи, потом истории), continue-on-error | Массовое закрытие; частичные сбои агрегируются в сообщение результата |
| `STORY_DONE_OPEN_CHILDREN` | Закрывает все открытые подзадачи истории, continue-on-error | Массовое закрытие |
| `STORY_FULLY_LOGGED_NOT_DONE` | Переводит историю сразу в Done | Может проскочить промежуточные статусы (review/testing) |
| `TIME_LOGGED_NOT_IN_SUBTASK` | Переносит ворклоги issue на выбранную подзадачу: сначала создаёт запись на подзадаче с исходным `started` (add-first), затем удаляет с исходного issue (delete-after) | Авторство ворклога переписывается на аккаунт, выполняющий фикс (реальный автор теряется); `add-before-delete` может оставить дубликат при сбое посередине, но никогда не теряет время; удаление чужого ворклога может требовать доп. прав Jira (403 всплывает как есть) |

Для рискованных фиксов `FixPreview.risky=true` + `warning` (текст) + `affectedIssues` (список затронутых ключей) — рендерятся в модалке красным блоком.

## API Endpoints
- `GET /api/data-quality?teamId={id}` — без изменений сигнатуры, но `violations[].violations[]` (`ViolationDto`) теперь несёт `fixable: boolean`.
- `GET /api/data-quality/fix-preview?issueKey={key}&rule={RULE_NAME}` — превью фикса (`FixPreview`): `fixType`, `title`, `applicable`/`notApplicableReason`, `risky`/`warning`, `authMode`, `changes`, `affectedIssues`, `inputs`, `choices`. Роли: ADMIN/PROJECT_MANAGER/TEAM_LEAD. 400 на неизвестное правило/issue.
- `POST /api/data-quality/fix` — тело `{issueKey, rule, choiceId?, params}` → `FixResult` `{success, message, updatedIssues}`. Роли: ADMIN/PROJECT_MANAGER/TEAM_LEAD. 400 (валидация/не найдено), 409 (нарушение уже исчезло), 502 (ошибка Jira API).

## Configuration
None — новая колонка `team_id_manual` добавлена миграцией `T14`, конфигурация фикса (доступные приоритеты, участники, команды) читается из существующих сервисов (`JiraMetadataService`, `TeamService`, `TeamMemberRepository`).

## Files
- `backend/src/main/java/com/leadboard/quality/fix/FixHandler.java`, `FixService.java`, `FixSupport.java`, `FixConflictException.java`
- `backend/src/main/java/com/leadboard/quality/fix/dto/FixPreview.java`, `FixChange.java`, `FixChoice.java`, `FixInput.java`, `FixRequest.java`, `FixResult.java`
- `backend/src/main/java/com/leadboard/quality/fix/handlers/*.java` — 18 хендлеров (`AbstractEstimateFixHandler` + 17 конкретных)
- `backend/src/main/java/com/leadboard/quality/DataQualityController.java` — эндпоинты `fix-preview`/`fix`, exception mapping
- `backend/src/main/java/com/leadboard/quality/dto/IssueViolations.java` — поле `fixable`
- `backend/src/main/java/com/leadboard/jira/JiraClient.java` — `updateDueDate`, `updatePriority`, `addWorklogAt`, `deleteWorklog`
- `backend/src/main/java/com/leadboard/jira/JiraWriteService.java` — `hasUserCreds`, `*WithFallback`
- `backend/src/main/java/com/leadboard/sync/SyncService.java` — `syncSingleIssue`, `team_id_manual`-aware team resolution
- `backend/src/main/java/com/leadboard/sync/JiraIssueEntity.java` — поле `teamIdManual`
- `backend/src/main/resources/db/tenant/T14__jira_issue_team_manual.sql`
- `backend/src/test/java/com/leadboard/quality/fix/FixHandlersTest.java` (14 тестов), `FixServiceTest.java` (9 тестов)
- `backend/src/test/java/com/leadboard/jira/JiraWriteServiceTest.java` (9 тестов, новый файл)
- `backend/src/test/java/com/leadboard/quality/DataQualityControllerTest.java` (1 тест, новый файл — `fixable` в отчёте)
- `backend/src/test/java/com/leadboard/sync/SyncServiceTest.java` (+5 тестов на `syncSingleIssue`/`team_id_manual`)
- `frontend/src/api/dataQuality.ts`
- `frontend/src/components/quality/FixModal.tsx`, `FixModal.css`, `FixModal.test.tsx`
- `frontend/src/pages/DataQualityPage.tsx`, `DataQualityPage.test.tsx` — кнопка Fix, role guard
- `frontend/src/components/board/AlertIcon.tsx`, `frontend/src/components/board/types.ts` — лейблы алертов из бэкенд-каталога вместо хардкода

## Tests
- Backend: 38 новых unit/slice-тестов (JUnit5 + Mockito) — `FixHandlersTest` (14, по каждому хендлеру: применимый/неприменимый сценарий, choices, risky-предупреждения), `FixServiceTest` (9: реестр, re-check на 409, post-fix sync, local-фиксы без sync), `JiraWriteServiceTest` (9: OAuth-vs-Basic fallback для transition/assign/worklog), `DataQualityControllerTest` (1: `fixable` в ответе `GET /api/data-quality`), `SyncServiceTest` (+5: `syncSingleIssue`, сохранение/сброс `team_id_manual`).
- Frontend: `FixModal.test.tsx` (новый, ~7 сценариев: превью, choices, risky, not-applicable, 409-как-успех, ошибка), `DataQualityPage.test.tsx` (расширен на кнопку Fix / role guard).
- `./gradlew test` пройден перед коммитом (правило versioning.md).

## Constraints & Known Limitations
- **Авторство ворклога переписывается.** И `SUBTASK_DONE_NO_TIME_LOGGED` (лог = estimate), и перенос ворклогов в `TIME_LOGGED_NOT_IN_SUBTASK` создают записи от имени аккаунта, выполняющего фикс (OAuth-пользователь или сервисный аккаунт), а не от исходного автора. Модалка явно предупреждает об этом в `risky`-блоке.
- **Group C — необратимые/массовые операции.** Закрытие открытых детей (`EPIC_DONE_OPEN_CHILDREN`/`STORY_DONE_OPEN_CHILDREN`) переводит issue сразу в Done, минуя промежуточные статусы; частичные сбои не откатывают уже применённые изменения — отчёт в `FixResult.message` перечисляет, что не получилось.
- **`TIME_LOGGED_NOT_IN_SUBTASK`** использует add-before-delete: если `deleteWorklog` упадёт после успешного `addWorklogAt`, время временно задвоится (не потеряется). Удаление чужого ворклога может требовать дополнительных прав в Jira — 403 всплывает как есть в результате.
- **`EPIC_NO_TEAM`/`TEAM_FIELD_UNMAPPED`** — фиксы только локальные (Lead Board), поле команды в самой Jira-задаче не меняется; для `EPIC_NO_TEAM` это держится флагом `team_id_manual`, который снимается сам, как только Jira когда-нибудь начнёт резолвить команду штатно.
- Кнопка Fix видна только для 19 из 38 правил — остальные 19 требуют либо ручного анализа (например `STORY_CIRCULAR_DEPENDENCY`, `STORY_BLOCKED_BY_MISSING`), либо не имеют однозначного авто-исправления.
- Роли, допущенные к фиксам (ADMIN/PROJECT_MANAGER/TEAM_LEAD), продублированы на фронтенде (`FIX_ROLES`) только для скрытия кнопки — единственная точка правды — `@PreAuthorize` на бэкенде.

## Related
- Расширяет [F18](F18_DATA_QUALITY.md) (Data Quality MVP) и [F83](F83_DQ_CATEGORIES_AND_RULES.md) (категории + 38 правил) — auto-fix доступен для подмножества из 38 правил.
- Опирается на существующие `TeamService`, `TeamMemberRepository`, `JiraMetadataService`, `RiceForm` (без изменений их контракта, кроме точечных вызовов из хендлеров).

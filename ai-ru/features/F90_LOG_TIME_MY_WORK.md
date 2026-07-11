# F90: Log time — списание времени со страницы My Work

**Версия:** 0.90.0
**Статус:** Реализовано
**Дата спеки:** 2026-07-08
**Базируется на:** F88 (My Work), ветка feat/f90-log-time поверх feat/f88-my-work

## Проблема

My Work (F88) показывает задачи и worklog-календарь, но списать время нельзя —
только ссылка в Jira. Участник видит недосписанный день, а закрыть его должен
в другой системе. В F88 это осознанно выносилось за скоуп; теперь делаем.

## Решение

Кнопка «Log time» на строках задач My Work → мини-форма → запись ворклога
в Jira **от имени текущего пользователя** → мгновенное обновление страницы.

## Ключевые решения (из брейншторма)

| Вопрос | Решение |
|--------|---------|
| UX | Кнопка на строке задачи → `Modal` с формой (часы, дата, комментарий) |
| Охват задач | In Progress + Up Next + таблица Completed в My Performance |
| Авторство в Jira | Персональный OAuth-токен пользователя (3LO), НЕ «последний токен тенанта» |
| Консистентность | Jira-first: сначала POST в Jira, локальный апсерт только после успеха |
| Remaining estimate | Не трогаем — Jira уменьшает remaining по своему дефолту |

## Инфраструктура (уже существует)

- `JiraWriteService.logWork(issueKey, timeSpentSeconds, date)` — пишет ворклог
  через `JiraClient.addWorklog(...)` OAuth-токеном (используется AI-чатом F52).
  **Недостаток:** `requireCreds()` → `OAuthService.getValidAccessToken()` →
  `resolveLatestToken()` — «последний токен тенанта», автор может быть чужим.
- `OAuthService.getValidAccessTokenForUser(String atlassianAccountId)` (стр. ~512) —
  per-user токен с refresh. Именно его нужно протянуть в write-путь.
- Скоупы OAuth уже включают `write:jira-work` и `offline_access`
  (`AtlassianOAuthProperties`), токены хранятся per-user (`OAuthTokenEntity`:
  access/refresh/cloudId).
- Локальное хранилище ворклогов — `IssueWorklogEntity` (`issue_worklogs`):
  issueKey, worklogId, authorAccountId, timeSpentSeconds, startedDate, roleCode.

## UI

Кнопка-иконка «Log time» в строках:
- **In Progress** и **Up Next** (основные списки);
- **Completed** в My Performance (кейс «закрыл вчера, забыл списать»).

Клик → `Modal` (существующий компонент):
- Заголовок: ключ + название задачи (read-only);
- **Hours** — number, шаг 0.5, обязательное, (0, 24];
- **Date** — date-input, default сегодня, будущее запрещено;
- **Comment** — текст, опционально;
- Submit → спиннер → успех: модалка закрывается, `getMyWork` рефетчится
  (календарь, Spent, DSR обновляются сразу);
- Ошибка — сообщение внутри модалки, введённые значения сохраняются;
- Нет валидного токена (протух refresh) → сообщение
  «Jira session expired — re-login via Atlassian».

UI-тексты — английские.

## Backend

### API
`POST /api/me/worklog` в `MyWorkController`. Тело:
```json
{ "issueKey": "LB-697", "date": "2026-07-08", "hours": 2.5, "comment": "..." }
```
Личность — только `authorizationService.getCurrentAuth().getAtlassianAccountId()`.

Валидации:
- задача существует локально и `boardCategory == 'SUBTASK'` → иначе 400;
- `assigneeAccountId == я` → иначе 403 (для Completed выполняется автоматически);
- `hours` ∈ (0, 24] → иначе 400;
- `date` не в будущем → иначе 400.

Ответы: 200 (успех), 400/403 (валидация), 401 (нет сессии),
409 «Jira session expired» (нет валидного per-user токена),
502 (ошибка Jira API; локально ничего не записано).

### Поток записи
1. `JiraWriteService.logWorkAs(accountId, issueKey, seconds, date, comment)` —
   новая per-user перегрузка: креды через
   `oauthService.getValidAccessTokenForUser(accountId)` + cloudId того же токена.
   Возвращает `worklogId` из ответа Jira.
2. Локальный апсерт `IssueWorklogEntity`: worklogId из Jira, authorAccountId = я,
   startedDate = date, timeSpentSeconds, roleCode = `workflowRole` задачи
   (fallback `WorkflowConfigService.getSubtaskRole(issueType)`).
3. Инкремент `timeSpentSeconds` на `JiraIssueEntity`.
4. Ошибка Jira → 502, локальных изменений нет (Jira-first).

### Миграции
Не требуются.

## Тесты

- **JUnit (сервис):** успех → Jira вызван per-user токеном, апсерт + инкремент;
  Jira-ошибка → исключение, локальных записей нет; чужая задача → отказ;
  не-сабтаск → отказ; roleCode fallback.
- **JUnit (контроллер):** 401 без сессии; 400 будущая дата / hours вне диапазона;
  403 чужая задача; 200 успех; 409 без токена.
- **Vitest:** кнопка открывает модалку с датой-сегодня; submit дёргает API и
  рефетчит getMyWork; ошибка остаётся в модалке; кнопка есть в трёх таблицах.

## Итерация 2 — управление остатком + месячный календарь

Доработка по живому фидбэку (2026-07-11):

### Модалка Log time как нативный Jira Log work
- Два поля рядом: **Time spent** (обязательное) и **Remaining** (предзаполнено
  текущим остатком). Формат ввода — Jira-строки `2w 4d 6h 45m` (1d=8h, 1w=5d=40h);
  парсинг/форматирование — `frontend/src/utils/duration.ts` и
  `com.leadboard.jira.JiraDuration` (бареное число без единиц невалидно).
- Прогресс-бар (`logged / (logged+remaining)`, live-пересчёт) + строка
  «Original estimate — …».
- **Авто-уменьшение остатка** (режим Jira `auto`): при вводе Time spent поле
  Remaining пересчитывается как `max(0, текущий_остаток − spent)`, пока пользователь
  не отредактирует его вручную (тогда значение уважается).
- Запись: worklog + `adjustEstimate=new&newEstimate=<остаток>` одним запросом.
  Контракт POST `/api/me/worklog` переведён на секунды:
  `timeSpentSeconds`, `remainingEstimateSeconds`. Локально обновляется
  `remaining_estimate_seconds`. `MyTask`/`CompletedTaskWithTeam` получили `remainingH`.

### Worklog-календарь — месячный, с навигацией
- Вместо «последних 4 недель» — календарный месяц (полные недели Mon–Sun) с шапкой
  `‹ Month YYYY ›`: **‹** без ограничений назад, **›** заблокирована на текущем месяце.
- Новый эндпоинт `GET /api/me/worklog-calendar?month=YYYY-MM` → `CalendarDay[]`.
  `/api/me/work` отдаёт календарь за текущий месяц. Общая логика вынесена в
  `buildWorklogCalendarForRange` / `buildWorklogCalendarForMonth`.
- Дни с логированием подсвечены зелёным; дни соседних месяцев — бледные; серый
  нативный `title` заменён на styled hover-тултип с разбивкой по задачам.

## Вне скоупа (бэклог)

- Редактирование/удаление ворклогов из UI.
- Клик по дню календаря как вход в форму (следующая итерация).
- Списание на произвольную задачу по ключу.
- См. TECH_DEBT: задача без оценки — Remaining уходит в 0 при списании.

## Связанные фичи

F88 (My Work — базовая страница), F57 (Worklog Sync), F52 (AI Chat — прецедент
JiraWriteService), F82 (Jira Access Membership).

# F23 Planning Poker — Rework (poker→Jira publishing, days, comparison, epic-key rooms)

> Крупный рефактор F23 после дизайн-ревью. Утверждённый макет всех экранов:
> https://claude.ai/code/artifact/c0ceee71-99d2-4b7c-b94a-c5c787d265b0
> Ветка: `fix/f23-poker-qa` (worktree `.claude/worktrees/f23-poker`). Версия бампится
> при финализации (координировать с F88 параллельного агента; вероятно 0.88.0/0.89.0).

## Решения (утверждены пользователем)

1. **Комната адресуется ключом эпика**, а не room-code. URL `/poker/:epicKey` для
   активной сессии. Room-code из UI убран (шаринг — «Copy link»). Внутренний
   `session.id` остаётся для истории (несколько завершённых сессий на эпик).
2. **Итоги — в человеко-днях** (1 d = 8 h, десятичные). Голосование остаётся в часах.
3. **Публикация в Jira — одной кнопкой в конце** («Publish to Jira»): для каждой
   стори финальная оценка роли пишется в **Original Estimate** сабтаска (SA/DEV/QA),
   недостающие сабтаски создаются.
4. **Новая стори создаётся в Jira сразу с сабтасками** (по выбранным ролям) — сразу
   получает ключ. Описание (description) собирается в форме. **Компонент —
   селектор в форме** (тянем компоненты проекта из Jira; обязательное поле).
5. **Доска (главная): rough→clean.** Как только у эпика есть стори с покерными
   (финальными) оценками — бейдж эпика показывает **чистую** оценку (сумма по стори,
   = сумма Original Estimate сабтасков). Частично оценённый эпик = сумма того, что
   оценено. Пока покера не было — **грязная** (rough). Бейджи визуально различаются
   (rough — приглушённый/контурный, clean — залитый).
6. **Итог сессии:** сравнение «rough → poker → Δ» по ролям + **ошибка планирования**
   = Σ|poker_role − rough_role| (и «−», и «+» — ошибка), в % от rough_total.
7. Иконки эпика/стори (`getIssueIcon` через WorkflowConfig) везде. Описание стори
   показывается в комнате. Весь UI — английский (данные Jira остаются как есть).

## Data model / миграции

- `poker_stories.description` (TEXT, nullable) — описание стори. Tenant-миграция.
- `poker_stories.jira_component` (varchar, nullable) — выбранный компонент (для create).
- `poker_sessions` — без изменений схемы (session.id уже есть).
- Финальные оценки уже в `poker_stories.final_estimates` (jsonb, role→hours).

## API-контракт (backend)

Базовый префикс `/api/poker`.

- `GET  /sessions/epic/{epicKey}` — активная (PREPARING/ACTIVE) сессия по ключу
  эпика + `epicSummary`, `epicDescription`. 404 если нет активной. (Заменяет
  `/sessions/room/{roomCode}` для роутинга; room-эндпоинт можно оставить для истории.)
- `GET  /projects/{projectKey}/components` — список компонентов Jira-проекта
  `[{id,name}]` (для селектора). Через JiraClient + JiraConfigResolver.
- `POST /sessions/{sessionId}/stories?createInJira=true` — тело
  `{title, description, needsRoles[], component}`. Создаёт Story в Jira с
  `description` и `components:[{name|id}]`, затем по одному сабтаску на роль
  (`getSubtaskTypeName(role)`), сохраняет story с ключом. Ошибку Jira отдаёт
  400/502 с понятным сообщением (не 500).
- `POST /sessions/{sessionId}/publish` — публикация: для каждой COMPLETED-стори с
  `final_estimates` создать недостающие сабтаски и записать Original Estimate
  (`timetracking.originalEstimate = "{hours}h"`) на сабтаск роли. Идемпотентно.
  Возвращает per-story статус (ok/ошибка + ключи сабтасков). Facilitator-only.
- `GET  /sessions/{sessionId}/summary` — итог: список стори с финальными оценками
  (в часах, конверсия в дни на фронте), + сравнение rough vs poker по ролям
  (rough из EpicService/RoughEstimate эпика; poker = сумма final по роли),
  Δ по ролям, ошибка = Σ|Δ|, ошибка% = Σ|Δ|/rough_total.
- StoryResponse / AddStoryRequest / SessionResponse — добавить `description`,
  `component`; SessionResponse уже с epicSummary/epicDescription.

### Board (главная)
- `BoardService`: для EPIC-узла считать **clean estimate** = сумма Original
  Estimate сабтасков дочерних стори (или их final estimates), если есть хоть одна
  оценённая стори; иначе — существующий rough. Пометить в BoardNode флаг
  `estimateSource: 'rough' | 'clean'` (или булев `cleanEstimate`), чтобы фронт
  отрисовал разные бейджи. Частичный эпик = сумма оценённого.

## Frontend (по макету)

Токены/стиль — существующая CSS-система (`PlanningPokerPage.css`), палитра
Atlassian, `StatusBadge`, role-цвета из WorkflowConfig, `getIssueIcon`.

1. **Роутинг:** `/poker/:epicKey` (было `/poker/room/:roomCode`). «Copy link»
   вместо room-code. «Join by key» (ввод ключа эпика). Лобби: колонку room-code
   убрать, действие «Open».
2. **Комната — состояния** (см. макет, блоки 3–7):
   - Preparing: шапка с иконкой эпика + название + описание; шаги; import/new.
   - Voting: карты (poker cards), **описание текущей стори** сверху, статус по
     ролям (кто проголосовал N/M), «Reveal cards».
   - Revealed: **role-columns** (голоса + avg/median/range) + consensus/spread
     индикатор; **Final estimate** предзаполнен медианой, поля вровень, цвет роли.
   - Completed: таблица стори в **днях** + сумма-строка; таблица **rough→poker→Δ**
     в днях + сумма-строка; метрики (Итог d, Ошибка %); внизу одна кнопка
     **«Publish to Jira»**.
3. **Add story modal:** поля Title + **Description** (textarea) + **Component**
   (селектор из `/projects/{key}/components`) + роли (цветные тогглы). Ширина
   поля = ширине блока ролей. Создаёт в Jira (createInJira=true).
4. **Иконки** эпика (фиолетовая молния) и стори (зелёная закладка) через
   `getIssueIcon(type, getIssueTypeIconUrl(type), category)`.
5. **Доска:** бейдж оценки эпика — clean vs rough по флагу из BoardNode;
   визуально различать (rough приглушённый/контурный, clean залитый). Тултип/label
   даёт понять, предварительная это оценка или уже после покера.

## Тесты
- Backend: publish (Original Estimate записывается, идемпотентность, недостающие
  сабтаски), create-with-component, summary (ошибка = Σ|Δ|, %), epic-key lookup,
  board clean-vs-rough. JUnit5, `./gradlew test` (Docker для Testcontainers).
- Frontend: обновить строки на английские; тесты компонентов комнаты/лобби;
  `npx vitest run`, `npm run build`.

## Порядок реализации
Backend-агент и frontend-агент работают параллельно (разные директории) по этому
контракту. Frontend временно мокает недостающие эндпоинты, если backend отстаёт.
Интеграция и живая проверка — после (в worktree, порты 8080/5173).

# BF23. Sprint Retrospective (Easy Retro)

## Обзор

Встроенный инструмент для проведения ретроспектив в реальном времени. Команда собирается, пишет карточки, голосует за важные, обсуждает топ и фиксирует action items — всё внутри Lead Board, с контекстом метрик команды.

**Проблема:** Команды используют отдельные инструменты для ретро (EasyRetro, Miro, FigJam) — разрыв контекста. Метрики в Lead Board, обсуждение в другом месте. Action items теряются, результаты прошлых ретро не отслеживаются.

**Решение:** Доска ретро с реалтайм WebSocket, голосованием, таймером, action items → Jira. Привязка к спринту (Scrum) или произвольному периоду (Kanban).

---

## Формат ретро

Классический формат с тремя колонками:

```
┌─────────────────────┬─────────────────────┬─────────────────────┐
│  ✅ Что хорошо       │  🔧 Что улучшить     │  🎯 Action Items     │
│                     │                     │                     │
│  [карточка]         │  [карточка]         │  [карточка]         │
│  [карточка]         │  [карточка]         │  [карточка]         │
│  [карточка]         │  [карточка]         │                     │
│                     │                     │                     │
│  [+ Добавить]       │  [+ Добавить]       │  [+ Добавить]       │
└─────────────────────┴─────────────────────┴─────────────────────┘
```

---

## Фазы ретро

Ретро проходит последовательно через 4 фазы. Ведущий управляет переходом между фазами.

### 1. Сбор карточек (Write)

- Участники добавляют карточки в колонки "Что хорошо" и "Что улучшить"
- Карточки не видны другим участникам (если анонимный режим)
- Таймер: настраиваемый, default 5 минут
- Ведущий видит количество карточек от каждого участника (без содержания)

### 2. Reveal & Группировка (Group)

- Ведущий нажимает "Reveal" — все карточки становятся видны
- Участники (или ведущий) группируют похожие карточки drag & drop
- Группе даётся заголовок-тема
- Таймер: default 3 минуты

### 3. Голосование (Vote)

- Каждый участник получает 3 голоса
- Можно распределить как угодно: 3 на одну карточку или по 1 на разные
- Голосование скрытое, результаты показываются после завершения
- Таймер: default 2 минуты
- После голосования карточки/группы сортируются по количеству голосов

### 4. Обсуждение & Action Items (Discuss)

- Обсуждаем карточки сверху вниз (по голосам)
- Ведущий помечает обсуждённые карточки
- Из обсуждения создаются Action Items (третья колонка)
- Action Items можно сразу отправить в Jira
- Таймер: default 15 минут (на всё обсуждение)

---

## Роли

### Ведущий (Facilitator)

- Создаёт ретро, настраивает параметры
- Управляет фазами (переключает, может вернуть назад)
- Управляет таймером (старт, пауза, добавить время)
- Группирует карточки
- Создаёт action items
- Закрывает ретро

### Участник (Participant)

- Пишет карточки в фазе Write
- Голосует в фазе Vote
- Участвует в обсуждении
- Может предлагать action items (ведущий подтверждает)

---

## Анонимность

Настраивается ведущим при создании ретро:

| Режим | Фаза Write | Фаза Group | Фаза Discuss |
|-------|-----------|------------|-------------|
| **Анонимно** | Автор скрыт | Автор скрыт | Автор скрыт |
| **Открыто** | Автор виден | Автор виден | Автор виден |

Default: анонимно.

---

## Таймер

Синхронизированный таймер через WebSocket — все видят одинаковый обратный отсчёт.

### Настройки по умолчанию

| Фаза | Default | Min | Max |
|------|---------|-----|-----|
| Write | 5 мин | 1 мин | 15 мин |
| Group | 3 мин | 1 мин | 10 мин |
| Vote | 2 мин | 1 мин | 5 мин |
| Discuss | 15 мин | 5 мин | 60 мин |

### Управление

- Ведущий может: запустить, пауза, +1 мин, сбросить
- По истечении таймера — звуковой сигнал + визуальное уведомление
- Таймер рекомендательный: фаза не переключается автоматически

---

## Action Items → Jira

При создании action item ведущий заполняет:

```
┌─ Создать Action Item ──────────────────────────────┐
│                                                      │
│  Описание: [Настроить алерты на cycle time > 5d   ] │
│                                                      │
│  Ответственный: [Алексей Петров ▼]                  │
│                                                      │
│  Дедлайн: [2026-02-28]                              │
│                                                      │
│  [Создать в Jira]  [Только записать]                │
│                                                      │
└──────────────────────────────────────────────────────┘
```

**"Создать в Jira":**
- Создаёт Task в Jira через API: `POST /rest/api/3/issue`
- Issue type: Task
- Summary: описание action item
- Assignee: выбранный ответственный
- Due date: дедлайн
- Label: `retro-action`
- Ссылка на ретро в description

**"Только записать":**
- Сохраняет внутри Lead Board, не создаёт в Jira
- Показывается на следующем ретро в секции "Проверка action items"

---

## Проверка Action Items с прошлого ретро

При создании нового ретро — автоматически подгружаются незакрытые action items с предыдущего:

```
┌─ Action Items с прошлого ретро ────────────────────────┐
│                                                         │
│  ✅ PROJ-234  Настроить CI pipeline    Алексей   Done  │
│  ⬜ PROJ-235  Обновить документацию    Мария     To Do │
│  ✅ PROJ-236  Добавить unit тесты      Дмитрий   Done  │
│                                                         │
│  Выполнено: 2/3 (67%)                                  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

Для action items в Jira — статус подтягивается автоматически при sync. Для внутренних — ведущий отмечает вручную.

---

## Контекст метрик

В шапке ретро (или sidebar) показываются метрики за период/спринт — для контекста обсуждения:

```
┌─ Метрики Sprint 24 ────────────────────────────────────┐
│                                                         │
│  Velocity: 98h (↑11%)   Carry-over: 3 (15%)           │
│  Cycle Time: 3.2d       DSR: 0.95                      │
│  Закрыто: 16/20 (80%)   Scope change: 15%             │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

Для Kanban-команд — метрики за выбранный период (from/to).
Для Scrum-команд — метрики привязанного спринта.

Метрики не генерируются AI, а берутся из существующих сервисов (TeamMetricsService, SprintMetricsService).

---

## Привязка ретро

### Scrum-команда (methodology = SCRUM)

При создании ретро — выбор спринта:

```
Спринт: [Sprint 24 (closed) ▼]
```

Метрики подгружаются из SprintMetricsService. Ретро привязано к sprint_id.

### Kanban-команда (methodology = KANBAN)

При создании ретро — выбор периода:

```
Период: [2026-01-27] — [2026-02-10]
```

Метрики за выбранный период из TeamMetricsService.

---

## Реалтайм (WebSocket)

Архитектура аналогична Planning Poker (F23):

### Подключение

```
WS /ws/retro/{retroId}?userId={accountId}
```

### Сообщения Server → Client

```json
// Состояние ретро (при подключении и при изменении)
{
  "type": "RETRO_STATE",
  "phase": "WRITE",                    // WRITE | GROUP | VOTE | DISCUSS | CLOSED
  "timer": { "remaining": 245, "running": true },
  "participants": [...],
  "cards": [...],                       // скрытые в фазе WRITE (для неведущих)
  "votes": {...},                       // скрытые в фазе VOTE
  "actionItems": [...]
}

// Новая карточка (видна только в фазах GROUP, VOTE, DISCUSS)
{
  "type": "CARD_ADDED",
  "card": { "id": "c1", "column": "good", "text": "...", "author": null, "votes": 0 }
}

// Голос
{
  "type": "VOTE_CAST",
  "votesRemaining": 2                  // сколько голосов осталось у участника
}

// Смена фазы
{
  "type": "PHASE_CHANGED",
  "phase": "VOTE",
  "timer": { "remaining": 120, "running": false }
}

// Таймер
{
  "type": "TIMER_TICK",
  "remaining": 244
}

{
  "type": "TIMER_EXPIRED",
  "phase": "WRITE"
}

// Action item создан
{
  "type": "ACTION_ITEM_CREATED",
  "actionItem": { "id": "a1", "text": "...", "assignee": "...", "jiraKey": "PROJ-234" }
}
```

### Сообщения Client → Server

```json
// Добавить карточку
{ "type": "ADD_CARD", "column": "good", "text": "Хорошая коммуникация в команде" }

// Удалить свою карточку (только в фазе WRITE)
{ "type": "DELETE_CARD", "cardId": "c1" }

// Голосовать (только в фазе VOTE)
{ "type": "VOTE", "cardId": "c3" }

// Убрать голос
{ "type": "UNVOTE", "cardId": "c3" }

// Ведущий: сменить фазу
{ "type": "CHANGE_PHASE", "phase": "VOTE" }

// Ведущий: управление таймером
{ "type": "TIMER_START" }
{ "type": "TIMER_PAUSE" }
{ "type": "TIMER_ADD", "seconds": 60 }
{ "type": "TIMER_RESET" }

// Ведущий: группировка карточек
{ "type": "GROUP_CARDS", "cardIds": ["c1", "c2"], "groupTitle": "Коммуникация" }

// Ведущий: создать action item
{ "type": "CREATE_ACTION", "text": "...", "assigneeAccountId": "...", "deadline": "2026-02-28", "createInJira": true }

// Ведущий: отметить карточку обсуждённой
{ "type": "MARK_DISCUSSED", "cardId": "c3" }
```

---

## Схема БД

### Таблица `retrospectives`

```sql
CREATE TABLE retrospectives (
    id              BIGSERIAL PRIMARY KEY,
    team_id         BIGINT NOT NULL REFERENCES teams(id),
    sprint_id       BIGINT REFERENCES sprints(id),       -- NULL для Kanban
    period_from     DATE,                                  -- NULL для Scrum
    period_to       DATE,                                  -- NULL для Scrum
    title           VARCHAR(255) NOT NULL,
    phase           VARCHAR(20) NOT NULL DEFAULT 'WRITE',  -- WRITE, GROUP, VOTE, DISCUSS, CLOSED
    anonymous       BOOLEAN NOT NULL DEFAULT TRUE,
    timer_write     INT NOT NULL DEFAULT 300,              -- секунды
    timer_group     INT NOT NULL DEFAULT 180,
    timer_vote      INT NOT NULL DEFAULT 120,
    timer_discuss   INT NOT NULL DEFAULT 900,
    facilitator_id  VARCHAR(255) NOT NULL,                 -- jira account id
    created_at      TIMESTAMP DEFAULT NOW(),
    closed_at       TIMESTAMP,

    UNIQUE(team_id, sprint_id)                             -- один ретро на спринт
);

CREATE INDEX idx_retrospectives_team ON retrospectives(team_id);
```

### Таблица `retro_cards`

```sql
CREATE TABLE retro_cards (
    id              BIGSERIAL PRIMARY KEY,
    retro_id        BIGINT NOT NULL REFERENCES retrospectives(id) ON DELETE CASCADE,
    column_type     VARCHAR(20) NOT NULL,                  -- GOOD, IMPROVE
    text            TEXT NOT NULL,
    author_id       VARCHAR(255) NOT NULL,                 -- jira account id
    group_id        BIGINT REFERENCES retro_card_groups(id),
    discussed       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_retro_cards_retro ON retro_cards(retro_id);
```

### Таблица `retro_card_groups`

```sql
CREATE TABLE retro_card_groups (
    id              BIGSERIAL PRIMARY KEY,
    retro_id        BIGINT NOT NULL REFERENCES retrospectives(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP DEFAULT NOW()
);
```

### Таблица `retro_votes`

```sql
CREATE TABLE retro_votes (
    id              BIGSERIAL PRIMARY KEY,
    retro_id        BIGINT NOT NULL REFERENCES retrospectives(id) ON DELETE CASCADE,
    card_id         BIGINT NOT NULL REFERENCES retro_cards(id) ON DELETE CASCADE,
    voter_id        VARCHAR(255) NOT NULL,                 -- jira account id

    UNIQUE(retro_id, card_id, voter_id)                    -- один голос за карточку от участника
);

CREATE INDEX idx_retro_votes_card ON retro_votes(card_id);
```

### Таблица `retro_action_items`

```sql
CREATE TABLE retro_action_items (
    id                  BIGSERIAL PRIMARY KEY,
    retro_id            BIGINT NOT NULL REFERENCES retrospectives(id) ON DELETE CASCADE,
    text                TEXT NOT NULL,
    assignee_account_id VARCHAR(255),
    assignee_name       VARCHAR(255),
    deadline            DATE,
    jira_issue_key      VARCHAR(50),                       -- PROJ-234 если создано в Jira
    status              VARCHAR(20) DEFAULT 'OPEN',        -- OPEN, DONE
    created_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_retro_actions_retro ON retro_action_items(retro_id);
```

---

## API

### REST (CRUD, создание ретро)

```
POST   /api/teams/{teamId}/retros                      — создать ретро
GET    /api/teams/{teamId}/retros                       — список ретро команды
GET    /api/teams/{teamId}/retros/{retroId}             — детали ретро (карточки, голоса, action items)
DELETE /api/teams/{teamId}/retros/{retroId}              — удалить ретро
GET    /api/teams/{teamId}/retros/{retroId}/actions      — action items ретро
GET    /api/teams/{teamId}/retros/pending-actions        — незакрытые action items с прошлых ретро
POST   /api/teams/{teamId}/retros/{retroId}/actions/{id}/resolve  — отметить action item выполненным
```

### WebSocket (реалтайм)

```
WS /ws/retro/{retroId}
```

Вся логика фаз, карточек, голосования, таймера — через WebSocket (описана выше).

### Создание action item в Jira

```
POST /api/teams/{teamId}/retros/{retroId}/actions/{id}/create-jira
```

Вызывает Jira API `POST /rest/api/3/issue` с:
```json
{
  "fields": {
    "project": { "key": "PROJ" },
    "issuetype": { "name": "Task" },
    "summary": "Retro: Настроить алерты на cycle time",
    "description": "Action item из ретроспективы Sprint 24\n\nSource: Lead Board Retro #12",
    "assignee": { "accountId": "..." },
    "duedate": "2026-02-28",
    "labels": ["retro-action"]
  }
}
```

---

## UI

### 1. Создание ретро

```
┌─ Создать ретроспективу ──────────────────────────────┐
│                                                        │
│  Название: [Ретро Sprint 24                         ] │
│                                                        │
│  Привязка:                                             │
│  [● Спринт]  [○ Период]                              │
│  Спринт: [Sprint 24 (closed) ▼]                       │
│                                                        │
│  Анонимность:                                          │
│  [● Анонимно]  [○ Открыто]                            │
│                                                        │
│  Таймеры (минуты):                                     │
│  Сбор [5]  Группировка [3]  Голосование [2]  Обсуждение [15] │
│                                                        │
│  [Отмена]  [Создать и начать]                          │
│                                                        │
└────────────────────────────────────────────────────────┘
```

### 2. Список ретро команды

Доступ: Teams → Team → Retro (таб или кнопка)

```
┌─ Ретроспективы — Platform Team ─────────────────────────────────┐
│                                                                   │
│  [+ Новое ретро]                                                 │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ 🟢 Ретро Sprint 24       14 фев 2026     Sprint 24      │    │
│  │    12 карточек  4 action items  3 выполнено              │    │
│  │    [Открыть]                                              │    │
│  ├──────────────────────────────────────────────────────────┤    │
│  │ 🟢 Ретро Sprint 23       31 янв 2026     Sprint 23      │    │
│  │    18 карточек  5 action items  5 выполнено              │    │
│  │    [Открыть]                                              │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

### 3. Доска ретро — фаза Write

```
┌─ Ретро Sprint 24 ──────────────────────────────── ⏱ 3:42 ──────┐
│                                                                   │
│  Фаза: СБОР КАРТОЧЕК          Участников: 5     [⏸ Пауза] [+1м] │
│                                                                   │
│  ┌─ Метрики Sprint 24 ─────────────────────────────────────────┐ │
│  │ Velocity: 98h (↑11%)  Carry-over: 15%  Cycle: 3.2d  DSR: 0.95│
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌─ ✅ Что хорошо ──────┐  ┌─ 🔧 Что улучшить ──┐               │
│  │                       │  │                      │               │
│  │  ┌─────────────────┐ │  │  ┌────────────────┐  │               │
│  │  │ Моя карточка 1  │ │  │  │ Моя карточка 2 │  │               │
│  │  └─────────────────┘ │  │  └────────────────┘  │               │
│  │  ┌─────────────────┐ │  │                      │               │
│  │  │ Моя карточка 3  │ │  │                      │               │
│  │  └─────────────────┘ │  │                      │               │
│  │                       │  │                      │               │
│  │  [+ Добавить]        │  │  [+ Добавить]        │               │
│  │                       │  │                      │               │
│  │  Всего: 8 карточек   │  │  Всего: 6 карточек   │               │
│  └───────────────────────┘  └──────────────────────┘               │
│                                                                   │
│  [Ведущий: Перейти к группировке →]                               │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

В анонимном режиме участник видит только свои карточки + счётчик общего количества.

### 4. Доска ретро — фаза Vote

```
┌─ Ретро Sprint 24 ──────────────────────────────── ⏱ 1:15 ──────┐
│                                                                   │
│  Фаза: ГОЛОСОВАНИЕ        Голосов осталось: 1     [⏸] [+1м]     │
│                                                                   │
│  ┌─ ✅ Что хорошо ──────────────┐  ┌─ 🔧 Что улучшить ──────────┐│
│  │                               │  │                             ││
│  │  ┌─────────────────────────┐ │  │  ┌───────────────────────┐  ││
│  │  │ 🗳2  Хорошая коммуника- │ │  │  │ 🗳   Долгий code     │  ││
│  │  │      ция в команде      │ │  │  │      review           │  ││
│  │  │      [Голосовать]       │ │  │  │      [Голосовать]     │  ││
│  │  └─────────────────────────┘ │  │  └───────────────────────┘  ││
│  │                               │  │                             ││
│  │  ┌─ Группа: CI/CD ────────┐ │  │  ┌───────────────────────┐  ││
│  │  │ 🗳3  Быстрый деплой    │ │  │  │ 🗳1  Нет документации │  ││
│  │  │      Стабильные тесты  │ │  │  │      [✓ Мой голос]    │  ││
│  │  │      [Голосовать]       │ │  │  └───────────────────────┘  ││
│  │  └─────────────────────────┘ │  │                             ││
│  └───────────────────────────────┘  └─────────────────────────────┘│
│                                                                   │
│  [Ведущий: Завершить голосование →]                               │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

### 5. Доска ретро — фаза Discuss

```
┌─ Ретро Sprint 24 ──────────────────────────────── ⏱ 8:30 ──────┐
│                                                                   │
│  Фаза: ОБСУЖДЕНИЕ (по голосам)                    [⏸] [+1м]     │
│                                                                   │
│  ┌─ Обсуждаем (отсортировано по голосам) ────────────────────┐   │
│  │                                                            │   │
│  │  ✅ 🗳5  Группа: CI/CD                                    │   │
│  │         Быстрый деплой, Стабильные тесты                  │   │
│  │         [Обсуждено ✓]                                      │   │
│  │                                                            │   │
│  │  → 🗳3  Долгий code review                                │   │
│  │         [Обсуждается...]                                   │   │
│  │                                                            │   │
│  │  ○ 🗳2  Хорошая коммуникация в команде                    │   │
│  │  ○ 🗳2  Нет документации                                  │   │
│  │  ○ 🗳1  ...                                                │   │
│  └────────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌─ 🎯 Action Items ────────────────────────────────────────┐    │
│  │                                                            │    │
│  │  PROJ-234  Настроить алерты на cycle time  Алексей  28фев │    │
│  │  PROJ-235  Обновить документацию API       Мария    28фев │    │
│  │                                                            │    │
│  │  [+ Создать Action Item]                                   │    │
│  └────────────────────────────────────────────────────────────┘   │
│                                                                   │
│  [Ведущий: Завершить ретро →]                                     │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

### 6. Закрытое ретро (read-only)

После завершения ретро — доступен для просмотра:
- Все карточки с голосами
- Action items со статусами (обновляются из Jira)
- Метрики периода/спринта
- Статистика: участников, карточек, action items выполнено/всего

---

## Backend — архитектура

### Новые сервисы

```
RetroService               — CRUD ретро, управление фазами
RetroWebSocketHandler      — WebSocket handler (аналог PokerWebSocketHandler)
RetroActionService         — Action items: создание, Jira интеграция, статус
```

### Расширение существующих

```
JiraClient                 — createIssue() для action items
```

### WebSocket

Переиспользуем паттерн из Planning Poker:
- `RetroWebSocketHandler` extends `TextWebSocketHandler`
- `RetroSessionManager` — управление сессиями и участниками
- `RetroTimerService` — таймеры с ScheduledExecutorService
- Broadcast state при каждом изменении

---

## Frontend — компоненты

### Новые

```
pages/
├── RetroListPage.tsx           — список ретро команды
└── RetroRoomPage.tsx           — доска ретро (основной экран)

components/retro/
├── RetroBoard.tsx              — три колонки с карточками
├── RetroCard.tsx               — карточка (текст, голоса, автор)
├── RetroCardGroup.tsx          — группа карточек с заголовком
├── RetroTimer.tsx              — таймер с управлением
├── RetroVoteIndicator.tsx      — индикатор оставшихся голосов
├── RetroActionItemForm.tsx     — форма создания action item
├── RetroMetricsBar.tsx         — метрики спринта/периода
└── RetroPreviousActions.tsx    — action items с прошлого ретро

hooks/
└── useRetroWebSocket.ts        — WebSocket hook (аналог usePokerWebSocket)
```

### Роутинг

```
/board/teams/:teamId/retros                — список ретро
/board/teams/:teamId/retros/:retroId       — доска ретро
```

---

## Навигация

Доступ к ретро:

1. **Teams → Team → кнопка "Retro"** — список ретро команды
2. **Sprint Metrics → кнопка "Провести ретро"** — создать ретро привязанное к спринту
3. **Прямая ссылка** — `/board/teams/1/retros/5` — для приглашения участников

---

## Связь с другими фичами

| Фича | Связь |
|------|-------|
| **F23** Planning Poker | Переиспользуем WebSocket паттерн, session management |
| **BF12** Sprint Integration | Привязка к спринту, sprint metrics в контексте |
| **BF7** Notifications | "Ретро начинается через 15 минут" |
| **F22/F24** Team Metrics | Метрики отображаются как контекст |
| **F27** RBAC | Facilitator = ADMIN или TEAM_LEAD |

---

## Миграционный план

### Phase 1: Базовая доска
1. Миграция: таблицы retrospectives, retro_cards, retro_votes, retro_card_groups, retro_action_items
2. RetroService: CRUD, фазы
3. RetroWebSocketHandler: реалтайм карточки, фазы
4. RetroRoomPage: доска с тремя колонками, добавление карточек

### Phase 2: Голосование и группировка
5. Голосование: 3 голоса, скрытое, сортировка по результату
6. Группировка: drag & drop, заголовок группы
7. RetroTimer: синхронизированный таймер

### Phase 3: Action Items
8. Создание action items из UI
9. Интеграция с Jira (createIssue)
10. Проверка прошлых action items при создании нового ретро
11. Автообновление статуса из Jira при sync

### Phase 4: Polish
12. RetroListPage: список ретро команды
13. Метрики спринта/периода в контексте
14. Закрытое ретро: read-only view
15. Роутинг, навигация, ссылки

---

## Acceptance Criteria

1. Ведущий может создать ретро (привязка к спринту или периоду, настройка анонимности и таймеров)
2. Участники добавляют карточки в реальном времени (WebSocket)
3. Ведущий управляет фазами: Write → Group → Vote → Discuss → Closed
4. Голосование: 3 голоса на участника, скрытое, сортировка по результату
5. Таймер синхронизирован для всех участников, ведущий управляет
6. Action items создаются в Jira как Task с label `retro-action`
7. На следующем ретро видны незакрытые action items с прошлого
8. Метрики спринта/периода отображаются как контекст
9. Закрытое ретро доступно для просмотра (read-only)

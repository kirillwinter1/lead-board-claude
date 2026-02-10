# BF7. Notifications (Уведомления)

## Обзор

In-app уведомления о событиях в системе. Персонализированные по роли и команде. Bell icon с dropdown, toast для real-time через WebSocket, настройки по типам.

В будущем: интеграция с мессенджерами (Telegram, etc.).

---

## Типы уведомлений

### Статусы и движение

| Тип | Описание | Кто получает |
|-----|----------|-------------|
| `EPIC_STATUS_CHANGED` | Эпик сменил статус | Team Lead команды, PM проекта |
| `PROJECT_STATUS_CHANGED` | Проект сменил статус | PM проекта, Admin |
| `EPIC_DONE` | Эпик завершён | Team Lead, PM проекта |
| `PROJECT_DONE` | Все эпики проекта завершены | PM проекта, Admin |

### Forecast и сроки

| Тип | Описание | Кто получает |
|-----|----------|-------------|
| `FORECAST_SHIFTED` | Expected Done эпика сдвинулся на ≥ N дней | Team Lead команды, PM проекта |
| `DUE_DATE_APPROACHING` | До due date осталось ≤ N рабочих дней | Team Lead, PM проекта |
| `DUE_DATE_EXCEEDED` | Due date пропущен | Team Lead, PM проекта, Admin |

### Data Quality и RICE

| Тип | Описание | Кто получает |
|-----|----------|-------------|
| `DATA_QUALITY_ALERTS` | Новые алерты Data Quality | Team Lead команды |
| `RICE_MISSING` | Проект/эпик в Planning+ без RICE | PM проекта, PO |

### WIP и Capacity

| Тип | Описание | Кто получает |
|-----|----------|-------------|
| `WIP_EXCEEDED` | WIP лимит превышен | Team Lead команды |

### Cross-team (BF5)

| Тип | Описание | Кто получает |
|-----|----------|-------------|
| `ALIGNMENT_DELAY` | Эпик отстаёт от проекта на ≥ N дней | Team Lead команды, PM проекта |
| `ALIGNMENT_RECOMMENDATION` | Системная рекомендация по проекту | PM проекта |

### Дайджесты (BF6)

| Тип | Описание | Кто получает |
|-----|----------|-------------|
| `DIGEST_READY` | Дайджест сгенерирован | Team Lead, PM, все участники команды |
| `DIGEST_FAILED` | Ошибка генерации дайджеста | Admin |

### Poker (F23)

| Тип | Описание | Кто получает |
|-----|----------|-------------|
| `POKER_SESSION_CREATED` | Новая сессия Planning Poker | Участники команды |
| `POKER_VOTING_COMPLETE` | Голосование завершено | Участники сессии |

### Система

| Тип | Описание | Кто получает |
|-----|----------|-------------|
| `SYNC_ERROR` | Ошибка синхронизации с Jira | Admin |
| `SYNC_COMPLETED` | Синхронизация завершена (с изменениями) | Admin |

---

## Персонализация

Уведомления привязаны к роли и контексту пользователя:

```
Admin           → всё
PROJECT_MANAGER → свои проекты + все эпики проектов
PRODUCT_OWNER   → проекты/эпики с RICE
TEAM_LEAD       → своя команда
MEMBER          → своя команда (ограниченный набор)
VIEWER          → только дайджесты
```

### Правила маршрутизации

```java
// Пример: эпик сменил статус
recipients = new HashSet<>();
recipients.add(getTeamLead(epic.teamId));           // Team Lead команды
if (epic.projectId != null) {
    recipients.add(getProjectPM(epic.projectId));   // PM проекта
}
// Фильтруем по настройкам пользователя
recipients = filterByUserPreferences(recipients, "EPIC_STATUS_CHANGED");
```

---

## Настройки пользователя

Каждый пользователь может включить/выключить типы уведомлений:

```
┌─ Настройки уведомлений ───────────────────────────────────┐
│                                                            │
│  Статусы и движение                                       │
│    ☑ Смена статуса эпика                                  │
│    ☑ Смена статуса проекта                                │
│    ☑ Эпик завершён                                        │
│                                                            │
│  Forecast и сроки                                         │
│    ☑ Сдвиг forecast (порог: [3] дней)                     │
│    ☑ Приближение due date (за [5] дней)                   │
│    ☑ Пропущен due date                                    │
│                                                            │
│  Data Quality                                             │
│    ☑ Новые алерты                                         │
│    ☐ RICE не заполнен                                     │
│                                                            │
│  Дайджесты                                                │
│    ☑ Дайджест готов                                       │
│                                                            │
│  Planning Poker                                           │
│    ☑ Новая сессия                                         │
│    ☐ Голосование завершено                                │
│                                                            │
│  Система                                                  │
│    ☐ Синхронизация завершена                              │
│    ☑ Ошибка синхронизации                                 │
│                                                            │
│                                         [Сохранить]       │
└────────────────────────────────────────────────────────────┘
```

Некоторые настройки имеют **пороги** (threshold):
- Сдвиг forecast: минимум N дней для срабатывания
- Приближение due date: за N дней

---

## UI

### Bell Icon (хедер)

```
┌─ Header ──────────────────────────────── 🔔 3 ── 👤 ─┐
```

- Красный badge с числом непрочитанных
- Badge скрывается если непрочитанных = 0
- Клик открывает dropdown panel

### Dropdown Panel

```
┌─────────────────────────────────────────┐
│  Уведомления                    ✓ Всё  │
├─────────────────────────────────────────┤
│ 🔴 LB-30 → Developing                  │
│    Team Beta · 2 мин назад             │
│                                         │
│ 🔴 Дайджест Team Alpha за 3-9 фев      │
│    готов                                │
│    15 мин назад                         │
│                                         │
│ 🔴 Forecast LB-20 сдвинулся +5 дней    │
│    Team Alpha · 1 час назад            │
│                                         │
│ ○  Due date LB-100 через 3 дня         │
│    вчера                               │
│                                         │
│ ○  Data Quality: 3 новых алерта        │
│    Team Alpha · вчера                  │
│                                         │
│             Все уведомления →           │
└─────────────────────────────────────────┘
```

- 🔴 = непрочитанное, ○ = прочитанное
- "✓ Всё" = отметить все прочитанными
- Клик на уведомление → переход к контексту (эпик на Board, дайджест, и т.д.)
- "Все уведомления →" → полная страница
- Показывать последние 10-15 в dropdown

### Toast (real-time через WebSocket)

```
                        ┌──────────────────────────────┐
                        │ 🔔 LB-30 → Developing        │
                        │ Team Beta                     │
                        │                          ✕   │
                        └──────────────────────────────┘
```

- Появляется в правом верхнем углу
- Автоисчезает через 5 секунд
- Можно закрыть вручную (✕)
- Клик → переход к контексту
- Не показывать если пользователь на той же странице (контекстуально)

### Notifications Page (`/notifications`)

Полная история с фильтрами:

```
┌─ Уведомления ──────────────────────────────────────────┐
│  [Все ▼]  [Тип ▼]  [Непрочитанные ☐]  [🔍 Поиск]     │
├────────────────────────────────────────────────────────┤
│  Сегодня                                               │
│  🔴 LB-30 → Developing           Team Beta   14:23    │
│  🔴 Дайджест Team Alpha готов                 12:00    │
│  🔴 Forecast LB-20 +5 дней       Team Alpha  11:15    │
│                                                        │
│  Вчера                                                 │
│  ○  Due date LB-100 через 3 дня              18:00    │
│  ○  Data Quality: 3 алерта        Team Alpha  10:30    │
│  ○  LB-10 → Готово                Team Alpha  09:15    │
│                                                        │
│  7 февраля                                             │
│  ○  Planning Poker: сессия #12    Team Beta   16:00    │
│  ...                                                   │
│                                                        │
│                        [Загрузить ещё]                  │
└────────────────────────────────────────────────────────┘
```

- Группировка по дате
- Фильтр по типу, непрочитанным
- Пагинация (infinite scroll или "загрузить ещё")
- Настройки (⚙) → переход к настройкам уведомлений

---

## Архитектура

### Backend

```
com.leadboard.notification/
├── NotificationEntity          — JPA entity
├── NotificationPreferenceEntity — Настройки пользователя
├── NotificationRepository      — JPA repository
├── NotificationService         — Создание, маршрутизация, фильтрация
├── NotificationDispatcher      — Определение получателей по типу и контексту
├── NotificationWebSocketHandler — Real-time доставка через WebSocket
├── NotificationController      — REST API
└── NotificationConfig          — Конфигурация (пороги по умолчанию)
```

### Генерация уведомлений (источники)

```java
// В SyncService — после обнаружения смены статуса
notificationService.notify(NotificationType.EPIC_STATUS_CHANGED, epicKey, metadata);

// В DigestScheduler — после генерации дайджеста
notificationService.notify(NotificationType.DIGEST_READY, digestId, metadata);

// В UnifiedPlanningService — при сдвиге forecast
notificationService.notify(NotificationType.FORECAST_SHIFTED, epicKey, Map.of("deltaDays", 5));

// В DataQualityService — при новых алертах
notificationService.notify(NotificationType.DATA_QUALITY_ALERTS, teamId, metadata);
```

### WebSocket

Расширение существующей WebSocket инфраструктуры (из Planning Poker):

```
ws://host/ws/notifications?userId={userId}
```

Сервер отправляет JSON при новом уведомлении:
```json
{
  "type": "NOTIFICATION",
  "data": {
    "id": 123,
    "notificationType": "EPIC_STATUS_CHANGED",
    "title": "LB-30 → Developing",
    "message": "Team Beta",
    "link": "/board?highlight=LB-30",
    "createdAt": "2026-02-10T14:23:00Z"
  }
}
```

### DB Schema

```sql
-- Миграция: V__create_notifications.sql

CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    type                VARCHAR(50) NOT NULL,       -- EPIC_STATUS_CHANGED, FORECAST_SHIFTED, etc.
    title               VARCHAR(500) NOT NULL,      -- "LB-30 → Developing"
    message             TEXT,                       -- детали
    link                VARCHAR(500),               -- куда перейти при клике
    context_type        VARCHAR(50),                -- EPIC, PROJECT, TEAM, DIGEST
    context_key         VARCHAR(100),               -- LB-30, team-1, digest-5
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_preferences (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    notification_type   VARCHAR(50) NOT NULL,       -- тип уведомления
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    threshold_value     INTEGER,                    -- порог (дни для forecast/due date)
    updated_at          TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, notification_type)
);

CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_user_date ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notif_prefs_user ON notification_preferences(user_id);
```

### API Endpoints

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| GET | `/api/notifications` | Список уведомлений (пагинация, фильтры) | Свои |
| GET | `/api/notifications/unread-count` | Количество непрочитанных | Свои |
| PUT | `/api/notifications/{id}/read` | Отметить прочитанным | Свои |
| PUT | `/api/notifications/read-all` | Отметить все прочитанными | Свои |
| GET | `/api/notifications/preferences` | Настройки уведомлений | Свои |
| PUT | `/api/notifications/preferences` | Обновить настройки | Свои |

### Frontend

```
frontend/src/
├── components/
│   └── notifications/
│       ├── NotificationBell.tsx       — Bell icon с badge в хедере
│       ├── NotificationDropdown.tsx   — Dropdown panel
│       ├── NotificationItem.tsx       — Одно уведомление
│       ├── NotificationToast.tsx      — Toast всплывашка
│       └── NotificationPreferences.tsx — Настройки
├── pages/
│   └── NotificationsPage.tsx          — Полная история
├── api/
│   └── notificationApi.ts            — API клиент
└── hooks/
    └── useNotificationWebSocket.ts    — WebSocket для real-time
```

Роутинг:
- `/notifications` — полная история
- Настройки встраиваются в профиль пользователя или отдельная страница

---

## Будущие расширения

| Расширение | Когда |
|------------|-------|
| Telegram интеграция | После MVP нотификаций |
| Email дайджест уведомлений | После Telegram |
| Slack интеграция | По запросу |
| @mention в комментариях | После комментариев |
| Группировка уведомлений | Если станет шумно |

---

## План реализации (поэтапно)

### Этап 1: Инфраструктура + базовые уведомления
1. DB миграции (notifications, notification_preferences)
2. NotificationEntity, NotificationPreferenceEntity
3. NotificationService — создание, маршрутизация
4. NotificationDispatcher — определение получателей
5. NotificationController — API
6. Интеграция: SyncService → epic status changed
7. Frontend: NotificationBell + NotificationDropdown
8. Тесты

### Этап 2: WebSocket + Toast
1. NotificationWebSocketHandler
2. Frontend: useNotificationWebSocket hook
3. NotificationToast компонент
4. Real-time доставка
5. Тесты

### Этап 3: Все типы уведомлений
1. Forecast shifted (UnifiedPlanningService)
2. Due date approaching/exceeded (Scheduler)
3. Data Quality alerts (DataQualityService)
4. WIP exceeded
5. Digest ready/failed (DigestService)
6. Poker session (PokerSessionService)
7. Sync error/completed (SyncService)
8. Тесты

### Этап 4: Настройки + страница истории
1. NotificationPreferences UI
2. Фильтрация по настройкам
3. NotificationsPage (полная история)
4. Пороги (forecast shift days, due date days)
5. Тесты

### Этап 5: Интеграция с BF4/BF5
1. RICE missing (BF4)
2. Alignment delay/recommendation (BF5)
3. Project status changed (BF5)
4. Тесты

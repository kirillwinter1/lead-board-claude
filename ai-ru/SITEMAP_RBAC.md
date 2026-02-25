# Карта сайта — Роли и доступ

> Актуально на 2026-02-25. Субдомен определяет tenant (`test.onelane.ru`). Лендинг на `onelane.ru`.

## Роли

| Роль | Описание | Permissions |
|------|----------|-------------|
| **ADMIN** | Полный доступ | teams:manage, priorities:edit, board:view, poker:participate, sync:trigger, admin:access |
| **PROJECT_MANAGER** | Управление проектами и RICE | projects:manage, board:view, poker:participate |
| **TEAM_LEAD** | Управление своей командой | teams:manage:own, priorities:edit, board:view, poker:participate |
| **MEMBER** | Просмотр + участие в покере | board:view, poker:participate |
| **VIEWER** | Только просмотр | board:view |

---

## Табы навигации

| Таб | Путь | ADMIN | PM | TL | MEMBER | VIEWER |
|-----|------|:-----:|:--:|:--:|:------:|:------:|
| Board | `/` | + | + | + | + | + |
| Timeline | `/timeline` | + | + | + | + | + |
| Metrics | `/metrics` | + | + | + | + | + |
| Data Quality | `/data-quality` | + | + | + | + | + |
| Bugs | `/bug-metrics` | + | + | + | + | + |
| Poker | `/poker` | + | + | + | + | **—** |
| Teams | `/teams` | + | + | + | + | + |
| Projects | `/projects` | + | + | + | + | + |
| Project Timeline | `/project-timeline` | + | + | + | + | + |
| Settings | `/settings` | + | **—** | **—** | **—** | **—** |

> Poker скрыт для VIEWER (нет `poker:participate`). Settings скрыт для всех кроме ADMIN.
> Workflow (`/workflow`) доступен только через Settings, защищён ProtectedRoute.

---

## Страницы — Подробный доступ

### `/` — Board (Kanban-доска)

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр доски | `GET /api/board` | + | + | + | + | + | authenticated | — |
| Drag-n-drop epic порядок | `PUT /api/epics/{key}/order` | + | — | + | — | — | ADMIN, TL | **НЕТ** |
| Drag-n-drop story порядок | `PUT /api/stories/{key}/order` | + | — | + | — | — | ADMIN, TL | **НЕТ** |
| Rough estimate (inline edit) | `PUT /api/epics/{key}/rough-estimate` | + | + | + | — | — | ? | **НЕТ** |
| Sync trigger | `POST /api/sync/trigger` | + | — | — | — | — | ADMIN | **НЕТ** |

### `/timeline` — Gantt Timeline

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр таймлайна | `GET /api/planning/*` | + | + | + | + | + | authenticated | — |
| WIP Snapshot | `POST /api/metrics/wip-history/snapshot` | + | + | + | + | — | ? | **НЕТ** |

### `/metrics` — Team Metrics

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр метрик | `GET /api/metrics/*` | + | + | + | + | + | authenticated | — |
| WIP Snapshot | `POST /api/metrics/wip-history/snapshot` | + | + | + | + | — | ? | **НЕТ** |

### `/data-quality` — Data Quality

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр правил | `GET /api/data-quality` | + | + | + | + | + | authenticated | — |

> Только чтение.

### `/bug-metrics` — Bug Metrics

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр метрик | `GET /api/metrics/bugs` | + | + | + | + | + | authenticated | — |

> Только чтение.

### `/poker` — Planning Poker Lobby

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр сессий | `GET /api/poker/sessions` | + | + | + | + | — | authenticated | Таб скрыт |
| Создать сессию | `POST /api/poker/sessions` | + | + | + | + | — | authenticated | **НЕТ** |
| Войти по коду | навигация | + | + | + | + | — | — | Таб скрыт |

### `/poker/room/:code` — Poker Room

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Голосовать | WebSocket | + | + | + | + | — | WS auth | canVote (role) |
| Reveal votes | WebSocket | facilitator | facilitator | facilitator | facilitator | — | WS auth | isFacilitator |
| Set final estimate | WebSocket | facilitator | facilitator | facilitator | facilitator | — | WS auth | isFacilitator |
| Add story | `POST /api/poker/sessions/{id}/stories` | facilitator | facilitator | facilitator | facilitator | — | authenticated | isFacilitator |
| Import stories | `POST /api/poker/sessions/{id}/stories` | facilitator | facilitator | facilitator | facilitator | — | authenticated | isFacilitator |
| Start session | WebSocket | facilitator | facilitator | facilitator | facilitator | — | WS auth | isFacilitator |

### `/teams` — Teams List

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр команд | `GET /api/teams` | + | + | + | + | + | authenticated | — |
| Создать команду | `POST /api/teams` | + | — | — | — | — | ADMIN | canManageTeams |
| Редактировать команду | `PUT /api/teams/{id}` | + | — | + (своя) | — | — | ADMIN / canManageTeam | canManageTeams |
| Деактивировать команду | `DELETE /api/teams/{id}` | + | — | — | — | — | ADMIN | canManageTeams |
| Сменить цвет | `PUT /api/teams/{id}` | + | — | + (своя) | — | — | ADMIN / canManageTeam | **НЕТ** |

### `/teams/:id` — Team Members

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр участников | `GET /api/teams/{id}/members` | + | + | + | + | + | authenticated | — |
| Добавить участника | `POST /api/teams/{id}/members` | + | — | + (своя) | — | — | canManageTeam | **НЕТ** |
| Редактировать участника | `PUT /api/teams/{id}/members/{mid}` | + | — | + (своя) | — | — | canManageTeam | **НЕТ** |
| Деактивировать участника | `POST /api/teams/{id}/members/{mid}/deactivate` | + | — | + (своя) | — | — | canManageTeam | **НЕТ** |
| Сохранить planning config | `PUT /api/teams/{id}/planning-config` | + | — | + (своя) | — | — | canManageTeam | **НЕТ** |
| Добавить отсутствие | `POST /api/teams/{id}/members/{mid}/absences` | + | — | + (своя) | — | — | canManageTeam | **НЕТ** |
| Редактировать отсутствие | `PUT /api/teams/{id}/members/{mid}/absences/{aid}` | + | — | + (своя) | — | — | canManageTeam | **НЕТ** |
| Удалить отсутствие | `DELETE /api/teams/{id}/members/{mid}/absences/{aid}` | + | — | + (своя) | — | — | canManageTeam | **НЕТ** |

### `/teams/:id/member/:mid` — Member Profile

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр профиля | `GET /api/teams/{id}/members/{mid}/profile` | + | + | + | + | + | authenticated | — |
| Оценка компетенций | `POST /api/competency/members/{mid}` | + | — | + | — | — | ? | **НЕТ** |

### `/teams/:id/competency` — Competency Matrix

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр матрицы | `GET /api/competency/teams/{id}/matrix` | + | + | + | + | + | authenticated | — |
| Оценка компетенций | `POST /api/competency/members/{mid}` | + | — | + | — | — | ? | **НЕТ** |

### `/projects` — Projects

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр проектов | `GET /api/projects` | + | + | + | + | + | authenticated | — |
| RICE оценка | `POST /api/rice/assessments` | + | + | + | — | — | ADMIN/PM/TL | **НЕТ** |
| Recommendations | `GET /api/projects/{key}/recommendations` | + | + | + | — | — | ADMIN/PM/TL | **НЕТ** |

### `/project-timeline` — Project Timeline

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр Gantt | `GET /api/projects/timeline` | + | + | + | + | + | authenticated | — |

> Только чтение.

### `/settings` — Settings (Admin)

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Просмотр пользователей | `GET /api/admin/users` | + | — | — | — | — | ADMIN | ProtectedRoute |
| Изменить роль | `PATCH /api/admin/users/{id}/role` | + | — | — | — | — | ADMIN | ProtectedRoute |
| Count changelogs | `GET /api/sync/import-changelogs/count` | + | — | — | — | — | ADMIN | ProtectedRoute |
| Import changelogs | `POST /api/sync/import-changelogs` | + | — | — | — | — | ADMIN | ProtectedRoute |
| Bug SLA CRUD | `POST/PUT/DELETE /api/bug-sla/*` | + | + | — | — | — | ADMIN/PM | ProtectedRoute* |

> *Bug SLA Settings встроена в Settings, но backend допускает и PM.

### `/workflow` — Workflow Configuration (Admin)

| Действие | API | ADMIN | PM | TL | MEMBER | VIEWER | Защита backend | Защита frontend |
|----------|-----|:-----:|:--:|:--:|:------:|:------:|:--------------:|:---------------:|
| Все CRUD operations | `/api/admin/workflow-config/*` | + | — | — | — | — | ? (нет @PreAuthorize!) | ProtectedRoute |

---

## Проблемы — что нужно исправить

### Frontend: кнопки видны всем

Кнопки мутирующих действий на этих страницах **видны всем авторизованным**, хотя backend вернёт 403:

| Страница | Что скрыть | Для кого скрыть |
|----------|-----------|----------------|
| Board | Drag-n-drop (order) | MEMBER, VIEWER, PM |
| Board | Rough estimate edit | MEMBER, VIEWER |
| Board | Sync button | Все кроме ADMIN |
| Teams | Color picker (inline) | MEMBER, VIEWER, PM |
| Team Members | + Add Member, Edit, Deactivate | MEMBER, VIEWER, PM |
| Team Members | Planning Config section | MEMBER, VIEWER, PM |
| Team Members | Absences CRUD | MEMBER, VIEWER, PM |
| Member Profile | Competency rating (edit) | MEMBER, VIEWER |
| Competency Matrix | Rating (edit) | MEMBER, VIEWER |
| Projects | RICE form | MEMBER, VIEWER |
| Timeline | WIP Snapshot button | VIEWER |
| Metrics | WIP Snapshot button | VIEWER |

### Backend: отсутствует @PreAuthorize

| Controller | Endpoints | Нужно |
|-----------|-----------|-------|
| WorkflowConfigController | `/api/admin/workflow-config/**` | `@PreAuthorize("hasRole('ADMIN')")` |
| CompetencyController | `POST /api/competency/members/*` | `@PreAuthorize("hasAnyRole('ADMIN','TEAM_LEAD')")` |
| AutoScoreController | `/api/autoscore/**` | Проверить и добавить |
| ForecastController | `/api/forecast/**` | Мутации — ADMIN/TL |
| EpicController | `PUT /api/epics/*/rough-estimate` | `@PreAuthorize("hasAnyRole('ADMIN','PM','TEAM_LEAD')")` |
| CalendarController | mutations | ADMIN |
| TeamMetricsController | `POST /api/metrics/wip-history/snapshot` | Не VIEWER |

---

## Сводная матрица видимости

```
Страница              ADMIN  PM     TL     MEMBER  VIEWER
─────────────────────────────────────────────────────────
Board (view)          +      +      +      +       +  (read-only)
Board (edit)          +      ~      +      —       —
Timeline              +      +      +      +       +  (read-only)
Metrics               +      +      +      +       +  (read-only)
Data Quality          +      +      +      +       +  (read-only)
Bug Metrics           +      +      +      +       +  (read-only)
Poker                 +      +      +      +       —
Teams (view)          +      +      +      +       +  (read-only)
Teams (manage)        +      —      —      —       —
Team Members (view)   +      +      +      +       +  (read-only)
Team Members (manage) +      —      +own   —       —
Member Profile        +      +      +      +       +  (read-only)
Competency (edit)     +      —      +      —       —
Projects (view)       +      +      +      +       +  (read-only)
Projects (RICE)       +      +      +      —       —
Project Timeline      +      +      +      +       +  (read-only)
Settings              +      —      —      —       —
Workflow Config       +      —      —      —       —
```

> `+` = полный доступ, `~` = частичный, `—` = нет доступа, `+own` = только своя команда

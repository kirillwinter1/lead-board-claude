# Список фич Lead Board

## Порядок реализации

Фичи упорядочены по зависимостям. Каждая следующая фича может зависеть от предыдущих.

### Статус реализации

| Фича | Статус | Дата |
|------|--------|------|
| F1. Bootstrap проекта | ✅ Готово | 2026-01-22 |
| F2. Jira Integration MVP | ✅ Готово | 2026-01-22 |
| F3. Jira Sync & Cache | ✅ Готово | 2026-01-23 |
| F4. OAuth 2.0 + RBAC | ✅ Готово | 2026-01-23 |
| F5. Team Management Backend | ✅ Готово | 2026-01-23 |
| F6. Team Management UI | ✅ Готово | 2026-01-23 |
| F7. Team Sync from Atlassian | ✅ Готово | 2026-01-23 |
| F8. Board v2 (Epic как root) | ✅ Готово | 2026-01-23 |
| F9. Sub-task Estimates | ✅ Готово | 2026-01-23 |
| F10. Epic-Team Mapping | ✅ Готово | 2026-01-23 |
| F11. Rough Estimates для Epics | ✅ Готово | 2026-01-23 |
| F13. Автопланирование (MVP) | ✅ Готово | 2026-01-24 |
| F14. Timeline/Gantt | ✅ Готово | 2026-01-24 |

---

## Технические исправления

### 2026-01-23: Jira API cursor-based pagination

**Проблема:** Jira REST API `/rest/api/3/search/jql` изменил формат пагинации с offset-based (`startAt`, `total`) на cursor-based (`nextPageToken`, `isLast`).

**Симптомы:**
- Синхронизация загружала только часть задач
- Ошибка "200 OK from GET..." в статусе синхронизации
- `DataBufferLimitException` при больших ответах (>256KB)

**Исправления:**
1. `JiraSearchResponse` — добавлены поля `nextPageToken`, `isLast`
2. `JiraClient` — поддержка `nextPageToken` в запросах, увеличен буфер до 16MB
3. `SyncService` — использование cursor-based пагинации вместо offset

---

## Фаза 1: Базовая инфраструктура

### F1. Bootstrap проекта ✅
**Цель:** Создать монорепозиторий с базовой структурой.

**Состав:**
- Backend: Java 21, Spring Boot 3, OpenAPI
- Frontend: React + Vite + TypeScript
- docker-compose для зависимостей
- Минимальный Hello World API и UI

**Критерии готовности:**
- Проект собирается
- Backend запускается
- Frontend запускается
- API возвращает ответ

---

### F2. Jira Integration MVP ✅
**Цель:** Отобразить реальные данные из Jira на доске.

**Состав:**
- Интеграция с Jira REST API v3
- Basic Auth (email + API token)
- Получение Epic и Story
- Маппинг в BoardNode

**API Jira:**
- GET /rest/api/3/search (JQL)

**Конфигурация:**
```yaml
jira:
  base-url: ${JIRA_BASE_URL}
  email: ${JIRA_EMAIL}
  api-token: ${JIRA_API_TOKEN}
```

**Критерии готовности:**
- Board показывает Epic и Story из Jira
- Клик по элементу открывает Jira
- Тесты с мок-HTTP

---

### F3. Jira Sync & Cache (PostgreSQL) ✅
**Цель:** Перестать вызывать Jira в реальном времени.

**Состав:**
- Периодическая синхронизация в PostgreSQL
- Инкрементальный sync по updated timestamp
- Ручной refresh через UI
- API для статуса синхронизации

**Инкрементальная синхронизация:**
- Первый запуск: полная загрузка всех задач проекта
- Последующие: только задачи с `updated >= lastSyncCompletedAt - 1 minute`
- JQL: `project = KEY AND updated >= 'YYYY-MM-DD HH:mm' ORDER BY updated DESC`

**Таблицы:**
- `jira_issues` — кэш задач
- `jira_sync_state` — состояние синхронизации

**Конфигурация:**
```
JIRA_PROJECT_KEY
JIRA_SYNC_INTERVAL_SECONDS (default 300)
```

**Критерии готовности:**
- /api/board читает только из PostgreSQL ✅
- Кнопка "Refresh" запускает синхронизацию ✅
- Показывается статус синхронизации ✅
- Инкрементальная синхронизация работает ✅

---

## Фаза 2: Аутентификация и команды

### F4. OAuth 2.0 (Atlassian 3LO) + RBAC
**Цель:** Защитить приложение авторизацией через Atlassian.

**Состав:**
- Authorization Code grant
- Получение и хранение токенов
- Определение ролей: TEAM_MEMBER, TEAM_LEAD, ORG_ADMIN
- Защита API-эндпоинтов

**Таблицы:**
- `users` — пользователи
- `oauth_tokens` — токены (потом зашифровать)

**Конфигурация:**
```
ATLASSIAN_CLIENT_ID
ATLASSIAN_CLIENT_SECRET
ATLASSIAN_REDIRECT_URI
LB_ADMIN_ACCOUNT_IDS
```

**Критерии готовности:**
- "Sign in with Atlassian" работает
- Токены сохраняются
- RBAC проверяется на API

---

### F5. Team Management Backend
**Цель:** Создать основу для управления командами.

**Состав:**
- CRUD для команд и участников
- Связь с jiraAccountId
- Валидация hoursPerDay (>0, <=12)
- Деактивация вместо удаления

**API:**
- GET /api/team
- GET /api/team/members
- POST /api/team/members
- PUT /api/team/members/{id}
- POST /api/team/members/{id}/deactivate

**Критерии готовности:**
- API работает
- Данные сохраняются в БД
- Тесты покрывают основные сценарии

---

### F6. Team Management UI
**Цель:** UI для управления командой.

**Маршрут:** /team

**Состав:**
- Таблица участников
- Модальные окна для добавления/редактирования
- Деактивация с подтверждением

**Колонки таблицы:**
1. Name
2. Jira user (accountId)
3. Role (SA/DEV/QA)
4. Grade (Junior/Middle/Senior)
5. Hours/day
6. Status (Active/Inactive)
7. Actions

**Критерии готовности:**
- Страница команды доступна
- CRUD работает через UI
- Валидация на фронте

---

## Фаза 3: Синхронизация команд

### F7. Team Sync from Jira (Atlassian Teams) ✅
**Цель:** Автоматическая синхронизация команд из Atlassian.

**Состав:**
- Получение команд из Atlassian Teams API
- Синхронизация участников
- Защита локальных полей (role, grade, hoursPerDay)
- Manual trigger через UI

**API:**
- GET /api/teams/config — конфигурация (manualTeamManagement, organizationId)
- GET /api/teams/sync/status — статус синхронизации
- POST /api/teams/sync/trigger — запустить синхронизацию

**Конфигурация:**
```
JIRA_ORGANIZATION_ID — ID организации Atlassian (из admin.atlassian.com)
JIRA_MANUAL_TEAM_MANAGEMENT — разрешить ручное управление командами (default: false)
```

**Правила синхронизации:**
- Новая команда в Atlassian → создать
- Команда пропала → пометить неактивной (не удалять)
- Участник пропал → active = false (не удалять)

**Дефолты для новых участников:**
- role = DEV
- grade = MIDDLE
- hoursPerDay = 6.0

**Критерии готовности:**
- Команды синхронизируются из Atlassian ✅
- Локальные поля не перезаписываются ✅
- Кнопка ручной синхронизации работает ✅

---

## Фаза 4: Board и метрики

### F8. Board v2 (Epic как root)
**Цель:** Доска с Epic как корневым элементом.

**Иерархия:** Epic → Story → Sub-task

**API:**
```
GET /api/board
{
  "items": [EpicNode...],
  "nextCursor": "..." | null
}
```

**Фильтры:**
- query — поиск по issueKey или title
- teamIds — фильтр по командам
- statuses — фильтр по статусам

**Критерии готовности:**
- Board показывает Epic → Story
- Пагинация курсорная
- Фильтры работают

---

### F9. Sub-task Estimates (Analytics/Development/Testing)
**Цель:** Поддержка оценок по ролям в sub-tasks.

**Маппинг типов задач:**
- "Аналитика" → ANALYTICS
- "Разработка" → DEVELOPMENT
- "Тестирование" → TESTING

**Поля из Jira:**
- `fields.timetracking.originalEstimateSeconds`
- `fields.timetracking.timeSpentSeconds`

**Агрегация:**
- Story.totalEstimate = sum(sub-task estimates)
- Story.analyticsEstimate = sum(ANALYTICS)
- Story.developmentEstimate = sum(DEVELOPMENT)
- Story.testingEstimate = sum(TESTING)

**Data Quality alerts:**
- Sub-task: logged > 0, estimate = 0
- Story: нет Development sub-task
- Epic: есть stories, но estimate = 0

**Критерии готовности:**
- Оценки синхронизируются из Jira
- Агрегация по ролям работает
- После Refresh данные обновляются

---

### F10. Epic-Team Mapping (Jira Team Field)
**Цель:** Связать Epic с командой через Jira-поле.

**Конфигурация:**
```
JIRA_TEAM_FIELD_ID (e.g. customfield_12345)
```

**Логика маппинга:**
1. Получить значение Team-поля из Epic
2. Найти Team где jiraTeamValue == значение
3. Если найдено → epic.teamId = team.id
4. Если нет → epic.teamId = null + data quality issue

**Критерии готовности:**
- Epic корректно связывается с командой
- Отсутствие маппинга отображается как проблема
- Система толерантна к изменениям схемы Jira

---

### F11. Rough Estimates для Epics ✅
**Цель:** Возможность вводить грубые оценки для Epic по ролям (SA/DEV/QA).

**Поля Epic в БД:**
- `rough_estimate_sa_days` (DECIMAL) — оценка для SA
- `rough_estimate_dev_days` (DECIMAL) — оценка для DEV
- `rough_estimate_qa_days` (DECIMAL) — оценка для QA
- `rough_estimate_updated_at` — время последнего обновления
- `rough_estimate_updated_by` — кто обновил

**API:**
```
GET /api/epics/config/rough-estimate — конфигурация
PATCH /api/epics/{epicKey}/rough-estimate/{role} — обновление (role: sa|dev|qa)
{ "days": number | null }
```

**Конфигурация:**
```yaml
rough-estimate:
  enabled: true
  allowed-epic-statuses: [Backlog, To Do, Бэклог, Сделать]
  step-days: 0.5
  min-days: 0.0
  max-days: 365
```

**UI поведение:**
- Epic в TODO (Backlog/To Do): редактируемые чипы SA/DEV/QA с пунктирной рамкой
- Epic в работе: прогресс-бары с залогированным/оценкой из сабтасков
- ESTIMATE колонка для Epic в TODO = сумма SA + DEV + QA rough estimates
- ESTIMATE для Epic в работе = агрегация из сабтасков

**Валидация:**
- Редактирование только для Epic в разрешённых статусах
- Значение кратно stepDays
- Диапазон: minDays - maxDays

**Критерии готовности:**
- Rough estimates сохраняются в БД ✅
- Редактирование только в разрешённых статусах ✅
- Значения переживают Jira sync ✅
- UI отображает разные состояния для TODO/в работе ✅

---

## Фаза 5: Multi-tenancy

### F12. Multi-tenancy + RBAC + Configurable Jira
**Цель:** Полноценная многоарендность с гибкой конфигурацией.

**Иерархия:**
```
Company
└── JiraSpace (per Jira project)
    └── Team
        └── TeamMember
```

**Роли RBAC:**
- COMPANY_ADMIN — управление всем в компании
- JIRA_SPACE_ADMIN — управление в рамках JiraSpace
- TEAM_LEAD — управление своей командой
- TEAM_MEMBER — только чтение

**Конфигурация JiraSpace:**
```yaml
jiraKey: PROJECT
epicIssueType: Epic
storyIssueTypes: [Story]
subTaskTypes:
  ANALYTICS: Аналитика
  DEVELOPMENT: Разработка
  TESTING: Тестирование
statusMapping:
  planning: [Backlog, To Do]
  in_progress: [In Progress, In Review]
  done: [Done, Closed]
fieldMapping:
  teamFieldId: customfield_12345
  originalEstimateField: timetracking
calculationRules:
  hoursPerDay: 8
  capProgressAt100: true
```

**Маршруты UI:**
```
/{companySlug}/{jiraKey}/board
/{companySlug}/{jiraKey}/team
```

**Критерии готовности:**
- Несколько компаний сосуществуют
- JiraSpace имеют независимые конфигурации
- RBAC проверяется на каждом API
- Изменение конфигурации не требует изменения кода

---

## Фаза 6: Автопланирование

### F13. Автопланирование (Auto Planning) ✅

**Цель:** Прогнозирование даты закрытия эпиков.

**Подробная спецификация:** [F13_AUTOPLANNING.md](./F13_AUTOPLANNING.md)
**План реализации:** [F13_PLAN.md](./F13_PLAN.md)

**Реализованные возможности:**
- AutoScore — автоматический расчёт приоритета эпиков (7 факторов)
- Расчёт Expected Done с учётом capacity команды
- Колонка Expected Done в Board (дата + уверенность + дельта от Due date)
- Производственный календарь РФ (xmlcalendar.ru)
- Коэффициенты грейдов (Senior 0.8 / Middle 1.0 / Junior 1.5)
- Коэффициент рисков (по умолчанию 20%)
- Конвейерная модель: SA → DEV → QA с параллельностью
- Drag & drop для изменения приоритета эпиков
- Автопересчёт AutoScore после Jira sync
- Тултип с деталями расчёта (фазы, остаток работы)

**Тестовое покрытие:**
- AutoScoreCalculator: 30+ тестов
- ForecastService: 18 тестов

**Критерии готовности MVP:**
- [x] Производственный календарь РФ работает
- [x] Конфигурация планирования в настройках команды
- [x] AutoScore рассчитывается для эпиков
- [x] Expected Done отображается в Board
- [x] Прогноз учитывает capacity и последовательность ролей
- [x] Ручное изменение приоритета работает (drag & drop)

---

### F14. Timeline/Gantt ✅

**Цель:** Визуализация плана работ в виде Gantt-диаграммы.

**Подробная спецификация:** [F14_TIMELINE_GANTT.md](./F14_TIMELINE_GANTT.md)

**Реализованные возможности:**
- Gantt-диаграмма на странице `/timeline`
- Горизонтальные бары для фаз SA/DEV/QA
- Цветовая кодировка по ролям
- Opacity по уровню уверенности
- Индикаторы Today и Due Date
- Индикатор No Resource для ролей без capacity
- Zoom уровни: день/неделя/месяц
- Конвейерная модель планирования (Pipeline)
- Настройка StoryDuration в конфигурации команды

---

## Технический долг (TODO)

### Критические (CRITICAL)
- [ ] Зашифровать OAuth токены в БД
- [ ] Исправить UNIQUE constraint для team_members
- [ ] Добавить CSRF защиту

### Высокий приоритет (HIGH)
- [ ] Добавить индексы в БД
- [ ] Исправить N+1 запросы
- [ ] Исправить memory leak в sync locks

### Средний приоритет (MEDIUM)
- [ ] Разбить большие компоненты на меньшие
- [ ] Добавить Error Boundary во frontend
- [ ] Добавить валидацию API-ответов
- [ ] Добавить Rate Limiting
- [ ] Добавить frontend тесты

---

## Диаграмма зависимостей фич

```
F1 (Bootstrap)
  │
  ├─► F2 (Jira MVP)
  │     │
  │     └─► F3 (Sync & Cache)
  │           │
  │           ├─► F8 (Board v2)
  │           │     │
  │           │     ├─► F9 (Sub-task Estimates)
  │           │     │
  │           │     └─► F10 (Epic-Team Mapping)
  │           │           │
  │           │           └─► F11 (Rough Estimates)
  │           │                 │
  │           │                 └─► F13 (Автопланирование) ◄──┐
  │           │                                               │
  │           └─► F4 (OAuth + RBAC)                           │
  │                 │                                         │
  │                 ├─► F5 (Team Backend) ─────────────────────┘
  │                 │     │
  │                 │     └─► F6 (Team UI)
  │                 │
  │                 └─► F7 (Team Sync)
  │
  └─────────────────────────► F12 (Multi-tenancy)
```

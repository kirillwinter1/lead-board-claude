# Список фич Lead Board

## Порядок реализации

Фичи упорядочены по зависимостям. Каждая следующая фича может зависеть от предыдущих.

### Статус реализации

| Фича | Статус | Дата |
|------|--------|------|
| F1. Bootstrap проекта | ✅ Готово | 2026-01-22 |
| F2. Jira Integration MVP | ✅ Готово | 2026-01-22 |
| F3. Jira Sync & Cache | ✅ Готово | 2026-01-23 |
| F4. OAuth 2.0 + RBAC | ⏳ Запланировано | — |
| F5-F6. Team Management | ⏳ Запланировано | — |

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

**Таблицы:**
- `jira_issues` — кэш задач
- `jira_sync_state` — состояние синхронизации

**Конфигурация:**
```
JIRA_PROJECT_KEY
JIRA_SYNC_INTERVAL_SECONDS (default 300)
```

**Критерии готовности:**
- /api/board читает только из PostgreSQL
- Кнопка "Refresh" запускает синхронизацию
- Показывается статус синхронизации

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

### F7. Team Sync from Jira (Atlassian Teams)
**Цель:** Автоматическая синхронизация команд из Atlassian.

**Состав:**
- Получение команд из Atlassian Teams API
- Синхронизация участников
- Защита локальных полей (role, grade, hoursPerDay)
- Scheduled sync + manual trigger

**Правила синхронизации:**
- Новая команда в Atlassian → создать
- Команда пропала → пометить неактивной (не удалять)
- Участник пропал → active = false (не удалять)

**Дефолты для новых участников:**
- role = DEV
- grade = MIDDLE
- hoursPerDay = 6.0

**Критерии готовности:**
- Команды синхронизируются из Atlassian
- Локальные поля не перезаписываются
- Кнопка ручной синхронизации работает

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

### F11. Rough Estimates для Epics
**Цель:** Возможность вводить dirty estimates для Epic.

**Поля Epic:**
- roughEstimateDays (DECIMAL, шаг 0.1)
- roughEstimateUpdatedAt
- roughEstimateUpdatedBy

**API:**
```
PATCH /api/{companySlug}/{jiraKey}/epics/{epicKey}/rough-estimate
{ "days": number }
```

**Конфигурация (per JiraSpace):**
```yaml
roughEstimate:
  enabled: true
  allowedEpicStatuses: [status1, status2]
  stepDays: 0.1
  minDays: 0.0
```

**Валидация:**
- Только COMPANY_ADMIN или JIRA_SPACE_ADMIN
- Epic в разрешённом статусе
- Значение кратно 0.1

**Критерии готовности:**
- Rough estimate сохраняется в БД
- Редактирование только в разрешённых статусах
- Значение переживает Jira sync

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
  │           │
  │           └─► F4 (OAuth + RBAC)
  │                 │
  │                 ├─► F5 (Team Backend)
  │                 │     │
  │                 │     └─► F6 (Team UI)
  │                 │
  │                 └─► F7 (Team Sync)
  │
  └─────────────────────────► F12 (Multi-tenancy)
```

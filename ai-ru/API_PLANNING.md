# API Автопланирования (Planning API)

## Обзор

API для прогнозирования дат завершения эпиков на основе capacity команды.

**Base URL:** `/api/planning`

---

## Planning Endpoints

### GET /api/planning/forecast

Получает прогноз завершения для эпиков команды.

**Параметры запроса:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| teamId | number | Да | ID команды |
| statuses | string[] | Нет | Фильтр по статусам (можно несколько) |

**Пример запроса:**
```
GET /api/planning/forecast?teamId=1&statuses=In%20Progress&statuses=Backlog
```

**Ответ:**
```json
{
  "calculatedAt": "2026-01-24T12:00:00Z",
  "teamId": 1,
  "roleCapacity": {
    "SA": 6.0,
    "DEV": 16.0,
    "QA": 6.0
  },
  "wipStatus": {
    "limit": 6,
    "current": 3,
    "exceeded": false,
    "roleWip": {
      "SA": { "limit": 2, "current": 1, "exceeded": false },
      "DEV": { "limit": 3, "current": 2, "exceeded": false },
      "QA": { "limit": 2, "current": 1, "exceeded": false }
    }
  },
  "epics": [
    {
      "epicKey": "LB-123",
      "summary": "Реализация автопланирования",
      "autoScore": 85.5,
      "expectedDone": "2026-03-15",
      "confidence": "HIGH",
      "dueDateDeltaDays": -3,
      "dueDate": "2026-03-18",
      "remainingByRole": {
        "SA": { "hours": 16.0, "days": 2.0 },
        "DEV": { "hours": 80.0, "days": 10.0 },
        "QA": { "hours": 24.0, "days": 3.0 }
      },
      "phaseSchedule": {
        "SA": {
          "startDate": "2026-01-27",
          "endDate": "2026-01-28",
          "workDays": 2.0,
          "noCapacity": false
        },
        "DEV": {
          "startDate": "2026-01-29",
          "endDate": "2026-02-11",
          "workDays": 10.0,
          "noCapacity": false
        },
        "QA": {
          "startDate": "2026-02-05",
          "endDate": "2026-03-15",
          "workDays": 3.0,
          "noCapacity": false
        }
      },
      "queuePosition": null,
      "queuedUntil": null,
      "isWithinWip": true,
      "phaseWaitInfo": {
        "SA": { "waiting": false, "waitingUntil": null, "queuePosition": null },
        "DEV": { "waiting": false, "waitingUntil": null, "queuePosition": null },
        "QA": { "waiting": false, "waitingUntil": null, "queuePosition": null }
      }
    }
  ]
}
```

**Поля ответа:**

| Поле | Тип | Описание |
|------|-----|----------|
| calculatedAt | string | Время расчёта (ISO 8601) |
| teamId | number | ID команды |
| roleCapacity | Map<string, number> | Capacity команды по ролям (ключ = код роли, значение = часов/день) |
| wipStatus | WipStatus | Статус WIP лимитов |
| epics | array | Список прогнозов по эпикам |

**Поля WipStatus:**

| Поле | Тип | Описание |
|------|-----|----------|
| limit | number | WIP лимит команды |
| current | number | Текущее количество эпиков в WIP |
| exceeded | boolean | Превышен ли лимит |
| roleWip | Map<string, RoleWipStatus> | WIP статус по ролям (ключ = код роли) |

**Поля RoleWipStatus:**

| Поле | Тип | Описание |
|------|-----|----------|
| limit | number | Лимит для роли |
| current | number | Текущее количество эпиков на этой фазе |
| exceeded | boolean | Превышен ли лимит |

**Поля EpicForecast:**

| Поле | Тип | Описание |
|------|-----|----------|
| epicKey | string | Ключ эпика в Jira |
| summary | string | Название эпика |
| autoScore | number | Автоматический приоритет (для рекомендаций) |
| expectedDone | string | Прогнозная дата завершения |
| confidence | string | Уровень уверенности: HIGH, MEDIUM, LOW |
| dueDateDeltaDays | number | Разница с due date (+ опоздание, - запас) |
| dueDate | string | Due date из Jira |
| remainingByRole | Map<string, object> | Остаток работы по ролям (ключ = код роли) |
| phaseSchedule | Map<string, object> | Расписание фаз (ключ = код роли) |
| queuePosition | number/null | Позиция в очереди (null если в WIP) |
| queuedUntil | string/null | До какой даты в очереди |
| isWithinWip | boolean | Входит ли в активный WIP |
| phaseWaitInfo | Map<string, RoleWaitInfo> | Информация об ожидании по фазам (ключ = код роли) |

> **Важно:** Роли динамические (из `workflow_roles`). Ключи в `roleCapacity`, `wipStatus.roleWip`, `remainingByRole`, `phaseSchedule`, `phaseWaitInfo` — коды ролей из конфигурации (SA, DEV, QA и т.д.), а не хардкод.

**Уровни уверенности:**

| Уровень | Условия |
|---------|---------|
| HIGH | Есть rough estimates и все роли с capacity |
| MEDIUM | Есть оценки, но не хватает одной роли |
| LOW | Нет оценок или не хватает 2+ ролей |

---

### GET /api/planning/role-load

Получает загруженность команды по ролям.

**Параметры запроса:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| teamId | number | Да | ID команды |

**Ответ:** `RoleLoadResponse` — данные о capacity/utilization по каждой роли.

---

### GET /api/planning/retrospective

Получает ретроспективную таймлайн-диаграмму (план vs факт).

**Параметры запроса:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| teamId | number | Да | ID команды |

**Ответ:** `RetrospectiveResult` — фактические vs планируемые даты.

---

### GET /api/planning/epics/{epicKey}/story-forecast

Получает детальный story-level прогноз для эпика.

**Path параметры:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| epicKey | string | Ключ эпика (например, LB-123) |

**Ответ:** `StoryForecastResponse` — прогноз по каждой story с assignee capacity.

---

### POST /api/planning/recalculate

Пересчитывает AutoScore для всех или для команды.

**Параметры запроса:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| teamId | number | Нет | ID команды (если не указан — все) |

**Ответ:**
```json
{
  "status": "completed",
  "epicsUpdated": 15
}
```

---

### GET /api/planning/wip-history

Получает историю WIP для построения графика.

**Параметры запроса:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| teamId | number | Да | ID команды |
| days | number | Нет | Количество дней (по умолчанию 30) |

**Ответ:**
```json
{
  "teamId": 1,
  "from": "2025-12-25",
  "to": "2026-01-24",
  "dataPoints": [
    {
      "date": "2026-01-20",
      "teamLimit": 6,
      "teamCurrent": 4,
      "roleData": {
        "SA": { "limit": 2, "current": 1 },
        "DEV": { "limit": 3, "current": 2 },
        "QA": { "limit": 2, "current": 1 }
      },
      "inQueue": 2,
      "totalEpics": 6
    }
  ]
}
```

> **Важно:** `roleData` — динамическая Map. Ключи — коды ролей из `workflow_roles`.

---

### POST /api/planning/wip-snapshot

Создаёт снапшот WIP для команды (ручной запуск).

**Параметры запроса:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| teamId | number | Да | ID команды |

**Ответ:**
```json
{
  "status": "created",
  "date": "2026-01-24",
  "teamWip": "4/6"
}
```

---

## AutoScore Endpoints

### GET /api/planning/autoscore/epics/{epicKey}

Получает детализацию AutoScore для эпика.

**Ответ:**
```json
{
  "epicKey": "LB-123",
  "summary": "Реализация автопланирования",
  "totalScore": 85.5,
  "calculatedAt": "2026-01-24T12:00:00Z",
  "factors": {
    "status": 25.0,
    "progress": 7.5,
    "dueDate": 25.0,
    "priority": 15.0,
    "size": 4.0,
    "age": 3.0
  }
}
```

**Факторы AutoScore:**

| Фактор | Макс. баллов | Описание |
|--------|--------------|----------|
| status | 30 | Позиция в workflow: Acceptance=30, Developing=25, Planned=15, New=-5 |
| progress | 10 | (logged / estimate) * 10 |
| dueDate | 25 | Экспоненциальный рост к дедлайну |
| priority | 20 | Highest=20, High=15, Medium=10, Low=5 |
| size | 5 | Инверсия от размера (меньше = выше), без оценки = -5 |
| age | 5 | Логарифм от дней с создания |

**Примечание:** AutoScore используется только для рекомендаций. Порядок эпиков определяется через `manual_order`.

---

### GET /api/planning/autoscore/teams/{teamId}/epics

Получает список эпиков команды отсортированных по AutoScore.

**Параметры запроса:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| statuses | string[] | Нет | Фильтр по статусам |

**Ответ:**
```json
[
  {
    "epicKey": "LB-123",
    "summary": "Эпик 1",
    "autoScore": 85.5,
    "calculatedAt": "2026-01-24T12:00:00Z"
  }
]
```

**Примечание:** Эпики возвращаются отсортированными по `manual_order` (не по autoScore).

---

### POST /api/planning/autoscore/teams/{teamId}/recalculate

Пересчитывает AutoScore для эпиков конкретной команды.

**Ответ:** количество обновлённых эпиков.

---

### POST /api/planning/autoscore/epics/{epicKey}/recalculate

Пересчитывает AutoScore для одного эпика.

**Ответ:** обновлённый AutoScoreDto.

---

## Issue Order Endpoints

### PUT /api/epics/{epicKey}/order

Обновляет позицию эпика в списке команды (drag & drop).

**Body:**
```json
{
  "newPosition": 3
}
```

**Ответ:**
```json
{
  "epicKey": "LB-123",
  "newPosition": 3,
  "teamId": 1
}
```

**Примечание:** `newPosition` — позиция в списке (1-based). Остальные эпики автоматически сдвигаются.

---

### PUT /api/stories/{storyKey}/order

Обновляет позицию story внутри эпика (drag & drop).

**Body:**
```json
{
  "newPosition": 2
}
```

**Ответ:**
```json
{
  "storyKey": "LB-456",
  "newPosition": 2,
  "parentKey": "LB-123"
}
```

---

## Calendar API

### GET /api/calendar/workdays

Получает рабочие дни за период.

**Параметры:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| from | LocalDate | Да | Начальная дата (YYYY-MM-DD) |
| to | LocalDate | Да | Конечная дата (YYYY-MM-DD) |
| country | string | Нет | Код страны (по умолчанию "RU") |

**Ответ:**
```json
{
  "from": "2026-01-01",
  "to": "2026-01-31",
  "country": "RU",
  "totalDays": 31,
  "workdays": 20,
  "weekends": 8,
  "holidays": 3,
  "workdayDates": ["2026-01-09", "2026-01-12", "..."],
  "holidayList": [
    { "date": "2026-01-01", "name": "Новый год" }
  ]
}
```

### GET /api/calendar/is-workday

Проверяет, является ли дата рабочим днём.

**Параметры:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| date | LocalDate | Дата (YYYY-MM-DD) |
| country | string | Код страны (по умолчанию "RU") |

**Ответ:**
```json
{
  "date": "2026-01-27",
  "isWorkday": true,
  "country": "RU"
}
```

### GET /api/calendar/count-workdays

Считает количество рабочих дней за период.

**Параметры:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| from | LocalDate | Начальная дата |
| to | LocalDate | Конечная дата |
| country | string | Код страны (по умолчанию "RU") |

**Ответ:**
```json
{
  "from": "2026-01-01",
  "to": "2026-01-31",
  "country": "RU",
  "workdays": 20
}
```

### GET /api/calendar/add-workdays

Возвращает дату через N рабочих дней.

**Параметры:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| from | LocalDate | Начальная дата |
| days | int | Количество рабочих дней |
| country | string | Код страны (по умолчанию "RU") |

**Ответ:**
```json
{
  "from": "2026-01-24",
  "workdaysToAdd": 5,
  "country": "RU",
  "resultDate": "2026-01-31"
}
```

### POST /api/calendar/refresh

Обновляет календарь праздников с xmlcalendar.ru.

**Параметры:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| year | int | Год для загрузки |
| country | string | Код страны (по умолчанию "RU") |

### GET /api/calendar/config

Возвращает текущую конфигурацию календаря.

---

## Team Planning Config API

### GET /api/teams/{id}/planning-config

Получает конфигурацию планирования команды.

**Ответ:**
```json
{
  "gradeCoefficients": {
    "senior": 0.8,
    "middle": 1.0,
    "junior": 1.5
  },
  "riskBuffer": 0.2,
  "wipLimits": {
    "team": 6,
    "SA": 2,
    "DEV": 3,
    "QA": 2
  },
  "storyDuration": {
    "SA": 2,
    "DEV": 2,
    "QA": 2
  }
}
```

> **Важно:** Ключи в `wipLimits` и `storyDuration` — динамические коды ролей из `workflow_roles`.

### PUT /api/teams/{id}/planning-config

Обновляет конфигурацию планирования.

**Body:** аналогично GET ответу.

**Валидация:**
- gradeCoefficients > 0
- riskBuffer >= 0
- wipLimits >= 1
- storyDuration >= 1

---

## Workflow Config API (Admin)

Base path: `/api/admin/workflow-config` | Требует роль ADMIN.

### GET /api/admin/workflow-config

Получает полную конфигурацию workflow.

### PUT /api/admin/workflow-config

Обновляет проектную конфигурацию.

### GET /api/admin/workflow-config/roles

Список ролей pipeline.

### PUT /api/admin/workflow-config/roles

Обновляет роли (code, displayName, color, sortOrder, isDefault).

### GET /api/admin/workflow-config/issue-types

Список маппингов типов задач.

### PUT /api/admin/workflow-config/issue-types

Обновляет маппинги типов задач → EPIC/STORY/SUBTASK/IGNORE.

### GET /api/admin/workflow-config/statuses

Список маппингов статусов.

### PUT /api/admin/workflow-config/statuses

Обновляет маппинги статусов → TODO/IN_PROGRESS/DONE + фаза роли.

### GET /api/admin/workflow-config/status-issue-counts

Количество задач по каждому статусу/категории (для UI предпросмотра).

### GET /api/admin/workflow-config/link-types

Список маппингов типов связей.

### PUT /api/admin/workflow-config/link-types

Обновляет маппинги связей → BLOCKS/RELATED/IGNORE.

### POST /api/admin/workflow-config/validate

Валидирует текущую конфигурацию workflow.

### POST /api/admin/workflow-config/auto-detect

Автоматически определяет маппинги из метаданных Jira.

### GET /api/admin/workflow-config/status

Проверяет, настроен ли workflow (для Setup Wizard).

---

## Jira Metadata API (Admin)

Base path: `/api/admin/jira-metadata` | Для настройки workflow.

### GET /api/admin/jira-metadata/issue-types

Список типов задач из Jira.

### GET /api/admin/jira-metadata/statuses

Список статусов из Jira.

### GET /api/admin/jira-metadata/link-types

Список типов связей из Jira.

---

## Public Config API

Base path: `/api/config/workflow` | Без авторизации.

### GET /api/config/workflow/roles

Список ролей для фронтенда.

### GET /api/config/workflow/issue-type-categories

Маппинг типов задач → категории.

### GET /api/config/workflow/status-styles

Стили статусов (цвет, категория) для StatusBadge.

---

## Ошибки

| Код | Описание |
|-----|----------|
| 400 | Невалидные параметры |
| 401 | Не аутентифицирован |
| 403 | Нет доступа (RBAC) |
| 404 | Команда или эпик не найдены |
| 500 | Внутренняя ошибка сервера |
| 502 | Jira недоступна |

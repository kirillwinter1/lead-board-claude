# API Автопланирования (Planning API)

## Обзор

API для прогнозирования дат завершения эпиков на основе capacity команды.

**Base URL:** `/api/planning`

---

## Endpoints

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
  "teamCapacity": {
    "saHoursPerDay": 6.0,
    "devHoursPerDay": 16.0,
    "qaHoursPerDay": 6.0
  },
  "wipStatus": {
    "limit": 6,
    "current": 3,
    "exceeded": false,
    "sa": { "limit": 2, "current": 1, "exceeded": false },
    "dev": { "limit": 3, "current": 2, "exceeded": false },
    "qa": { "limit": 2, "current": 1, "exceeded": false }
  },
  "epics": [
    {
      "epicKey": "LB-123",
      "summary": "Реализация автопланирования",
      "autoScore": 85.5,
      "manualPriorityBoost": 0,
      "expectedDone": "2026-03-15",
      "confidence": "HIGH",
      "dueDateDeltaDays": -3,
      "dueDate": "2026-03-18",
      "remainingByRole": {
        "sa": { "hours": 16.0, "days": 2.0 },
        "dev": { "hours": 80.0, "days": 10.0 },
        "qa": { "hours": 24.0, "days": 3.0 }
      },
      "phaseSchedule": {
        "sa": {
          "startDate": "2026-01-27",
          "endDate": "2026-01-28",
          "workDays": 2.0,
          "noCapacity": false
        },
        "dev": {
          "startDate": "2026-01-29",
          "endDate": "2026-02-11",
          "workDays": 10.0,
          "noCapacity": false
        },
        "qa": {
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
        "sa": { "waiting": false, "waitingUntil": null, "queuePosition": null },
        "dev": { "waiting": false, "waitingUntil": null, "queuePosition": null },
        "qa": { "waiting": false, "waitingUntil": null, "queuePosition": null }
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
| teamCapacity | object | Capacity команды по ролям (часов/день) |
| wipStatus | object | Статус WIP лимитов |
| epics | array | Список прогнозов по эпикам |

**Поля WipStatus:**

| Поле | Тип | Описание |
|------|-----|----------|
| limit | number | WIP лимит команды |
| current | number | Текущее количество эпиков в WIP |
| exceeded | boolean | Превышен ли лимит |
| sa | RoleWipStatus | WIP статус для SA (null если не рассчитан) |
| dev | RoleWipStatus | WIP статус для DEV (null если не рассчитан) |
| qa | RoleWipStatus | WIP статус для QA (null если не рассчитан) |

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
| autoScore | number | Автоматический приоритет (0-100+) |
| manualPriorityBoost | number | Ручная корректировка приоритета |
| expectedDone | string | Прогнозная дата завершения |
| confidence | string | Уровень уверенности: HIGH, MEDIUM, LOW |
| dueDateDeltaDays | number | Разница с due date (+ опоздание, - запас) |
| dueDate | string | Due date из Jira |
| remainingByRole | object | Остаток работы по ролям |
| phaseSchedule | object | Расписание фаз SA/DEV/QA |
| queuePosition | number/null | Позиция в очереди (null если в WIP) |
| queuedUntil | string/null | До какой даты в очереди |
| isWithinWip | boolean | Входит ли в активный WIP |
| phaseWaitInfo | PhaseWaitInfo | Информация об ожидании по фазам (null если нет) |

**Поля PhaseWaitInfo:**

| Поле | Тип | Описание |
|------|-----|----------|
| sa | RoleWaitInfo | Информация об ожидании SA фазы |
| dev | RoleWaitInfo | Информация об ожидании DEV фазы |
| qa | RoleWaitInfo | Информация об ожидании QA фазы |

**Поля RoleWaitInfo:**

| Поле | Тип | Описание |
|------|-----|----------|
| waiting | boolean | Ожидает ли вход в фазу |
| waitingUntil | string/null | До какой даты ждёт |
| queuePosition | number/null | Позиция в очереди на эту фазу |

**Уровни уверенности:**

| Уровень | Условия |
|---------|---------|
| HIGH | Есть rough estimates и все роли с capacity |
| MEDIUM | Есть оценки, но не хватает одной роли |
| LOW | Нет оценок или не хватает 2+ ролей |

---

### POST /api/planning/recalculate

Пересчитывает AutoScore для всех или для команды.

**Параметры запроса:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| teamId | number | Нет | ID команды (если не указан — все) |

**Пример запроса:**
```
POST /api/planning/recalculate?teamId=1
```

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

**Пример запроса:**
```
GET /api/planning/wip-history?teamId=1&days=30
```

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
      "saLimit": 2,
      "saCurrent": 1,
      "devLimit": 3,
      "devCurrent": 2,
      "qaLimit": 2,
      "qaCurrent": 1,
      "inQueue": 2,
      "totalEpics": 6
    }
  ]
}
```

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

### PATCH /api/planning/autoscore/epics/{epicKey}/boost

Обновляет ручной boost приоритета для эпика.

**Path параметры:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| epicKey | string | Ключ эпика (например, LB-123) |

**Body:**
```json
{
  "boost": 10
}
```

**Ответ:**
```json
{
  "epicKey": "LB-123",
  "boost": 10,
  "newAutoScore": 95.5
}
```

**Примечание:** boost может быть любым целым числом (положительным или отрицательным). Используется для drag & drop переупорядочивания.

---

### GET /api/planning/autoscore/epics/{epicKey}

Получает детализацию AutoScore для эпика.

**Ответ:**
```json
{
  "epicKey": "LB-123",
  "summary": "Реализация автопланирования",
  "totalScore": 85.5,
  "manualPriorityBoost": 0,
  "calculatedAt": "2026-01-24T12:00:00Z",
  "factors": {
    "status": 20.0,
    "progress": 7.5,
    "dueDate": 25.0,
    "priority": 15.0,
    "size": 8.0,
    "age": 5.0,
    "manualBoost": 5.0
  }
}
```

**Факторы AutoScore:**

| Фактор | Макс. баллов | Описание |
|--------|--------------|----------|
| status | 20 | In Progress = 20, остальные = 0 |
| progress | 15 | (logged / estimate) * 15 |
| dueDate | 25 | Экспоненциальный рост к дедлайну |
| priority | 15 | Highest=15, High=10, Medium=5, Low=2 |
| size | 10 | Инверсия от размера (меньше = больше) |
| age | 10 | Логарифм от дней с создания |
| manualBoost | ∞ | Ручная корректировка |

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
    "totalScore": 85.5,
    "manualPriorityBoost": 0,
    "calculatedAt": "2026-01-24T12:00:00Z"
  },
  {
    "epicKey": "LB-124",
    "summary": "Эпик 2",
    "totalScore": 72.3,
    "manualPriorityBoost": 0,
    "calculatedAt": "2026-01-24T12:00:00Z"
  }
]
```

---

## Calendar API

### GET /api/calendar/workdays

Получает рабочие дни за период.

**Параметры:**

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| from | string | Да | Начальная дата (YYYY-MM-DD) |
| to | string | Да | Конечная дата (YYYY-MM-DD) |

**Ответ:**
```json
{
  "workdays": ["2026-01-27", "2026-01-28", "2026-01-29"],
  "holidays": [
    { "date": "2026-01-01", "name": "Новый год" }
  ]
}
```

### GET /api/calendar/is-workday

Проверяет, является ли дата рабочим днём.

**Параметры:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| date | string | Дата (YYYY-MM-DD) |

**Ответ:**
```json
{
  "date": "2026-01-27",
  "isWorkday": true
}
```

### GET /api/calendar/add-workdays

Возвращает дату через N рабочих дней.

**Параметры:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| date | string | Начальная дата |
| days | number | Количество рабочих дней |

**Ответ:**
```json
{
  "startDate": "2026-01-24",
  "workdaysToAdd": 5,
  "resultDate": "2026-01-31"
}
```

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
    "sa": 2,
    "dev": 3,
    "qa": 2
  },
  "storyDuration": {
    "sa": 2,
    "dev": 2,
    "qa": 2
  }
}
```

### PUT /api/teams/{id}/planning-config

Обновляет конфигурацию планирования.

**Body:** аналогично GET ответу.

**Валидация:**
- gradeCoefficients > 0
- riskBuffer >= 0
- wipLimits >= 1
- storyDuration >= 1

---

## Ошибки

| Код | Описание |
|-----|----------|
| 400 | Невалидные параметры |
| 404 | Команда или эпик не найдены |
| 500 | Внутренняя ошибка сервера |

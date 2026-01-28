# F17: Configurable Status Mapping

## Цель
Вынести хардкоженные статусы в конфигурацию для поддержки разных Jira-проектов (русский/английский статусы).

## Архитектура

```
application.yml (системные дефолты)
       ↓
teams.planning_config JSONB (override на уровне команды)
       ↓
StatusMappingService (мержит и использует)
```

## Конфигурация (application.yml)

```yaml
status-mapping:
  epic-workflow:
    todo-statuses: [New, Backlog, Новый, Бэклог, ...]
    in-progress-statuses: [Developing, В разработке, ...]
    done-statuses: [Done, Closed, Готово, ...]
  story-workflow:
    todo-statuses: [New, Ready, Новый, ...]
    in-progress-statuses: [Development, Testing, ...]
    done-statuses: [Done, Готово]
  subtask-workflow:
    todo-statuses: [New, Новый]
    in-progress-statuses: [In Progress, В работе, ...]
    done-statuses: [Done, Готово]
  phase-mapping:
    sa-statuses: [Analysis, Анализ, ...]
    dev-statuses: [Development, Разработка, ...]
    qa-statuses: [Testing, Тестирование, ...]
    sa-issue-types: [Аналитика, Analysis]
    qa-issue-types: [Тестирование, Testing, Bug, ...]
```

## Алгоритм определения статуса
1. Точное совпадение (case-insensitive)
2. Fallback: substring matching
3. По умолчанию → TODO + warning в лог

## Override для команды
```json
PUT /api/teams/{id}/planning-config
{
  "statusMapping": {
    "epicWorkflow": { "todoStatuses": ["Бэклог", "Новый"] },
    "phaseMapping": { "qaIssueTypes": ["QA", "Баг"] }
  }
}
```

## Файлы
- `status/StatusMappingService.java` (300 LOC) — основной сервис
- `status/StatusCategory.java` — enum: TODO, IN_PROGRESS, DONE
- `status/WorkflowConfig.java`, `PhaseMapping.java`, `StatusMappingConfig.java`, `StatusMappingProperties.java`

## Тесты
30+ unit-тестов: категоризация, фазы, isDone/isInProgress, team override, merge.

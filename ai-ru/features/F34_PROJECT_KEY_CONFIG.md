# F34: Привязка project_key к project_configurations

**Дата:** 2026-02-16
**Статус:** ✅ Done

## Проблема

`project_configurations` — синглтон (одна строка, `is_default=TRUE`). Workflow config (статусы, роли, типы задач) не привязан к Jira-проекту. Для multi-project SaaS нужна связь config ↔ project_key.

## Решение

Nullable колонка `project_key` в `project_configurations` с уникальным partial index. Все сервисы резолвят конфиг по цепочке: project_key → fallback default. При первом запуске project_key автоприсваивается из env (`JIRA_PROJECT_KEY`).

## Изменения

### База данных

**V31__add_project_key_to_config.sql:**
- `ALTER TABLE project_configurations ADD COLUMN project_key VARCHAR(50)`
- `CREATE UNIQUE INDEX idx_project_configurations_project_key ON project_configurations(project_key) WHERE project_key IS NOT NULL`

### Backend

**Entity:** `ProjectConfigurationEntity` — поле `projectKey` + getter/setter

**Repository:** `ProjectConfigurationRepository` — метод `findByProjectKey(String)`

**WorkflowConfigService:**
- Inject `JiraProperties`
- `loadConfiguration()`: ищет по project_key → fallback `findByIsDefaultTrue()` → auto-assign project_key
- Геттер `getProjectKey()`

**MappingAutoDetectService:**
- Inject `JiraProperties`
- `getDefaultConfigId()` / `getOrCreateDefaultConfigId()`: резолв по project_key → fallback default
- При создании нового конфига — сразу ставит project_key

**WorkflowConfigController:**
- Inject `JiraProperties`
- `getDefaultConfig()` / `getOrCreateDefaultConfig()`: резолв по project_key → fallback default
- Передаёт `projectKey` в DTO

**DTO:** `WorkflowConfigResponse` — поле `String projectKey`

### Frontend

**API:** `WorkflowConfigResponse` — поле `projectKey: string | null`

**UI:** `WorkflowConfigPage` — заголовок "Workflow Configuration — LB" (project key)

### Тесты

- `MappingAutoDetectServiceTest` — добавлен mock `JiraProperties`
- `WorkflowConfigControllerTest` — добавлен `@MockBean JiraProperties`
- `DataQualityServiceTest` — обновлён конструктор `WorkflowConfigService`

## Логика резолва конфига

```
1. findByProjectKey(env.JIRA_PROJECT_KEY)  →  найден? → используем
2. fallback: findByIsDefaultTrue()          →  найден? → используем
3. auto-assign: если config.projectKey == null && env есть → присваиваем и сохраняем
```

## API

`GET /api/admin/workflow-config` — в ответе появилось поле `projectKey`:
```json
{
  "configId": 1,
  "configName": "Default",
  "projectKey": "LB",
  "roles": [...],
  ...
}
```

## Обратная совместимость

- Колонка nullable — старые конфиги продолжают работать через `is_default=TRUE`
- Auto-assign при первом запуске — миграция без ручных действий
- Frontend gracefully handles `projectKey: null`

## Файлы

| Файл | Изменение |
|------|-----------|
| `V31__add_project_key_to_config.sql` | Новый: миграция |
| `ProjectConfigurationEntity.java` | +projectKey |
| `ProjectConfigurationRepository.java` | +findByProjectKey() |
| `WorkflowConfigService.java` | Резолв по project_key, auto-assign |
| `MappingAutoDetectService.java` | Резолв по project_key |
| `WorkflowConfigController.java` | Резолв по project_key, DTO |
| `WorkflowConfigResponse.java` | +projectKey |
| `workflowConfig.ts` | +projectKey в тип |
| `WorkflowConfigPage.tsx` | Project key в заголовке |
| `MappingAutoDetectServiceTest.java` | Mock JiraProperties |
| `WorkflowConfigControllerTest.java` | MockBean JiraProperties |
| `DataQualityServiceTest.java` | Обновлён конструктор |

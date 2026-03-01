# F48: Per-Project Workflow Configuration

**Версия:** 0.48.0
**Дата:** 2026-03-01

## Проблема

Тенант может иметь несколько Jira project keys (`project_keys = "PROJ1,PROJ2"`). В Jira у разных проектов могут быть **разные типы задач, статусы и workflow**. До F48 система хранила ОДНУ конфигурацию workflow на весь тенант. При первом синке второго проекта `autoDetect()` **удалял** маппинги первого проекта и заменял их новыми.

## Решение: Per-Project Storage + Merged Reading

- **Запись:** каждый project key получает свою `ProjectConfigurationEntity` с отдельным `config_id`. Auto-detect работает только с конфигом конкретного проекта — не трогает другие.
- **Чтение:** `WorkflowConfigService` загружает ВСЕ конфиги тенанта и мержит их в единый кэш (union by key, first-wins). 39+ сервисов-потребителей НЕ меняются.

## Изменения

### Backend

#### JiraConfigResolver
- Новый метод `getAllProjectKeys()` — возвращает все ключи из `tenant_jira_config.project_keys` или `.env`

#### JiraMetadataService
- Overload: `getIssueTypes(String projectKey)` — использует явный projectKey
- Overload: `getStatuses(String projectKey)` — аналогично
- Существующие no-arg методы делегируют в новые

#### MappingAutoDetectService
- `getOrCreateConfigIdForProject(String projectKey)` — находит/создаёт конфиг для проекта. Первый проект = `is_default=true`
- `isConfigEmptyForProject(String projectKey)` — проверяет конфиг конкретного проекта
- `autoDetectForProject(String projectKey)` — полный auto-detect только для этого проекта (delete+create его config_id)
- `registerUnknownTypeIfNeeded(String typeName, String projectKey)` — регистрирует в конфиге нужного проекта
- Существующий `autoDetect()` делегирует в `autoDetectForProject(resolver.getProjectKey())`

#### WorkflowConfigService
- `loadConfiguration()` загружает ВСЕ `ProjectConfigurationEntity` для тенанта, мержит через `putIfAbsent`:
  - Roles: union по code (first-wins)
  - Issue types: union по jiraTypeName.toLowerCase()
  - Statuses: union по `boardCategory:statusName`
  - Link types: union по jiraLinkTypeName.toLowerCase()
- `getAllConfigIds()` — для PublicConfigController
- `getConfigIdForProject(String projectKey)` — для write-операций контроллера

#### ProjectConfigurationRepository
- Новый метод `findAllByProjectKeyIn(List<String> projectKeys)`

#### SyncService
- `autoDetectIfNeeded(projectKey)`: per-project вместо global
- `saveOrUpdateIssue()`: `registerUnknownTypeIfNeeded(typeName, projectKey)`

#### PublicConfigController
- `getIssueTypeCategories()` и `getStatusStyles()` итерируют `getAllConfigIds()` для merged view

#### WorkflowConfigController
- `GET /api/admin/workflow-config/projects` — список проектных конфигов
- `@RequestParam(required = false) String projectKey` на всех GET/PUT endpoints
- Auto-detect принимает `projectKey`, вызывает `autoDetectForProject(key)`

### Frontend

#### workflowConfig.ts
- `ProjectConfigInfo` interface
- `getProjectConfigs()` API
- Все API-методы принимают optional `projectKey`

#### WorkflowConfigPage.tsx
- Project selector (tabs) при наличии >1 проекта
- Badge "NEW" для ненастроенных проектов
- Все save/load/detect передают `selectedProjectKey`

### Тесты

- `PerProjectWorkflowConfigTest` (6 тестов): isolation, unknown type registration, config empty, default assignment
- Обновлены `MappingAutoDetectServiceTest` и `WorkflowConfigServiceScoreTest` для совместимости

## Backward Compatibility

- Тенанты с 1 проектом: поведение идентично предыдущему
- API без `?projectKey`: работает как раньше (default config)
- 39+ сервисов-потребителей: НОЛЬ изменений (читают из merged кэша)
- Setup Wizard: работает как раньше для первого проекта

## Ключевые файлы

| Файл | Изменения |
|------|-----------|
| `JiraConfigResolver.java` | +getAllProjectKeys() |
| `JiraMetadataService.java` | +overloads с projectKey |
| `MappingAutoDetectService.java` | +per-project auto-detect |
| `WorkflowConfigService.java` | merged loading из всех конфигов |
| `SyncService.java` | per-project auto-detect при синке |
| `PublicConfigController.java` | merged view из всех configIds |
| `WorkflowConfigController.java` | +projects endpoint, +projectKey param |
| `workflowConfig.ts` | +ProjectConfigInfo, +projectKey params |
| `WorkflowConfigPage.tsx` | project selector UI |
| `PerProjectWorkflowConfigTest.java` | 6 тестов |

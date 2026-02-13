# F29: Workflow Configuration

## Статус: ✅ Done (2026-02-13)

## Описание

Перенос всей конфигурации workflow (роли, типы задач, статусы, связи) из хардкода/YAML в PostgreSQL с Admin API для управления.

## Что реализовано

### Фаза 1: DB Schema + Entities + Repositories
- **Flyway V26** — 6 новых таблиц:
  - `project_configurations` — конфигурация проекта (name, isDefault, statusScoreWeights JSONB)
  - `workflow_roles` — роли pipeline (code, displayName, color, sortOrder, isDefault)
  - `issue_type_mappings` — маппинг Jira-типов → EPIC/STORY/SUBTASK/IGNORE
  - `status_mappings` — маппинг статусов → категории (NEW/REQUIREMENTS/PLANNED/IN_PROGRESS/DONE)
  - `link_type_mappings` — маппинг типов связей → BLOCKS/RELATED/IGNORE
  - `tracker_metadata_cache` — кэш метаданных Jira
- Добавлены колонки `board_category`, `workflow_role` в `jira_issues`
- Seed data с дефолтной конфигурацией

### Фаза 2: WorkflowConfigService
- Единый центральный сервис, заменяющий StatusMappingService + JiraProperties.getRoleForSubtaskType()
- Загрузка конфигурации из БД при старте в ConcurrentHashMap
- Fallback: substring matching для неизвестных статусов/типов
- Cache invalidation через `clearCache()` при изменениях через Admin API

### Фаза 3: Big Bang Refactoring
- StatusMappingService → фасад-адаптер, делегирующий в WorkflowConfigService
- Все 20+ сервисов переведены на WorkflowConfigService
- Динамический pipeline из БД вместо хардкоженного SA→DEV→QA
- `board_category` заполняется при sync, используется в запросах вместо хардкоженных типов
- 547 тестов проходят

### Фаза 4: Admin API + Jira Metadata
- **WorkflowConfigController** (`/api/admin/workflow-config`):
  - GET/PUT config, roles, issue-types, statuses, link-types
  - POST /validate — валидация конфигурации
- **JiraMetadataController** (`/api/admin/jira-metadata`):
  - GET /issue-types, /statuses, /link-types — метаданные из Jira API
- **MappingValidationService** — проверка полноты и корректности конфигурации

## Enums

- `BoardCategory`: EPIC, STORY, SUBTASK, IGNORE
- `StatusCategory`: NEW, REQUIREMENTS, PLANNED, IN_PROGRESS, DONE
- `LinkCategory`: BLOCKS, RELATED, IGNORE

## API Endpoints

```
GET    /api/admin/workflow-config              — полная конфигурация
PUT    /api/admin/workflow-config              — обновить настройки проекта
GET    /api/admin/workflow-config/roles        — роли pipeline
PUT    /api/admin/workflow-config/roles        — обновить роли (batch)
GET    /api/admin/workflow-config/issue-types  — маппинг типов задач
PUT    /api/admin/workflow-config/issue-types  — обновить маппинг (batch)
GET    /api/admin/workflow-config/statuses     — маппинг статусов
PUT    /api/admin/workflow-config/statuses     — обновить маппинг (batch)
GET    /api/admin/workflow-config/link-types   — маппинг связей
PUT    /api/admin/workflow-config/link-types   — обновить маппинг (batch)
POST   /api/admin/workflow-config/validate     — валидация
GET    /api/admin/jira-metadata/issue-types    — типы из Jira
GET    /api/admin/jira-metadata/statuses       — статусы из Jira
GET    /api/admin/jira-metadata/link-types     — связи из Jira
```

## Файлы

### Новые
- `config/entity/` — 6 JPA entities (ProjectConfiguration, WorkflowRole, IssueTypeMapping, StatusMapping, LinkTypeMapping, TrackerMetadataCache)
- `config/repository/` — 6 Spring Data repositories
- `config/dto/` — 7 DTO records
- `config/service/WorkflowConfigService.java` — центральный сервис
- `config/service/MappingValidationService.java` — валидация
- `config/service/JiraMetadataService.java` — метаданные Jira
- `config/controller/WorkflowConfigController.java` — Admin API
- `config/controller/JiraMetadataController.java` — Jira metadata API
- `config/entity/BoardCategory.java`, `LinkCategory.java` — enums
- `db/migration/V26__workflow_configuration.sql` — миграция

### Модифицированные (основные)
- `sync/JiraIssueEntity.java` — добавлены boardCategory, workflowRole
- `sync/JiraIssueRepository.java` — запросы по board_category
- `sync/SyncService.java` — заполнение boardCategory/workflowRole при sync
- `planning/UnifiedPlanningService.java` — динамический pipeline
- `board/BoardService.java` — WorkflowConfigService вместо JiraProperties
- `quality/DataQualityService.java` — WorkflowConfigService
- `planning/AutoScoreCalculator.java` — score weights из конфигурации
- `status/StatusMappingService.java` — фасад к WorkflowConfigService

### Фаза 5: Frontend UI
- **WorkflowConfigPage** (`/board/workflow`) — страница с 4 табами:
  - **Roles** — код, название, цвет (color picker), порядок, default
  - **Issue Types** — Jira тип → BoardCategory, роль (для SUBTASK)
  - **Statuses** — Jira статус → StatusCategory, фильтр по issueCategory, scoreWeight
  - **Link Types** — Jira link type → LinkCategory
- Inline-редактирование в таблицах, Add/Delete строк, Save по табу
- Кнопка Validate — показывает ошибки и предупреждения
- Ссылка из Settings page
- Доступно только для ADMIN

### Frontend файлы
- `frontend/src/api/workflowConfig.ts` — API клиент с TypeScript интерфейсами
- `frontend/src/pages/WorkflowConfigPage.tsx` — страница конфигурации
- `frontend/src/pages/WorkflowConfigPage.css` — стили
- `frontend/src/App.tsx` — роут `/board/workflow`
- `frontend/src/pages/SettingsPage.tsx` — ссылка на Workflow Configuration

## Тесты

- WorkflowConfigControllerTest — 5 тестов (GET config, roles, issue-types; PUT roles; validate)
- MappingValidationServiceTest — 8 тестов (valid config, empty roles, missing categories, duplicate codes, etc.)
- Все 547+ существующих тестов проходят

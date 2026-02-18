# F38: Incremental Workflow Configuration

## Статус: ✅ Реализовано (2026-02-18)

## Проблема

При синке из Jira, если встречается неизвестный тип задачи (не зарегистрирован в `issue_type_mappings`), система использовала fallback-эвристики — угадывала категорию по подстрокам ("bug" → STORY, "epic" → EPIC) или ставила IGNORE. Пользователь не контролировал этот процесс.

## Решение

Инкрементальное управление типами задач:
1. При синке — автоматическая регистрация неизвестных типов с `board_category = NULL`
2. Задачи с NULL category сохраняются в `jira_issues`, но **не отображаются на доске**
3. В UI (Workflow Config → Issue Types) — unmapped типы с бейджем **NEW**
4. При выборе board_category — автоматический фетч статусов из Jira + авто-маппинг
5. Fallback-эвристики убраны из `categorizeIssueType()` — только явный маппинг

## Миграция

- **V39**: `ALTER TABLE issue_type_mappings ALTER COLUMN board_category DROP NOT NULL`

## Backend

### Изменения в WorkflowConfigService
- `categorizeIssueType()` — возвращает `null` для неизвестных типов (вместо IGNORE через fallback)
- `computeBoardCategory()` — обрабатывает null
- `categorize()` — возвращает NEW для unmapped типов
- `loadConfiguration()` — пропускает записи с `boardCategory = null` при загрузке кеша
- `getSubtaskRole()` — убран fallback по подстрокам

### Новые методы в MappingAutoDetectService
- `registerUnknownTypeIfNeeded(String jiraTypeName)` — регистрация с `REQUIRES_NEW` транзакцией, идемпотентный
- `detectStatusesForIssueType(String jiraTypeName, BoardCategory boardCategory)` — фетч статусов из Jira + авто-маппинг

### Новый endpoint
```
POST /api/admin/workflow-config/issue-types/{typeName}/detect-statuses
Body: { "boardCategory": "STORY" }
Response: { "typeName": "...", "boardCategory": "STORY", "statusesDetected": 5 }
```

### Хук в SyncService
В `saveOrUpdateIssue()`: если `computeBoardCategory()` вернул null, вызывает `registerUnknownTypeIfNeeded()`.

## Frontend

### WorkflowConfigPage — Issue Types таб
- Unmapped типы (boardCategory === null) отображаются первыми с жёлтым фоном и бейджем **NEW**
- Таб "Issue Types" показывает бейдж `(N new)` с количеством unmapped типов
- При выборе категории для unmapped типа — автоматический вызов `detectStatusesForType` API
- Toast с результатом: "Mapped X → STORY, detected 5 statuses"
- Dropdown для unmapped типов включает пустой вариант "-- unmapped --"

### API клиент
- `IssueTypeMappingDto.boardCategory` — nullable (`| null`)
- Новый метод `detectStatusesForType(typeName, boardCategory)`

## Тесты

### Backend
- `IncrementalWorkflowConfigTest` — registerUnknownTypeIfNeeded (идемпотентность, null handling), detectStatusesForIssueType (создание маппингов, skip existing, cache clear)

### Component Tests
- `ComponentTestBase.seedWorkflowConfig()` — создаёт default project config если не существует (для тестов с Hibernate ddl-auto вместо Flyway)

## Поведение на доске

Задачи с `board_category = NULL` **не показываются** на Board/Timeline — запросы типа `findByBoardCategoryAndTeamId("EPIC", teamId)` автоматически их отфильтровывают.

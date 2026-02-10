# BF9. Universal Workflow Configuration (Гибкая настройка маппинга)

## Обзор

Полностью конфигурируемый маппинг между внешним трекером и Lead Board через Admin UI. Вместо захардкоженных ролей (SA/DEV/QA), типов задач (Epic/Story/Sub-task), статусов и связей — всё настраивается через интерфейс администратора.

Конфигурация привязана к **проекту трекера** (в одном проекте свои workflows, типы задач, статусы). В одном проекте может быть несколько команд, в одной компании — несколько проектов.

При первом подключении система автоматически подтягивает метаданные через API трекера и запускает Onboarding Wizard, который помогает админу всё настроить.

### Multi-Tracker Architecture

Архитектура построена на **Tracker Provider** — абстракции, позволяющей подключать любые трекеры задач. MVP реализует только Jira, но интерфейсы, таблицы и UI не привязаны к конкретному трекеру.

**Поддерживаемые трекеры (план):**

| Трекер | Статус | API | Популярность |
|--------|--------|-----|-------------|
| **Jira Cloud** | MVP | REST API v3 | Enterprise, глобально |
| **Yandex Tracker** | Planned | REST API v2 | Россия/СНГ, крупный бизнес |
| **YouTrack** | Planned | REST API | Dev-команды, JetBrains ecosystem |
| **Linear** | Planned | GraphQL | Стартапы |
| **Azure DevOps** | Planned | REST API | Microsoft ecosystem |
| **GitHub Projects** | Idea | GraphQL | Open Source, небольшие команды |
| **Asana** | Idea | REST API | Non-tech teams |

---

## Проблема

Сейчас в коде **40+ мест** с захардкоженными ролями SA/DEV/QA, **15+ мест** с захардкоженными типами задач, **30+ мест** с именами статусов. Это делает систему непригодной для команд с другими ролями (Design, Security, DevOps, UX) и нестандартными Jira-воркфлоу.

### Что захардкожено сейчас

| Категория | Количество мест | Примеры |
|-----------|----------------|---------|
| Роли SA/DEV/QA | 40+ | `case "SA" →`, `processPhase("SA")`, `ROLES = ['SA', 'DEV', 'QA']` |
| Типы задач | 15+ | `"Epic"/"Эпик"`, `typeLower.contains("sub-task")`, SQL: `issueType IN ('Epic', 'Эпик')` |
| Имена статусов | 30+ | `"Done"`, `"Готово"`, `"Developing"`, `"В разработке"` |
| Pipeline order | 5+ | Фиксированный порядок SA → DEV → QA в планировании и симуляции |
| Link types | 2 | Только `"Blocks"`, остальные связи игнорируются |

### Затронутые файлы (основные)

**Backend:**
- `StatusMappingService.java` — определение ролей, категоризация статусов, фазы
- `UnifiedPlanningService.java` — планирование по ролям SA/DEV/QA
- `SimulationPlanner.java` — симуляция с фиксированным pipeline SA→DEV→QA
- `RoleLoadService.java` — алерты загрузки по ролям
- `EpicService.java` — `isEpic()`, валидация rough estimates по ролям
- `PokerSessionService.java` — роли голосующих
- `ForecastController.java` — агрегация по ролям
- `StoryInfo.java` — `determinePhase()`, `determineRole()`
- `AutoScoreService.java` — `EPIC_TYPES = List.of("Epic", "Эпик")`
- `JiraIssueRepository.java` — SQL с `'Epic', 'Эпик'`
- `SyncService.java` — обработка link types (только "Blocks")
- `StatusMappingConfig.java` — дефолтные статусы
- `PhaseMapping.java` — структура SA/DEV/QA

**Frontend:**
- `TeamMembersPage.tsx` — `ROLES = ['SA', 'DEV', 'QA']`
- `TimelinePage.tsx` — отображение ролей, цвета статусов
- `DemoBoard.tsx` — названия ролей

---

## Внутренняя модель Lead Board

### Концепции (абстракции)

Lead Board оперирует собственными концепциями, которые маппятся на Jira:

```
┌─────────────────────────────────────────────────────────────┐
│                    Lead Board Model                          │
│                                                              │
│  IssueCategory:  EPIC │ STORY │ SUBTASK │ IGNORE            │
│  StatusCategory: TODO │ IN_PROGRESS │ DONE                   │
│  WorkflowRole:   SA │ DEV │ QA │ Design │ Security │ ...    │
│  LinkCategory:   DEPENDENCY │ RELATED │ IGNORE               │
│                                                              │
│  Pipeline:  Role₁ → Role₂ → ... → Roleₙ  (по sort_order)   │
└──────────────────────────┬──────────────────────────────────┘
                           │ mapping
┌──────────────────────────▼──────────────────────────────────┐
│                    Jira Project                               │
│                                                              │
│  Issue Types:  Epic, Story, Sub-task, Bug, Аналитика, ...   │
│  Statuses:     New, In Progress, Done, В разработке, ...     │
│  Link Types:   Blocks, Relates, Duplicates, ...              │
│  Workflows:    Epic Workflow, Story Workflow, ...             │
└─────────────────────────────────────────────────────────────┘
```

### IssueCategory (фиксированные, структурные)

Три категории — определяют роль задачи в иерархии борда:

| Категория | Описание | Пример Jira-типов |
|-----------|----------|-------------------|
| **EPIC** | Верхний уровень — планирование, forecast, timeline | Epic, Эпик, Initiative |
| **STORY** | Средний уровень — рабочая единица внутри эпика | Story, Bug, Task, User Story |
| **SUBTASK** | Нижний уровень — конкретная работа конкретной роли | Sub-task, Подзадача, Аналитика, Тестирование |
| **IGNORE** | Не используется в борде | Documentation, Meta-task |

### StatusCategory (фиксированные, универсальные)

| Категория | Описание |
|-----------|----------|
| **TODO** | Работа не начата |
| **IN_PROGRESS** | В процессе выполнения |
| **DONE** | Завершено |

### WorkflowRole (полностью кастомизируемые)

Заменяет хардкод SA/DEV/QA. Каждый Jira-проект определяет свой набор ролей:

| Поле | Описание | Пример |
|------|----------|--------|
| code | Уникальный код | `QA` |
| displayName | Отображаемое имя | `Тестирование` |
| color | Цвет в UI | `#10B981` |
| sortOrder | Порядок в pipeline | `3` |
| isDefault | Роль по умолчанию (если subtask не матчится) | `true` (для DEV) |

**Примеры конфигураций:**

Классическая команда:
```
SA (1) → DEV (2) → QA (3)
```

Команда с дизайном:
```
UX (1) → SA (2) → DEV (3) → QA (4)
```

Инфраструктурная команда:
```
SA (1) → DEV (2) → DEVOPS (3) → QA (4)
```

Команда с security review:
```
SA (1) → DEV (2) → SECURITY (3) → QA (4)
```

### LinkCategory

| Категория | Описание | Как используется |
|-----------|----------|-----------------|
| **DEPENDENCY** | Блокирующая зависимость | Учитывается в планировании, сортировке, визуализации |
| **RELATED** | Информационная связь | Показывается в UI, не влияет на планирование |
| **IGNORE** | Не используется | Не импортируется |

---

## Tracker Provider (абстракция для мульти-трекера)

### Архитектура

```
┌──────────────────────────────────────────────────────────────┐
│                    Lead Board Core                            │
│                                                              │
│  WorkflowConfigService    UnifiedPlanningService             │
│  SyncService              SimulationService                  │
│          │                        │                          │
│          ▼                        ▼                          │
│  ┌─────────────────────────────────────────┐                │
│  │         TrackerProvider (interface)       │                │
│  │                                          │                │
│  │  + getProviderType(): TrackerType        │                │
│  │  + fetchIssueTypes(projectKey): List     │                │
│  │  + fetchStatuses(projectKey): List       │                │
│  │  + fetchLinkTypes(): List                │                │
│  │  + fetchIssues(query): List              │                │
│  │  + transitionIssue(key, status): void    │                │
│  │  + addWorklog(key, hours): void          │                │
│  │  + testConnection(projectKey): boolean   │                │
│  └──────────────┬───────────────────────────┘                │
│                 │                                            │
└─────────────────┼────────────────────────────────────────────┘
                  │
    ┌─────────────┼──────────────┬──────────────────┐
    ▼             ▼              ▼                  ▼
┌────────┐ ┌───────────┐ ┌───────────┐ ┌────────────────┐
│  Jira  │ │  Yandex   │ │  YouTrack │ │  Linear        │
│Provider│ │  Tracker  │ │  Provider │ │  Provider      │
│ (MVP)  │ │  Provider │ │           │ │                │
└────────┘ └───────────┘ └───────────┘ └────────────────┘
```

### TrackerProvider Interface

```java
public interface TrackerProvider {

    /** Тип трекера */
    TrackerType getType();  // JIRA, YANDEX_TRACKER, YOUTRACK, LINEAR, ...

    /** Проверить подключение к проекту */
    ConnectionTestResult testConnection(String projectKey, String accessToken);

    // ─── Metadata (для onboarding и настроек) ───

    /** Типы задач доступные в проекте */
    List<TrackerIssueType> fetchIssueTypes(String projectKey, String accessToken);

    /** Статусы (сгруппированные по workflow или типу задачи) */
    List<TrackerStatusGroup> fetchStatuses(String projectKey, String accessToken);

    /** Типы связей */
    List<TrackerLinkType> fetchLinkTypes(String accessToken);

    // ─── Sync (для синхронизации данных) ───

    /** Поиск задач (с пагинацией) */
    TrackerSearchResult searchIssues(String projectKey, TrackerSearchQuery query, String accessToken);

    /** Получить одну задачу */
    TrackerIssue getIssue(String issueKey, String accessToken);

    // ─── Actions (для симуляции и будущих фич) ───

    /** Сменить статус задачи */
    void transitionIssue(String issueKey, String targetStatus, String accessToken);

    /** Добавить worklog */
    void addWorklog(String issueKey, double hours, String comment, String accessToken);
}
```

### Unified DTOs (tracker-agnostic)

```java
/** Тип задачи из трекера */
public record TrackerIssueType(
    String id,              // ID в трекере
    String name,            // "Epic", "Эпик", "Задача"
    boolean isSubtask,      // Подзадача?
    String description      // Описание из трекера
) {}

/** Группа статусов (workflow) */
public record TrackerStatusGroup(
    String issueTypeName,   // К какому типу задачи относится
    List<TrackerStatus> statuses
) {}

/** Статус из трекера */
public record TrackerStatus(
    String id,
    String name,            // "In Progress", "В работе"
    String categoryHint     // Подсказка от трекера: "new", "indeterminate", "done" (если есть)
) {}

/** Тип связи из трекера */
public record TrackerLinkType(
    String id,
    String name,            // "Blocks"
    String inwardName,      // "is blocked by"
    String outwardName      // "blocks"
) {}

/** Задача из трекера (для sync) */
public record TrackerIssue(
    String key,             // "LB-123"
    String summary,
    String issueTypeName,
    String statusName,
    String parentKey,       // Для subtask/story
    String assigneeId,
    String assigneeName,
    String priority,
    // ... worklog, timetracking, links, etc.
    List<TrackerIssueLink> links,
    TrackerTimeTracking timeTracking
) {}
```

### Реализации провайдеров

#### JiraTrackerProvider (MVP)

Оборачивает существующий `JiraClient`:

```java
@Component
public class JiraTrackerProvider implements TrackerProvider {

    @Override
    public TrackerType getType() { return TrackerType.JIRA; }

    @Override
    public List<TrackerIssueType> fetchIssueTypes(String projectKey, String accessToken) {
        // GET /rest/api/3/project/{projectKey}
        // → маппинг JiraIssueType → TrackerIssueType
    }

    @Override
    public List<TrackerStatusGroup> fetchStatuses(String projectKey, String accessToken) {
        // GET /rest/api/3/project/{projectKey}/statuses
        // → маппинг в TrackerStatusGroup
    }

    @Override
    public List<TrackerLinkType> fetchLinkTypes(String accessToken) {
        // GET /rest/api/3/issueLinkType
        // → маппинг в TrackerLinkType
    }

    // ... остальные методы оборачивают JiraClient
}
```

#### YandexTrackerProvider (будущее)

```java
@Component
@ConditionalOnProperty("tracker.yandex.enabled")
public class YandexTrackerProvider implements TrackerProvider {

    @Override
    public TrackerType getType() { return TrackerType.YANDEX_TRACKER; }

    @Override
    public List<TrackerIssueType> fetchIssueTypes(String projectKey, String accessToken) {
        // GET https://api.tracker.yandex.net/v2/issuetypes
        // Yandex Tracker: "Задача", "Баг", "Эпик", "История"
    }

    @Override
    public List<TrackerStatusGroup> fetchStatuses(String projectKey, String accessToken) {
        // GET https://api.tracker.yandex.net/v2/statuses
        // Yandex Tracker: "Открыт", "В работе", "Закрыт"
    }

    // ...
}
```

#### YouTrackProvider (будущее)

```java
// GET https://{instance}.youtrack.cloud/api/admin/projects/{id}/issueTypes
// GET https://{instance}.youtrack.cloud/api/admin/customFieldSettings/bundles/state
// YouTrack: custom workflows, state machine
```

### TrackerProviderRegistry

```java
@Service
public class TrackerProviderRegistry {

    private final Map<TrackerType, TrackerProvider> providers;

    public TrackerProviderRegistry(List<TrackerProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(TrackerProvider::getType, Function.identity()));
    }

    public TrackerProvider getProvider(TrackerType type) {
        return Optional.ofNullable(providers.get(type))
            .orElseThrow(() -> new IllegalArgumentException("Unsupported tracker: " + type));
    }

    public TrackerProvider getProviderForProject(String projectKey) {
        ProjectConfigurationEntity config = projectConfigRepo.findByProjectKey(projectKey);
        return getProvider(config.getTrackerType());
    }

    public List<TrackerType> getAvailableProviders() {
        return List.copyOf(providers.keySet());
    }
}
```

### Сравнение API трекеров

| Возможность | Jira Cloud | Yandex Tracker | YouTrack | Linear |
|-------------|-----------|----------------|----------|--------|
| Issue Types | `/project/{key}` → issueTypes | `/v2/issuetypes` | `/api/admin/projects/{id}/issueTypes` | GraphQL `issueLabels` |
| Statuses | `/project/{key}/statuses` | `/v2/statuses` | `/api/admin/customFieldSettings/bundles/state` | GraphQL `workflowStates` |
| Link Types | `/issueLinkType` | `/v2/linktypes` (only "relates") | `/api/issueLinkTypes` | нет (parent-child only) |
| Search | `/search/jql` (JQL) | `/v2/issues/_search` (query language) | `/api/issues?query=` (search query) | GraphQL `issues(filter:)` |
| Worklog | `/issue/{key}/worklog` | `/v2/issues/{key}/worklog` | `/api/issues/{id}/timeTracking/workItems` | нет |
| Transitions | `/issue/{key}/transitions` | `PATCH /v2/issues/{key}` (status change) | `POST /api/commands` | GraphQL mutation |
| Auth | OAuth 2.0 (3LO) | OAuth 2.0 / IAM token | Bearer token / Hub OAuth | API key / OAuth |
| Subtask flag | `issuetype.subtask` | нет (по типу задачи) | нет (по link type) | нет (sub-issues) |
| Status hints | `statusCategory` (new/indeterminate/done) | нет | `isResolved` | `type` (started/completed/...) |

### Что это значит для BF9

1. **DB-схема — tracker-agnostic**: вместо `jira_*` полей используем `tracker_*` или нейтральные имена
2. **Onboarding Wizard** показывает UI одинаково для любого трекера — отличается только источник данных
3. **MVP = только JiraTrackerProvider** — остальные добавляются по одному, каждый как отдельная фича
4. **Автопредложение маппинга** учитывает особенности трекера (statusCategory в Jira, isResolved в YouTrack)

---

## Tracker-specific: Jira Cloud API

### Эндпоинты для получения метаданных (MVP)

При подключении проекта или по кнопке "Обновить" система вызывает через `JiraTrackerProvider`:

#### 1. Типы задач проекта

```
GET /rest/api/3/project/{projectKey}
→ fields: issueTypes[] { id, name, subtask, description }
```

Возвращает типы задач, доступные в конкретном проекте. Поле `subtask: true/false` помогает автоматически предложить категорию SUBTASK.

#### 2. Статусы по типам задач

```
GET /rest/api/3/project/{projectKey}/statuses
→ [{ id, name, untranslatedName, statuses: [{ id, name, statusCategory }] }]
```

Возвращает статусы, сгруппированные по типу задачи. Поле `statusCategory` из Jira (To Do / In Progress / Done) используется для автоматического предложения маппинга.

#### 3. Типы связей (глобальные)

```
GET /rest/api/3/issueLinkType
→ { issueLinkTypes: [{ id, name, inward, outward }] }
```

Типы связей глобальны для всего Jira (не per-project).

### Кэширование метаданных

Результаты API-вызовов кэшируются в `tracker_metadata_cache` для:
- Отображения в UI без повторных вызовов к трекеру
- Быстрого рендеринга страницы настроек
- Обновление по кнопке "Обновить из трекера" или автоматически при входе в настройки (если кэш > 24ч)

---

## DB Schema

### Таблицы конфигурации

```sql
-- Миграция: V__create_workflow_configuration.sql

-- ============================================
-- 1. Конфигурация проекта (tracker-agnostic)
-- ============================================

CREATE TABLE project_configurations (
    id              BIGSERIAL PRIMARY KEY,
    tracker_type    VARCHAR(50) NOT NULL DEFAULT 'JIRA',  -- JIRA, YANDEX_TRACKER, YOUTRACK, LINEAR
    project_key     VARCHAR(50) NOT NULL,                  -- Ключ проекта в трекере
    project_name    VARCHAR(300),                          -- Имя проекта
    tracker_base_url VARCHAR(500),                         -- https://your-domain.atlassian.net
    setup_completed  BOOLEAN NOT NULL DEFAULT FALSE,       -- wizard пройден?
    setup_completed_at TIMESTAMP,
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW(),
    UNIQUE(tracker_type, project_key)
);

-- ============================================
-- 2. Роли (замена хардкода SA/DEV/QA)
-- ============================================

CREATE TABLE workflow_roles (
    id                  BIGSERIAL PRIMARY KEY,
    project_config_id   BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    code                VARCHAR(50) NOT NULL,           -- "SA", "DEV", "QA", "DESIGN"
    display_name        VARCHAR(200) NOT NULL,          -- "Системный анализ"
    color               VARCHAR(7) NOT NULL DEFAULT '#6B7280',
    sort_order          INTEGER NOT NULL,               -- Pipeline order: 1, 2, 3...
    is_default          BOOLEAN NOT NULL DEFAULT FALSE, -- Subtask без маппинга → эта роль
    created_at          TIMESTAMP DEFAULT NOW(),
    UNIQUE(project_config_id, code)
);

-- ============================================
-- 3. Маппинг типов задач (tracker-agnostic naming)
-- ============================================

CREATE TABLE issue_type_mappings (
    id                      BIGSERIAL PRIMARY KEY,
    project_config_id       BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    source_type_id          VARCHAR(50),                -- ID типа в трекере
    source_type_name        VARCHAR(200) NOT NULL,      -- "Epic", "Sub-task", "Аналитика", "Задача"
    source_is_subtask       BOOLEAN,                    -- Подзадача? (если трекер поддерживает)
    board_category          VARCHAR(50) NOT NULL,       -- EPIC, STORY, SUBTASK, IGNORE
    workflow_role_id        BIGINT REFERENCES workflow_roles(id), -- Для SUBTASK: к какой роли
    created_at              TIMESTAMP DEFAULT NOW(),
    UNIQUE(project_config_id, source_type_name)
);

-- ============================================
-- 4. Маппинг статусов (tracker-agnostic naming)
-- ============================================

CREATE TABLE status_mappings (
    id                      BIGSERIAL PRIMARY KEY,
    project_config_id       BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    source_status_id        VARCHAR(50),                -- ID статуса в трекере
    source_status_name      VARCHAR(200) NOT NULL,      -- "In Progress", "В работе", "Открыт"
    issue_category          VARCHAR(50) NOT NULL,       -- EPIC, STORY, SUBTASK
    board_status_category   VARCHAR(50) NOT NULL,       -- TODO, IN_PROGRESS, DONE
    sort_order              INTEGER DEFAULT 0,          -- Порядок внутри категории
    created_at              TIMESTAMP DEFAULT NOW(),
    UNIQUE(project_config_id, source_status_name, issue_category)
);

-- ============================================
-- 5. Маппинг типов связей (tracker-agnostic naming)
-- ============================================

CREATE TABLE link_type_mappings (
    id                      BIGSERIAL PRIMARY KEY,
    project_config_id       BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    source_link_type_id     VARCHAR(50),                -- ID типа связи в трекере
    source_link_type_name   VARCHAR(200) NOT NULL,      -- "Blocks", "Relates"
    source_inward           VARCHAR(200),               -- "is blocked by"
    source_outward          VARCHAR(200),               -- "blocks"
    board_link_category     VARCHAR(50) NOT NULL,       -- DEPENDENCY, RELATED, IGNORE
    created_at              TIMESTAMP DEFAULT NOW(),
    UNIQUE(project_config_id, source_link_type_name)
);

-- ============================================
-- 6. Кэш метаданных трекера
-- ============================================

CREATE TABLE tracker_metadata_cache (
    id                  BIGSERIAL PRIMARY KEY,
    project_config_id   BIGINT NOT NULL REFERENCES project_configurations(id) ON DELETE CASCADE,
    metadata_type       VARCHAR(50) NOT NULL,           -- ISSUE_TYPES, STATUSES, LINK_TYPES
    data                JSONB NOT NULL,
    fetched_at          TIMESTAMP NOT NULL,
    UNIQUE(project_config_id, metadata_type)
);

-- ============================================
-- Индексы
-- ============================================

CREATE INDEX idx_workflow_roles_config ON workflow_roles(project_config_id, sort_order);
CREATE INDEX idx_issue_type_mappings_config ON issue_type_mappings(project_config_id);
CREATE INDEX idx_status_mappings_config ON status_mappings(project_config_id, issue_category);
CREATE INDEX idx_link_type_mappings_config ON link_type_mappings(project_config_id);
```

### Связь с существующими таблицами

```sql
-- Команда ссылается на конфигурацию проекта
ALTER TABLE teams ADD COLUMN project_config_id BIGINT REFERENCES project_configurations(id);

-- При первой миграции: создаём project_configuration для текущего JIRA_PROJECT_KEY
-- и привязываем все существующие команды к нему
```

---

## Onboarding Flow

### Когда запускается

1. **Первый запуск** — нет ни одного `project_configurations` с `setup_completed = true`
2. **Новый проект** — админ добавляет новый Jira Project Key
3. **Ручной запуск** — кнопка "Настроить проект" в Admin UI

### Детекция первичной настройки

При загрузке приложения фронтенд проверяет:

```
GET /api/setup/status
→ { setupRequired: true, projects: [{ key: "LB", setupCompleted: false }] }
```

Если `setupRequired = true` — показываем баннер/redirect на wizard.

### Wizard (пошаговый)

```
┌────────────────────────────────────────────────────────────────┐
│  Настройка проекта                                       1/5   │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ① Трекер  ② Типы задач  ③ Роли  ④ Статусы  ⑤ Связи         │
│  ━━━━━━━━  ──────────   ─────   ────────   ─────────         │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

#### Шаг 1: Выбор трекера и проекта

```
┌────────────────────────────────────────────────────────────────┐
│  Шаг 1: Подключение трекера                                   │
│                                                                │
│  Трекер:  (●) Jira Cloud    ( ) Yandex Tracker                │
│           ( ) YouTrack       ( ) Linear                        │
│                                                                │
│  Project Key: [ LB        ]  [Подключить]                     │
│                                                                │
│  ✅ Проект найден: "Lead Board" (LB)                           │
│  ✅ Загружено: 5 типов задач, 12 статусов, 4 типа связей      │
│                                                                │
│                                              [Далее →]         │
└────────────────────────────────────────────────────────────────┘
```

- Выбор трекера → показываем нужные поля авторизации
- Ввод project key → вызов TrackerProvider.fetchIssueTypes/fetchStatuses/fetchLinkTypes
- Если OAuth не настроен — показать ссылку на авторизацию
- Показать summary: сколько типов/статусов/связей найдено
- **MVP:** активен только Jira Cloud, остальные с пометкой "скоро"

#### Шаг 2: Маппинг типов задач

```
┌────────────────────────────────────────────────────────────────┐
│  Шаг 2: Типы задач                                            │
│                                                                │
│  Укажите роль каждого типа задачи в Lead Board:               │
│                                                                │
│  Тип в Jira          │ Категория Lead Board                   │
│  ─────────────────────┼───────────────────────                 │
│  Epic                 │ [▼ EPIC      ]                         │
│  Story                │ [▼ STORY     ]                         │
│  Bug                  │ [▼ STORY     ]                         │
│  Sub-task             │ [▼ SUBTASK   ]  → роль: [следующий шаг]│
│  Аналитика            │ [▼ SUBTASK   ]  → роль: [следующий шаг]│
│  Тестирование         │ [▼ SUBTASK   ]  → роль: [следующий шаг]│
│  Documentation        │ [▼ IGNORE    ]                         │
│                                                                │
│  ℹ️  Jira-поле subtask=true → автоматически предложено SUBTASK │
│  ℹ️  Нужен минимум 1 EPIC, 1 STORY и 1 SUBTASK               │
│                                                                │
│                                    [← Назад]  [Далее →]       │
└────────────────────────────────────────────────────────────────┘
```

**Автоматическое предложение:**
- Jira `subtask: true` → SUBTASK
- Имя содержит "epic"/"эпик" → EPIC
- Имя содержит "story"/"bug"/"task" → STORY
- Остальное → админ выбирает вручную

#### Шаг 3: Определение ролей и pipeline

```
┌────────────────────────────────────────────────────────────────┐
│  Шаг 3: Роли инженеров и pipeline                             │
│                                                                │
│  Определите роли и порядок работы (pipeline):                  │
│                                                                │
│  ┌─────────────────────────────────────────────────────┐      │
│  │  ☰ 1. SA — Системный анализ        🟦  [✏️] [🗑️]  │      │
│  │  ☰ 2. DEV — Разработка             🟩  [✏️] [🗑️]  │      │
│  │  ☰ 3. QA — Тестирование            🟨  [✏️] [🗑️]  │      │
│  └─────────────────────────────────────────────────────┘      │
│                     [+ Добавить роль]                          │
│                                                                │
│  ☰ — перетаскивание для изменения порядка в pipeline           │
│  ★ DEV — роль по умолчанию (subtask без привязки к роли)      │
│                                                                │
│  Привязка subtask-типов к ролям:                               │
│  ─────────────────────────────────                             │
│  Аналитика         → [▼ SA  ]                                 │
│  Sub-task           → [▼ DEV ] (по умолчанию)                 │
│  Тестирование       → [▼ QA  ]                                │
│                                                                │
│                                    [← Назад]  [Далее →]       │
└────────────────────────────────────────────────────────────────┘
```

**Автоматическое предложение ролей:**
- Если в типах задач есть "Аналитика"/"Analysis" → предложить роль SA
- Если есть "Тестирование"/"Testing"/"QA" → предложить роль QA
- Всегда создавать DEV как роль по умолчанию
- Админ может добавить/удалить/переименовать роли
- Drag & drop для порядка pipeline

#### Шаг 4: Маппинг статусов

```
┌────────────────────────────────────────────────────────────────┐
│  Шаг 4: Маппинг статусов                                     │
│                                                                │
│  [EPIC ▼] [STORY] [SUBTASK]  ← переключение между категориями │
│                                                                │
│  Статусы для EPIC:                                             │
│                                                                │
│  ┌─ TODO ─────────────────────────────────────────────┐       │
│  │  ✅ New                                              │       │
│  │  ✅ Requirements                                     │       │
│  │  ✅ Rough Estimate                                   │       │
│  │  ✅ Backlog                                          │       │
│  └────────────────────────────────────────────────────┘       │
│                                                                │
│  ┌─ IN PROGRESS ──────────────────────────────────────┐       │
│  │  ✅ Planned                                          │       │
│  │  ✅ Developing                                       │       │
│  │  ✅ E2E Testing                                      │       │
│  │  ✅ Acceptance                                       │       │
│  └────────────────────────────────────────────────────┘       │
│                                                                │
│  ┌─ DONE ─────────────────────────────────────────────┐       │
│  │  ✅ Done                                             │       │
│  │  ✅ Closed                                           │       │
│  └────────────────────────────────────────────────────┘       │
│                                                                │
│  ℹ️  Автоматически предложено по Jira statusCategory          │
│  ⚠️  Все статусы должны быть распределены                     │
│                                                                │
│                                    [← Назад]  [Далее →]       │
└────────────────────────────────────────────────────────────────┘
```

**Автоматическое предложение:**
- Jira `statusCategory: "new"` → TODO
- Jira `statusCategory: "indeterminate"` → IN_PROGRESS
- Jira `statusCategory: "done"` → DONE
- Drag & drop статусов между категориями

#### Шаг 5: Маппинг связей

```
┌────────────────────────────────────────────────────────────────┐
│  Шаг 5: Типы связей                                          │
│                                                                │
│  Тип в Jira            │ Как использовать                     │
│  ──────────────────────┼──────────────────────                │
│  Blocks                │ [▼ DEPENDENCY ]  (блокирует)         │
│  (blocks / is blocked) │                                      │
│                         │                                      │
│  Relates               │ [▼ RELATED    ]  (информационно)     │
│  (relates to)          │                                      │
│                         │                                      │
│  Duplicates            │ [▼ IGNORE     ]                      │
│  (duplicates / is dup) │                                      │
│                         │                                      │
│  Cloners               │ [▼ IGNORE     ]                      │
│  (clones / is cloned)  │                                      │
│                                                                │
│  ℹ️  DEPENDENCY — учитывается в планировании и сортировке     │
│  ℹ️  RELATED — показывается в UI, не влияет на планирование   │
│                                                                │
│                                    [← Назад]  [Готово ✓]      │
└────────────────────────────────────────────────────────────────┘
```

**Автоматическое предложение:**
- "Blocks" → DEPENDENCY
- "Relates" → RELATED
- Остальное → IGNORE

#### Финализация

```
┌────────────────────────────────────────────────────────────────┐
│  ✅ Настройка проекта LB завершена!                            │
│                                                                │
│  Сводка:                                                       │
│  • 3 типа задач настроено (1 Epic, 1 Story, 3 Subtask)       │
│  • 3 роли: SA → DEV → QA                                     │
│  • 12 статусов распределено по категориям                     │
│  • 1 тип связи как зависимость (Blocks)                       │
│                                                                │
│  Система готова к синхронизации.                               │
│                                                                │
│                              [Перейти на Board →]              │
└────────────────────────────────────────────────────────────────┘
```

---

## Admin UI: Страница настроек

После начальной настройки — постоянная страница `/settings/workflow` (доступ: Admin).

### Вкладки

```
┌─ Настройки проекта LB ──────────────────────────────────────┐
│                                                               │
│  [Типы задач]  [Роли и Pipeline]  [Статусы]  [Связи]        │
│  ━━━━━━━━━━━━  ────────────────  ─────────  ──────          │
│                                                               │
│  Последнее обновление из Jira: 10 фев 2026, 14:30           │
│  [🔄 Обновить из Jira]                                       │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐     │
│  │  ... содержимое вкладки ...                          │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                               │
│  [Сохранить]  [Отменить]                                     │
│                                                               │
│  ⚠️ Preview: 3 эпика сменят категорию TODO → IN_PROGRESS     │
│              2 стори потеряют привязку к роли                 │
└───────────────────────────────────────────────────────────────┘
```

### "Обновить из Jira"

При нажатии:
1. Вызываем Jira API → получаем актуальные метаданные
2. Сравниваем с текущим маппингом
3. Показываем diff:
   - `+2 новых типа задач (нужно настроить)`
   - `+1 новый статус (нужно распределить)`
   - `-1 статус удалён из Jira (маппинг будет удалён)`
4. Админ настраивает новые элементы
5. Сохраняет

### Preview при сохранении

Перед применением нового маппинга — показать что изменится:

```
⚠️  Изменения затронут существующие данные:
    • 5 эпиков: статус "Planned" сменит категорию TODO → IN_PROGRESS
    • 3 стори: тип "Bug" сменит категорию SUBTASK → STORY
    • Пересчёт forecast потребуется для 8 эпиков

    [Применить]  [Отменить]
```

---

## Архитектура Backend

### Новые классы

```
com.leadboard.config/
├── entity/
│   ├── ProjectConfigurationEntity     — Конфигурация проекта
│   ├── WorkflowRoleEntity             — Роль в pipeline
│   ├── IssueTypeMappingEntity         — Маппинг типов задач
│   ├── StatusMappingEntity            — Маппинг статусов (новая, заменяет старую)
│   ├── LinkTypeMappingEntity          — Маппинг связей
│   └── JiraMetadataCacheEntity        — Кэш метаданных Jira
├── repository/
│   ├── ProjectConfigurationRepository
│   ├── WorkflowRoleRepository
│   ├── IssueTypeMappingRepository
│   ├── StatusMappingRepository        — (новый, заменяет конфигурационный)
│   ├── LinkTypeMappingRepository
│   └── JiraMetadataCacheRepository
├── service/
│   ├── ProjectConfigService           — CRUD конфигурации проекта
│   ├── WorkflowConfigService          — Единый сервис: определение категорий, ролей, статусов
│   ├── JiraMetadataService            — Получение метаданных из Jira API
│   ├── MappingAutoDetectService       — Автоматическое предложение маппинга
│   ├── MappingValidationService       — Валидация конфигурации
│   └── MappingMigrationService        — Preview изменений при обновлении
├── controller/
│   ├── SetupController                — /api/setup/* (wizard, status)
│   └── WorkflowConfigController       — /api/config/* (CRUD маппингов)
└── dto/
    ├── SetupStatusDto
    ├── JiraMetadataDto
    ├── IssueTypeMappingDto
    ├── StatusMappingDto
    ├── WorkflowRoleDto
    ├── LinkTypeMappingDto
    └── MappingPreviewDto              — Preview изменений
```

### WorkflowConfigService (замена StatusMappingService)

Ключевой сервис — единая точка для определения категорий, ролей, статусов:

```java
@Service
public class WorkflowConfigService {

    /**
     * Определить категорию задачи (EPIC/STORY/SUBTASK) по типу задачи из трекера.
     * Читает из БД вместо substring matching.
     */
    public IssueCategory categorizeIssueType(String projectKey, String issueTypeName) {
        return issueTypeMappingRepository
            .findByProjectKeyAndTypeName(projectKey, issueTypeName)
            .map(m -> IssueCategory.valueOf(m.getBoardCategory()))
            .orElse(IssueCategory.STORY); // fallback
    }

    /**
     * Определить роль subtask (SA/DEV/QA/...) по типу задачи из трекера.
     * Читает из БД вместо хардкода.
     */
    public String determineRole(String projectKey, String issueTypeName) {
        return issueTypeMappingRepository
            .findByProjectKeyAndTypeName(projectKey, issueTypeName)
            .filter(m -> m.getWorkflowRole() != null)
            .map(m -> m.getWorkflowRole().getCode())
            .orElse(getDefaultRole(projectKey)); // fallback → роль с is_default=true
    }

    /**
     * Категоризировать статус (TODO/IN_PROGRESS/DONE).
     * Читает из БД вместо substring matching.
     */
    public StatusCategory categorizeStatus(String projectKey, String statusName, IssueCategory issueCategory) {
        return statusMappingRepository
            .findByProjectKeyAndStatusAndCategory(projectKey, statusName, issueCategory.name())
            .map(m -> StatusCategory.valueOf(m.getBoardStatusCategory()))
            .orElse(StatusCategory.TODO); // fallback
    }

    /**
     * Получить все роли проекта в порядке pipeline.
     */
    public List<WorkflowRoleEntity> getRolesInPipelineOrder(String projectKey) {
        return workflowRoleRepository
            .findByProjectKeyOrderBySortOrder(projectKey);
    }

    /**
     * Определить категорию связи (DEPENDENCY/RELATED/IGNORE).
     */
    public LinkCategory categorizeLinkType(String projectKey, String linkTypeName) {
        return linkTypeMappingRepository
            .findByProjectKeyAndLinkName(projectKey, linkTypeName)
            .map(m -> LinkCategory.valueOf(m.getBoardLinkCategory()))
            .orElse(LinkCategory.IGNORE); // fallback
    }

    /**
     * Проверить что конфигурация проекта завершена.
     */
    public boolean isSetupCompleted(String projectKey) { ... }
}
```

### Кэширование

Маппинги меняются редко → агрессивное кэширование:

```java
@Cacheable("issueTypeMapping")
public IssueCategory categorizeIssueType(String projectKey, String issueTypeName) { ... }

@CacheEvict(value = "issueTypeMapping", allEntries = true)
public void saveIssueTypeMappings(String projectKey, List<IssueTypeMappingDto> mappings) { ... }
```

Используем Spring Cache (Caffeine). Инвалидация при сохранении маппингов.

---

## API Endpoints

### Setup (Onboarding Wizard)

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| GET | `/api/setup/status` | Статус настройки (нужен ли wizard?) | All |
| POST | `/api/setup/fetch-metadata` | Подтянуть метаданные Jira `{projectKey}` | Admin |
| POST | `/api/setup/complete` | Завершить настройку `{projectKey}` | Admin |

### Configuration CRUD

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| GET | `/api/config/projects` | Список проектов с конфигурацией | Admin |
| GET | `/api/config/projects/{key}` | Полная конфигурация проекта | Admin |
| GET | `/api/config/projects/{key}/jira-metadata` | Кэшированные метаданные Jira | Admin |
| POST | `/api/config/projects/{key}/refresh-metadata` | Обновить метаданные из Jira | Admin |

### Issue Type Mappings

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| GET | `/api/config/projects/{key}/issue-types` | Текущий маппинг типов задач | Admin |
| PUT | `/api/config/projects/{key}/issue-types` | Сохранить маппинг `[{jiraTypeName, boardCategory, roleCode?}]` | Admin |

### Workflow Roles

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| GET | `/api/config/projects/{key}/roles` | Роли и pipeline order | Admin, TeamLead |
| PUT | `/api/config/projects/{key}/roles` | Сохранить роли `[{code, displayName, color, sortOrder, isDefault}]` | Admin |

### Status Mappings

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| GET | `/api/config/projects/{key}/statuses` | Маппинг статусов по категориям | Admin |
| PUT | `/api/config/projects/{key}/statuses` | Сохранить маппинг `[{jiraStatusName, issueCategory, boardStatusCategory}]` | Admin |

### Link Type Mappings

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| GET | `/api/config/projects/{key}/link-types` | Маппинг связей | Admin |
| PUT | `/api/config/projects/{key}/link-types` | Сохранить маппинг `[{jiraLinkTypeName, boardLinkCategory}]` | Admin |

### Preview

| Метод | Путь | Описание | Доступ |
|-------|------|----------|--------|
| POST | `/api/config/projects/{key}/preview` | Preview изменений перед сохранением | Admin |

---

## Архитектура Frontend

### Компоненты

```
frontend/src/
├── pages/
│   ├── SetupWizardPage.tsx              — Onboarding Wizard (полноэкранный)
│   └── WorkflowSettingsPage.tsx         — Страница настроек (для Admin)
├── components/
│   └── workflow-config/
│       ├── SetupBanner.tsx              — Баннер "Требуется настройка" на главной
│       ├── WizardStepProject.tsx        — Шаг 1: подключение проекта
│       ├── WizardStepIssueTypes.tsx     — Шаг 2: маппинг типов задач
│       ├── WizardStepRoles.tsx          — Шаг 3: роли и pipeline
│       ├── WizardStepStatuses.tsx       — Шаг 4: маппинг статусов
│       ├── WizardStepLinks.tsx          — Шаг 5: маппинг связей
│       ├── WizardSummary.tsx            — Финальная сводка
│       ├── IssueTypeMappingTable.tsx    — Таблица маппинга типов
│       ├── RolePipelineEditor.tsx       — Редактор ролей с drag & drop
│       ├── StatusMappingEditor.tsx      — Drag & drop статусов по категориям
│       ├── LinkTypeMappingTable.tsx     — Таблица маппинга связей
│       └── MappingPreviewDialog.tsx     — Диалог preview изменений
├── api/
│   └── workflowConfigApi.ts            — API клиент
└── hooks/
    └── useSetupStatus.ts               — Хук: нужен ли wizard?
```

### Роутинг

```
/setup                 — Onboarding Wizard (redirect если setup_completed = false)
/settings/workflow     — Настройки маппинга (Admin)
```

### SetupBanner

На главной странице (Board, Timeline, etc.) — если `setupRequired`:

```
┌────────────────────────────────────────────────────────────────┐
│  ⚠️ Требуется первичная настройка проекта.                     │
│     Настройте маппинг типов задач, статусов и ролей.          │
│                                        [Начать настройку →]    │
└────────────────────────────────────────────────────────────────┘
```

---

## Рефакторинг существующего кода

### Принцип: поэтапная замена

Каждый захардкоженный вызов заменяется на вызов `WorkflowConfigService`. Старый `StatusMappingService` оборачивается адаптером на время миграции.

### Ключевые замены

#### 1. Определение категории задачи

**Было:**
```java
// StatusMappingService.java
String typeLower = issueType.toLowerCase();
if (typeLower.contains("epic") || typeLower.contains("эпик")) → categorizeEpic()
if (typeLower.contains("sub-task") || typeLower.contains("подзадача")) → categorizeSubtask()
→ categorizeStory() // default

// JiraIssueRepository.java
"e.issueType IN ('Epic', 'Эпик')"
```

**Стало:**
```java
// WorkflowConfigService
IssueCategory category = workflowConfigService.categorizeIssueType(projectKey, issueTypeName);

// JiraIssueRepository — по полю board_category из join или предрассчитанному значению
// Вариант: добавить поле board_category в jira_issues, заполнять при sync
```

#### 2. Определение роли subtask

**Было:**
```java
// StoryInfo.java — hardcoded substring matching
if (typeLower.contains("analysis") || typeLower.contains("анализ")) return "SA";
if (typeLower.contains("test") || typeLower.contains("qa")) return "QA";
return "DEV"; // default
```

**Стало:**
```java
String role = workflowConfigService.determineRole(projectKey, subtaskIssueTypeName);
// → "SA", "DEV", "QA", "DESIGN", "SECURITY", ...
```

#### 3. Pipeline order в планировании

**Было:**
```java
// UnifiedPlanningService.java
case "SA" -> saHours = saHours.add(...)
case "QA" -> qaHours = qaHours.add(...)
default -> devHours = devHours.add(...)

// SimulationPlanner.java
processPhase(phases.sa(), "SA", ...);
processPhase(phases.dev(), "DEV", ...);
processPhase(phases.qa(), "QA", ...);
```

**Стало:**
```java
// Динамический pipeline из конфигурации
List<WorkflowRoleEntity> roles = workflowConfigService.getRolesInPipelineOrder(projectKey);

// Аккумуляция часов по ролям
Map<String, BigDecimal> hoursByRole = new HashMap<>();
for (var role : roles) {
    hoursByRole.put(role.getCode(), BigDecimal.ZERO);
}

// В switch заменяем на lookup
String roleCode = workflowConfigService.determineRole(projectKey, subtask.getIssueType());
hoursByRole.merge(roleCode, hours, BigDecimal::add);

// SimulationPlanner — итерация по динамическому pipeline
for (var role : roles) {
    processPhase(subtasksByRole.get(role.getCode()), role.getCode(), ...);
}
```

#### 4. Категоризация статусов

**Было:**
```java
// StatusMappingConfig.defaults() — хардкод списков
// StatusMappingService — substring fallback
```

**Стало:**
```java
StatusCategory category = workflowConfigService.categorizeStatus(
    projectKey, statusName, issueCategory
);
```

#### 5. Обработка связей

**Было:**
```java
// SyncService.java
boolean isBlocksLink = "Blocks".equalsIgnoreCase(linkType);
```

**Стало:**
```java
LinkCategory linkCategory = workflowConfigService.categorizeLinkType(projectKey, linkTypeName);
if (linkCategory == LinkCategory.DEPENDENCY) {
    // добавить в blocks/isBlockedBy
}
if (linkCategory == LinkCategory.RELATED) {
    // добавить в relatedTo (новое поле)
}
```

#### 6. Frontend: роли

**Было:**
```typescript
const ROLES = ['SA', 'DEV', 'QA'] as const;
```

**Стало:**
```typescript
// Загружаем из API
const { data: roles } = useQuery(['workflow-roles', projectKey],
    () => workflowConfigApi.getRoles(projectKey)
);
// roles = [{ code: 'SA', displayName: 'Анализ', color: '#3B82F6' }, ...]
```

### Добавление board_category в jira_issues

Для оптимизации SQL-запросов (замена `issueType IN ('Epic', 'Эпик')`):

```sql
ALTER TABLE jira_issues ADD COLUMN board_category VARCHAR(50);
-- EPIC, STORY, SUBTASK, IGNORE

ALTER TABLE jira_issues ADD COLUMN workflow_role VARCHAR(50);
-- SA, DEV, QA, DESIGN, ... (для subtasks)
```

Заполняется при sync на основе маппинга из `WorkflowConfigService`. SQL-запросы используют `board_category = 'EPIC'` вместо хардкода типов.

---

## Миграция существующих данных

### Стратегия: defaults → DB

При миграции текущие хардкоженные значения переносятся в БД как конфигурация по умолчанию. Все тесты должны пройти без изменений.

### Миграция Flyway

```sql
-- V__seed_default_workflow_config.sql

-- 1. Создаём конфигурацию для текущего проекта (из env JIRA_PROJECT_KEY)
-- ПРИМЕЧАНИЕ: project key подставляется через application.yml / runtime
INSERT INTO project_configurations (jira_project_key, jira_project_name, setup_completed)
VALUES ('${JIRA_PROJECT_KEY}', 'Default Project', true);

-- 2. Роли по умолчанию (SA, DEV, QA)
INSERT INTO workflow_roles (project_config_id, code, display_name, color, sort_order, is_default)
VALUES
    (1, 'SA',  'Системный анализ', '#3B82F6', 1, false),
    (1, 'DEV', 'Разработка',       '#10B981', 2, true),
    (1, 'QA',  'Тестирование',     '#F59E0B', 3, false);

-- 3. Маппинг типов задач (текущие захардкоженные)
INSERT INTO issue_type_mappings (project_config_id, jira_issue_type_name, board_category, workflow_role_id)
VALUES
    (1, 'Epic',          'EPIC',    NULL),
    (1, 'Эпик',          'EPIC',    NULL),
    (1, 'Story',         'STORY',   NULL),
    (1, 'Bug',           'STORY',   NULL),
    (1, 'Task',          'STORY',   NULL),
    (1, 'Sub-task',      'SUBTASK', (SELECT id FROM workflow_roles WHERE code='DEV' AND project_config_id=1)),
    (1, 'Подзадача',     'SUBTASK', (SELECT id FROM workflow_roles WHERE code='DEV' AND project_config_id=1)),
    (1, 'Аналитика',     'SUBTASK', (SELECT id FROM workflow_roles WHERE code='SA'  AND project_config_id=1)),
    (1, 'Analysis',      'SUBTASK', (SELECT id FROM workflow_roles WHERE code='SA'  AND project_config_id=1)),
    (1, 'Тестирование',  'SUBTASK', (SELECT id FROM workflow_roles WHERE code='QA'  AND project_config_id=1)),
    (1, 'Testing',       'SUBTASK', (SELECT id FROM workflow_roles WHERE code='QA'  AND project_config_id=1));

-- 4. Маппинг статусов (из текущего StatusMappingConfig.defaults())
-- Epic statuses
INSERT INTO status_mappings (project_config_id, jira_status_name, issue_category, board_status_category, sort_order)
VALUES
    (1, 'New',            'EPIC', 'TODO', 1),
    (1, 'Requirements',   'EPIC', 'TODO', 2),
    (1, 'Rough Estimate', 'EPIC', 'TODO', 3),
    (1, 'Backlog',        'EPIC', 'TODO', 4),
    (1, 'Новый',          'EPIC', 'TODO', 5),
    (1, 'Требования',     'EPIC', 'TODO', 6),
    (1, 'Planned',        'EPIC', 'IN_PROGRESS', 1),
    (1, 'Developing',     'EPIC', 'IN_PROGRESS', 2),
    (1, 'E2E Testing',    'EPIC', 'IN_PROGRESS', 3),
    (1, 'Acceptance',     'EPIC', 'IN_PROGRESS', 4),
    (1, 'Done',           'EPIC', 'DONE', 1),
    (1, 'Closed',         'EPIC', 'DONE', 2);
    -- ... аналогично для STORY и SUBTASK

-- 5. Маппинг связей
INSERT INTO link_type_mappings (project_config_id, jira_link_type_name, jira_inward, jira_outward, board_link_category)
VALUES
    (1, 'Blocks', 'is blocked by', 'blocks', 'DEPENDENCY');

-- 6. Заполнить board_category и workflow_role в jira_issues
UPDATE jira_issues SET board_category = 'EPIC'
    WHERE LOWER(issue_type) IN ('epic', 'эпик');
UPDATE jira_issues SET board_category = 'SUBTASK'
    WHERE LOWER(issue_type) IN ('sub-task', 'подзадача', 'аналитика', 'analysis', 'тестирование', 'testing');
UPDATE jira_issues SET board_category = 'STORY'
    WHERE board_category IS NULL;

UPDATE jira_issues SET workflow_role = 'SA'
    WHERE LOWER(issue_type) IN ('аналитика', 'analysis', 'analytics');
UPDATE jira_issues SET workflow_role = 'QA'
    WHERE LOWER(issue_type) IN ('тестирование', 'testing', 'qa', 'bug', 'баг');
UPDATE jira_issues SET workflow_role = 'DEV'
    WHERE board_category = 'SUBTASK' AND workflow_role IS NULL;
```

---

## Валидация конфигурации

### Правила

| Правило | Уровень | Описание |
|---------|---------|----------|
| Минимум 1 EPIC | ERROR | Должен быть хотя бы один тип задачи → EPIC |
| Минимум 1 STORY | ERROR | Должен быть хотя бы один тип → STORY |
| Минимум 1 SUBTASK | ERROR | Должен быть хотя бы один тип → SUBTASK |
| Минимум 1 роль | ERROR | Должна быть хотя бы одна роль |
| Роль по умолчанию | ERROR | Ровно одна роль с `is_default = true` |
| Все статусы распределены | WARNING | Каждый статус из Jira должен быть в TODO/IN_PROGRESS/DONE |
| DONE статус есть | ERROR | Для каждой категории должен быть хотя бы один DONE статус |
| Уникальные sort_order ролей | ERROR | Порядок pipeline не должен иметь дубликатов |
| Subtask привязан к роли | WARNING | Каждый SUBTASK-тип должен иметь привязку к роли |

### Ответ API валидации

```json
{
  "valid": false,
  "errors": [
    { "code": "NO_DONE_STATUS", "message": "Для STORY не определён статус DONE", "field": "statuses" }
  ],
  "warnings": [
    { "code": "UNMAPPED_STATUS", "message": "Статус 'Archived' не распределён", "field": "statuses" }
  ]
}
```

---

## Безопасность

- **Доступ к настройкам:** только Admin (RBAC F27)
- **Wizard:** только Admin
- **Чтение ролей/маппингов:** Admin + Team Lead (для отображения в UI)
- **Tracker API calls:** используют OAuth-токен / API-ключ текущего пользователя
- **Audit log:** все изменения конфигурации логируются

---

## План реализации (поэтапно)

### Этап 1: DB Schema + Migration + WorkflowConfigService

**Цель:** перенести хардкод в БД, не ломая существующий функционал.

1. Flyway-миграция: создание таблиц (`project_configurations`, `workflow_roles`, `issue_type_mappings`, `status_mappings`, `link_type_mappings`, `jira_metadata_cache`)
2. Flyway-миграция: seed дефолтных значений (текущие хардкоженные → БД)
3. Flyway-миграция: `ALTER TABLE jira_issues ADD board_category, workflow_role` + заполнение
4. JPA entities для всех новых таблиц
5. Repositories
6. `WorkflowConfigService` — чтение маппингов из БД
7. Адаптер: `StatusMappingService` делегирует в `WorkflowConfigService`
8. **Все существующие тесты проходят**

### Этап 2: TrackerProvider + Jira Metadata API

1. `TrackerProvider` interface + `TrackerProviderRegistry`
2. `JiraTrackerProvider` — реализация для Jira (обёртка над JiraClient)
3. Unified DTOs: `TrackerIssueType`, `TrackerStatus`, `TrackerLinkType`
4. `TrackerMetadataCacheEntity` — кэширование ответов
5. `MappingAutoDetectService` — автоматическое предложение маппинга
6. API endpoints: `fetch-metadata`, `refresh-metadata`
7. Тесты

### Этап 3: Рефакторинг Backend (замена хардкода)

1. `StatusMappingService` → `WorkflowConfigService` (полная замена)
2. `StoryInfo.determinePhase/determineRole()` → `WorkflowConfigService`
3. `UnifiedPlanningService` → динамический pipeline по ролям
4. `SimulationPlanner` → динамический pipeline
5. `RoleLoadService` → динамические роли
6. `EpicService` → `categorizeIssueType()`
7. `PokerSessionService` → динамические роли
8. `ForecastController` → динамические роли
9. `JiraIssueRepository` → `board_category` вместо хардкода типов
10. `SyncService` → `categorizeLinkType()` + заполнение `board_category`/`workflow_role`
11. Удаление `StatusMappingConfig.defaults()`, `PhaseMapping` record
12. **Все тесты проходят**

### Этап 4: Admin API

1. `SetupController` — `/api/setup/status`, `/api/setup/complete`
2. `WorkflowConfigController` — CRUD для всех маппингов
3. `MappingValidationService` — валидация конфигурации
4. `MappingMigrationService` — preview изменений
5. API тесты

### Этап 5: Onboarding Wizard (Frontend)

1. `useSetupStatus` hook
2. `SetupBanner` — баннер на главной
3. `SetupWizardPage` — полноэкранный wizard
4. Шаги wizard: Project → Issue Types → Roles → Statuses → Links → Summary
5. `workflowConfigApi.ts` — API клиент
6. Роутинг: `/setup`

### Этап 6: Admin Settings Page (Frontend)

1. `WorkflowSettingsPage` — вкладки: Типы, Роли, Статусы, Связи
2. `RolePipelineEditor` — drag & drop ролей
3. `StatusMappingEditor` — drag & drop статусов по категориям
4. `MappingPreviewDialog` — preview перед сохранением
5. "Обновить из трекера" — refresh + diff
6. Рефакторинг фронтенда: `ROLES` → из API, цвета ролей → из конфигурации

### Этап 7: Рефакторинг Frontend (удаление хардкода)

1. `TeamMembersPage` — роли из API вместо `['SA', 'DEV', 'QA']`
2. `TimelinePage` — динамические роли и цвета
3. `DemoBoard` — использовать конфигурацию
4. Все компоненты, использующие роли — рефакторинг
5. E2E проверка

---

## Зависимости

| Зависимость | Тип | Описание |
|-------------|-----|----------|
| F27 RBAC | Требуется | Доступ к настройкам только для Admin |
| F4 OAuth | Требуется | Jira API вызовы через OAuth-токен |
| F17 Status Mapping | Заменяется | Текущая конфигурация → мигрирует в новую систему |

## Будущие расширения

### Новые TrackerProvider

Каждый новый трекер — отдельная фича, использующая готовый `TrackerProvider` interface:

| Трекер | Сложность | Особенности |
|--------|-----------|-------------|
| **Yandex Tracker** | Средняя | REST API v2, OAuth 2.0 / IAM, нет statusCategory hint, русские названия статусов по умолчанию |
| **YouTrack** | Средняя | REST API, Hub OAuth, кастомные workflows (state machine), поле `isResolved` как hint |
| **Linear** | Низкая | GraphQL, простая модель (нет subtask issue types — sub-issues через parent), workflow states с type hint |
| **Azure DevOps** | Высокая | REST API, Work Item Types, сложная модель (Area Paths, Iterations), PAT auth |
| **GitHub Projects** | Низкая | GraphQL, простая модель, нет worklogs, ограниченные link types |
| **Asana** | Средняя | REST API, секции вместо статусов, custom fields для workflow |

### Другие расширения

| Расширение | Когда |
|------------|-------|
| Workflow Templates (Scrum, Kanban, SAFe) | После MVP |
| Поддержка нескольких проектов одновременно | После MVP |
| Маппинг custom fields (team, estimate, priority) | Отдельная фича |
| Автоматический re-detect при изменениях в трекере | После стабилизации |
| Import/Export конфигурации (JSON) | После стабилизации |

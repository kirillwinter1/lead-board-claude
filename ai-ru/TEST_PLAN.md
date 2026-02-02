# Test Plan — Lead Board

Поэтапный план покрытия тестами: Unit → Component → Integration.

---

## Фаза 1: Unit тесты (2-3 дня)

Изолированные тесты с моками. Быстрые, запускаются на каждый коммит.

### P0 — Критично (блокеры релиза)

| Сервис | LOC | Тестов | Сценарии |
|--------|-----|--------|----------|
| ~~SyncService~~ | 400 | ✅ 13 | Готово |
| **BoardService** | 621 | 0 → 15 | См. ниже |
| **AutoScoreService** | 150 | 0 → 8 | Обёртка над Calculator |

#### BoardService — план тестов (15 шт)

```
BoardServiceTest
├── getBoard()
│   ├── shouldReturnEpicsForTeam
│   ├── shouldAggregateStoriesUnderEpics
│   ├── shouldAggregateSubtasksUnderStories
│   ├── shouldCalculateEpicProgress
│   ├── shouldCalculateStoryProgress
│   ├── shouldCalculateRoleProgress (SA/DEV/QA)
│   ├── shouldExcludeDoneEpics
│   ├── shouldIncludeEpicsInTodoStatus
│   ├── shouldReturnEmptyWhenNoEpics
│   └── shouldFilterByTeamId
├── Alerts
│   ├── shouldIncludeDataQualityAlerts
│   ├── shouldIncludeNoTeamAlert
│   └── shouldIncludeNoDueDateAlert
└── Sorting
    ├── shouldSortEpicsByAutoScore
    └── shouldSortStoriesByAutoScore
```

#### AutoScoreService — план тестов (8 шт)

```
AutoScoreServiceTest
├── recalculateAll()
│   ├── shouldRecalculateAllEpics
│   ├── shouldSkipDoneEpics
│   └── shouldUpdateAutoScoreCalculatedAt
├── recalculateForTeam()
│   ├── shouldRecalculateOnlyTeamEpics
│   └── shouldReturnUpdatedCount
└── calculateForEpic()
    ├── shouldDelegateToCalculator
    ├── shouldSaveResult
    └── shouldHandleNullFields
```

### P1 — Важно (функциональность)

| Сервис | LOC | Тестов | Сценарии |
|--------|-----|--------|----------|
| **TeamSyncService** | 200 | 0 → 10 | Синхронизация команд из Jira |
| **PokerSessionService** | 300 | 0 → 12 | WebSocket сессии, голосование |
| **EpicService** | 100 | 0 → 6 | CRUD rough estimates |

#### TeamSyncService — план тестов (10 шт)

```
TeamSyncServiceTest
├── syncTeams()
│   ├── shouldCreateNewTeams
│   ├── shouldUpdateExistingTeams
│   ├── shouldNotDeleteTeamsWithIssues
│   ├── shouldExtractTeamValuesFromJira
│   └── shouldHandleEmptyResponse
├── getStatus()
│   ├── shouldReturnLastSyncTime
│   └── shouldReturnTeamCount
└── Edge cases
    ├── shouldHandleJiraApiError
    ├── shouldHandleDuplicateNames
    └── shouldHandleNullTeamField
```

#### PokerSessionService — план тестов (12 шт)

```
PokerSessionServiceTest
├── createSession()
│   ├── shouldCreateNewSession
│   ├── shouldGenerateUniqueCode
│   └── shouldSetInitialState
├── joinSession()
│   ├── shouldAddParticipant
│   ├── shouldRejectInvalidCode
│   └── shouldAllowRejoin
├── vote()
│   ├── shouldRecordVote
│   ├── shouldUpdateOnRevote
│   └── shouldRejectAfterReveal
├── revealVotes()
│   ├── shouldRevealAllVotes
│   └── shouldCalculateAverage
└── closeSession()
    └── shouldMarkAsClosed
```

### P2 — Желательно (вспомогательные)

| Сервис | LOC | Тестов | Сценарии |
|--------|-----|--------|----------|
| WipSnapshotService | 100 | 0 → 4 | Снэпшоты WIP |
| ForecastAccuracyService | 150 | 0 → 6 | Расчёт точности |
| OAuthService | 150 | 0 → 5 | Токены (мокать внешний API) |

---

## Фаза 2: Component тесты (3-4 дня)

Тестирование связки Controller + Service + Repository с H2 in-memory БД. Используют `@SpringBootTest` с профилем `test`.

### Настройка H2

**application-test.yml:**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false  # H2 создаст схему через JPA
```

**Зависимость в build.gradle.kts:**
```kotlin
testImplementation("com.h2database:h2:2.2.224")
```

### Базовый класс

```java
// src/test/java/com/leadboard/component/ComponentTestBase.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
abstract class ComponentTestBase {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JiraIssueRepository issueRepository;

    @Autowired
    protected TeamRepository teamRepository;

    @BeforeEach
    void cleanUp() {
        issueRepository.deleteAll();
    }

    protected TeamEntity createTeam(String name) {
        TeamEntity team = new TeamEntity();
        team.setName(name);
        team.setJiraTeamValue(name);
        team.setActive(true);
        return teamRepository.save(team);
    }

    protected JiraIssueEntity createEpic(String key, String summary, Long teamId) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setIssueId("id-" + key);
        epic.setSummary(summary);
        epic.setIssueType("Epic");
        epic.setStatus("Новое");
        epic.setTeamId(teamId);
        epic.setProjectKey("LB");
        return issueRepository.save(epic);
    }
}
```

### Структура

```
src/test/java/com/leadboard/component/
├── ComponentTestBase.java
├── BoardComponentTest.java
├── SyncComponentTest.java
├── TeamComponentTest.java
├── ForecastComponentTest.java
└── PokerComponentTest.java
```

### P0 — Критичные эндпоинты

| Controller | Эндпоинты | Тесты |
|------------|-----------|-------|
| **BoardController** | GET /api/board | 8 |
| **SyncController** | GET/POST /api/sync/* | 5 |
| **ForecastController** | GET /api/forecast/* | 6 |

#### BoardComponentTest — план (8 шт)

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BoardComponentTest extends ComponentTestBase {

    @Test void getBoard_returns200WithEpics()
    @Test void getBoard_returns200EmptyWhenNoData()
    @Test void getBoard_returns400WhenNoTeamId()
    @Test void getBoard_filtersCorrectly()
    @Test void getBoard_includesAlerts()
    @Test void getBoard_calculatesProgress()
    @Test void getBoard_sortsbyAutoScore()
    @Test void getBoard_aggregatesHierarchy()
}
```

### P1 — Важные эндпоинты

| Controller | Эндпоинты | Тесты |
|------------|-----------|-------|
| **TeamController** | CRUD /api/teams/* | 10 |
| **PokerController** | WebSocket /api/poker/* | 8 |
| **MetricsController** | GET /api/metrics/* | 6 |

---

## Фаза 3: Integration тесты (4-5 дней)

Полный стек с реальной PostgreSQL (Testcontainers). Проверяют:
- PostgreSQL-специфичные функции (JSONB, arrays)
- FK constraints (регрессия бага SyncService)
- Индексы и производительность
- Flyway миграции

### Настройка Testcontainers

```java
// src/test/java/com/leadboard/integration/IntegrationTestBase.java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("leadboard_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

### P0 — Критичные интеграции

| Тест | Что проверяет | Сценарии |
|------|---------------|----------|
| **SyncIntegrationTest** | Jira → DB → AutoScore | 6 |
| **BoardIntegrationTest** | DB → Aggregation → API | 5 |
| **ForecastIntegrationTest** | Planning pipeline | 5 |

#### SyncIntegrationTest — план (6 шт)

```
SyncIntegrationTest extends IntegrationTestBase
├── shouldSaveNewIssuesToDatabase
├── shouldUpdateExistingIssues
├── shouldCreateChangelogOnStatusChange (регрессия FK bug!)
├── shouldMapTeamFieldToTeamId
├── shouldPreserveLocalFields (rough estimates)
└── shouldHandleTransactionRollback
```

#### BoardIntegrationTest — план (5 шт)

```
BoardIntegrationTest extends IntegrationTestBase
├── shouldAggregateRealHierarchy
├── shouldCalculateProgressFromSubtasks
├── shouldFilterByTeamCorrectly
├── shouldSortByAutoScoreFromDb
└── shouldIncludeAlertsFromQualityRules
```

### P1 — Важные интеграции

| Тест | Что проверяет | Сценарии |
|------|---------------|----------|
| **TeamIntegrationTest** | CRUD + Members + Config | 8 |
| **MetricsIntegrationTest** | DSR, Throughput, Accuracy | 6 |
| **PokerIntegrationTest** | WebSocket + Persistence | 5 |

### P2 — E2E сценарии

| Тест | Сценарий | Шаги |
|------|----------|------|
| **FullSyncE2E** | Полный цикл синхронизации | Sync → Board → Forecast |
| **PlanningE2E** | Планирование команды | Config → Forecast → Timeline |
| **PokerE2E** | Сессия покера | Create → Join → Vote → Reveal |

---

## Сравнение типов тестов

| Характеристика | Unit | Component (H2) | Integration (Testcontainers) |
|----------------|------|----------------|------------------------------|
| **БД** | Моки | H2 in-memory | PostgreSQL в Docker |
| **Скорость** | ~5 сек | ~30 сек | ~2 мин |
| **Flyway** | Нет | Нет | Да |
| **FK constraints** | Нет | Да (базовые) | Да (полные) |
| **JSONB** | Нет | Нет | Да |
| **Транзакции** | Нет | Да | Да |
| **Когда запускать** | Каждый коммит | PR | Nightly / Release |

---

## Зависимости для Testcontainers

Добавить в `build.gradle.kts`:

```kotlin
dependencies {
    // Existing test dependencies...

    // Testcontainers
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
}
```

---

## Приоритеты и сроки

| Фаза | Приоритет | Тестов | Срок | Результат |
|------|-----------|--------|------|-----------|
| **Unit P0** | 🔴 | ~36 | 1 день | BoardService, AutoScoreService |
| **Unit P1** | 🟡 | ~28 | 1-2 дня | TeamSync, Poker, Epic |
| **Unit P2** | 🟢 | ~15 | 1 день | Вспомогательные |
| **Component P0** | 🔴 | ~19 | 1-2 дня | Board, Sync, Forecast |
| **Component P1** | 🟡 | ~24 | 2 дня | Team, Poker, Metrics |
| **Integration P0** | 🔴 | ~16 | 2 дня | Sync, Board, Forecast |
| **Integration P1** | 🟡 | ~19 | 2-3 дня | Team, Metrics, Poker |
| **E2E** | 🟢 | ~3 | 1 день | Full scenarios |

**Итого:** ~160 новых тестов за 10-14 дней

---

## Метрики покрытия

### Текущее состояние

```
Unit:        429 тестов (29 файлов)    +139 новых
Component:    32 теста (5 классов)     +32 новых
Integration:  35 тестов (6 классов)   +35 новых
E2E:           6 тестов (3 класса)    +6 новых
─────────────────────────────
Total:       502 теста
Coverage:    ~73% (estimated)
```

### Целевое состояние

```
Unit:        ~370 тестов (+80)
Component:   ~43 тестов (+43)
Integration: ~35 тестов (+35)
E2E:         ~3 тестов (+3)
─────────────────────────────
Total:       ~450 тестов
Coverage:    ~70% (target)
```

---

## Чеклист выполнения

### Фаза 1: Unit

- [x] SyncService (13 тестов) ✅
- [x] BoardService (18 тестов) ✅
- [x] AutoScoreService (13 тестов) ✅
- [x] TeamSyncService (11 тестов) ✅
- [x] PokerSessionService (14 тестов) ✅
- [x] EpicService (12 тестов) ✅
- [x] WipSnapshotService (10 тестов) ✅
- [x] ForecastAccuracyService (6 тестов) ✅
- [x] OAuthService (10 тестов) ✅

### Фаза 2: Component

- [x] BoardComponentTest (8 тестов) ✅
- [x] SyncComponentTest (4 тестов) ✅
- [x] ForecastComponentTest (5 тестов) ✅
- [x] TeamComponentTest (9 тестов) ✅
- [ ] PokerComponentTest (8 тестов) - WebSocket, отложено
- [x] MetricsComponentTest (6 тестов) ✅

### Фаза 3: Integration

- [x] Настройка Testcontainers ✅
- [x] SyncIntegrationTest (6 тестов) ✅
- [x] BoardIntegrationTest (5 тестов) ✅
- [x] ForecastIntegrationTest (5 тестов) ✅
- [x] TeamIntegrationTest (8 тестов) ✅
- [x] MetricsIntegrationTest (6 тестов) ✅
- [x] PokerIntegrationTest (5 тестов) ✅

### Фаза 4: E2E

- [x] FullSyncE2E (2 теста) ✅
- [x] PlanningE2E (2 теста) ✅
- [x] PokerE2E (2 теста) ✅

---

## История изменений

| Дата | Изменение |
|------|-----------|
| 2026-02-01 | Создание плана |
| 2026-02-01 | SyncServiceTest готов (13 тестов) |
| 2026-02-01 | BoardServiceTest готов (18 тестов) |
| 2026-02-01 | AutoScoreServiceTest готов (13 тестов) |
| 2026-02-01 | TeamSyncServiceTest готов (11 тестов) |
| 2026-02-01 | PokerSessionServiceTest готов (14 тестов) |
| 2026-02-01 | EpicServiceTest готов (12 тестов) |
| 2026-02-01 | Исправлен PlannedEpic (добавлены rough estimate поля) |
| 2026-02-01 | WipSnapshotServiceTest готов (10 тестов) |
| 2026-02-01 | ForecastAccuracyServiceTest готов (6 тестов) |
| 2026-02-01 | OAuthServiceTest готов (10 тестов) |
| 2026-02-01 | **Фаза 1: Unit тесты завершена** |
| 2026-02-01 | BoardComponentTest готов (8 тестов) |
| 2026-02-01 | SyncComponentTest готов (4 тестов) |
| 2026-02-01 | ForecastComponentTest готов (5 тестов) |
| 2026-02-01 | TeamComponentTest готов (9 тестов) |
| 2026-02-01 | MetricsComponentTest готов (6 тестов) |
| 2026-02-01 | **Фаза 2: Component тесты завершена (32 теста)** |
| 2026-02-01 | SyncIntegrationTest готов (6 тестов) |
| 2026-02-01 | BoardIntegrationTest готов (5 тестов) |
| 2026-02-01 | ForecastIntegrationTest готов (5 тестов) |
| 2026-02-01 | **Фаза 3 P0: Integration тесты завершены (16 тестов)** |
| 2026-02-02 | TeamIntegrationTest готов (8 тестов) |
| 2026-02-02 | MetricsIntegrationTest готов (6 тестов) |
| 2026-02-02 | PokerIntegrationTest готов (5 тестов) |
| 2026-02-02 | **Фаза 3: Integration тесты полностью завершены (35 тестов)** |
| 2026-02-02 | FullSyncE2ETest готов (2 теста) |
| 2026-02-02 | PlanningE2ETest готов (2 теста) |
| 2026-02-02 | PokerE2ETest готов (2 теста) |
| 2026-02-02 | **Фаза 4: E2E тесты завершены (6 тестов)** |
| 2026-02-02 | **TEST_PLAN.md ЗАВЕРШЁН: 502 теста** |

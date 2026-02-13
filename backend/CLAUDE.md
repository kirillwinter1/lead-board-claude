# Backend — Lead Board

## Технологии
- Java 21, Spring Boot 3, PostgreSQL, Flyway
- Gradle (Kotlin DSL)

## Запуск
```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/backend
./gradlew bootRun    # запуск на порту 8080
./gradlew test       # запуск тестов
./gradlew build      # сборка
```

## Пакетная структура (`com.leadboard`)

| Пакет | Назначение |
|-------|-----------|
| `auth` | OAuth 2.0 Atlassian, пользователи, токены |
| `board` | BoardService — агрегация данных доски |
| `calendar` | Производственный календарь РФ (WorkCalendarService) |
| `config` | **WorkflowConfigService** (центральный сервис конфигурации workflow), JiraProperties, WebConfig, Admin API |
| `controller` | BoardController, HealthController |
| `epic` | Rough estimates |
| `forecast` | Снэпшоты прогнозов |
| `jira` | JiraClient — REST API клиент |
| `metrics` | Командные метрики (throughput, DSR, forecast accuracy) |
| `planning` | **Ядро**: UnifiedPlanningService, AutoScore, StoryForecast |
| `poker` | Planning Poker + WebSocket |
| `quality` | Data Quality — 17 правил проверки |
| `status` | StatusMappingService — фасад к WorkflowConfigService (legacy) |
| `sync` | SyncService — инкрементальная синхронизация из Jira |
| `team` | CRUD команд/участников, planning config |

## Ключевые паттерны

- **Layered**: Controller → Service → Repository → Entity
- **JSONB config**: `teams.planning_config` хранит конфиг планирования (грейды, WIP, risk buffer)
- **Workflow Configuration (F29)**: Роли, типы задач, статусы, связи хранятся в БД (таблицы `workflow_roles`, `issue_type_mappings`, `status_mappings`, `link_type_mappings`). Единая точка доступа — `WorkflowConfigService`. **ЗАПРЕЩЕНО хардкодить SA/DEV/QA, Epic/Story/Sub-task, конкретные статусы в коде.** Используй методы WorkflowConfigService: `isEpic()`, `isStory()`, `getSubtaskRole()`, `getRolesInPipelineOrder()`, `categorize()`, `isDone()` и т.д.
- **Sync**: Инкрементальная (by `updated >=`), cursor-based pagination, upsert
- **Calendar**: Внешний API xmlcalendar.ru с кэшированием в БД

## Миграции БД (Flyway)
Расположены в `src/main/resources/db/migration/` (V1-V27).

## Тесты
- JUnit5 + Mockito
- Ключевые тесты: AutoScoreCalculator (30+), ForecastService (18), DataQuality (25+), StatusMapping (30+)
- Запуск: `./gradlew test`

## Конфигурация
- `src/main/resources/application.yml` — основной конфиг
- `backend/.env` — секреты (Jira, OAuth, DB). НЕ коммитить!

## Важные бизнес-правила
- Оценки и time logging **только в Subtask** (Story/Epic estimate игнорируется)
- Иерархия: EPIC → STORY → SUBTASK (категории из `issue_type_mappings`, НЕ хардкод)
- Pipeline планирования: динамический из `workflow_roles` (дефолт SA → DEV → QA)
- **НЕ ХАРДКОДИТЬ роли, типы задач, статусы** — всегда через `WorkflowConfigService`
- 1 человеко-день = 8 часов
- Коэффициенты грейдов: Senior 0.8, Middle 1.0, Junior 1.5

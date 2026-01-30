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
| `config` | JiraProperties, WebConfig |
| `controller` | BoardController, HealthController |
| `epic` | Rough estimates |
| `forecast` | Снэпшоты прогнозов |
| `jira` | JiraClient — REST API клиент |
| `metrics` | Командные метрики (throughput, DSR, forecast accuracy) |
| `planning` | **Ядро**: UnifiedPlanningService, AutoScore, StoryForecast |
| `poker` | Planning Poker + WebSocket |
| `quality` | Data Quality — 17 правил проверки |
| `status` | StatusMappingService — маппинг статусов Jira |
| `sync` | SyncService — инкрементальная синхронизация из Jira |
| `team` | CRUD команд/участников, planning config |

## Ключевые паттерны

- **Layered**: Controller → Service → Repository → Entity
- **JSONB config**: `teams.planning_config` хранит конфиг планирования (грейды, WIP, risk buffer)
- **Status Mapping**: Все статусы конфигурируемы через `application.yml` + team override
- **Sync**: Инкрементальная (by `updated >=`), cursor-based pagination, upsert
- **Calendar**: Внешний API xmlcalendar.ru с кэшированием в БД

## Миграции БД (Flyway)
Расположены в `src/main/resources/db/migration/` (V1-V20+).

## Тесты
- JUnit5 + Mockito
- Ключевые тесты: AutoScoreCalculator (30+), ForecastService (18), DataQuality (25+), StatusMapping (30+)
- Запуск: `./gradlew test`

## Конфигурация
- `src/main/resources/application.yml` — основной конфиг
- `backend/.env` — секреты (Jira, OAuth, DB). НЕ коммитить!

## Важные бизнес-правила
- Оценки и time logging **только в Subtask** (Story/Epic estimate игнорируется)
- Иерархия: Epic → Story → Subtask (Аналитика/Разработка/Тестирование)
- Pipeline планирования: SA → DEV → QA (строгая последовательность)
- 1 человеко-день = 8 часов
- Коэффициенты грейдов: Senior 0.8, Middle 1.0, Junior 1.5

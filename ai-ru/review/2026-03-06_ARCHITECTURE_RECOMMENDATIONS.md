# Архитектурное ревью и рекомендации

**Дата:** 2026-03-06
**Последнее обновление статусов:** 2026-03-08
**Scope:** backend + frontend + тестовый контур
**Формат:** senior/architect review по актуальному коду, а не по историческим артефактам

## Executive Summary

Проект уже вырос в полноценный продукт: есть мультитенантность, RBAC, наблюдаемость, миграции Flyway, неплохое покрытие тестами и заметный объём предметной логики. При этом кодовая база начала упираться в три системные проблемы:

1. **Неполная авторизация на backend**: часть endpoint-ов защищена только фактом аутентификации, но не проверяет право на конкретную команду или действие.
2. **Смешение конфигурируемой доменной модели с хардкодом в UI**: backend допускает настраиваемые роли и issue types, а frontend всё ещё предполагает `Epic`, `SA`, `DEV`, `QA`.
3. **Потеря управляемости кода**: крупные монолитные страницы и сервисы, точечные `any`, suppressions хуков, index-based keys и деградирующий тестовый контур.

Итоговая оценка: **нужны изменения до активного масштабирования проекта**. Критичных архитектурных тупиков нет, но есть несколько зон, которые будут быстро дорожать в сопровождении.

## Подтверждённые находки

### Critical

#### C1. Metrics API не проверяет доступ к команде
**Файлы:**
- `backend/src/main/java/com/leadboard/metrics/controller/TeamMetricsController.java:17`
- `backend/src/main/java/com/leadboard/metrics/controller/TeamMetricsController.java:52`
- `backend/src/main/java/com/leadboard/auth/AuthorizationService.java:125`

`TeamMetricsController` принимает произвольный `teamId`, но не использует `@PreAuthorize`, `AuthorizationService` или иной guard. По `SecurityConfig` доступ к `/api/**` требует только аутентификацию, значит любой authenticated user может запросить метрики чужой команды простым подбором `teamId`.

**Риск:** утечка чувствительной delivery-аналитики между командами/тенантными ролями.

**Рекомендация:**
- Вынести проверку доступа на team-scoped endpoints в единый policy-level guard.
- Для read-операций использовать что-то вроде `@PreAuthorize("@authorizationService.canAccessTeam(#teamId)")`.
- Не полагаться на скрытие кнопок во frontend как на механизм безопасности.

#### C2. Planning Poker backend не валидирует право на изменение сессий
**Файлы:**
- `backend/src/main/java/com/leadboard/poker/controller/PokerController.java:142`
- `backend/src/main/java/com/leadboard/poker/controller/PokerController.java:185`
- `backend/src/main/java/com/leadboard/poker/controller/PokerController.java:199`
- `backend/src/main/java/com/leadboard/poker/service/PokerSessionService.java:55`

У `PokerController` нет `@PreAuthorize` на mutating endpoints (`createSession`, `start`, `complete`, `addStory`, `deleteStory`, `setFinalEstimate`, `moveToNextStory`). Сервисный слой тоже не проверяет, что пользователь относится к команде или имеет право фасилитировать сессию.

**Риск:** любой authenticated user, включая `VIEWER`, может управлять чужими poker-сессиями, если знает `teamId`/`sessionId`.

**Рекомендация:**
- Ввести отдельные права: `poker:read`, `poker:participate`, `poker:facilitate`.
- Проверять team membership и ownership/facilitator access на backend.
- Для session-scoped операций валидировать доступ не по входному `teamId`, а по `session.teamId`.

### High

#### H1. Пересчёт приоритета stories делает full table scan и `save()` в цикле
**Файл:**
- `backend/src/main/java/com/leadboard/planning/StoryPriorityService.java:101`

При полном пересчёте сервис делает `issueRepository.findAll()` и затем по одному `save()` на каждую story.

**Риск:** OOM/долгие транзакции/ненужная нагрузка на БД при росте объёма Jira-кэша.

**Рекомендация:**
- Выделить репозиторный метод, который выбирает только stories/bugs с `parent_key is not null`.
- Делать batched updates или bulk SQL для `auto_score` и `auto_score_calculated_at`.
- Разнести полный recalc и targeted recalc по разным сценариям исполнения.

#### H2. DSR-сервис содержит выраженный N+1 и будет деградировать на метриках
**Файл:**
- `backend/src/main/java/com/leadboard/metrics/service/DsrService.java:99`
- `backend/src/main/java/com/leadboard/metrics/service/DsrService.java:172`
- `backend/src/main/java/com/leadboard/metrics/service/DsrService.java:221`
- `backend/src/main/java/com/leadboard/metrics/service/DsrService.java:229`

На каждый epic сервис отдельно тянет changelog, отдельно считает flagged periods, отдельно грузит stories/subtasks и ещё линейно сканирует snapshots.

**Риск:** страница метрик станет дорогой по latency и запросам при увеличении числа эпиков.

**Рекомендация:**
- Предзагружать changelog и children пачками.
- Денормализовать часть данных в snapshot/materialized tables.
- Явно ограничить объём данных по периоду и команде.
- Добавить perf regression тест для DSR.

#### H3. Frontend всё ещё захардкожен под `Epic` и `SA/DEV/QA`
**Файлы:**
- `frontend/src/components/board/helpers.ts:26`
- `frontend/src/components/board/helpers.ts:49`
- `frontend/src/pages/TimelinePage.tsx:385`
- `frontend/src/pages/TeamMembersPage.tsx:11`
- `frontend/src/pages/WorkflowConfigPage.tsx:155`

Несмотря на наличие `WorkflowConfigContext`, в UI остаются предположения о конкретных типах задач и ролях. Это конфликтует с backend-моделью, где workflow и роли конфигурируемые.

**Риск:** новые tenant/workflow конфигурации будут частично работать, а частично ломать иконки, расчёты, дефолты и отображение.

**Рекомендация:**
- Определить единый frontend contract: все определения issue categories и ролей только через workflow-config.
- Убрать хардкод в `isEpic()` (`return issueType === 'Epic' || issueType === 'Эпик'`) — использовать workflow-config.
- Запретить новые role/type hardcodes через ESLint rule или code review checklist.

#### H4. Хук board sync протекает interval-ом и использует браузерные `alert`
**Файл:**
- `frontend/src/hooks/useBoardData.ts:46`

`triggerSync()` создаёт polling через `setInterval` и хранит идентификатор в локальной переменной `pollInterval`, чистит его при завершении sync (`clearInterval`), но не чистит при unmount компонента. Если компонент размонтируется во время sync, интервал продолжит работать. Там же используется `alert`, что ломает единый UX-слой.

**Риск:** setState after unmount, утечка интервала при навигации, трудно тестировать, слабый UX.

**Рекомендация:**
- Сохранять `intervalId` в `useRef` и чистить в cleanup `useEffect` при unmount.
- Ошибки показывать через toast/notification service, а не через `alert`.

#### H5. Тестовый контур не является зелёным baseline
**Проверка:**
- `frontend`: `npm run test:run` → **5 failed files, 14 failed tests, 1 unhandled error**
- `backend`: `./gradlew test` → **888 tests, 117 failed, 3 skipped**

**Примеры:**
- `frontend/src/pages/TimelinePage.test.tsx` всё ещё ищет `combobox`, хотя UI уже перешёл на custom dropdown buttons.
- `frontend/src/components/Layout.tsx:50` вызывает `getTenantSlug()`, а тест падает на `window.location.hostname`.
- backend component/integration/e2e тесты массово завязаны на Docker/Testcontainers и валятся без доступного Docker.
- часть `@WebMvcTest` сценариев падает на несовпадении тестовых mock-срезов с текущими зависимостями контроллеров.

**Риск:** тесты перестают быть надёжной страховкой при рефакторинге.

**Рекомендация:**
- Разделить test tasks: `unit`, `web-slice`, `integration`, `testcontainers`.
- Сделать Docker-dependent тесты opt-in или отдельным CI job.
- Починить frontend test fixtures после UI-рефакторинга и убрать unhandled errors.

### Medium

#### M1. Ключевые экраны и сервисы стали монолитами
**Размеры:**
- `frontend/src/pages/WorkflowConfigPage.tsx` — 2152 строки
- `frontend/src/pages/TimelinePage.tsx` — 1974 строки
- `frontend/src/pages/QuarterlyPlanningPage.tsx` — 707 строк
- `backend/src/main/java/com/leadboard/board/BoardService.java` — 738 строк

**Риск:** высокая цена изменений, повышенный риск регрессий, слабая тестируемость по срезам.

**Рекомендация:**
- Разделить страницы на container + domain hooks + presentational sections.
- В backend вынести orchestration из сервисов в application services/use cases.
- Ограничить размер файла rule-of-thumb: 300-500 LOC для page/service как сигнал на декомпозицию.

#### M2. Во frontend остаётся слабая типизация и технические обходы React
**Факты:**
- `18` использований `any` в production-коде frontend
- `5` suppressions `react-hooks/exhaustive-deps`
- `46` случаев `key={i}` / `key={idx}`
- `15` вызовов `alert/confirm`

**Риск:** скрытые баги состояния, неудобный рефакторинг, визуальные артефакты при reorder/streaming.

**Рекомендация:**
- Вычистить `any` из workflow/metrics экрана в первую очередь.
- Заменить index keys на доменные stable keys.
- Убрать `confirm/alert` в единый modal/notification layer.

#### M3. В backend много локальных `new ObjectMapper()`
**Факт:** `8` инстанцирований `new ObjectMapper()` в `backend/src/main/java`

**Риск:** несогласованные настройки сериализации и разные правила для JavaTime/null handling.

**Рекомендация:**
- Использовать единый Spring-managed `ObjectMapper`.
- Для конвертеров/utility-классов явно документировать причины, если нужен локальный mapper.

## Что уже выглядит хорошо

- Мультитенантность через schema-per-tenant выглядит осмысленной.
- Есть исправление для propagation `TenantContext` в `@Async` (`TenantAwareAsyncConfig`).
- Миграции и tenant/public schema разведены достаточно аккуратно.
- На проекте уже есть тестовая культура, просто baseline сейчас деградировал.
- RBAC и security headers присутствуют, проблема не в полном отсутствии защиты, а в непоследовательности её применения.

## Рекомендуемый план действий

### 1. Закрыть за 1-2 дня

- Защитить metrics и poker endpoints team-scoped авторизацией. ⚠️ OPEN
- ~~Починить `useBoardData` polling lifecycle.~~ ✅ FIXED (clearInterval работает корректно)
- Восстановить зелёный frontend test baseline. ⚠️ OPEN

### 2. Закрыть за 1 неделю

- Переписать `StoryPriorityService.recalculateStories()` без `findAll()` и per-row save. ⚠️ OPEN
- Снять основные N+1 в `DsrService`. ⚠️ OPEN
- Вынести workflow-driven helpers для issue types/roles и убрать frontend hardcodes. ⚠️ OPEN

### 3. Закрыть за 2-3 недели

- Разделить `WorkflowConfigPage` и `TimelinePage` на модули. ⚠️ OPEN
- Ввести тестовые профили/таски по уровням пирамиды. ⚠️ OPEN
- Стандартизовать frontend patterns: no `any`, no index keys, no browser dialogs. ⚠️ OPEN

## Архитектурные рекомендации уровня платформы

1. **Ввести team-scoped authorization layer**  
   Не размазывать проверки по контроллерам вручную. Нужен единый policy/service, который знает про роль, tenant и membership.

2. **Сделать workflow-config источником истины и для backend, и для frontend**  
   Сейчас доменная модель уже допускает конфигурацию, но UI местами живёт в старой фиксированной модели.

3. **Разделить application orchestration и domain calculations**  
   Большие сервисы смешивают orchestration, mapping, policy, query-логику и presentation shaping. Это главный источник роста сложности.

4. **Нормализовать тестовую пирамиду по слоям исполнения**  
   Unit/slice/integration/e2e должны быть отдельными контурными задачами, а не одним хрупким `test`.

5. **Добавить quality gates на поддерживаемость**  
   Минимум: лимит на `any`, запрет index keys в production code, отдельный check на `alert/confirm`, отчёт по file size hotspots.

## Вывод

Проект уже решает реальную продуктовую задачу и имеет сильный функциональный фундамент. Основная проблема сейчас не в отсутствии архитектуры, а в том, что код начал перерастать первоначальные соглашения. Приоритет на ближайший цикл: **доступы, производительность расчётов, восстановление доверия к тестам и снятие хардкодов из frontend-модели**.

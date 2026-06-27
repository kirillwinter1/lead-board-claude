# Launch Plan — Lead Board

**Цель:** подготовить борду к реальному запуску как multi-tenant SaaS.

**Последнее обновление:** 2026-06-27 (статусы пере-сверены с кодом)

> **Сверка 2026-06-27.** Прежний план (от 2026-02-23) устарел на ~4 месяца: половина «Planned» уже реализована (L7/L9/L10/L11), а часть «P0-багов» (tenant-aware scheduled jobs, BUG-94/95/76/77) исправлена. Единственный подтверждённый по коду остаточный блокер — **RBAC enforcement (L6)**. Статусы ниже отражают фактическое состояние кода.

---

## Обзор

| # | Задача | Приоритет | Статус | Зависимости |
|---|--------|-----------|--------|-------------|
| L1 | [Мультитенантность](#l1-мультитенантность) | **P0** | ✅ Done (F44) | — |
| L2 | [QA: дотестировать оставшиеся экраны](#l2-qa-дотестировать-оставшиеся-экраны) | **P0** | ✅ Done (QA_STATUS 34/34) | — |
| L3 | [Фикс выявленных багов](#l3-фикс-выявленных-багов) | **P0** | 🚧 Re-triage (см. ниже) | L2 |
| L4 | [Security hardening](#l4-security-hardening) | **P0** | ✅ Done (F46) | L1 |
| L5 | [Sync стабильность](#l5-sync-стабильность) | **P1** | ✅ QA done, bugs fixed | — |
| L6 | [RBAC enforcement + тест ролей](#l6-rbac--тест-от-разных-ролей) | **P1** | 🔴 Остаток — enforcement (РЕАЛЬНЫЙ блокер) | L1 |
| L7 | [Setup Wizard — полный флоу](#l7-setup-wizard--полный-флоу) | **P1** | ✅ Done (F33) | L1 |
| L8 | [Backup стратегия + rollback plan](#l8-backup-стратегия--rollback-plan) | **P1** | ❌ Не сделано | L1 |
| L9 | [Нагрузочное тестирование](#l9-нагрузочное-тестирование) | **P1** | ✅ Done (F50, k6) | L1, L3 |
| L10 | [DB оптимизация + Jira rate limits](#l10-db-оптимизация--jira-rate-limits) | **P1** | ✅ Done (F50/F51/F46) | L1 |
| L11 | [Мониторинг и алертинг](#l11-мониторинг-и-алертинг) | **P1** | ✅ Done (F49 + F60) | — |
| L12 | [Graceful degradation](#l12-graceful-degradation) | **P2** | ❌ Не сделано | — |
| L13 | [Ручное тестирование по всем фичам](#l13-ручное-тестирование-по-всем-фичам) | **P2** | 🚧 Чек-листы есть, нет CI smoke | L2, L3 |
| L14 | [Подсказки + документация для пользователей](#l14-подсказки--документация-для-пользователей) | **P2** | ❌ Не сделано (BF21) | — |
| L15 | [Кроссбраузерность + мобильная адаптивность](#l15-кроссбраузерность--мобильная-адаптивность) | **P3** | ❌ Не сделано | — |

**Остаток до запуска (по приоритету):** 🔴 L6 RBAC enforcement (P0/P1) → L8 Backup (P1) → L12/L13/L14/L15 (P2–P3).

---

## Детали задач

### L1. Мультитенантность

**Приоритет:** P0 | **Статус:** ✅ Done (реализована как F44) | **Фича:** BF19

Полноценная multi-tenant архитектура.

**Решения:**
- **Изоляция данных:** Schema per tenant (отдельная PostgreSQL schema на каждую компанию)
- **Идентификация:** По поддомену (company1.leadboard.app)
- **Иерархия:** Компания (tenant) → Jira project key(s) → Команды

**Scope:**
- [ ] Tenant entity + management (CRUD компаний)
- [ ] Schema per tenant: создание/миграция схем через Flyway
- [ ] Tenant resolver: определение tenant по поддомену (Spring Filter/Interceptor)
- [ ] Jira connection per tenant: у каждого tenant свой OAuth / API token
- [ ] User ↔ Tenant привязка (пользователь принадлежит компании)
- [ ] Все запросы фильтруются по tenant (schema routing)
- [ ] Setup Wizard: адаптация под multi-tenant (регистрация компании → подключение Jira)
- [ ] Admin panel: управление тенантами (суперадмин)
- [ ] Scheduled sync per tenant: каждый tenant синкается независимо
- [ ] Frontend: поддержка поддоменов, tenant context

**Зависимости:** Затрагивает весь стэк. Должна быть реализована ДО security hardening и RBAC тестов.

---

### L2. QA: дотестировать оставшиеся экраны

**Приоритет:** P0 | **Статус:** ✅ Done — QA пройден по всем модулям

По [QA_STATUS.md](testing/QA_STATUS.md) (обновление 2026-05-16) покрыто **34/34 модуля** (все экраны + cross-cutting). Прежняя оценка «19 экранов / 63%» была заниженной. Экраны из старого списка (AutoScore/Planning, Workflow Config, Timeline, Simulation, Member Profile, Setup Wizard, Auth/OAuth) — ✅ протестированы.

---

### L3. Фикс выявленных багов

**Приоритет:** P0 | **Статус:** 🚧 Re-triage (старый список устарел)

**Сверка 2026-06-27.** Старый список «12 открытых багов» (и более широкий бэклог в QA_STATUS.md) **не отражает текущий код** — несколько «открытых» багов фактически закрыты:
- BUG-94/95/76/77 (изоляция тенантов, simulation race) — ✅ исправлены (проверено по коду).
- Tenant-aware scheduled jobs (ForecastSnapshotService/WipSnapshotService) — ✅ исправлены.
- BUG-4/BUG-10 (сломанные frontend-тесты) — ✅ закрыто (фронт-сьют 231/231 зелёный).

**Подтверждённый по коду остаточный блокер:** RBAC enforcement — см. [L6](#l6-rbac--тест-от-разных-ролей). Это единственный реальный P0/P1-блокер.

**Возможно ещё открыто (требует точечной сверки, не подтверждено):** хардкод цветов (BUG-35/37/38 — Design System долг), кэш-инвалидация борда (L10). Перед запуском QA_STATUS.md стоит пере-триажить целиком против текущего кода.

---

### L4. Security hardening

**Приоритет:** P0 | **Статус:** ✅ Done (F46, v0.46.0)

- [x] CORS: whitelist доменов, убраны wildcards, явные headers
- [x] CSRF: проверено — stateless API + HttpOnly/Secure/SameSite cookie (безопасно)
- [x] Rate limiting: RateLimitFilter (OAuth 20/min, Sync 5/min, Registration 10/min, API 200/min)
- [x] SQL injection: проверено — все queries параметризованы через JPA @Param
- [x] XSS: проверено — React auto-escape, нет dangerouslySetInnerHTML
- [x] Secrets: проверено — токены не логируются, .env в gitignore
- [x] HTTPS: SSL на nginx (prod), DB sslmode=require
- [x] Headers: HSTS, CSP, X-Frame-Options DENY, Referrer-Policy, Permissions-Policy
- [x] WebSocket auth: валидация сессии при подключении к Poker
- [x] Tenant isolation: subdomain приоритет, header только для localhost
- [x] Error sanitization: generic messages клиенту, детали только в логи

---

### L5. Sync стабильность

**Приоритет:** P1 | **Статус:** ✅ QA done, critical bugs fixed

QA проведён 2026-02-21. Найдено 10 багов (2 Critical, 2 High), все исправлены.
Отчёт: [reports/2026-02-21_SYNC.md](testing/reports/2026-02-21_SYNC.md)

**Остаётся:**
- [ ] Интеграционный тест: полный sync на реальных данных после фикса мультитенантности
- [ ] Мониторинг: алерт если sync не завершился за N минут

---

### L6. RBAC — тест от разных ролей

**Приоритет:** P1 | **Статус:** 🔴 Остаток — enforcement неполный (РЕАЛЬНЫЙ блокер к запуску)

**Сверка 2026-06-27 (по коду).** RBAC реализован (F27) и фильтр грузит per-tenant роль из `tenant_users` на каждый запрос (BUG-94 исправлен). НО `SecurityConfig` имеет `.anyRequest().permitAll()` → авторизация целиком на method-level `@PreAuthorize`, а его **нет на мутирующих эндпоинтах** у ряда контроллеров:
- 🔴 `WorkflowConfigController` (`/api/admin/workflow-config`) — **9 write-эндпоинтов, `@PreAuthorize=0`** (любой авторизованный, даже VIEWER, может переписать конфиг workflow тенанта).
- `ForecastSnapshotController`, `AutoScoreController`, `ForecastController`, `EpicController`, `StoryController`, `CompetencyController`, `CalendarController`, `AuditRequestController` — мутации без авторизации.
- Частично: `PokerController` (8 мутаций / 1 `@PreAuthorize`), `JiraProjectController` (3/1), `TenantJiraConfigController` (3/1).
- ⚠️ WebSocket: `PokerWebSocketConfig` — raw WS без channel-интерсептора аутентификации (вторично, только Poker).

**Остаётся:**
- [ ] Матрица доступа: endpoint × role → allow/deny
- [ ] Проставить `@PreAuthorize` на мутирующие эндпоинты (старт с `WorkflowConfigController`)
- [ ] WebSocket: auth-интерсептор на handshake/channel
- [ ] Тест UI: скрытие управления для read-only ролей
- [ ] Тест API: 403 на запрещённые endpoints

Ручное тестирование всех экранов от лица каждой роли:

| Роль | Что проверить |
|------|---------------|
| ADMIN | Полный доступ ко всему, управление пользователями |
| PROJECT_MANAGER | Доступ к Projects, RICE, настройки проекта |
| TEAM_LEAD | Управление командой, участниками, метрики |
| MEMBER | Только просмотр своих данных, покер |
| VIEWER | Только read-only доступ |

- [ ] Матрица доступа: endpoint × role → allow/deny
- [ ] Тест UI: скрытие элементов управления для read-only ролей
- [ ] Тест API: 403 на запрещённые endpoints

---

### L7. Setup Wizard — полный флоу

**Приоритет:** P1 | **Статус:** ✅ Done (F33 Setup Wizard, QA 2026-02-25)

Реализован 4-шаговый мастер (Period → Sync → Workflow → Done), `SetupWizardPage.tsx` + `SyncController`. Полный end-to-end флоу подключения тенанта:

- [ ] Регистрация / создание компании
- [ ] OAuth подключение к Jira
- [ ] Выбор периода синхронизации
- [ ] Первый sync
- [ ] Auto-detect workflow
- [ ] Настройка команд
- [ ] Переход на Board

---

### L8. Backup стратегия + rollback plan

**Приоритет:** P1 | **Статус:** ❌ Не сделано (нет автобэкапа/протестированного отката; DEPLOY.md описывает только ручной docker save/load)

- [ ] Автоматический бэкап PostgreSQL (pg_dump, cron, retention)
- [ ] Бэкап перед каждым деплоем
- [ ] Процедура восстановления (документ + тест)
- [ ] Rollback деплоя: docker compose откат на предыдущую версию
- [ ] Миграции: Flyway undo или ручные SQL-скрипты отката

---

### L9. Нагрузочное тестирование

**Приоритет:** P1 | **Статус:** ✅ Done (F50 — k6-сьют, 7 сценариев, seed 183K issues; результаты в PERF_RESULTS.md) | **Зависимости:** L1, L3

- [ ] Инструмент: k6 / JMeter / Gatling
- [ ] Сценарии:
  - Board: 50 concurrent users, 500+ задач
  - Sync: 1000+ задач из Jira, concurrent syncs разных тенантов
  - API: throughput + latency P95/P99
  - Planning: расчёт AutoScore / Expected Done на 200+ эпиков
- [ ] Профиль нагрузки: normal load, peak load, stress test
- [ ] Критерии: P95 < 500ms, P99 < 2s, 0 errors at normal load
- [ ] Bottleneck анализ: DB slow queries, memory usage, CPU

---

### L10. DB оптимизация + Jira rate limits

**Приоритет:** P1 | **Статус:** ✅ Done (DB), 🚧 проверить кэш-инвалидацию

DB-оптимизация выполнена (F50 индексы/T5-миграция, HikariCP-тюнинг, F51 early-exit 5–8s → 89ms, N+1-фиксы). Jira rate limits — `RateLimitFilter` (F46) с обработкой 429/backoff. Открытый вопрос: инвалидация кэша борда при смене состава команды (если подтвердится в коде).

- [x] Анализ индексов: `EXPLAIN ANALYZE` на основные запросы
- [ ] Slow query log: включить pg_stat_statements
- [ ] Connection pooling: проверить HikariCP настройки per-tenant
- [ ] Jira rate limits: retry с backoff, мониторинг оставшегося лимита
- [ ] Кэширование: Jira metadata (уже есть, проверить TTL)

---

### L11. Мониторинг и алертинг

**Приоритет:** P1 | **Статус:** ✅ Done (F49 Observability + F60 Production Monitoring)

Стек: Prometheus + Grafana + Micrometer (F49); Alertmanager + Telegram-алерты (F60: Backend down, Sync failed, High error rate, High memory). Настройка в DEPLOY.md.

- [x] Health endpoint: `/api/health` — статус DB, Jira, sync
- [ ] Metrics: Prometheus / Micrometer (запросы, latency, errors)
- [ ] Logging: structured logs (JSON), ротация
- [ ] Alerts:
  - Sync не завершился > 10 min
  - Error rate > 5%
  - DB connections exhausted
  - Disk usage > 80%
- [ ] Dashboard: Grafana или простой status page
- [ ] Uptime: external ping (UptimeRobot / Healthchecks.io)

---

### L12. Graceful degradation

**Приоритет:** P2 | **Статус:** ❌ Не сделано (нет circuit breaker / error boundaries; есть лишь точечные fallback'и)

- [ ] Jira недоступна → борда работает на кэшированных данных + баннер
- [ ] Sync fail → retry + уведомление, борда не ломается
- [ ] DB перегружена → circuit breaker, очередь запросов
- [ ] Frontend: error boundaries на каждом экране (не весь app fallback)

---

### L13. Ручное тестирование по всем фичам

**Приоритет:** P2 | **Статус:** 🚧 Чек-листы готовы (TEST_PLAN.md S1–S14 + X1–X5), нет CI smoke/regression

Подготовить чек-лист для ручного тестирования каждого экрана. Основа: [TEST_PLAN.md](testing/TEST_PLAN.md).

- [ ] Чек-лист по экранам (Board, Teams, Projects, Metrics, etc.)
- [ ] Smoke test suite: минимальный набор для проверки после деплоя
- [ ] Regression test после каждого крупного изменения

---

### L14. Подсказки + документация для пользователей

**Приоритет:** P2 | **Статус:** ❌ Не сделано (BF21 не реализован; спека BF21 отсутствует)

- [ ] Onboarding tooltips: первый визит → подсказки по основным элементам
- [ ] Help page / Knowledge base: как подключить Jira, что значат метрики
- [ ] Contextual help: иконка "?" рядом с DSR, AutoScore, RICE
- [ ] FAQ: частые вопросы и проблемы
- [ ] Changelog / What's new: уведомление о новых фичах

---

### L15. Кроссбраузерность + мобильная адаптивность

**Приоритет:** P3 | **Статус:** ❌ Не сделано (нет систематической кроссбраузерной/мобильной проверки)

- [ ] Браузеры: Chrome, Firefox, Safari, Edge
- [ ] Responsive: минимум read-only просмотр Board с мобильного
- [ ] Tablet: полноценная работа на iPad

---

## Порядок выполнения

```
Фаза 1 — Архитектура:
  L1 Мультитенантность ──→ L4 Security ──→ L6 RBAC тест
                           └──→ L7 Wizard  └──→ L8 Backup

Фаза 2 — Качество (параллельно с Фазой 1):
  L2 QA экраны ──→ L3 Фикс багов ──→ L13 Ручное тестирование

Фаза 3 — Production readiness:
  L9 Нагрузка ──→ L10 DB оптимизация
  L11 Мониторинг
  L12 Graceful degradation

Фаза 4 — UX:
  L14 Подсказки + документация
  L15 Кроссбраузерность
```

---

## Связь с бэклогом фич

| Launch задача | Бэклог фича | Примечание |
|---------------|-------------|------------|
| L1 Мультитенантность | **BF19** Multi-tenancy & SaaS Packaging | Ядро запуска |
| L14 Подсказки | **BF21** Smart Hints & Onboarding | Часть L14 |
| L11 Мониторинг | — | Инфраструктурная задача, не фича |
| L6 RBAC тест | F27 (реализована) | Тестирование существующей фичи |

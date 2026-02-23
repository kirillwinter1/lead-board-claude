# Launch Plan — Lead Board

**Цель:** подготовить борду к реальному запуску как multi-tenant SaaS.

**Последнее обновление:** 2026-02-23

---

## Обзор

| # | Задача | Приоритет | Статус | Зависимости |
|---|--------|-----------|--------|-------------|
| L1 | [Мультитенантность](#l1-мультитенантность) | **P0** | 📋 Planned | — |
| L2 | [QA: дотестировать оставшиеся экраны](#l2-qa-дотестировать-оставшиеся-экраны) | **P0** | 🚧 В работе (63%) | — |
| L3 | [Фикс выявленных багов](#l3-фикс-выявленных-багов) | **P0** | 🚧 В работе (12 open) | L2 |
| L4 | [Security hardening](#l4-security-hardening) | **P0** | 📋 Planned | L1 |
| L5 | [Sync стабильность](#l5-sync-стабильность) | **P1** | ✅ QA done, bugs fixed | — |
| L6 | [RBAC — тест от разных ролей](#l6-rbac--тест-от-разных-ролей) | **P1** | 📋 Planned | L1 |
| L7 | [Setup Wizard — полный флоу](#l7-setup-wizard--полный-флоу) | **P1** | 📋 Planned | L1 |
| L8 | [Backup стратегия + rollback plan](#l8-backup-стратегия--rollback-plan) | **P1** | 📋 Planned | L1 |
| L9 | [Нагрузочное тестирование](#l9-нагрузочное-тестирование) | **P1** | 📋 Planned | L1, L3 |
| L10 | [DB оптимизация + Jira rate limits](#l10-db-оптимизация--jira-rate-limits) | **P1** | 📋 Planned | L1 |
| L11 | [Мониторинг и алертинг](#l11-мониторинг-и-алертинг) | **P1** | 📋 Planned | — |
| L12 | [Graceful degradation](#l12-graceful-degradation) | **P2** | 📋 Planned | — |
| L13 | [Ручное тестирование по всем фичам](#l13-ручное-тестирование-по-всем-фичам) | **P2** | 📋 Planned | L2, L3 |
| L14 | [Подсказки + документация для пользователей](#l14-подсказки--документация-для-пользователей) | **P2** | 📋 Planned | — |
| L15 | [Кроссбраузерность + мобильная адаптивность](#l15-кроссбраузерность--мобильная-адаптивность) | **P3** | 📋 Planned | — |

---

## Детали задач

### L1. Мультитенантность

**Приоритет:** P0 | **Статус:** 📋 Planned | **Фича:** BF19

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

**Приоритет:** P0 | **Статус:** 🚧 В работе | **Прогресс:** 12/19 экранов (63%)

Оставшиеся экраны для QA-агента (см. [QA_STATUS.md](testing/QA_STATUS.md)):

| Приоритет | Экран | Фичи | Статус |
|-----------|-------|------|--------|
| P1 | AutoScore / Planning | F13, F19, F20, F21 | ❌ |
| P1 | Workflow Config | F17, F29, F38 | ❌ |
| P2 | Timeline | F14 | ❌ |
| P2 | Simulation | F28 | ❌ |
| P3 | Member Profile | F30 | ❌ |
| P3 | Setup Wizard | F33 | ❌ |
| P3 | Auth / OAuth | F4, F27 | ❌ |

---

### L3. Фикс выявленных багов

**Приоритет:** P0 | **Статус:** 🚧 В работе

**Открытые баги: 12** (из 49 найденных, 37 исправлено)

| Bug ID | Severity | Описание | Экран |
|--------|----------|----------|-------|
| BUG-4 | High | 53/240 frontend тестов падают (missing mock) | Team Metrics |
| BUG-10 | High | 24 frontend теста сломаны (регрессия F35/F36/F37) | Projects |
| BUG-13 | Medium | Нет тестов для TeamService color methods | Teams |
| BUG-14 | Medium | Нет controller-тестов для ProjectController/RiceController | Projects |
| BUG-19 | High | 0 frontend тестов для AbsenceTimeline/AbsenceModal | Absences |
| BUG-22 | Medium | 0 controller-level тестов для absence endpoints | Absences |
| BUG-24 | Medium | Нет @DisplayName в backend тестах (absences) | Absences |
| BUG-25 | Medium | Несогласованность формата createdAt (offset vs UTC) | Absences |
| BUG-35 | Medium | Hardcoded PRIORITY_COLORS в BugSlaSettingsPage | Bug SLA |
| BUG-37 | Low | Hardcoded score colors в PriorityCell | Board |
| BUG-38 | Low | Hardcoded severity labels/rule names в AlertIcon | Board |

**Примечание:** BUG-4 и BUG-10 — по сути одна проблема (сломанные frontend тесты).

---

### L4. Security hardening

**Приоритет:** P0 | **Статус:** 📋 Planned

- [ ] CORS: whitelist доменов (поддомены тенантов)
- [ ] CSRF: защита для state-changing endpoints
- [ ] Rate limiting: на API endpoints (особенно sync, auth)
- [ ] SQL injection: проверить все raw queries
- [ ] XSS: sanitization user input (Jira data → HTML rendering)
- [ ] Secrets: убедиться что токены не утекают в логи и API responses
- [ ] HTTPS: SSL сертификат + redirect HTTP→HTTPS
- [ ] Headers: Security headers (X-Frame-Options, CSP, HSTS)

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

**Приоритет:** P1 | **Статус:** 📋 Planned

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

**Приоритет:** P1 | **Статус:** 📋 Planned

Полный end-to-end тест подключения нового тенанта:

- [ ] Регистрация / создание компании
- [ ] OAuth подключение к Jira
- [ ] Выбор периода синхронизации
- [ ] Первый sync
- [ ] Auto-detect workflow
- [ ] Настройка команд
- [ ] Переход на Board

---

### L8. Backup стратегия + rollback plan

**Приоритет:** P1 | **Статус:** 📋 Planned

- [ ] Автоматический бэкап PostgreSQL (pg_dump, cron, retention)
- [ ] Бэкап перед каждым деплоем
- [ ] Процедура восстановления (документ + тест)
- [ ] Rollback деплоя: docker compose откат на предыдущую версию
- [ ] Миграции: Flyway undo или ручные SQL-скрипты отката

---

### L9. Нагрузочное тестирование

**Приоритет:** P1 | **Статус:** 📋 Planned | **Зависимости:** L1, L3

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

**Приоритет:** P1 | **Статус:** 📋 Planned

- [ ] Анализ индексов: `EXPLAIN ANALYZE` на основные запросы
- [ ] Slow query log: включить pg_stat_statements
- [ ] Connection pooling: проверить HikariCP настройки per-tenant
- [ ] Jira rate limits: retry с backoff, мониторинг оставшегося лимита
- [ ] Кэширование: Jira metadata (уже есть, проверить TTL)

---

### L11. Мониторинг и алертинг

**Приоритет:** P1 | **Статус:** 📋 Planned

- [ ] Health endpoint: `/api/health` — статус DB, Jira, sync
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

**Приоритет:** P2 | **Статус:** 📋 Planned

- [ ] Jira недоступна → борда работает на кэшированных данных + баннер
- [ ] Sync fail → retry + уведомление, борда не ломается
- [ ] DB перегружена → circuit breaker, очередь запросов
- [ ] Frontend: error boundaries на каждом экране (не весь app fallback)

---

### L13. Ручное тестирование по всем фичам

**Приоритет:** P2 | **Статус:** 📋 Planned

Подготовить чек-лист для ручного тестирования каждого экрана. Основа: [TEST_PLAN.md](testing/TEST_PLAN.md).

- [ ] Чек-лист по экранам (Board, Teams, Projects, Metrics, etc.)
- [ ] Smoke test suite: минимальный набор для проверки после деплоя
- [ ] Regression test после каждого крупного изменения

---

### L14. Подсказки + документация для пользователей

**Приоритет:** P2 | **Статус:** 📋 Planned

- [ ] Onboarding tooltips: первый визит → подсказки по основным элементам
- [ ] Help page / Knowledge base: как подключить Jira, что значат метрики
- [ ] Contextual help: иконка "?" рядом с DSR, AutoScore, RICE
- [ ] FAQ: частые вопросы и проблемы
- [ ] Changelog / What's new: уведомление о новых фичах

---

### L15. Кроссбраузерность + мобильная адаптивность

**Приоритет:** P3 | **Статус:** 📋 Planned

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

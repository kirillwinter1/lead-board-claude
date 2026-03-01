# F50: Performance Testing Suite (k6)

**Версия:** 0.50.0
**Дата:** 2026-03-01
**Статус:** ✅ Готово

## Проблема

Отсутствие нагрузочного тестирования. Невозможно оценить пропускную способность, найти bottleneck'и, проверить как система ведёт себя при 50-500 concurrent users. Оптимизации (N+1 fix, кэш, pool tuning) невозможно верифицировать без воспроизводимых perf-тестов.

## Решение

k6 performance testing suite с seed-данными (3 тенанта × ~61K issues = 183K total), 6 сценариев, 8 flow-файлов, общая библиотека (auth, http, tenant routing, generators). Результаты трекаются в `PERF_RESULTS.md` с run-by-run сравнением.

## Архитектура

```
perf/
├── run.sh                    # CLI (seed|cleanup|smoke|load|stress|soak|multi|reorder)
├── PERF_RESULTS.md           # Трекинг результатов по run'ам
├── config/
│   └── env.js                # BASE_URL, tenant list, VU distribution
├── seed/
│   ├── seed.sql              # Master seed (вызывает 01-07)
│   ├── cleanup.sql           # Очистка тестовых данных
│   ├── 01_tenants_and_users.sql   # 3 тенанта (perf-alpha/beta/gamma), 10 users каждый
│   ├── 02_tenant_schemas.sql      # Tenant schemas со всеми 27 бизнес-таблицами
│   ├── 03_workflow_config.sql     # Workflow config, issue types, status mappings
│   ├── 04_teams_and_members.sql   # 50 teams per tenant, members
│   ├── 05_issues.sql              # ~61K issues per tenant (epics, stories, subtasks)
│   ├── 06_status_changelog.sql    # Status history для DSR и forecast accuracy
│   └── 07_rice_and_extras.sql     # RICE assessments, manual order
├── lib/
│   ├── auth.js               # getTenantAuthHeaders() — session cookie per tenant
│   ├── http.js               # apiGet/apiPut с custom metrics (Trend per group)
│   ├── tenant.js             # getVuContext() — VU → tenant/team/user routing
│   └── generators.js         # thinkTime(), random data generators
├── flows/
│   ├── board.js              # Board flow: GET /api/board + score-breakdown
│   ├── reorder-forecast.js   # Reorder chain: PUT order → unified → forecast → board
│   ├── metrics.js            # Team metrics flow
│   ├── projects.js           # Projects flow
│   ├── timeline.js           # Timeline flow
│   ├── data-quality.js       # Data quality flow
│   ├── bug-metrics.js        # Bug metrics flow
│   └── multi-tenant.js       # Cross-tenant flow
├── scenarios/
│   ├── smoke.js              # 1 VU, 30s — sanity check
│   ├── load.js               # 10→200 VUs, 5 min — normal load
│   ├── stress.js             # 0→500 VUs, 3 min — find breaking point
│   ├── soak.js               # 100 VUs, 30 min — memory leaks, connection leaks
│   ├── multi-tenant.js       # 3×50 VUs, 4 min — cross-tenant isolation
│   └── reorder-stress.js     # 50 writers + 10 readers, 5 min — write contention
└── results/                  # JSON output (gitignored)
```

## Seed Data

| Тенант | Slug | Issues | Teams | Users |
|--------|------|--------|-------|-------|
| Alpha | perf-alpha | ~61K | 50 | 10 |
| Beta | perf-beta | ~61K | 50 | 10 |
| Gamma | perf-gamma | ~61K | 50 | 10 |
| **Total** | — | **~183K** | **150** | **30** |

Каждый тенант содержит полный набор данных: workflow config, issue type mappings, status mappings, RICE assessments, status changelog, manual order.

## Сценарии

| Сценарий | Команда | VUs | Длительность | Назначение |
|----------|---------|-----|-------------|-----------|
| Smoke | `./run.sh smoke` | 1 | 30s | Sanity check — всё работает |
| Load | `./run.sh load` | 10→200 | 5 min | Нормальная нагрузка, baseline latency |
| Stress | `./run.sh stress` | 0→500 | 3 min | Точка деградации |
| Soak | `./run.sh soak` | 100 | 30 min | Memory/connection leaks |
| Multi-tenant | `./run.sh multi` | 3×50 | 4 min | Tenant isolation под нагрузкой |
| Reorder Stress | `./run.sh reorder` | 50+10 | 5 min | Write contention, HikariCP saturation |

## Reorder Stress — подробно

Самый тяжёлый сценарий. 2 группы VU:

**Writers (50 VUs):** reorder chain из 5 шагов:
1. `GET /api/board?teamIds=N` — загрузка board, сбор epic keys
2. `PUT /api/epics/{key}/order` — перемещение random epic
3. `GET /api/planning/unified?teamId=N` — full forecast recalculation
4. `GET /api/planning/forecast?teamId=N` — forecast view
5. `GET /api/board?teamIds=N` — verify new order

**Readers (10 VUs):** concurrent board loads (contend with writers).

**Фазы:** warm-up (5 VUs, 30s) → ramp (5→50, 2 min) → peak (50 VUs, 2 min) → cool (30s).

## Библиотека

**`lib/http.js`** — обёртка над k6 http с custom metrics:
- `apiGet(url, headers, group, metricName)` / `apiPut(...)`
- Custom `Trend` per metric name (`api_duration_{group}`)
- Автоматический status check + error rate tracking

**`lib/tenant.js`** — маршрутизация VU → tenant:
- `getVuContext()` распределяет VU по тенантам round-robin
- Возвращает `{tenantSlug, tenantName, userIndex, teamIndex}`

**`lib/auth.js`** — аутентификация:
- `getTenantAuthHeaders(slug, name, userIndex)` — cookie `LEAD_SESSION` + `X-Tenant-Slug`

**`lib/generators.js`** — генераторы:
- `thinkTime()` — случайная пауза 0.5-2s (имитация пользователя)

## Результаты оптимизации

Трекинг в `perf/PERF_RESULTS.md`. 3 run'а проведены:

| Метрика | Run #1 (baseline) | Run #2 (optimized) | Run #3 (fixed filter) |
|---------|-------------------|--------------------|-----------------------|
| Board (all) p50 | 30,000ms | **1,041ms** | 7,031ms |
| Error rate | 84.4% | **4.4%** | 34.8% |
| Total requests | 314 | 503 | 494 |

**Run #1 → #2:** N+1 fix + HikariCP 10→30 + TTL cache + DQ decoupling = **28.8x ускорение board**.

**Run #3:** Исправлен параметр `teamId` → `teamIds`. Хуже Run #2, но это реалистичный тест — обнажил bottleneck cold `calculatePlan()` (5-8s per team).

## Ключевые файлы

| Файл | Описание |
|------|----------|
| `perf/run.sh` | CLI runner (seed, cleanup, 6 сценариев) |
| `perf/PERF_RESULTS.md` | Трекинг результатов с анализом |
| `perf/seed/*.sql` | 7 SQL-скриптов seed-данных (183K issues) |
| `perf/scenarios/*.js` | 6 k6 сценариев |
| `perf/flows/*.js` | 8 flow-файлов (board, reorder, metrics, etc.) |
| `perf/lib/*.js` | 4 файла общей библиотеки (auth, http, tenant, generators) |
| `perf/config/env.js` | Конфигурация (BASE_URL, tenants) |

## Запуск

```bash
# Prerequisites
brew install k6
# Backend running on localhost:8080 with raised rate limit:
APP_RATE_LIMIT_GENERAL=100000 ./gradlew bootRun

# Seed data (first time)
cd perf && ./run.sh seed

# Run specific scenario
./run.sh reorder

# Cleanup
./run.sh cleanup
```

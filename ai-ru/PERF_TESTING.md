# Performance Testing Suite (k6)

## Обзор

k6-based нагрузочное тестирование для Lead Board. Тестирует API-эндпоинты в мультитенантном окружении со значительным объёмом данных.

## Объём данных

| Метрика | Per tenant | Всего (3 тенанта) |
|---------|------------|-------------------|
| Project keys | 10 (PERF-A...J) | 30 |
| Команды | 50 (5 per key) | 150 |
| Участники | 500 (10 per team) | 1,500 |
| Эпики | 1,000 | 3,000 |
| Стори | 15,000 | 45,000 |
| Сабтаски | 45,000 | 135,000 |
| **Итого issues** | **61,000** | **183,000** |
| Status changelog | ~107,000 | ~320,000 |
| RICE assessments | 1,000 | 3,000 |

## Структура

```
perf/
├── run.sh                      # Launcher: seed/cleanup/smoke/load/stress/soak/multi/all
├── seed/
│   ├── seed.sql                # Orchestrator (\i 01..07)
│   ├── 01_tenants_and_users.sql # 3 tenants, 30 users, sessions
│   ├── 02_tenant_schemas.sql    # CREATE SCHEMA + Flyway migrations × 3
│   ├── 03_workflow_config.sql   # 10 project configs, roles, mappings
│   ├── 04_teams_and_members.sql # 50 teams × 10 members
│   ├── 05_issues.sql            # 61K issues per tenant (PL/pgSQL)
│   ├── 06_status_changelog.sql  # Status transitions
│   ├── 07_rice_and_extras.sql   # RICE, sync state
│   └── cleanup.sql              # DROP schemas + DELETE public rows
├── config/
│   └── env.js                  # BASE_URL, tenants, project keys, thresholds
├── lib/
│   ├── auth.js                 # LEAD_SESSION cookie + X-Tenant-Slug
│   ├── tenant.js               # SharedArray user pool, getVuContext()
│   ├── http.js                 # apiGet() с custom metrics per group
│   └── generators.js           # randomTeamIndex(), randomDateRange()
├── flows/
│   ├── board.js                # /api/board + score-breakdown
│   ├── timeline.js             # unified + retrospective + forecast + role-load
│   ├── metrics.js              # throughput + lead-time + cycle-time + summary + dsr
│   ├── data-quality.js         # /api/data-quality (all + teamId)
│   ├── bug-metrics.js          # /api/metrics/bugs
│   ├── projects.js             # /api/projects + /api/projects/timeline
│   └── multi-tenant.js         # Cycle через 3 тенанта
└── scenarios/
    ├── smoke.js                # 1 VU, 30s — sanity check
    ├── load.js                 # 10→200 VUs, 5 min
    ├── stress.js               # 0→500 VUs, 3 min
    ├── soak.js                 # 100 VUs, 30 min
    └── multi-tenant.js         # 3×50 VUs per tenant
```

## Быстрый старт

### Предварительные требования

```bash
brew install k6          # k6 load testing tool
docker compose up -d     # PostgreSQL
```

### Запуск

```bash
# 1. Поднять rate limit (обязательно!)
APP_RATE_LIMIT_GENERAL=100000 ./gradlew bootRun  # в backend/

# 2. Seed данных (~2 мин)
cd perf && ./run.sh seed

# 3. Smoke test
./run.sh smoke

# 4. Нагрузочный тест
./run.sh load
```

### Все команды

| Команда | Описание |
|---------|----------|
| `./run.sh seed` | Seed БД (3 тенанта, ~183K issues) |
| `./run.sh cleanup` | Удалить все тестовые данные |
| `./run.sh smoke` | Smoke test (1 VU, 30s) |
| `./run.sh load` | Load test (10→200 VUs, 5 min) |
| `./run.sh stress` | Stress test (0→500 VUs, 3 min) |
| `./run.sh soak` | Soak test (100 VUs, 30 min) |
| `./run.sh multi` | Multi-tenant test (3×50 VUs) |
| `./run.sh all` | seed + smoke + load |

## Сценарии

### Smoke (1 VU, 30s)
Проверяет что всё работает. Board + Metrics flows.

### Load (10→200 VUs, 5 min)
Устойчивая нагрузка. Weighted random flows:
- Board 35%, Metrics 25%, Timeline 15%, Data Quality 10%, Projects 10%, Bug Metrics 5%
- Think time: 1-3s

### Stress (0→500 VUs, 3 min)
Поиск точки перелома. Только тяжёлые эндпоинты: Board, Timeline, Data Quality.

### Soak (100 VUs, 30 min)
Длительная нагрузка для поиска memory leaks и pool exhaustion. Board + Metrics + Timeline.

### Multi-tenant (3×50 VUs, 4 min)
Три параллельных сценария, каждый на своём тенанте. Per-tenant thresholds.

## Thresholds

| Группа | p95 | p99 | Error rate |
|--------|-----|-----|-----------|
| Global | 500ms | 2s | <1% |
| Board | 800ms | 3s | <1% |
| Timeline | 1s | 3s | <1% |
| Metrics | 400ms | 1.5s | <1% |
| Data Quality | 600ms | 2s | <1% |
| Bug Metrics | 300ms | 1s | <1% |
| Projects | 400ms | 1.5s | <1% |

## Rate Limiter

`RateLimitFilter` по умолчанию ограничивает 200 req/min per IP. Для нагрузочных тестов поднять лимит:

```bash
APP_RATE_LIMIT_GENERAL=100000 ./gradlew bootRun
```

Или в `application.yml`:
```yaml
app:
  rate-limit:
    general: 100000
```

## Тенанты

| Slug | Schema | Sessions |
|------|--------|----------|
| perf-alpha | tenant_perf_alpha | perf-session-alpha-u01..u10 |
| perf-beta | tenant_perf_beta | perf-session-beta-u01..u10 |
| perf-gamma | tenant_perf_gamma | perf-session-gamma-u01..u10 |

## Результаты

JSON-результаты сохраняются в `perf/results/` (в .gitignore).

## Известные bottleneck'и

- **HikariCP:** 10 connections на все тенанты — pool exhaustion при высокой нагрузке
- **UnifiedPlanningService:** N+1 паттерны (~300 queries/call)
- **BoardService:** вызывает planning per team (50 teams = 50x planning)
- **RetrospectiveTimelineService:** загружает все transitions, O(N²)
- **DataQualityService:** DFS per story для circular deps
- **F48 Multi-Project:** `loadConfiguration()` мержит конфиги из N project_configurations

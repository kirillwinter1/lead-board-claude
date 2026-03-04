# QA Report: Sync & Multi-Tenant Fixes (BUG-108, ObservabilityMetrics, CallerRunsPolicy)

**Дата:** 2026-03-04
**Тестировщик:** Claude QA Agent
**Скоуп:** Cross-cutting fixes — TenantAwareAsyncConfig, SyncService, ObservabilityMetrics, SchemaBasedConnectionProvider

---

## Summary

- **Общий статус:** PASS WITH ISSUES
- **Unit tests:** 770 passed, 0 failed (73 pre-existing integration/component failures)
- **API tests:** 6 passed, 1 issue (500 without tenant header — pre-existing)
- **Visual:** N/A (backend-only fixes)
- **Code review:** 4 critical/high bugs found (same class of bug in other scheduled tasks)

---

## Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `TenantAwareAsyncConfig.java` | CallerRunsPolicy + TenantContextTaskDecorator fix for caller-thread detection |
| `SyncService.java` (line 147) | `if (!TenantContext.hasTenant()) return;` guard on legacy `scheduledSync()` |
| `SchemaBasedConnectionProvider.java` (line 44) | Added debug-level logging to `getConnection()` |
| `ObservabilityMetrics.java` (line 72-96) | Tenant-aware `refreshGauges()` iterating all active tenants |

---

## Проверки исправлений

### FIX-1: TenantAwareAsyncConfig CallerRunsPolicy (BUG-108)
| # | Проверка | Статус |
|---|----------|--------|
| 1 | CallerRunsPolicy правильно установлен | ✅ PASS |
| 2 | TenantContextTaskDecorator captures tenantId at decoration time | ✅ PASS |
| 3 | CallerThread detection: existingTenantId equals captured tenantId → skip clear() | ✅ PASS |
| 4 | Separate thread: existingTenantId is null → callerThread=false → clear() after | ✅ PASS |
| 5 | Null tenantId: callerThread always false, no TenantContext set, clear() is no-op | ✅ PASS |

### FIX-2: SyncService.scheduledSync() guard
| # | Проверка | Статус |
|---|----------|--------|
| 1 | `hasTenant()` returns false on scheduling-1 thread → early return | ✅ PASS |
| 2 | No DB queries executed before guard check | ✅ PASS |
| 3 | Comment documents intent clearly | ✅ PASS |

### FIX-3: ObservabilityMetrics tenant-aware refreshGauges()
| # | Проверка | Статус |
|---|----------|--------|
| 1 | Iterates all active tenants | ✅ PASS |
| 2 | Sets TenantContext per tenant, clears in finally | ✅ PASS |
| 3 | Exception per tenant doesn't break loop | ✅ PASS |
| 4 | Total count = sum across all tenants | ✅ PASS (538 + 605068 + 604819 + 607314 = 1,817,739 ✓) |
| 5 | Production verified: 0 errors after 75s | ✅ PASS |

### FIX-4: SchemaBasedConnectionProvider debug logging
| # | Проверка | Статус |
|---|----------|--------|
| 1 | Uses `log.debug()` — not visible in production | ✅ PASS |
| 2 | Logs both tenantIdentifier and ThreadLocal schema | ✅ PASS |

---

## API Tests

| # | Endpoint | Tenant | Auth | Expected | Actual | Статус |
|---|----------|--------|------|----------|--------|--------|
| 1 | GET /api/health | — | — | 200 | 200 | ✅ PASS |
| 2 | GET /api/sync/status | test2 | ✅ | 200 + JSON | 200 + JSON | ✅ PASS |
| 3 | POST /api/sync/trigger | test2 | ✅ | 200 + start | 200 + syncInProgress=true | ✅ PASS |
| 4 | GET /api/sync/status (after sync) | test2 | ✅ | 200 + completed | 200 + lastSyncCompletedAt updated | ✅ PASS |
| 5 | GET /api/sync/status | — | ✅ | 400/404 | 500 | ⚠️ NOTE (pre-existing) |
| 6 | GET /api/sync/status | nonexistent | ✅ | 404 | 404 | ✅ PASS |
| 7 | POST /api/sync/trigger | — | — | 401 | 401 | ✅ PASS |

---

## Bugs Found

### BUG-149: ForecastSnapshotService.createDailySnapshots() не tenant-aware — ERROR каждый день в 3:00 AM (High)

**Severity:** High
**Файл:** `ForecastSnapshotService.java:150-171`
**Описание:** `@Scheduled(cron = "0 0 3 * * *")` метод `createDailySnapshots()` вызывает `teamRepository.findByActiveTrue()` без TenantContext. Таблица `teams` находится в tenant-схемах, не в public. Результат: `ERROR: relation "teams" does not exist` каждый день в 3:00 AM.
**Шаги:** Дождаться 3:00 AM → проверить логи → увидеть ERROR от Hibernate SqlExceptionHelper.
**Ожидаемый результат:** Снапшоты создаются для всех команд во всех тенантах.
**Фактический результат:** 500 ERROR, снапшоты не создаются ни для одного тенанта.
**Рекомендация:** Итерировать по `tenantRepository.findAllActive()`, устанавливать `TenantContext` для каждого тенанта (как в ObservabilityMetrics.refreshGauges()).

### BUG-150: ForecastSnapshotService.cleanupOldSnapshots() не tenant-aware (High)

**Severity:** High
**Файл:** `ForecastSnapshotService.java:177-191`
**Описание:** `@Scheduled(cron = "0 0 4 * * SUN")` метод `cleanupOldSnapshots()` вызывает `teamRepository.findAll()` без TenantContext. Та же проблема что BUG-149.
**Результат:** Старые снапшоты никогда не удаляются → таблица растёт бесконечно.

### BUG-151: WipSnapshotService.createDailySnapshots() не tenant-aware — ERROR каждый день в 9:00 AM (High)

**Severity:** High
**Файл:** `WipSnapshotService.java:113-131`
**Описание:** `@Scheduled(cron = "0 0 9 * * *")` метод `createDailySnapshots()` вызывает `teamRepository.findByActiveTrue()` без TenantContext. Таблица `teams` в tenant-схемах.
**Результат:** `ERROR: relation "teams" does not exist` каждый день в 9:00 AM.

### BUG-152: WipSnapshotService.cleanupOldSnapshots() не tenant-aware (Medium)

**Severity:** Medium
**Файл:** `WipSnapshotService.java:137-143`
**Описание:** `@Scheduled(cron = "0 0 3 * * SUN")` вызывает `snapshotRepository.deleteBySnapshotDateBefore(cutoff)` без TenantContext. Таблица `wip_snapshots` в tenant-схемах.
**Результат:** Старые WIP-снапшоты никогда не удаляются.

### BUG-153: SyncService.scheduledSync() фактически dead code (Low)

**Severity:** Low
**Файл:** `SyncService.java:142-170`
**Описание:** Guard `if (!TenantContext.hasTenant()) return;` всегда срабатывает (scheduling-1 thread никогда не имеет TenantContext). Метод scheduledSync() — мёртвый код. В мультитенантном режиме TenantSyncScheduler полностью заменяет его функциональность.
**Рекомендация:** Удалить метод scheduledSync() и связанное поле scheduledSyncCount для чистоты кода.

### BUG-154: Нет тестов для TenantAwareAsyncConfig (Medium)

**Severity:** Medium
**Описание:** TenantContextTaskDecorator — критически важный компонент (race condition в CallerRunsPolicy = потеря данных). При этом 0 тестов:
- Нет теста на CallerRunsPolicy + TenantContext preservation
- Нет теста на separate thread + TenantContext propagation
- Нет теста на null tenantId case
**Рекомендация:** Написать минимум 3 юнит-теста для TenantContextTaskDecorator.

### BUG-155: Нет тестов для ObservabilityMetrics.refreshGauges() (Low)

**Severity:** Low
**Описание:** `refreshGauges()` — новая tenant-aware логика без тестового покрытия.
**Рекомендация:** 1 тест на multi-tenant summing, 1 тест на exception handling per tenant.

### BUG-156: ObservabilityMetrics.refreshGauges() дважды вызывает findAllActive() (Low)

**Severity:** Low
**Файл:** `ObservabilityMetrics.java:76,92`
**Описание:** `tenantRepository.findAllActive()` вызывается дважды: в цикле подсчёта issues и для gauge tenantsActive. Это два одинаковых запроса к БД за 1 цикл.
**Рекомендация:** Кешировать результат в переменную.

### BUG-157: Thread pool sizing для embedding-heavy syncs (Low)

**Severity:** Low
**Файл:** `TenantAwareAsyncConfig.java:24-27`
**Описание:** corePoolSize=4, maxPoolSize=8, queueCapacity=100. При синке 292 issues каждый триггерит `embeddingService.generateAndStoreAsync()`. 292 задач при ёмкости 108 (8 threads + 100 queue) → 184 задачи выполняются через CallerRunsPolicy в scheduling-1 thread, блокируя основной поток синка. При синке perf-тенантов (600K+ issues) это полный блок.
**Рекомендация:** Увеличить queueCapacity для embedding-heavy workloads или использовать отдельный executor для embeddings.

---

## Regression Testing

| Suite | Результат |
|-------|-----------|
| Unit tests | 770/770 PASS ✅ |
| Integration tests | 73 FAIL (pre-existing, require DB) |
| Frontend tests | N/A (backend-only fixes) |

---

## Test Coverage Gaps

| Компонент | Тесты | Покрытие |
|-----------|-------|----------|
| TenantAwareAsyncConfig | 0 | ❌ Нет тестов |
| TenantContextTaskDecorator | 0 | ❌ Нет тестов |
| ObservabilityMetrics | 0 | ❌ Нет тестов |
| TenantSyncScheduler | 0 | ❌ Нет тестов |
| ForecastSnapshotService (scheduled) | 1 file | ⚠️ Нет тестов для scheduled методов |
| WipSnapshotService (scheduled) | 1 file | ⚠️ Нет тестов для scheduled методов |

---

## Аналитика: Класс бага "Scheduled Task без TenantContext"

Все 4 найденных бага (BUG-149..152) + ранее исправленный ObservabilityMetrics — **один и тот же класс ошибки**: `@Scheduled` методы, написанные до F44 Multi-Tenancy, не были обновлены для мультитенантного режима.

**Полный список @Scheduled методов и их статус:**

| Метод | Период | Таблицы | Tenant-aware? | Статус |
|-------|--------|---------|---------------|--------|
| TenantSyncScheduler.scheduledTenantSync() | 60s | tenant tables | ✅ | ОК |
| SyncService.scheduledSync() | 300s | — (early return) | ✅ guard | ОК (dead code) |
| ObservabilityMetrics.refreshGauges() | 60s | jira_issues | ✅ | ✅ FIXED |
| ForecastSnapshotService.createDailySnapshots() | 3:00 AM | teams, forecast_snapshots | ❌ | **BUG-149** |
| ForecastSnapshotService.cleanupOldSnapshots() | 4:00 AM SUN | teams, forecast_snapshots | ❌ | **BUG-150** |
| WipSnapshotService.createDailySnapshots() | 9:00 AM | teams, wip_snapshots | ❌ | **BUG-151** |
| WipSnapshotService.cleanupOldSnapshots() | 3:00 AM SUN | wip_snapshots | ❌ | **BUG-152** |
| SimulationScheduler.runScheduled() | 19:00 MON-FRI | simulation_logs | ❌ | **BUG-77** (existing) |
| SessionCleanupTask.cleanupExpiredSessions() | 2:00 AM | user_sessions (public) | ✅ | ОК (@Table(schema="public")) |

---

## Recommendations

1. **P0:** Исправить BUG-149..152 — по аналогии с ObservabilityMetrics: итерировать tenants, setTenant/clear в try-finally
2. **P1:** Написать тесты для TenantContextTaskDecorator — критический компонент с нулевым покрытием
3. **P2:** Удалить SyncService.scheduledSync() — dead code, снижает читаемость
4. **P3:** Рассмотреть общий паттерн `TenantAwareScheduledTask` для DRY

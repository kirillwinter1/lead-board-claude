# TASK: Fix SimulationService TOCTOU race condition

**Priority:** Critical
**Review IDs:** C1, C2
**Files:**
- `backend/src/main/java/com/leadboard/simulation/SimulationService.java:41-42`
- `backend/src/main/java/com/leadboard/simulation/SimulationScheduler.java`

## Проблема

1. **TOCTOU в SimulationService:** `existsByStatus("RUNNING")` check не атомарен с insert. Два одновременных запроса оба проходят проверку.
2. **SimulationScheduler без TenantContext:** Scheduler вызывает `runSimulation()` без установки TenantContext → работает с public schema в мультитенантном режиме.

## Рекомендация

1. Добавить partial unique index: `CREATE UNIQUE INDEX ON simulation_logs (status) WHERE status = 'RUNNING'`
2. Или использовать advisory lock / `SELECT FOR UPDATE`
3. В SimulationScheduler: итерировать по tenant slugs, устанавливать `TenantContext.set(slug)` перед каждым вызовом, `TenantContext.clear()` в finally

## Tracked bugs
- BUG-76, BUG-77

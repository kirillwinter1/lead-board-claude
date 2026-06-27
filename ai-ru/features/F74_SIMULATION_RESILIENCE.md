# F74. Simulation Resilience (Circuit Breaker + Timeout)

**Версия:** 0.74.0 | **Дата:** 2026-06-27

Защита AI-симуляции (F28) от зависаний и каскадных сбоев. Перенесено из заброшенной
ветки delivery-guide на текущий main.

## Проблема
- Симуляция выполняет действия в Jira по очереди. Если Jira недоступна, все действия
  падают по одному, бесполезно долбя API.
- Запланированный прогон не имел таймаута — мог зависнуть надолго.

## Решение
1. **Circuit breaker** (`SimulationExecutor`): счётчик подряд идущих ошибок; при
   `>= maxConsecutiveFailures` (по умолч. 3) — прерывает остаток действий, помечая их
   `Skipped: circuit breaker...`. Сбрасывается при успехе.
2. **Таймаут** (`SimulationScheduler`): прогон обёрнут в `ExecutorService` с
   `timeoutMinutes` (по умолч. 5); при превышении — аборт. **Tenant-aware loop
   (BUG-77) сохранён** — таймаут оборачивает существующую итерацию по тенантам.
3. **Конфиг** (`SimulationProperties` / `application.yml`):
   `simulation.max-consecutive-failures` (`SIMULATION_MAX_FAILURES`, 3),
   `simulation.timeout-minutes` (`SIMULATION_TIMEOUT_MINUTES`, 5),
   `spring.task.scheduling.pool.size: 3`.

## Тесты
`SimulationExecutorTest`: `execute_circuitBreaker_abortsAfterConsecutiveFailures`,
`execute_circuitBreaker_resetsOnSuccess`. Tenant-aware тесты `SimulationSchedulerTest`
сохранены и зелёные.

# F49: Observability & Monitoring

**Версия:** 0.49.0
**Дата:** 2026-03-01
**Статус:** ✅ Готово

## Проблема

Отсутствие метрик и мониторинга. Невозможно понять что происходит в production: сколько запросов в секунду, какие endpoint'ы тормозят, состояние HikariCP пула, JVM heap. Проблемы обнаруживались только после жалоб пользователей или таймаутов в perf-тестах.

## Решение

Prometheus + Grafana стек с 3 дашбордами. Spring Boot Actuator экспортирует метрики через Micrometer, кастомный `ObservabilityMetrics` добавляет бизнес-метрики (sync duration, issue count).

## Изменения

### Backend

**Зависимости (`build.gradle.kts`):**
- `spring-boot-starter-actuator` — health, info, metrics endpoints
- `micrometer-registry-prometheus` — экспорт метрик в Prometheus формате

**Конфигурация (`application.yml`):**
- `management.endpoints.web.exposure.include: health,info,metrics,prometheus`
- `management.endpoint.health.show-details: when_authorized`
- `management.metrics.tags.application: lead-board` — глобальный тег

**ObservabilityMetrics (`config/ObservabilityMetrics.java`):**
- `@Component` с `MeterRegistry`
- `sync_duration_seconds` (Timer) — длительность Jira-синхронизации
- `sync_issues_total` (Gauge) — количество issues в кэше
- Методы: `recordSyncDuration(Duration)`, `updateIssueCount(long)`

**SyncService:**
- После завершения sync вызывает `observabilityMetrics.recordSyncDuration()` и `updateIssueCount()`

**TenantFilter:**
- Добавлен `tenant_slug` tag к метрикам HTTP-запросов (через `WebMvcTagsContributor`)

### Monitoring Stack

**Docker Compose (`monitoring/docker-compose.yml`):**
- Prometheus (порт 9090) — scrape каждые 15s с `localhost:8080/actuator/prometheus`
- Grafana (порт 3000) — admin/admin, auto-provisioned dashboards

**Grafana Dashboards (3 штуки):**

| Dashboard | Файл | Панели |
|-----------|------|--------|
| HTTP API | `http-api.json` | Request rate, p50/p95/p99 latency, error rate, top-10 slow endpoints |
| Business + HikariCP | `business-hikari.json` | Sync duration, issue count, pool active/pending/idle, connection wait time |
| JVM & System | `jvm-system.json` | Heap used/committed, GC pauses, threads, CPU, uptime |

## Ключевые файлы

| Файл | Изменения |
|------|-----------|
| `backend/build.gradle.kts` | +actuator, +micrometer-registry-prometheus |
| `backend/src/main/resources/application.yml` | management.endpoints, metrics config |
| `backend/src/main/java/.../config/ObservabilityMetrics.java` | Кастомные бизнес-метрики (sync, issues) |
| `backend/src/main/java/.../sync/SyncService.java` | Вызов recordSyncDuration/updateIssueCount |
| `backend/src/main/java/.../tenant/TenantFilter.java` | tenant_slug tag |
| `monitoring/docker-compose.yml` | Prometheus + Grafana stack |
| `monitoring/prometheus/prometheus.yml` | Scrape config |
| `monitoring/grafana/dashboards/*.json` | 3 dashboard JSON |
| `monitoring/grafana/provisioning/` | Auto-provisioning datasources + dashboards |

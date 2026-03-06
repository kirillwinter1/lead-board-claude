# F60 Production Monitoring

**Версия:** 0.60.0
**Дата:** 2026-03-06

## Описание

Расширение стека мониторинга для продакшн-деплоя: новые метрики, алертинг через Telegram, конфигурация Prometheus + Grafana + Alertmanager для сервера.

## Новые метрики (ObservabilityMetrics)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `leadboard.sync.issues_created` | Counter | Новые задачи при синке |
| `leadboard.sync.issues_updated` | Counter | Обновлённые задачи при синке |
| `leadboard.sync.last_success_timestamp` | Gauge | Epoch seconds последнего успешного синка |
| `leadboard.errors.total` | Counter (tag: type) | Ошибки по типам (jira_api, db, auth, rate_limit, other) |
| `leadboard.rate_limit.hits` | Counter | Срабатывания rate limit |

### Существующие метрики (F49)
- `leadboard.sync.duration` (Timer)
- `leadboard.sync.issues_synced` (Counter)
- `leadboard.sync.errors` (Counter)
- `leadboard.issues.total` (Gauge)
- `leadboard.tenants.active` (Gauge)

## Интеграция

- **SyncService**: отслеживает created vs updated при синке, вызывает `recordSyncSuccess()` при успехе
- **RateLimitFilter**: инкрементирует `recordRateLimitHit()` при срабатывании rate limit
- **SyncResult record**: внутренний record для передачи статуса (statusChanged, created) из saveOrUpdateIssue

## Alertmanager

### Правила алертов (alert.rules.yml)

| Alert | Условие | Severity |
|-------|---------|----------|
| BackendDown | `up == 0` за 2 мин | critical |
| SyncFailed | `sync_errors > 2` за 30 мин | critical |
| SyncStale | Нет успешного синка > 30 мин | warning |
| HighErrorRate | 5xx > 5% за 5 мин | warning |
| HighMemoryUsage | JVM heap > 80% за 5 мин | warning |
| HighDBPoolUsage | HikariCP active > 80% за 5 мин | warning |

### Telegram receiver
Alertmanager отправляет алерты через webhook в Telegram.

## Production Stack

### docker-compose.production.yml (monitoring/)

| Сервис | Image | Memory Limit | Port |
|--------|-------|-------------|------|
| prometheus | prom/prometheus:v2.51.0 | 256MB | 9090 (localhost) |
| grafana | grafana/grafana:10.4.1 | 128MB | 3030 (localhost) |
| alertmanager | prom/alertmanager:v0.27.0 | 64MB | 9093 (localhost) |

- Prometheus retention: 14 дней, max 2GB
- Grafana: доступ через nginx `/grafana/` sub-path
- Все порты привязаны к 127.0.0.1 (не доступны извне напрямую)

### Prometheus config (prometheus.prod.yml)
- Scrape target: `backend:8080` (Docker network)
- Scrape interval: 15s (prod) vs 10s (dev)

## Grafana Dashboard обновления

Новые панели в `business-hikari.json`:
- **Sync Created vs Updated** — stacked bar chart
- **Last Successful Sync** — stat panel (dateTimeFromNow)
- **Rate Limit Hits** — stat panel
- **Errors by Type** — stacked area chart

## Деплой

1. Скопировать `monitoring/` на сервер в `/opt/leadboard/monitoring/`
2. На prod использовать `prometheus.prod.yml` вместо `prometheus.yml`
3. Установить `GRAFANA_PASSWORD` в окружении
4. Настроить Telegram bot token в `alertmanager.yml`
5. Добавить nginx location для Grafana
6. `docker compose -f docker-compose.production.yml up -d`

## Тесты

- `ObservabilityMetricsTest` — 6 тестов (recordSyncDetails, recordSyncSuccess, recordRateLimitHit, recordError, recordSyncError, existingMetrics)

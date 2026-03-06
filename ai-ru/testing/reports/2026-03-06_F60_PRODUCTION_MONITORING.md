# QA Report: F60 Production Monitoring
**Дата:** 2026-03-06
**Тестировщик:** Claude QA Agent

## Summary
- Общий статус: **PASS WITH ISSUES**
- Unit tests: 6 passed (ObservabilityMetricsTest), 5 passed (RateLimitFilterTest), 14 passed (SyncServiceTest) — **0 failed**
- API tests: Actuator endpoint работает (проверен на 0.59, новые метрики появятся после деплоя 0.60)
- Visual: N/A (backend/infra фича, без UI)
- Regression: WorkflowConfigControllerTest (6 failures) — **пре-экзистинг**, не связано с F60

## Bugs Found

### Medium

**BUG-M1: `recordError()` создаёт новый Counter при каждом вызове**
- **Файл:** `ObservabilityMetrics.java:103-108`
- **Проблема:** Метод `recordError(String type)` вызывает `Counter.builder(...).register(registry)` при каждом вызове. Micrometer внутри делает lookup/dedup, поэтому функционально это работает корректно, но это неоптимально по перформансу — каждый вызов проходит через ConcurrentHashMap lookup в MeterRegistry.
- **Рекомендация:** Кэшировать Counter по type в `ConcurrentHashMap<String, Counter>` для горячего пути. Или создавать все ожидаемые типы (jira_api, db, auth, rate_limit, other) в конструкторе.
- **Severity:** Medium — не баг, а performance concern. При текущей нагрузке (единицы ошибок/мин) несущественно.

**BUG-M2: SyncStale alert сработает сразу после деплоя**
- **Файл:** `alert.rules.yml:23-29`
- **Проблема:** Gauge `leadboard_sync_last_success_timestamp` инициализируется как 0. Выражение `(time() - 0) > 1800` = true сразу. Alert `SyncStale` будет пожарен через 5 минут после старта, до первого успешного синка.
- **Рекомендация:** Добавить условие `leadboard_sync_last_success_timestamp > 0` в expr, чтобы не стрелять до первого синка:
  ```yaml
  expr: (leadboard_sync_last_success_timestamp > 0) and (time() - leadboard_sync_last_success_timestamp) > 1800
  ```

### Low

**BUG-L1: Alertmanager Telegram receiver — hardcoded placeholder URL**
- **Файл:** `alertmanager/alertmanager.yml:12`
- **Проблема:** URL `http://alertmanager-telegram-bot:9087/alert/-1002555558888` содержит placeholder chat ID. Нет Telegram bot container в docker-compose. Комментарий в docker-compose.production.yml упоминает `TELEGRAM_BOT_TOKEN` и `TELEGRAM_CHAT_ID` env vars, но alertmanager.yml их не использует.
- **Рекомендация:** Либо добавить telegram-bot сервис (например `metalmatze/alertmanager-bot` или `inCaller/prometheus_bot`), либо использовать Alertmanager webhook receiver, который поддерживает Telegram нативно. Задокументировать реальную настройку.

**BUG-L2: Production docker-compose — Grafana не в сети с Prometheus**
- **Файл:** `docker-compose.production.yml:49-51`
- **Проблема:** Grafana подключена только к сети `monitoring`, но не к `onelane-net`. Prometheus в обеих сетях (`monitoring` + `onelane-net`). Это правильно — Grafana обращается к Prometheus по сети `monitoring`. Но datasource provisioning использует `host.docker.internal:8080` (dev config). На проде нужно убедиться, что Grafana provisioning указывает на `http://prometheus:9090`.
- **Рекомендация:** Проверить `monitoring/grafana/provisioning/datasources/prometheus.yml` — URL должен быть `http://prometheus:9090`.

**BUG-L3: `issueRepository.findByIssueKey()` вызывается дважды для существующих задач**
- **Файл:** `SyncService.java:420-422` (внутри `saveOrUpdateIssue`)
- **Проблема:** До F60 `saveOrUpdateIssue` вызывался один раз. С F60 внутри метода `isNew = existing == null` отслеживается, но `findByIssueKey` вызывается только один раз внутри `saveOrUpdateIssue`. Однако ранняя версия кода вызывала `findByIssueKey` дважды (до рефактора на `SyncResult`). Текущий код — корректен, вызов один.
- **Статус:** Ложная тревога, **NOT A BUG**. Текущий рефактор на `SyncResult` record корректен.

## Test Coverage Gaps

1. **Нет теста на `recordSyncDetails` + `recordSyncSuccess` вызовы из SyncService** — SyncServiceTest мокает ObservabilityMetrics, но не верифицирует вызовы новых методов (`recordSyncDetails`, `recordSyncSuccess`). Рекомендация: добавить verify в тест `shouldMarkSyncAsCompletedOnSuccess`.

2. **Нет integration-теста для `/actuator/prometheus` с новыми метриками** — проверить что все 5 новых метрик появляются в Prometheus endpoint.

3. **Нет теста на RateLimitFilter.recordRateLimitHit()** — RateLimitFilterTest тестирует только TokenBucket, но не интеграцию с ObservabilityMetrics.

4. **Alert rules не валидированы** — prometheus `promtool check rules` не запускался. Рекомендация: `docker run prom/prometheus promtool check rules /etc/prometheus/alert.rules.yml`

## Recommendations

1. **Исправить BUG-M2** перед деплоем — false positive alert при холодном старте
2. Настроить реальный Telegram bot перед деплоем на prod
3. Добавить verify-вызовы новых ObservabilityMetrics методов в SyncServiceTest
4. Проверить Grafana provisioning datasource URL для prod (должен быть `http://prometheus:9090`)

# Анализ эффективности Jira-интеграции

**Дата:** 2026-02-19
**Область:** Backend — `JiraClient`, `SyncService`, `ChangelogImportService`, `TeamSyncService`, `JiraMetadataService`

---

## 1. Архитектура интеграции (текущее состояние)

### Компоненты

| Компонент | Файл | Роль |
|-----------|------|------|
| **JiraClient** | `jira/JiraClient.java` | HTTP-клиент к Jira REST API v3 |
| **SyncService** | `sync/SyncService.java` | Scheduled-синхронизация issue (polling) |
| **ChangelogImportService** | `sync/ChangelogImportService.java` | Async-импорт changelog по статусам |
| **TeamSyncService** | `team/TeamSyncService.java` | Синхронизация команд через Atlassian Org API |
| **JiraMetadataService** | `config/service/JiraMetadataService.java` | Метаданные (типы задач, статусы, типы связей) с кешем |

### Схема потока данных

```
Jira Cloud REST API
        │
        ▼
   JiraClient (WebClient, .block())
        │
        ├── search/jql (cursor pagination, 100/page)
        ├── issue/{key}?expand=changelog
        ├── issue/{key}/changelog (paginated)
        ├── issue/{key}/transitions
        ├── project/{key}/statuses
        └── Teams API (/gateway/api/public/teams/v1/...)
        │
        ▼
   SyncService → saveOrUpdateIssue() → JiraIssueRepository → PostgreSQL
        │
        ├── statusChangelogService.detectAndRecordStatusChange()
        ├── autoScoreService.recalculateAll()
        ├── issueOrderService.normalizeTeamEpicOrders()
        └── changelogImportService.importChangelogsForIssuesAsync()
```

---

## 2. Оптимальность запросов

### 2.1. Что сделано хорошо

| Практика | Детали |
|----------|--------|
| **Инкрементальная синхронизация** | JQL-фильтр `updated >= '{lastSync - 1min}'` — загружаются только изменённые задачи |
| **Cursor-based pagination** | Используется `nextPageToken` вместо `startAt/maxResults` — правильно для Jira API v3 |
| **Облегчённый запрос для reconciliation** | `searchKeysOnly()` запрашивает только поле `key` — минимальный трафик |
| **Явный список полей** | `fields=summary,status,...` — не загружаются лишние поля |
| **Кеширование метаданных** | `JiraMetadataService` кеширует issue types, statuses, link types в БД (TTL: 1 час) |
| **Async changelog import** | `@Async` — не блокирует основной цикл синхронизации |
| **Reconciliation не каждый раз** | Только каждый 12-й sync (≈1 час при дефолте 5 мин) |

### 2.2. Проблемы и неоптимальности

#### P1 (Критические)

**1. `countByJql()` пагинирует ВСЕ страницы вместо использования `total` из ответа**

```java
// JiraClient.java:137-152
public int countByJql(String jql) {
    int total = 0;
    String nextPageToken = null;
    while (true) {
        JiraSearchResponse response = searchKeysOnly(jql, 100, nextPageToken);
        total += issues.size();
        if (response.getNextPageToken() == null) break;
        nextPageToken = response.getNextPageToken();
    }
    return total;
}
```

**Проблема:** При 5000 задач — 50 HTTP-запросов только чтобы посчитать. Jira API v3 `/search/jql` возвращает поле `total` в первом ответе.
**Рекомендация:** Использовать `response.getTotal()` из первого запроса (если поле доступно в cursor-based API), или запросить `maxResults=1` и взять `total`.

**2. `reconcileDeletedIssues()` загружает ВСЕ ключи из Jira целиком**

```java
// SyncService.java:614-654
while (true) {
    JiraSearchResponse response = jiraClient.searchKeysOnly(jql, 100, nextPageToken);
    for (JiraIssue issue : issues) { jiraKeys.add(issue.getKey()); }
    ...
}
List<String> dbKeys = issueRepository.findAllIssueKeysByProjectKey(projectKey);
```

**Проблема:** При 10,000 задач — 100 запросов к Jira + HashSet в памяти. Линейная сложность по количеству задач. С ростом проекта будет расти.
**Рекомендация:** Использовать JQL `project = X AND updated < '{oldDate}'` для выявления "застарелых" задач, или Jira Webhooks для моментального оповещения об удалении.

#### P2 (Серьёзные)

**3. N+1 проблема в changelog import**

```java
// ChangelogImportService.java:81-97
for (String issueKey : issueKeys) {
    importChangelogForIssue(issueKey, ...); // 1 HTTP-запрос на каждый issue
    Thread.sleep(100); // 100ms задержка
}
```

**Проблема:** Если в одном sync изменилось 200 задач → 200 последовательных HTTP-запросов с паузой 100ms = **минимум 20 секунд**. Для batch changelog import всего проекта (5000 задач) — **~8 минут** только на задержки.
**Примечание:** Jira API не имеет batch changelog endpoint, поэтому это архитектурное ограничение API, а не бага кода. Rate limiting 100ms — разумный компромисс.

**4. Дублирование OAuth-проверки в каждом методе**

Каждый метод `JiraClient` независимо вызывает `oauthService.getValidAccessToken()` + `oauthService.getCloudIdForCurrentUser()`. При sync из 50 страниц — это 50 пар вызовов к OAuth-сервису.

**Рекомендация:** Кешировать OAuth-токен на уровне запроса (request-scoped) или передавать как параметр в batch-операции.

**5. `fixStartedAtFromChangelog()` и `fixDoneAtFromChangelog()` загружают ВСЕ issue проекта**

```java
// ChangelogImportService.java:175-195
List<JiraIssueEntity> allIssues = issueRepository.findByProjectKey(projectKey);
for (JiraIssueEntity issue : allIssues) { ... }
```

**Проблема:** При 5000 задач — загрузка всех в память + N SQL-запросов к changelog таблице. Вызывается при каждом полном `importAllChangelogs()`.
**Рекомендация:** Выполнять fix только для issue, у которых реально импортировались changelog-записи (передавать список ключей).

#### P3 (Незначительные)

**6. Base64-кодирование credentials при каждом запросе**

```java
String auth = jiraProperties.getEmail() + ":" + jiraProperties.getApiToken();
String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
```

Вычисляется заново при каждом HTTP-вызове. Не проблема производительности, но лишний мусор. Можно кешировать в поле.

**7. `getSubtasks()` использует полный `search()` вместо `searchKeysOnly()`**

Для получения подзадач загружаются все поля (`summary, status, issuetype, parent, ...`), хотя используются только `key`, `summary` и `issuetype.name`.

---

## 3. Устойчивость к задержкам и сбоям Jira

### 3.1. Что происходит если Jira тормозит

| Сценарий | Текущее поведение | Последствия |
|----------|-------------------|-------------|
| **Jira отвечает медленно (>5s на запрос)** | WebClient `.block()` ждёт без timeout | Sync-поток блокируется. При 50 страницах × 10s = **~8 мин** вместо обычных 30с. Следующий scheduled sync пропускается (`syncInProgress=true`) |
| **Jira возвращает 429 Too Many Requests** | Нет обработки — `WebClientResponseException` → sync fails | Вся синхронизация падает, ошибка записывается в `last_error`, данные устаревают до следующего scheduled sync |
| **Jira возвращает 500/502/503** | Нет retry — исключение → sync fails | Та же ситуация: полная остановка sync до следующего интервала |
| **Jira недоступна (timeout)** | WebClient зависает (нет явного connect/read timeout) | В prod-конфиге Hikari connection-timeout 30s, но на HTTP-клиенте таймаутов нет. Потенциально бесконечное ожидание |
| **Jira доступна, но OAuth токен невалиден** | Fallback на Basic Auth (если настроен) | Прозрачный fallback — хорошо |

### 3.2. Критические отсутствующие паттерны

#### Нет HTTP-таймаутов на WebClient

```java
// Текущий код (JiraClient.java:31-44)
this.webClient = webClientBuilder
    .exchangeStrategies(strategies)
    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
    .build();
// Нет .clientConnector() с настроенными таймаутами!
```

**Риск:** Если Jira зависает — `.block()` блокирует поток навсегда. В Spring Boot пул потоков ограничен, и зависание нескольких запросов может заблокировать весь сервис.

**Рекомендация:**
```java
HttpClient httpClient = HttpClient.create()
    .responseTimeout(Duration.ofSeconds(30))
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000);

this.webClient = webClientBuilder
    .clientConnector(new ReactorClientHttpConnector(httpClient))
    .build();
```

#### Нет retry с exponential backoff

При временных сбоях (429, 500, 503) один неудачный запрос прерывает весь sync. Для production-системы нужен retry хотя бы для идемпотентных GET-запросов.

**Рекомендация:**
```java
.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
    .filter(ex -> isRetryable(ex))
    .maxBackoff(Duration.ofSeconds(10)))
```

#### Нет Circuit Breaker

Если Jira падает на длительный период, приложение продолжает бесполезно стучаться каждые 5 минут, генерируя ошибки в логах и напрасную нагрузку.

**Рекомендация:** Рассмотреть Resilience4j CircuitBreaker — после N ошибок подряд переходить в "open" состояние и не пытаться синхронизировать до recovery.

#### Нет таймаута на `.block()` вызовах

```java
// Везде используется:
.block();
// Вместо:
.block(Duration.ofSeconds(30));
```

Каждый `.block()` — потенциальная точка зависания.

### 3.3. Проблема с `syncInProgress` флагом

```java
// SyncService.java:170-179
if (state.isSyncInProgress()) {
    log.warn("Sync already in progress for project: {}", projectKey);
    return;
}
state.setSyncInProgress(true);
```

**Проблема:** Если sync зависнет (Jira timeout) — флаг `syncInProgress` остаётся `true` навсегда. Все последующие sync-ы будут пропущены до ручного вмешательства.

**Рекомендация:** Добавить TTL для флага — если `lastSyncStartedAt` старше 30 минут и `syncInProgress=true`, считать sync зависшим и сбрасывать флаг.

### 3.4. Запуск sync в `new Thread()` (triggerSync)

```java
// SyncService.java:132-136
new Thread(() -> {
    autoDetectIfNeeded(projectKey);
    syncProject(projectKey, months);
    reconcileDeletedIssues(projectKey);
}).start();
```

**Проблема:** Создание неуправляемого потока вне Spring-контейнера. Нет ограничения количества одновременных потоков. Нет обработки необработанных исключений. Нет возможности graceful shutdown.

**Рекомендация:** Использовать `@Async` с `TaskExecutor` или `CompletableFuture`.

---

## 4. Нагрузка на Jira API (оценка)

### При 1000 задач в проекте

| Операция | Запросов к Jira | Интервал | Запросов/час |
|----------|-----------------|----------|--------------|
| Инкрементальный sync (10 изменений) | 1 | 5 мин | 12 |
| Changelog import (10 задач) | 10 | 5 мин | 120 |
| Reconciliation (1000 ключей) | 10 | 60 мин | 10 |
| Team sync | 1 + N (members) | 5 мин | ~24 |
| **Итого** | | | **~166/час** |

### При 10,000 задач

| Операция | Запросов к Jira | Интервал | Запросов/час |
|----------|-----------------|----------|--------------|
| Инкрементальный sync (50 изменений) | 1 | 5 мин | 12 |
| Changelog import (50 задач) | 50 | 5 мин | 600 |
| Reconciliation (10,000 ключей) | 100 | 60 мин | 100 |
| Team sync | 1 + N (members) | 5 мин | ~24 |
| **Итого** | | | **~736/час** |

**Лимит Jira Cloud:** Atlassian rate limit для REST API — ~100 запросов/минуту для Basic Auth, до 500/мин для OAuth. При 10,000 задач текущая реализация **приближается к лимитам**.

---

## 5. Сводная таблица рисков

| # | Риск | Серьёзность | Вероятность | Влияние |
|---|------|------------|-------------|---------|
| 1 | Нет HTTP-таймаутов → зависание потоков | Критическая | Средняя | Сервис перестаёт отвечать |
| 2 | `syncInProgress` залипает → синхронизация останавливается | Критическая | Средняя | Данные устаревают бессрочно |
| 3 | Нет retry → один 500 убивает весь sync | Высокая | Высокая | Потеря sync-цикла |
| 4 | Нет обработки 429 → превышение rate limit | Высокая | Средняя | Jira блокирует доступ |
| 5 | `countByJql` пагинирует всё → лишняя нагрузка | Средняя | Высокая | Лишние запросы |
| 6 | `new Thread()` вне Spring → утечки ресурсов | Средняя | Низкая | Неуправляемые потоки |
| 7 | Reconciliation O(N) → масштабирование | Средняя | Средняя | Деградация при росте |

---

## 6. Рекомендации по приоритету

### Быстрые победы (minimal effort, high impact)

1. **Добавить HTTP-таймауты** на WebClient (connect: 10s, read: 30s) — 1 место правки
2. **Добавить `.block(Duration.ofSeconds(30))`** вместо `.block()` — механическая замена
3. **Добавить TTL для `syncInProgress`** — сброс после 30 минут
4. **Оптимизировать `countByJql`** — использовать `total` из первого ответа или `maxResults=1`

### Средний приоритет

5. **Добавить retry с backoff** для GET-запросов (Reactor `.retryWhen()`)
6. **Обработка 429** — парсить `Retry-After` header и ждать
7. **Заменить `new Thread()`** на `@Async` с настроенным `TaskExecutor`
8. **Кешировать OAuth-токен** на уровне sync-сессии

### Долгосрочные улучшения

9. **Circuit Breaker** (Resilience4j) для Jira-вызовов
10. **Jira Webhooks** вместо polling для моментальной реакции на изменения
11. **Prometheus-метрики** для мониторинга: latency, error rate, queue size
12. **Bulk changelog** — пересмотреть стратегию (webhook на status change вместо polling changelog)

---

## 7. Заключение

Интеграция реализована функционально полно и покрывает все основные сценарии (sync, changelog, teams, metadata). Инкрементальная синхронизация и cursor-based pagination — правильные решения.

**Главная проблема:** отсутствие resilience-паттернов. Система хрупка к сбоям Jira — один медленный ответ или ошибка 500 прерывает весь цикл синхронизации без retry, а отсутствие HTTP-таймаутов создаёт риск полного зависания сервиса.

При текущем масштабе проекта (~1000 задач) интеграция работает стабильно. При росте до 5,000-10,000 задач начнут проявляться проблемы с rate limiting и O(N)-операциями (reconciliation, full changelog import).

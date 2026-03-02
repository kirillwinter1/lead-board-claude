# QA Report: F54 Semantic Search (pgvector)
**Дата:** 2026-03-02
**Тестировщик:** Claude QA Agent

## Summary
- Общий статус: **PASS WITH ISSUES**
- Unit tests: 7 passed, 0 failed (EmbeddingServiceTest)
- Regression tests: All ChatToolExecutorTest (16) + SyncServiceTest pass
- API tests: **NOT RUN** (backend не стартует — pre-existing multi-tenancy issue, не связано с F54)
- Visual tests: **NOT RUN** (backend down)
- Code review: **4 bugs found** (0 Critical, 1 High, 2 Medium, 1 Low)

## Bugs Found

### BUG-145 [High] — Semantic search RBAC bypass: `findByEmbeddingSimilarity` returns results from ALL teams when teamId=null

**Файл:** `ChatToolExecutor.java:296`, `JiraIssueRepository.java:271-273`

**Проблема:** Когда `teamId` не указан (null), `embeddingService.search(query, null, 20)` вызывает `findByEmbeddingSimilarity` — запрос без фильтра по `team_id`. Для ADMIN/PM это ОК, но для TEAM_LEAD/MEMBER с null teamId `checkTeamAccess(null)` возвращает `true`, и семантический поиск вернёт задачи ВСЕХ команд.

**Сравнение с fallback:** В fallback-ветке (substring match) при `teamId=null` тоже загружаются все задачи через `findByBoardCategory("EPIC")` — **это pre-existing поведение**, не регрессия F54. Но семантический поиск усиливает проблему: он может вернуть задачи любого типа (SUBTASK), а не только EPIC/STORY/BUG.

**Ожидаемое:** Семантический поиск для не-админов без teamId должен фильтровать по `getUserTeamIds()`.

**Шаги:**
1. Пользователь с ролью TEAM_LEAD (команда 1) вызывает task_search без teamId
2. Semantic search возвращает задачи команд 1, 2, 3 — все
3. RBAC нарушен

---

### BUG-146 [Medium] — Semantic search returns SUBTASK, but fallback only searches EPIC/STORY/BUG

**Файл:** `JiraIssueRepository.java:271-273`, `ChatToolExecutor.java:295-314`

**Проблема:** `findByEmbeddingSimilarity` ищет по ВСЕМ jira_issues (включая SUBTASK, задачи с NULL category). Fallback ищет только EPIC+STORY+BUG. Несогласованность: при одном и том же запросе, если embedding выключен — пользователь видит только эпики/стори/баги, если включен — может увидеть сабтаски.

**Ожидаемое:** Либо фильтровать по `board_category IN ('EPIC','STORY','BUG')` в SQL-запросе семантического поиска, либо фильтровать после в Java.

---

### BUG-147 [Medium] — @Async `generateAndStoreAsync` вызывается на non-persisted entity (потенциальная гонка)

**Файл:** `SyncService.java:590`

**Проблема:** `generateAndStoreAsync(entity)` вызывается сразу после `issueRepository.save(entity)` (строка 588). Метод `@Async` выполняется в другом потоке. Внутри `generateAndStore` вызывается `issueRepository.updateEmbedding(entity.getId(), vectorString)` — native UPDATE.

Потенциальный сценарий гонки:
1. Main thread: `save(entity)` → flush к БД
2. Main thread: `generateAndStoreAsync(entity)` — отправляет в очередь
3. Async thread: `generateEmbedding(text)` — сетевой вызов ~200ms
4. Main thread (следующая итерация синка): `save(entity)` для другой задачи
5. Async thread: `updateEmbedding(id, vec)` — ОК, entity уже персистирована

На практике маловероятно (save + flush гарантирует ID), но при batch-операциях и транзакционных проблемах `entity.getId()` может быть stale. **Рекомендация:** передавать в async метод `entity.getId()` и текст (`summary+description`), а не весь entity.

---

### BUG-148 [Low] — `toVectorString` использует `String.valueOf(float)` — потеря точности

**Файл:** `EmbeddingService.java:119`

**Проблема:** `String.valueOf(float)` для значений вроде `0.123456789` может дать `0.12345679` (потеря на 7-8 знаке из-за float precision). Embedding model возвращает float32, а `String.valueOf(float)` не всегда точно отражает значение.

**Влияние:** Минимальное. Потеря precision на 7-8 знаке float не влияет значимо на cosine similarity. Но для best practice лучше использовать `Float.toString()` (тот же результат) или `String.format("%.8f", value)` для контроля precision.

**Приоритет:** Low — не влияет на функциональность.

## Code Review Findings

### Положительные стороны:
1. **Graceful degradation** — все методы EmbeddingService начинаются с `if (!enabled || client == null) return` — безопасно при отключенном embedding
2. **@ConditionalOnProperty** на OpenAiEmbeddingClient — bean не создаётся при выключенном embedding, экономия ресурсов
3. **Conditional SQL migration (V46)** — проверяет pg_available_extensions перед CREATE EXTENSION, не ломает production без pgvector
4. **T6 tenant migration** — отдельная миграция для tenant-схем (проверяет pg_extension вместо pg_available_extensions — правильно, т.к. extension уже создана в public)
5. **TenantAwareAsyncConfig** — @Async корректно пропагирует TenantContext
6. **Fallback logic** — если semantic search пуст → substring match, корректная деградация
7. **`searchMode` в ответе** — позволяет отлаживать, каким путём найден результат
8. **No pgvector Java library needed** — умный подход с String + cast в SQL

### Замечания:
1. **Нет rate limiting на embedding API calls** — каждый sync-save вызывает generateAndStoreAsync. При bulk sync 1000 задач → 1000 concurrent embedding API calls. OpenRouter может заблокировать.
2. **Нет ограничения длины текста** — `buildText()` конкатенирует summary + description. Description может быть ~100KB. Embedding API имеет лимит токенов (обычно 8191 для text-embedding-3-small).
3. **`reindexAll()` не имеет batch/throttle** — если 10K задач без embedding, все вызовы последовательные, но могут занять часы и забить API.

## Test Coverage

### Покрытые сценарии (EmbeddingServiceTest — 7 тестов):
- [x] disabled mode: generateAndStore does nothing
- [x] disabled mode: search returns empty
- [x] generateAndStore: happy path (mock client + mock repository)
- [x] generateAndStore: null embedding from client → skipped
- [x] search: happy path (no teamId)
- [x] search: with teamId → uses team-filtered query
- [x] toVectorString: format validation

### Gaps:
- [ ] **ChatToolExecutor.taskSearch** с semantic search — нет теста на новую ветку кода (семантический → fallback)
- [ ] **reindexAll** — не тестировался
- [ ] **generateAndStoreAsync** — @Async не тестируется (нужен integration test с @SpringBootTest)
- [ ] **OpenAiEmbeddingClient** — нет unit-тестов (network mock needed)
- [ ] **buildText() с null summary + null description** — не тестировался (вернёт пустую строку, но isBlank() отловит)
- [ ] **Error handling** — exception в embeddingClient.generateEmbedding() → тест что warn logged и entity не обновлена

## DB Migration Review

### V46 (public schema):
```sql
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
    CREATE EXTENSION IF NOT EXISTS vector;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'jira_issues' AND table_schema = current_schema()) THEN
      ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS embedding vector(1536);
      CREATE INDEX IF NOT EXISTS idx_jira_issues_embedding ON jira_issues USING hnsw(embedding vector_cosine_ops);
    END IF;
  END IF;
END $$;
```
**Verdict:** ✅ Корректно. Двойная условность: pgvector доступен + таблица существует.

### T6 (tenant schema):
```sql
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
    ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS embedding vector(1536);
    CREATE INDEX IF NOT EXISTS idx_jira_issues_embedding ON jira_issues USING hnsw(embedding vector_cosine_ops);
  END IF;
END $$;
```
**Verdict:** ✅ Корректно. Проверяет pg_extension (уже установленный) вместо pg_available_extensions.

### Проверка на реальной БД:
- V46 migration: ✅ success=true в flyway_schema_history
- pgvector доступен на localhost: ✅ (но не через Docker — native PostgreSQL)
- `jira_issues` не существует в public schema → миграция корректно пропущена

## Recommendations

1. **[High] Исправить BUG-145** — добавить RBAC-фильтрацию в semantic search для не-админов
2. **[Medium] Исправить BUG-146** — добавить `WHERE board_category IN ('EPIC','STORY','BUG')` в similarity query или фильтровать в Java
3. **[Medium] Добавить text truncation** — ограничить input для embedding API (например, 4000 символов)
4. **[Low] Добавить batch throttle** в reindexAll — например, 50ms sleep между вызовами, или batch по 100
5. **[Low] Добавить тест** на taskSearch с семантическим поиском в ChatToolExecutorTest

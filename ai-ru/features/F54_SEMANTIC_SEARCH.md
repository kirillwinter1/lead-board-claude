# F54 — Semantic Search (pgvector)

**Статус:** ✅ Реализовано (v0.54.0)
**Дата:** 2026-03-02

## Описание

Семантический поиск задач через pgvector embeddings. Чат-ассистент (F52/F53) теперь ищет задачи по смыслу, а не только по подстроке. Полностью конфигурируемый — отключается через `CHAT_EMBEDDING_ENABLED=false` (по умолчанию выключен).

## Архитектура

### Компоненты

```
Jira Sync → SyncService → generateAndStoreAsync() → EmbeddingService → OpenAI API
                                                                         ↓
                                                                    pgvector DB
                                                                         ↑
ChatToolExecutor.taskSearch() → EmbeddingService.search() → cosine similarity
                              ↓ (fallback)
                         substring match
```

### Backend (embedding package)

| Класс | Назначение |
|-------|-----------|
| `EmbeddingClient` | Интерфейс генерации embeddings |
| `OpenAiEmbeddingClient` | Реализация через OpenAI-совместимый API. `@ConditionalOnProperty("chat.embedding-enabled")` |
| `EmbeddingService` | Оркестратор: генерация, хранение, поиск, реиндексация |

### Ключевые методы EmbeddingService

| Метод | Описание |
|-------|----------|
| `generateAndStore(entity)` | Генерация embedding из summary+description, сохранение через native UPDATE |
| `generateAndStoreAsync(entity)` | @Async обёртка — вызывается после sync |
| `search(query, teamId, limit)` | Семантический поиск через cosine similarity (pgvector `<=>`) |
| `reindexAll()` | Генерация embeddings для всех задач без них |

### Хранение embeddings

Embedding хранится как `vector(1536)` (pgvector тип) в колонке `jira_issues.embedding`. Индекс: `hnsw(embedding vector_cosine_ops)`.

**Подход без pgvector Java-библиотеки:** embedding передаётся как `String` формата `[0.1,0.2,...]` и кастится в SQL через `cast(:vec as vector)`. Это избавляет от зависимости на pgvector-java.

### Интеграция с ChatToolExecutor

`taskSearch()` (инструмент `task_search`):
1. Если embedding включён и есть `query` → семантический поиск
2. Если результатов нет → fallback на substring match
3. В ответе поле `searchMode: "semantic" | "substring"` — для отладки

### Интеграция с SyncService

После `issueRepository.save(entity)` вызывается `embeddingService.generateAndStoreAsync(entity)` — embedding обновляется асинхронно при каждой синхронизации.

## Миграции

### V46 (public schema)
```sql
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
    CREATE EXTENSION IF NOT EXISTS vector;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'jira_issues') THEN
      ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS embedding vector(1536);
      CREATE INDEX IF NOT EXISTS idx_jira_issues_embedding ON jira_issues USING hnsw(embedding vector_cosine_ops);
    END IF;
  END IF;
END $$;
```

### T6 (tenant schema)
```sql
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
    ALTER TABLE jira_issues ADD COLUMN IF NOT EXISTS embedding vector(1536);
    CREATE INDEX IF NOT EXISTS idx_jira_issues_embedding ON jira_issues USING hnsw(embedding vector_cosine_ops);
  END IF;
END $$;
```

**Условность:** Миграция проверяет наличие pgvector расширения. Без него — колонка и индекс не создаются, embedding просто отключён.

## Конфигурация

```yaml
chat:
  embedding-enabled: ${CHAT_EMBEDDING_ENABLED:false}
  embedding-model: ${CHAT_EMBEDDING_MODEL:text-embedding-3-small}
  embedding-base-url: ${CHAT_EMBEDDING_BASE_URL:https://api.openai.com/v1}
```

| Параметр | Описание | По умолчанию |
|----------|----------|-------------|
| `embedding-enabled` | Включение/отключение | `false` |
| `embedding-model` | Модель для embeddings | `text-embedding-3-small` |
| `embedding-base-url` | Базовый URL API | `https://api.openai.com/v1` |

Для работы embedding нужен API key провайдера (OpenAI или совместимый). Без ключа или с `embedding-enabled=false` — всё работает без embeddings, fallback на substring search.

## Docker

Для pgvector поддержки используется образ `pgvector/pgvector:pg15` вместо `postgres:15` в `docker-compose.yml`.

## Native Queries (JiraIssueRepository)

```java
findByEmbeddingSimilarity(vec, lim)           // cosine similarity, все задачи
findByEmbeddingSimilarityAndTeamId(vec, teamId, lim) // с фильтром по команде
findWithoutEmbedding()                        // задачи без embedding (для reindex)
updateEmbedding(id, vectorString)             // обновление embedding
```

**Важно:** Это native queries — они работают только в контексте tenant schema (Hibernate SET search_path). AdminController endpoint `/api/admin/embeddings/reindex` вызывает `reindexAll()`.

## Тесты

### EmbeddingServiceTest — 7 тестов

| Тест | Покрытие |
|------|----------|
| disabled mode: generateAndStore does nothing | Graceful degradation |
| disabled mode: search returns empty | Graceful degradation |
| generateAndStore: happy path | Mock client + mock repository |
| generateAndStore: null embedding → skipped | Null safety |
| search: happy path (no teamId) | Все задачи |
| search: with teamId | Team-filtered |
| toVectorString: format validation | String формат |

## Известные ограничения

1. **Нет rate limiting на embedding API calls** — при bulk sync возможна перегрузка API
2. **Нет ограничения длины текста** — description может быть очень длинным
3. **reindexAll() без throttle** — для 10K задач может занять часы
4. **Float precision** — `String.valueOf(float)` теряет точность на 7-8 знаке (минимальное влияние)

## Файлы

### Backend (NEW)
- `chat/embedding/EmbeddingClient.java` — интерфейс
- `chat/embedding/OpenAiEmbeddingClient.java` — реализация (@ConditionalOnProperty)
- `chat/embedding/EmbeddingService.java` — сервис
- `db/migration/V46__add_embeddings_support.sql`
- `db/tenant/T6__add_embeddings_support.sql`

### Backend (MODIFY)
- `chat/ChatProperties.java` — +embeddingEnabled, embeddingModel, embeddingBaseUrl
- `chat/tools/ChatToolExecutor.java` — taskSearch: semantic → fallback
- `sync/JiraIssueRepository.java` — +4 native queries (embedding similarity, update, findWithout)
- `sync/SyncService.java` — хук generateAndStoreAsync после save
- `admin/AdminController.java` — endpoint /api/admin/embeddings/reindex
- `application.yml` — chat.embedding-* properties
- `docker-compose.yml` — pgvector/pgvector:pg15

### Tests (NEW)
- `chat/embedding/EmbeddingServiceTest.java` — 7 тестов

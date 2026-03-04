# F58 — Семантический поиск на Board

**Статус:** ✅ Завершена
**Дата:** 2026-03-04
**Версия:** 0.58.0

## Описание

Интеграция семантического поиска (pgvector, F54) с фильтрацией доски. Поиск по содержимому задач (summary, description) через AI-embeddings с fallback на substring-поиск. Короткие запросы (< 3 символов) работают мгновенно через локальную фильтрацию по issue key.

## Архитектура

### Поисковый пайплайн

```
Пользователь вводит запрос (3+ символов)
  → 300ms debounce
  → GET /api/board/search?q=...&teamIds=...
  → EmbeddingService.search() (pgvector cosine similarity)
  → Резолвинг к epic keys:
      - Epic → issueKey
      - Story/Bug → parentKey (epic)
      - Subtask → parent.parentKey (grandparent epic)
  → Если пусто → fallback substring по key + summary
  → BoardSearchResponse(matchedEpicKeys, searchMode)
  → Фронтенд фильтрует загруженную доску по matchedEpicKeys
  → Бейдж AI (semantic) или TXT (substring)
```

### Два режима поиска

| Длина запроса | Режим | Где выполняется |
|---------------|-------|-----------------|
| 1-2 символа | Локальный по issue key | Frontend (мгновенно) |
| 3+ символов | Семантический → fallback substring | Backend (debounced 300ms) |

## Backend

### Новые файлы

| Файл | Описание |
|------|----------|
| `board/BoardSearchResponse.java` | DTO: `record(List<String> matchedEpicKeys, String searchMode)` |
| `board/BoardServiceSearchTest.java` | 8 тестов |

### Изменённые файлы

| Файл | Изменения |
|------|-----------|
| `board/BoardService.java` | `EmbeddingService` (optional `@Autowired`) + `searchForBoard()`, `resolveToEpicKeys()`, `substringSearch()` |
| `controller/BoardController.java` | `GET /api/board/search?q=&teamIds=` (валидация q >= 2) |

### API

```
GET /api/board/search?q={query}&teamIds={id1}&teamIds={id2}

Response:
{
  "matchedEpicKeys": ["LB-1", "LB-5", "LB-12"],
  "searchMode": "semantic" | "substring"
}

400 — если q.length < 2
```

### Метод `searchForBoard(query, teamIds)`

1. Проверка projectKey
2. Семантический поиск через `embeddingService.search(query, teamId, 30)`
3. Резолвинг найденных задач к epic keys:
   - **Epic** → берём issueKey напрямую
   - **Story/Bug** → берём parentKey (это epic)
   - **Subtask** → находим parent (story), берём его parentKey (epic)
4. Фильтрация по teamIds
5. Если семантика пустая → fallback: substring по key + summary среди эпиков и их стори
6. Возврат `BoardSearchResponse(epicKeys, "semantic"|"substring")`

### Optional dependency

`EmbeddingService` инжектится через `@Autowired(required = false)`. Если embeddings отключены (`CHAT_EMBEDDING_ENABLED=false`), поиск всегда идёт через substring fallback.

## Frontend

### Изменённые файлы

| Файл | Изменения |
|------|-----------|
| `api/board.ts` | `BoardSearchResult` интерфейс + `searchBoard()` функция |
| `hooks/useBoardFilters.ts` | Debounced search (300ms), `searchResult`, `searchLoading`, `searchMode` |
| `components/board/FilterPanel.tsx` | Placeholder "Search by key or content...", спиннер "...", бейдж AI/TXT |
| `pages/BoardPage.tsx` | Прокидывание `searchMode` + `searchLoading` в FilterPanel |
| `components/MultiSelectDropdown.css` | Стили `.search-mode-badge`, `.badge-ai`, `.badge-txt`, `.search-loading` |

### Логика в useBoardFilters

- При `searchKey.length < 3`: локальная фильтрация по issueKey (как было)
- При `searchKey.length >= 3`: debounced (300ms) запрос к `searchBoard()`, фильтрация по `matchedEpicKeys`
- `searchMode` и `searchLoading` экспортируются для UI

### UI индикаторы

- **Загрузка**: анимированные точки "..." (pulse animation)
- **AI**: синий бейдж — результат семантического поиска (pgvector)
- **TXT**: серый бейдж — результат substring-поиска (fallback)

## Тесты

8 тестов в `BoardServiceSearchTest`:

1. Semantic search возвращает epic → epic key в результате
2. Semantic search возвращает story → parent epic key
3. Semantic search возвращает subtask → grandparent epic key
4. Semantic пустой → fallback на substring
5. Substring поиск по summary стори → parent epic
6. Team filtering с semantic search
7. Без EmbeddingService → substring fallback
8. Пустой project key → пустой результат

## Зависимости

- **F54 Semantic Search (pgvector)** — `EmbeddingService`, embeddings в jira_issues
- **F51 Board Performance** — SQL-level team filtering, board cache

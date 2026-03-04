# QA Report: F58 Board Semantic Search
**Дата:** 2026-03-04
**Тестировщик:** Claude QA Agent

## Summary
- Общий статус: **PASS WITH ISSUES**
- Unit tests: 8 passed, 0 failed (BoardServiceSearchTest)
- API tests: не тестировались (backend запущен на версии 0.57.0 без кода F58)
- Visual: не тестировалось (требуется перезапуск backend)
- Frontend TypeScript: компилируется ✅
- Найдено багов: 1 Medium, 4 Low

---

## Bugs Found

### BUG-163 — Medium: Race condition — stale search results при быстром вводе

**Файл:** `frontend/src/hooks/useBoardFilters.ts:60-90`

**Описание:** При быстром вводе текста (например, "test" → "testing") debounce корректно отменяет предыдущий таймер, но если первый запрос уже ушёл на сервер (прошёл 300ms), его ответ вернётся позже и перезапишет `searchResult` для нового текста. Нет `AbortController` для отмены in-flight запросов, и нет проверки что ответ соответствует текущему `searchKey`.

**Шаги:**
1. Ввести "tes" (3 символа) → через 300ms уходит запрос
2. Быстро дописать "ting" → через 300ms уходит второй запрос
3. Если первый ответ придёт позже второго → на экране результаты для "tes", а не "testing"

**Ожидаемый результат:** Показаны результаты для "testing"
**Фактический результат:** Может показать stale результаты для "tes"

**Fix:** Добавить AbortController или проверку `searchKey === currentQuery` в `.then()` callback.

---

### BUG-164 — Low: Javadoc от score-breakdown привязан к search endpoint

**Файл:** `controller/BoardController.java:58-64`

**Описание:** Javadoc-комментарий `/** Get AutoScore breakdown for any issue (epic or story)... */` расположен прямо перед `@GetMapping("/search")`, хотя относится к `@GetMapping("/{issueKey}/score-breakdown")` ниже.

**Impact:** Misleading documentation, запутывает при чтении кода.

---

### BUG-165 — Low: teamIds всегда передаются как ALL team IDs

**Файл:** `frontend/src/hooks/useBoardFilters.ts:74`

**Описание:** Фронтенд всегда передаёт `allTeamIds` (все команды из борда) в `searchBoard()`, даже когда пользователь выбрал конкретную команду в фильтре. Это значит что team filter в поиске не учитывает UI-фильтр команды — результаты поиска не сужаются при выборе команды.

```typescript
searchBoard(searchKey, allTeamIds.length > 0 ? allTeamIds : undefined)
```

**Ожидаемый:** При выбранной команде передавать только ID этой команды.
**Fix:** Использовать отфильтрованные `selectedTeams` вместо `allTeamIds`.

---

### BUG-166 — Low: Нет ограничения длины query на backend

**Файл:** `controller/BoardController.java:69`

**Описание:** Валидация проверяет только `q.length() < 2`, но нет верхней границы. Очень длинный запрос (например, 10KB текста) будет передан в EmbeddingService для генерации embedding, что может быть медленным или вызвать ошибку у embedding API.

**Fix:** Добавить `q.length() > 500` → 400.

---

### BUG-167 — Low: N+1 queries при резолвинге subtask → grandparent

**Файл:** `board/BoardService.java:687`

**Описание:** В `resolveToEpicKeys()` для каждого subtask вызывается `issueRepository.findByIssueKey(parentKey)` — это N+1 query. При 30 subtask'ах в результате semantic search — 30 отдельных запросов к БД.

```java
Optional<JiraIssueEntity> parent = issueRepository.findByIssueKey(issue.getParentKey());
```

**Impact:** Потенциально медленно, но ограничено лимитом 30 результатов semantic search.
**Fix:** Собрать все parentKeys, сделать один `findByIssueKeyIn()`, построить map.

---

## Test Coverage Analysis

### Покрыто тестами (8 тестов в BoardServiceSearchTest):
- ✅ Epic → epic key напрямую
- ✅ Story → parent epic key
- ✅ Subtask → grandparent epic key
- ✅ Semantic empty → substring fallback
- ✅ Substring match story summary
- ✅ Team filtering
- ✅ No embedding service → fallback
- ✅ Empty project key

### Не покрыто тестами:
- ❌ Concurrent search requests (race condition)
- ❌ Query with special characters (SQL injection safe via JPQL, but no test)
- ❌ Multiple teamIds in semantic search (code passes only first team)
- ❌ Semantic search returns issues with null parentKey
- ❌ Controller-level tests (validation, response structure)
- ❌ Frontend — 0 тестов для debounce/filter logic

---

## Code Review Notes

### Положительное:
1. Optional dependency `@Autowired(required = false)` — корректно, не ломает приложение без embeddings
2. Fallback semantic → substring — graceful degradation
3. Debounce 300ms — разумное значение
4. `LinkedHashSet` для сохранения порядка результатов
5. Try-catch вокруг semantic search — не блокирует при ошибках
6. Чёткое разделение DTO (BoardSearchResponse record)

### Замечания:
1. **Multiple teamIds:** `searchForBoard()` передаёт в `embeddingService.search()` только первый teamId (`teamIds.size() == 1`). При нескольких командах поиск идёт без фильтра, а фильтрация происходит позже в `resolveToEpicKeys()`. Это может вернуть менее релевантные результаты.
2. **Board cache не учитывает search:** Endpoint `/search` не использует board cache и не инвалидирует его — это правильно (search результаты не кэшируются, что корректно для AI-поиска).

---

## Recommendations

1. **P1:** Добавить AbortController в debounced search (BUG-163) — стандартная практика для React
2. **P2:** Передавать selected team IDs вместо all team IDs (BUG-165)
3. **P3:** Добавить max length для query (BUG-166)
4. **P3:** Batch resolve для subtask parents (BUG-167)
5. **P3:** Добавить controller-level тест для /search endpoint

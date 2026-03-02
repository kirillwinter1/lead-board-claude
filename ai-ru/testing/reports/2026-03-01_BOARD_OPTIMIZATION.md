# QA Report: Board Performance Optimization (Runs 5-7)
**Дата:** 2026-03-01
**Тестировщик:** Claude QA Agent
**Scope:** SQL-level team filtering, board response cache, bulk reorder, covering index

## Summary
- **Общий статус: PASS WITH ISSUES**
- Unit tests: ALL PASSED (10 test classes, 0 failures)
- API tests: 15/15 passed
- Visual: Not available (tenant context limitation in Playwright)
- Code review: 5 issues found (0 Critical, 1 High, 2 Medium, 2 Low)

## Changes Tested

1. **Two-path loading в BoardService:** teamIds → SQL filter (~400 issues vs 12K)
2. **Board response cache:** 15s TTL ConcurrentHashMap, invalidation on reorder/sync
3. **Bulk reorder:** single UPDATE vs N individual saves (shiftEpicOrdersDown/Up, shiftStoryOrdersDown/Up)
4. **T5 migration:** covering index `(board_category, team_id, manual_order)`
5. **Composite index:** `(project_key, board_category)` для fast-path
6. **Cache invalidation:** IssueOrderService + SyncService вызывают `invalidateBoardCache()`

## API Test Results

| Test | Status | Details |
|------|--------|---------|
| Board full path (no filter) | ✅ PASS | 13 epics, correct order by manualOrder |
| Board fast path (teamIds=1) | ✅ PASS | 7 epics, SQL-level filtering |
| Board fast path (teamIds=999) | ✅ PASS | total=0, empty response |
| Board fast path (teamIds=1,2) | ✅ PASS | 13 epics (both teams) |
| Board (query filter) | ✅ PASS | Correct search |
| Reorder epic (move down) | ✅ PASS | LB-95 pos 1→3, bulk shift correct |
| Reorder epic (move up) | ✅ PASS | LB-95 pos 3→1, restored |
| Reorder (position < 1) | ✅ PASS | Clamped to 1 |
| Reorder (position > max) | ✅ PASS | Clamped to max (7) |
| Reorder (position = 0) | ✅ PASS | Clamped to 1 |
| Reorder (same position) | ✅ PASS | No-op, 200 OK |
| Reorder (non-epic) | ✅ PASS | 400 "Issue is not an epic: LB-96" |
| Reorder (non-existent) | ✅ PASS | 400 "Epic not found: FAKE-999" |
| Cache invalidation after reorder | ✅ PASS | Board reflects new order immediately |
| Full vs Fast path consistency | ✅ PASS | Same order for team 1 epics |

## Performance Results

| Operation | Measured | Status |
|-----------|----------|--------|
| Board cold (fast path) | 20ms | ✅ |
| Board warm (cached) | 6ms | ✅ |
| Reorder epic | <10ms | ✅ |
| Cache invalidation | Immediate | ✅ |

## Bugs Found

### High

**BUG-141: Dead code in epic filter — impossible branch**
- **Файл:** `BoardService.java:171-174`
- **Код:**
  ```java
  if (!hasTeamFilter && teamIds != null && !teamIds.isEmpty()) {
  ```
- **Описание:** `hasTeamFilter = teamIds != null && !teamIds.isEmpty()`. Если `!hasTeamFilter` = true, то `teamIds == null || teamIds.isEmpty()` → условие ВСЕГДА false. Блок никогда не выполняется.
- **Impact:** Нет функционального бага, но вводит в заблуждение. При рефакторинге может привести к реальным багам если кто-то решит "починить" этот код.
- **Fix:** Удалить строки 171-174.

### Medium

**BUG-142: Cache key collision vulnerability**
- **Файл:** `BoardService.java:525-533`
- **Описание:** Cache key: `projectKey|query|statuses|teamIds|page|size|includeDQ`. Использует `toString()` для List и `|` как разделитель. Если query содержит `|`, возможна коллизия ключей. Пример: `query="foo|[1"` + `statuses=null` + `teamIds=[1]` неотличим от `query="foo"` + `statuses=["1"]`.
- **Impact:** Крайне маловероятно в реальности. Board может вернуть данные от другого запроса.
- **Fix:** `Objects.hash(query, statuses, teamIds, page, size, includeDQ)` или JSON-сериализация.

**BUG-143: Unbounded cache growth**
- **Файл:** `BoardService.java:41`
- **Описание:** `ConcurrentHashMap<String, CachedBoard>` без ограничения размера. Просроченные записи проверяются при чтении, но не удаляются проактивно. При разнообразных параметрах (query, statuses, page) кэш растёт неограниченно.
- **Impact:** Потенциальная утечка памяти. `invalidateBoardCache()` при каждом reorder/sync спасает, но между этими событиями кэш может накопить записи.
- **Fix:** Заменить на Caffeine/Guava Cache с maxSize и auto-eviction, или добавить scheduled cleanup.

### Low

**BUG-144: Cache race on concurrent build + invalidate**
- **Файл:** `BoardService.java:77-81, 281`
- **Описание:** Thread A строит board (cache miss). Thread B делает reorder → `invalidateBoardCache()`. Thread A завершает → `boardCache.put()`. Stale данные в кэше.
- **Impact:** Resolves через 15s TTL. Крайне редкий race condition.

**BUG-145: Epic→Project mapping O(M×N) loop**
- **Файл:** `BoardService.java:507-510`
- **Описание:** Nested loop projects × epics. При M=5, N=100 → 500 iterations. Можно оптимизировать до O(N) через Map<parentKey, projectKey>.
- **Impact:** Незначительный. 500 iterations negligible.

## Auto-Test Review

### BoardServiceTest (22 теста)
- **Качество: Хорошее**
- Покрытие: basic, hierarchy, progress, filtering, sorting, pagination, DQ, team mapping, error handling
- Fast path тесты: `shouldFilterByTeamId` и `shouldExcludeEpicsWithoutTeam` мокают `findByBoardCategoryAndTeamIdIn` ✅
- **Пробелы:**
  - ❌ Нет тестов на board cache (TTL, invalidation, hit/miss)
  - ❌ Нет теста на эквивалентность fast path vs full path
  - ❌ Нет теста на `buildCacheKey()` — collision resistance

### IssueOrderServiceTest (20 тестов)
- **Качество: Хорошее**
- Bulk shift verify: `verify(issueRepository).shiftEpicOrdersDown(teamId, 2, 4)` ✅
- Edge cases: clamp, no-op, not found, wrong type ✅
- **Пробелы:**
  - ❌ Нет verify на `boardService.invalidateBoardCache()` вызов
  - ❌ Нет verify на `unifiedPlanningService.invalidatePlanCache()` вызов

## Recommendations

1. **Удалить мёртвый код** (BUG-141) — строки 171-174 в BoardService
2. **Добавить тесты на кэш** — TTL expiration, invalidation, concurrent access
3. **Заменить ConcurrentHashMap на Caffeine** — bounded size + auto-eviction (BUG-143)
4. **Использовать hash для cache key** — (BUG-142)
5. **Тест на эквивалентность путей** — full path vs fast path дают одинаковый результат
6. **Verify cache invalidation в тестах** — `boardService.invalidateBoardCache()` вызывается после reorder

# Performance Test Results — Reorder + Forecast

Трекинг прогресса оптимизации. Тест: `./run.sh reorder` (reorder-stress.js).

Данные: 3 тенанта × ~61K issues = 183K total. 50 teams per tenant.

---

## Run #1 — Baseline (2026-03-01)

**Конфигурация:** HikariCP 10 connections, no caching, no pagination on board.

| Endpoint | Requests | p50 | p95 | p99 | Max | Errors |
|----------|----------|-----|-----|-----|-----|--------|
| `GET /api/board` (all) | 36 | 30 000ms | 60 001ms | 60 001ms | 60 001ms | 75% |
| `GET /api/board?teamId=N` | 30 | 30 008ms | 60 001ms | 60 001ms | 60 001ms | 77% |
| `reorder_load_board` | 219 | 30 008ms | 60 001ms | 60 001ms | 60 001ms | 94% |
| `PUT /epics/{key}/order` | 5 | 13ms | 30 007ms | 30 007ms | 30 007ms | 40% |
| `GET /planning/unified` | 5 | 21ms | 33 866ms | 33 866ms | 33 866ms | 20% |
| `GET /planning/forecast` | 5 | 9ms | 30 007ms | 30 007ms | 30 007ms | 20% |
| `score_breakdown` | 9 | 39ms | 31 192ms | 31 192ms | 31 192ms | 33% |
| **TOTAL** | **314** | **30 008ms** | **60 001ms** | **60 001ms** | **60 001ms** | **84.4%** |

### Выводы

1. **`/api/board` — главный bottleneck.** p50 = 30s (k6 timeout). Тяжёлый SQL с агрегацией всех issues, блокирует HikariCP pool.
2. **HikariCP pool exhaustion.** 10 connections на 60 VUs. Каждый board запрос держит connection секундами, остальные ждут в pending → timeout.
3. **Reorder и forecast сами по себе быстрые** (13ms и 21ms p50), но blocked by pool — до них не доходит.
4. **94% ошибок на reorder_load_board** — VU не могут загрузить board чтобы узнать epic keys для reorder.

### Что нужно оптимизировать

- [x] Board endpoint: N+1 fix (subtasks batch load, childrenByParentKey index)
- [x] HikariCP pool size: увеличить с 10 → 30
- [x] Planning cache: 60s TTL для UnifiedPlanningService
- [x] DQ decoupling: includeDQ parameter (default false)
- [ ] Perf test bug: `teamId=N` → `teamIds=N` (filter не применяется)

---

## Run #2 — After Optimization (2026-03-01)

**Изменения:**
1. HikariCP pool: 10 → 30
2. BoardService: N+1 fix (pre-built `subtasksByParent` map + `childrenByParentKey` index)
3. UnifiedPlanningService: batch `findByParentKeyIn()` для stories/subtasks + 60s TTL cache
4. DQ decoupling: `includeDQ` parameter (default false)

| Endpoint | Requests | p50 | p95 | p99 | Max | Errors |
|----------|----------|-----|-----|-----|-----|--------|
| `GET /api/board` (all) | 88 | 1 041ms | 58 243ms | 60 002ms | 60 002ms | 5.7% |
| `GET /api/board?teamId=N` | 80 | 1 416ms | 47 315ms | 58 745ms | 58 745ms | 0% |
| `reorder_load_board` | 335 | 31 816ms | 59 648ms | 60 002ms | 60 002ms | 5.1% |
| **TOTAL** | **503** | **20 651ms** | **58 145ms** | **60 002ms** | **60 002ms** | **4.4%** |

### Сравнение Run #1 vs Run #2

| Метрика | Run #1 | Run #2 | Улучшение |
|---------|--------|--------|-----------|
| Board (all) p50 | 30 000ms | **1 041ms** | **28.8x** |
| Board (team) p50 | 30 008ms | **1 416ms** | **21.2x** |
| Error rate | 84.4% | **4.4%** | **19.2x** |
| Total requests | 314 | **503** | **1.6x** throughput |
| Board (readers, 10 VUs) | timeout | **~1s** | Работает |

### Выводы

1. **Board endpoint ускорен в 21-29x** — p50 с 30s до ~1s. N+1 fix + TTL cache + HikariCP pool расширение.
2. **Error rate снижен с 84.4% до 4.4%** — HikariCP pool 30 connections хватает для 60 VUs.
3. **Throughput вырос на 60%** — 503 vs 314 requests за то же время.
4. **reorder_load_board всё ещё медленный (p50 31s)** — это 50 VUs делающих board+planning chain последовательно. Board сам по себе 1s, но 50 concurrent chains создают queueing.
5. **p95/p99 всё ещё высокий** — при пиковой нагрузке (50 VUs) queueing эффект усиливается.

### Известный баг в perf-тесте

Perf-тесты используют `?teamId=N` вместо `?teamIds=N`. Фильтр по команде **не применяется** — board возвращает все 100 эпиков вместо ~20 для конкретной команды. Это значит:
- Board грузит planning для ВСЕХ 50 команд вместо 1
- Размер ответа ~2MB вместо ~800KB
- **После исправления параметра ожидается ещё 3-5x ускорение**

### Что нужно для Run #3

- [x] Исправить perf-тест: `teamId=N` → `teamIds=N`
- [x] После фикса: board фильтрует до ~20 эпиков и грузит planning только для 1 команды

---

## Run #3 — Fixed teamIds Filter (2026-03-01)

**Изменение:** `?teamId=N` → `?teamIds=N` в `board.js` и `reorder-forecast.js`.

| Endpoint | Requests | p50 | p95 | p99 | Max | Errors |
|----------|----------|-----|-----|-----|-----|--------|
| `GET /api/board` (all) | 57 | 7 031ms | 60 001ms | 60 002ms | 60 002ms | 22.8% |
| `GET /api/board?teamIds=N` | 50 | 10 318ms | 60 001ms | 60 002ms | 60 002ms | 18.0% |
| `reorder_load_board` | 216 | 37 861ms | 60 002ms | 60 002ms | 60 003ms | 51.4% |
| `PUT /epics/{key}/order` | 33 | 20ms | 43 270ms | 45 984ms | 45 984ms | 57.6% |
| `GET /planning/unified` | 33 | 10ms | 30 006ms | 32 391ms | 32 391ms | 6.1% |
| `GET /planning/forecast` | 33 | 5ms | 38 634ms | 38 635ms | 38 635ms | 15.2% |
| `score_breakdown` | 39 | 28ms | 32 406ms | 37 128ms | 37 128ms | 7.7% |
| `reorder_verify_board` | 33 | 23 744ms | 60 002ms | 60 002ms | 60 002ms | 30.3% |
| **TOTAL** | **494** | **25 450ms** | **60 001ms** | **60 002ms** | **60 003ms** | **34.8%** |

### Сравнение Run #1 → #2 → #3

| Метрика | Run #1 | Run #2 | Run #3 |
|---------|--------|--------|--------|
| Board (all) p50 | 30 000ms | 1 041ms | 7 031ms |
| Error rate | 84.4% | 4.4% | 34.8% |
| Reorder chain visible | Нет (blocked) | Нет (3 endpoints) | **Да (8 endpoints)** |
| Reorder move epic | 5 (40% err) | — | 33 (57.6% err) |
| Forecast unified | 5 (20% err) | — | 33 (6.1% err) |

### Анализ: почему Run #3 хуже Run #2

Run #3 **не регрессия** — это более реалистичный тест. В Run #2 сломанный `?teamId=N` фильтр случайно **прогревал все 50 кэшей** за один запрос board (грузил planning для всех команд). После исправления:

1. **Cache invalidation loop:** Reorder → `invalidatePlanCache(teamId)` → следующий board для той же команды = **cold calculatePlan()** (5-8s)
2. **Каждый из 33 reorder-ов** инвалидирует кэш своей команды → 33 cold planning recalculation
3. **Readers (board_all без фильтра)** грузят planning для ВСЕХ 50 команд — при инвалидированных кэшах это 50× cold calls
4. **Queueing при 60 VUs:** 50 writers + 10 readers конкурируют за 30 DB connections

**Ключевой bottleneck теперь — `calculatePlan()` cold cost (5-8s per team).** Board сам по себе быстрый (~120ms когда кэш тёплый).

### Изолированная проверка (вне perf-теста)

```
Board cached (1 VU):     120ms   ← после прогрева
Board cold (1 VU):     7 926ms   ← первый запрос, холодный planning
Board + DQ (1 VU):       384ms   ← кэш тёплый, DQ добавляет ~250ms
```

### Что нужно для Run #4

- [ ] Оптимизировать `calculatePlan()` — основной bottleneck (5-8s cold на 20 эпиков / 300 stories / 900 subtasks)
- [ ] Варианты: lazy planning (не считать на board), async prefetch, reduce algorithm complexity
- [ ] Board readers не должны пересчитывать planning — только reorder flow
- [ ] Рассмотреть: board endpoint БЕЗ planning enrichment, planning как отдельный запрос

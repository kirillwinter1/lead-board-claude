# QA Report: Team Metrics (F22 + F24)

**Дата:** 2026-02-17
**Тестировщик:** Claude QA Agent
**Статус:** FAIL

---

## Bugs

### BUG-1: epic-burndown 500 на несуществующем эпике [Critical]

- **Endpoint:** `GET /api/metrics/epic-burndown?epicKey=NONEXISTENT-999`
- **Ожидаемый результат:** 404 Not Found или пустой ответ
- **Фактический результат:** 500 Internal Server Error
- **Причина:** `EpicBurndownService.calculateBurndown()` не проверяет существование эпика — NullPointerException
- **Файл:** `backend/src/main/java/com/leadboard/metrics/service/EpicBurndownService.java`
- **Исправление:** добавить проверку + вернуть 404 или ResponseEntity.notFound()

---

### BUG-2: Broken Jira URL в ForecastAccuracyChart [High]

- **Компонент:** ForecastAccuracyChart.tsx, строка 141
- **Ожидаемый результат:** `https://domain.atlassian.net/browse/LB-123`
- **Фактический результат:** `https://domain.atlassian.netLB-123` (нет `/browse/`)
- **Файл:** `frontend/src/components/metrics/ForecastAccuracyChart.tsx:141`
- **Исправление:** заменить `` `${jiraBaseUrl}${epic.epicKey}` `` на `` `${jiraBaseUrl}/browse/${epic.epicKey}` ``

---

### BUG-3: Backend compilation error — ProjectService [High]

- **Ошибка:** `cannot find symbol: method computeAverageExpectedDone`
- **Файл:** `backend/src/main/java/com/leadboard/project/ProjectService.java:90`
- **Последствие:** невозможно собрать backend и запустить тесты (`./gradlew test` падает)
- **Исправление:** добавить отсутствующий метод или убрать вызов

---

### BUG-4: Frontend тесты падают — 53/240 [High]

- **Причина:** `TeamMetricsPage.test.tsx` падает из-за missing mock для `getRoleLoad` в `RoleLoadBlock`
- **Ошибка:** `vi.mock('../api/forecast')` не включает `getRoleLoad`
- **Файл:** `frontend/src/pages/TeamMetricsPage.test.tsx`
- **Последствие:** 7 тест-файлов падают, невозможно проверить регрессию
- **Исправление:** добавить mock для `getRoleLoad` в тест

---

### BUG-5: Inverted date range возвращает 200 [Medium]

- **Endpoint:** `GET /api/metrics/summary?teamId=1&from=2026-02-17&to=2025-01-01`
- **Ожидаемый результат:** 400 Bad Request
- **Фактический результат:** 200 OK с пустыми данными
- **Файл:** `backend/src/main/java/com/leadboard/metrics/controller/TeamMetricsController.java`
- **Исправление:** добавить валидацию `from < to` в контроллере

---

### BUG-6: Race conditions — нет AbortController [Medium]

- **Проблема:** быстрое переключение team/period → старые запросы перезаписывают свежие данные
- **Затрагивает:**
  - `frontend/src/pages/TeamMetricsPage.tsx:270-300` (основной эффект метрик)
  - `frontend/src/components/metrics/VelocityChart.tsx:27-34`
  - `frontend/src/components/metrics/EpicBurndownChart.tsx:32-45`
  - `frontend/src/components/metrics/RoleLoadBlock.tsx:19-31`
- **Исправление:** добавить AbortController в useEffect, cleanup при unmount/re-run

---

### BUG-7: Silent error swallowing [Medium]

- **Проблема:** ошибки API логируются в console.error, но error state не устанавливается
- **Следствие:** пользователь видит "No metrics data available" вместо сообщения об ошибке
- **Файл:** `frontend/src/pages/TeamMetricsPage.tsx:293-298`
- **Исправление:** в catch-блоке вызывать `setError(...)` вместо только `console.error`

---

### BUG-8: NaN из URL параметров [Medium]

- **Проблема:** `Number(searchParams.get('teamId'))` с невалидным значением (`?teamId=abc`) даёт NaN
- **Следствие:** NaN передаётся в API-вызовы как teamId
- **Файл:** `frontend/src/pages/TeamMetricsPage.tsx:215`
- **Исправление:** `parseInt(...) || null` с валидацией

---

### BUG-9: AssigneeTable .toFixed() на undefined [Low]

- **Проблема:** `avgLeadTimeDays` и `avgCycleTimeDays` вызывают `.toFixed()` без null-guard
- **Файл:** `frontend/src/components/metrics/AssigneeTable.tsx:66`
- **Исправление:** добавить `(a.avgLeadTimeDays ?? 0).toFixed(1)`

---

## Пробелы в тестовом покрытии

### P0 — Критично

| Что покрыть | Файл |
|-------------|------|
| ForecastAccuracyService: тест с реальными snapshot данными (accuracy ratio, schedule variance, EARLY/ON_TIME/LATE) | ForecastAccuracyServiceTest.java |
| 6 непокрытых controller endpoints: cycle-time, forecast-accuracy, dsr, velocity, epic-burndown, epics-for-burndown | TeamMetricsControllerTest.java |
| 3 непокрытых public метода: findFirstInProgressTransition, findLastDoneTransition, hasJiraChangelog | StatusChangelogServiceTest.java |

### P1 — Важно

| Что покрыть | Файл |
|-------------|------|
| VelocityService — 0 тестов | создать VelocityServiceTest.java |
| EpicBurndownService — 0 тестов | создать EpicBurndownServiceTest.java |
| Фильтры TeamMetricsService (issueType, epicKey, assigneeAccountId) | TeamMetricsServiceTest.java |
| Тренд DOWN и STABLE (сейчас только UP) | TeamMetricsServiceTest.java |
| DsrService: forecast DSR, rough estimate fallback | DsrServiceTest.java |

### P2 — Желательно

| Что покрыть | Файл |
|-------------|------|
| Integration тесты: заменить `assertTrue(total >= 0)` на реальные assertions | MetricsIntegrationTest.java |
| Controller: negative cases (missing params, invalid dates) | TeamMetricsControllerTest.java |
| Мигрировать на AssertJ `assertThat()` | все тест-файлы метрик |

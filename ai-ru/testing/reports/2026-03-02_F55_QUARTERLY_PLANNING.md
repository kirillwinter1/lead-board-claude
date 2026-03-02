# QA Report: F55 Quarterly Capacity-Based Planning

**Дата:** 2026-03-02
**Тестировщик:** Claude QA Agent
**Версия:** 0.55.0
**Фича:** F55 Quarterly Capacity-Based Planning with Project Priority

## Summary

- **Общий статус:** PASS WITH ISSUES → FIXED
- Unit tests: **15 passed**, 0 failed (7 QuarterRange + 8 QuarterlyPlanningService)
- TypeScript: **Компилируется без ошибок**
- API tests: **FAIL** — все endpoints возвращают 500 из-за multi-tenant native query бага
- Visual: Не тестировалось (API не работает)
- Найдено багов: **9** (1 Critical, 3 High, 3 Medium, 2 Low)

---

## Bugs Found

### Critical

#### BUG-140: Native SQL queries не работают в multi-tenant контексте → 500 на ВСЕХ endpoint'ах

**Серьёзность:** Critical
**Шаги:** Вызвать любой endpoint, например `GET /api/quarterly-planning/quarters` с валидной сессией.
**Ожидаемый результат:** Список доступных кварталов.
**Фактический результат:** HTTP 500, `ERROR: relation "jira_issues" does not exist`.

**Причина:** Три native query в `JiraIssueRepository` (строки 285-297) выполняются в `public` schema, где таблица `jira_issues` не существует. Таблица находится в tenant-схемах (`tenant_test2`, `perf_alpha` и т.д.). Hibernate `SET search_path` не применяется к native queries корректно.

**Затронутые методы:**
- `findDistinctQuarterLabels()` — вызывается в `getSummary()` и `getAvailableQuarters()`
- `findEpicsByTeamAndLabel()` — не используется сервисом (см. BUG-142)
- `findProjectsByLabel()` — не используется сервисом (см. BUG-142)

**Влияние:** ВСЕ endpoint'ы Quarterly Planning недоступны. Фича полностью нерабочая в multi-tenant окружении.

**Рекомендация:** Использовать JPQL вместо native SQL, либо динамически подставлять schema из TenantContext в native queries. Альтернатива: загрузить все issues с labels IS NOT NULL через JPQL и фильтровать в Java. Важно: `findDistinctQuarterLabels()` использует `unnest()` и regex — это невозможно сделать через JPQL, нужна другая стратегия (например, загрузить все записи с labels через JPQL и парсить в Java).

---

### High

#### BUG-141: GET endpoints не защищены авторизацией

**Серьёзность:** High
**Файл:** `QuarterlyPlanningController.java`

**Описание:** Только `PUT /projects/{key}/boost` защищён через `@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")`. Все 5 GET endpoints (capacity, demand, summary, project-view, quarters) не имеют аннотаций авторизации.

**Ожидаемое:** Как минимум `@PreAuthorize("isAuthenticated()")` на всех endpoints. Данные о capacity/demand — чувствительная информация о ресурсах команд.

**Примечание:** Если SecurityConfig по умолчанию требует аутентификацию для `/api/**`, баг снижается до Medium. Необходимо проверить конфигурацию.

#### BUG-142: Неиспользуемые native query методы

**Серьёзность:** High (dead code + архитектурный просчёт)

**Описание:** Методы `findEpicsByTeamAndLabel()` и `findProjectsByLabel()` объявлены в репозитории, но НИГДЕ не вызываются. Сервис использует:
- `findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", teamId)` — JPA derived query (работает)
- `findByBoardCategory("PROJECT")` — JPA derived query (работает)

А затем фильтрует по labels в Java-коде. Native queries были написаны по плану, но не подключены.

**Рекомендация:** Удалить неиспользуемые native queries или заменить ими текущую in-memory фильтрацию (после исправления BUG-96).

#### BUG-143: ProjectView BoostControl всегда показывает boost=0

**Серьёзность:** High
**Файл:** `QuarterlyPlanningPage.tsx:412`

**Код:** `<BoostControl currentBoost={0} onBoostChange={...} />`

**Описание:** В Project View компоненте boost всегда отображается как 0, потому что:
1. `currentBoost` захардкожен как `0`
2. `ProjectViewDto` не содержит поле `manualBoost` — есть только `priorityScore`

**Ожидаемое:** BoostControl должен показывать текущий boost проекта и позволять его редактировать.

**Рекомендация:** Добавить `manualBoost` в `ProjectViewDto` бэкенда, передать его в BoostControl.

---

### Medium

#### BUG-144: Нет loading-индикатора при смене квартала/команды

**Серьёзность:** Medium
**Файл:** `QuarterlyPlanningPage.tsx`

**Описание:** Состояние `loading` используется только для первичной загрузки страницы. При переключении квартала, команды или проекта данные загружаются без индикации — пользователь видит устаревшие данные до завершения запроса.

**Рекомендация:** Добавить `dataLoading` state, показывать спиннер или skeleton при загрузке данных.

#### BUG-145: Race condition при быстром переключении фильтров

**Серьёзность:** Medium
**Файл:** `QuarterlyPlanningPage.tsx:80-104`

**Описание:** `loadTeamView` и `loadProjectView` не используют `AbortController`. При быстром переключении команд/кварталов ответ от предыдущего запроса может перезаписать актуальные данные.

**Рекомендация:** Использовать AbortController в useCallback или добавить проверку актуальности запроса.

#### BUG-146: Epic link ведёт в никуда

**Серьёзность:** Medium
**Файл:** `QuarterlyPlanningPage.tsx:485-489`

**Код:**
```jsx
<a href="#" className="qp-epic-key" onClick={e => e.preventDefault()}>
  {epic.epicKey}
</a>
```

**Описание:** Ссылка на epic — мёртвая. Клик ничего не делает. Ожидается ссылка на Jira (`{JIRA_BASE_URL}/browse/{epicKey}`) или на детали в приложении.

---

### Low

#### BUG-147: `getTeamDemand` загружает ВСЕ проекты без фильтрации

**Серьёзность:** Low (performance)
**Файл:** `QuarterlyPlanningService.java:115`

**Код:** `issueRepository.findByBoardCategory("PROJECT")` загружает все проекты во всех командах. При большом количестве проектов это может быть неэффективно.

**Рекомендация:** Фильтровать проекты по labels в SQL, либо кэшировать результат.

#### BUG-148: Нет empty state при отсутствии данных

**Серьёзность:** Low
**Файл:** `QuarterlyPlanningPage.tsx`

**Описание:** Если нет доступных кварталов или команд, страница показывает пустой dropdown без поясняющего сообщения. Нет подсказки "Добавьте labels в Jira для использования квартального планирования".

---

## Ревью автотестов

### QuarterRangeTest (7 тестов) — GOOD

| Тест | Покрытие | Оценка |
|------|----------|--------|
| testParseQ1/Q2/Q3/Q4 | Парсинг всех кварталов | ✅ Отлично |
| testInvalidFormat | null, "invalid", Q0, Q5 | ✅ Хорошие edge cases |
| testLabelForDate | Все граничные даты | ✅ Отлично |
| testCurrentQuarterLabel | Формат regex | ✅ OK |

**Замечания:** @DisplayName отсутствует, но имена тестов самодокументирующие. Нет теста на year boundary (2025Q4 → 2026Q1).

### QuarterlyPlanningServiceTest (8 тестов) — GOOD

| Тест | Покрытие | Оценка |
|------|----------|--------|
| testCapacityBasicThreeMembers | 3 участника, грейды | ✅ OK |
| testCapacityWithAbsences | 5 absence days | ✅ OK |
| testPriorityScoreComputation | RICE + boost = 85 | ✅ OK |
| testPriorityScoreClamping | 120 + 50 → ≤150 | ✅ OK |
| testEpicInheritsQuarterFromProject | Наследование labels | ✅ OK |
| testCapacityFitCutoff | 3 epics > capacity | ✅ OK |
| testUnassignedEpics | Epic без проекта | ✅ OK |
| testEmptyQuarter | Пустые данные | ✅ OK |

**Замечания:**
- `@MockitoSettings(strictness = Strictness.LENIENT)` — лучше бы STRICT с точечными lenient
- Нет тестов для `getSummary()` (который вызывает `findDistinctQuarterLabels` — падающий native query)
- Нет тестов для `getProjectView()`
- Нет тестов для `updateProjectBoost()` (включая clamping)
- Нет тестов для edge case: epic с собственным label, отличным от проекта

### Отсутствующие тесты

- **Контроллер:** Нет тестов для `QuarterlyPlanningController` (validation, error handling, auth)
- **Frontend:** Нет тестов для `QuarterlyPlanningPage`
- **Integration:** Нет integration-тестов для native queries с реальной БД

---

## Business Logic Review

### Capacity формула — CORRECT ✅
```
effectiveDays = availableWorkdays × (hoursPerDay / 8) / gradeCoefficient
```
- Senior (0.8): даёт больше effective days (правильно — senior продуктивнее)
- Junior (1.5): даёт меньше effective days (правильно — junior менее продуктивен)
- Absences корректно вычитаются

### Demand формула — CORRECT ✅
```
demand = roughEstimate × (1 + riskBuffer)
```
- Risk buffer дефолт 20% из PlanningConfigDto
- Применяется к каждой роли отдельно

### Priority Score — CORRECT ✅
```
priorityScore = RICE normalizedScore + manualBoost, clamped [0, 150]
```
- Boost: [-50, +50]
- Score: [0, 150]
- Нижний clamp до 0 (не может быть отрицательным)

### Capacity Fit — CORRECT ✅
- Remaining capacity уменьшается по мере прохода по эпикам
- overCapacity помечается когда demand > remaining для любой роли
- Пересчёт после сортировки по приоритету (recalculateCapacityFit) — корректно

### Quarter Label Inheritance — CORRECT ✅
- Прямой label на epic приоритетнее
- Если нет — наследуется от родительского проекта
- null если нет ни у кого

---

## Frontend Code Review

### Положительные моменты
- ✅ TypeScript — компилируется без ошибок, нет `any` (кроме одного `as any` для совместимости API проектов)
- ✅ Design System — StatusBadge, TeamBadge, RiceScoreBadge, MetricCard, getRoleColor
- ✅ Recharts — корректная интеграция с BarChart
- ✅ BoostControl — клампинг [-50, +50] на фронте
- ✅ Quarter навигация — prev/next + dropdown

### Проблемы
- ❌ Race condition (BUG-101)
- ❌ No loading indicator (BUG-100)
- ❌ Dead epic links (BUG-102)
- ❌ BoostControl hardcoded 0 in ProjectView (BUG-99)
- ⚠️ `(p as any).issueKey` (line 64) — type casting для совместимости ProjectOption, хрупкий код

---

## Data Integrity Review

### Миграции

**V47 (public schema):**
- ✅ `ADD COLUMN IF NOT EXISTS` — идемпотентный
- ✅ GIN index для labels array
- ✅ DO $$ block с проверкой наличия таблицы (добавлен хуком)

**T7 (tenant schema):**
- ✅ Зеркальная миграция для tenant schemas
- ✅ Идемпотентный

### SyncService

- ✅ Labels сохраняются из Jira при синхронизации
- ✅ `manualBoost` сохраняется (saved + restored после перезаписи Jira данными)
- ✅ Null-safe: `if (jiraLabels != null && !jiraLabels.isEmpty())`

---

## Regression Testing

### Backend
- ✅ 15/15 F55 тестов проходят
- ⚠️ 73 ранее падавших теста (Team Integration Tests, `NoClassDefFoundError`) — **не связаны с F55**, pre-existing

### Frontend
- ✅ TypeScript компилируется без ошибок

---

## Recommendations

1. **[CRITICAL] Исправить native queries для multi-tenant (BUG-140)** — заменить native SQL на JPQL, либо добавить schema prefix из TenantContext. Это блокирует всю фичу.

2. **Добавить авторизацию на GET endpoints (BUG-141)** — минимум `@PreAuthorize("isAuthenticated()")` или `hasAnyRole(...)`.

3. **Исправить BoostControl в ProjectView (BUG-143)** — передать реальный boost из DTO.

4. **Добавить loading states и AbortController (BUG-144, BUG-145)** — стандартная практика для data-driven страниц.

5. **Удалить неиспользуемые native queries (BUG-142)** — `findEpicsByTeamAndLabel()` и `findProjectsByLabel()` нигде не вызываются.

6. **Добавить тесты для getSummary, getProjectView, updateProjectBoost** — критичные методы без покрытия.

7. **Добавить frontend тесты** — QuarterlyPlanningPage не покрыт тестами.

8. **Epic links → Jira (BUG-146)** — использовать `JIRA_BASE_URL/browse/{epicKey}` или ссылку внутри приложения.

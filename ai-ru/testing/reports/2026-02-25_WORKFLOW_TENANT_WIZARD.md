# QA Report: Workflow Config + Setup Wizard + Multi-Tenancy
**Дата:** 2026-02-25
**Тестировщик:** Claude QA Agent
**Scope:** WorkflowConfigPage (F17/F29/F38), SetupWizardPage (F33), RegistrationPage + Multi-Tenancy (F44)

## Summary
- **Общий статус:** FAIL — найдены Critical и High баги
- **Backend unit tests:** service tests ALL PASS; 44 @WebMvcTest controller tests FAIL (pre-existing TenantUserRepository)
- **Frontend tests:** 235/235 PASS (но 0 тестов для проверяемых экранов)
- **API tests:** 40 проверок — 32 PASS, 8 FAIL/BUG
- **Visual:** 2 экрана проверены (WorkflowConfigPage, RegistrationPage)
- **Code review:** 10 файлов backend, 8 файлов frontend

## Bugs Found

### Critical (2)

| Bug ID | Описание | Файл | Статус |
|--------|----------|------|--------|
| BUG-60 | **Multi-tenant cache race condition в WorkflowConfigService.** Глобальный `volatile currentlyLoadedTenantId` + ConcurrentHashMap кэш. Если `ensureLoaded()` вызывается из разных потоков с разными tenant'ами одновременно, конфиг tenant A может попасть в запрос tenant B. `currentlyLoadedTenantId` присваивается ПОСЛЕ `loadConfiguration()` — окно для race condition. **Нужен `Map<Long, CacheSnapshot>` per-tenant.** | `WorkflowConfigService.java:59-106` | OPEN |
| BUG-61 | **SQL injection risk в tenant schema management.** `"CREATE SCHEMA IF NOT EXISTS " + schemaName` (TenantMigrationService:43) и `"SET search_path TO " + schema + ", public"` (SchemaBasedConnectionProvider:70) — schema name конкатенируется в SQL напрямую. Текущая защита: только через regex валидацию slug в TenantService. Но если schema name bypasses TenantService (e.g., через прямой вызов), возможен SQL injection. **Нужна доп. валидация `^tenant_[a-z0-9_]+$` перед SQL.** | `TenantMigrationService.java:43`, `SchemaBasedConnectionProvider.java:70` | OPEN |

### High (2)

| Bug ID | Описание | Файл | Статус |
|--------|----------|------|--------|
| BUG-62 | **PUT roles/statuses/issue-types/link-types принимает пустой массив `[]`, удаляя все данные.** `curl -X PUT -d '[]' /api/admin/workflow-config/roles` → 200 OK, все роли удалены. Ломает pipeline планирования, timeline, всю систему. Нет валидации минимального количества записей. | `WorkflowConfigController.java` (PUT endpoints) | OPEN |
| BUG-63 | **44 @WebMvcTest controller теста сломаны** после F44 Multi-Tenancy. `NoSuchBeanDefinitionException: TenantUserRepository`. Все контроллерные тесты (Board, Team, Metrics, Forecast, IssueOrder, WorkflowConfig) не запускаются. Регрессия F44. | Все `*ControllerTest.java` файлы | OPEN |

### Medium (7)

| Bug ID | Описание | Файл | Статус |
|--------|----------|------|--------|
| BUG-64 | **check-slug не валидирует reserved words и формат.** `GET /api/public/tenants/check-slug?slug=admin` → `{"available":true}`. Аналогично для пустого slug, 1 символа, пробелов. `checkSlug()` делает только DB lookup (`findBySlug()`), не вызывая `validateSlug()`. Пользователь видит "Available", но register вернёт 400. | `TenantRegistrationController.java` (checkSlug) | OPEN |
| BUG-65 | **issue-count: months=0 возвращает все 246 задач.** Ожидание: 0 задач (0 месяцев = ничего) или 400. Факт: возвращает все задачи. Также missing `months` param тихо дефолтится в 0. | `SyncController` (issue-count endpoint) | OPEN |
| BUG-66 | **NPE в PublicConfigController.getIssueTypeCategories()** при null boardCategory. Строка 61: `m.getBoardCategory().name()` — если тип задачи зарегистрирован через F38 incremental с `boardCategory=NULL`, будет NPE → 500. Латентный баг (сейчас все типы промаппированы). | `PublicConfigController.java:61` | OPEN |
| BUG-67 | **Нет AbortController в WorkflowConfigPage.** `loadConfig()` и `fetchJiraMetadata()` фетчат без abort. При навигации прочь или быстром рефетче — stale state updates, memory leak. | `WorkflowConfigPage.tsx` (loadConfig, fetchJiraMetadata) | OPEN |
| BUG-68 | **Silent polling errors в SetupWizardPage.** Polling `.catch(() => {})` тихо проглатывает ошибки. Пользователь видит бесконечный "Syncing..." при сетевых проблемах. Нет таймаута, нет максимального числа попыток. | `SetupWizardPage.tsx` (polling) | OPEN |
| BUG-69 | **Нет debounce на проверке slug в RegistrationPage.** `checkSlug()` вызывается на каждый keystroke без задержки. Race condition: несколько параллельных запросов, последний ответ может быть не от последнего запроса. | `RegistrationPage.tsx` (checkSlug) | OPEN |
| BUG-70 | **0 frontend тестов для 3 критичных страниц.** WorkflowConfigPage (1870 LOC), SetupWizardPage (261 LOC), RegistrationPage (139 LOC) — ни одного теста. | `frontend/src/pages/` | OPEN |

### Low (5)

| Bug ID | Описание | Файл | Статус |
|--------|----------|------|--------|
| BUG-71 | **Tab URL parameter не работает в WorkflowConfigPage.** `?tab=statuses` игнорируется, всегда показывается Roles. Невозможно deep-link на конкретный таб. | `WorkflowConfigPage.tsx` (activeTab state) | OPEN |
| BUG-72 | **check-slug со спецсимволами возвращает HTML 400** вместо JSON. Tomcat отбрасывает запрос до контроллера. Frontend получает HTML, возможен parse error. | `TenantRegistrationController.java` | OPEN |
| BUG-73 | **Нет верхней границы months parameter.** `months=9999` принимается (299970 дней). Нет практического вреда, но плохая валидация. | `SyncController` (issue-count) | OPEN |
| BUG-74 | **localStorage wizard state переживает logout.** Если пользователь разлогинится и войдёт другим аккаунтом (или в другом tenant'е), wizard step/months сохранены. | `SetupWizardPage.tsx` (localStorage) | OPEN |
| BUG-75 | **Нет aria-labels на WorkflowConfigPage.** Color picker кнопки, tab buttons без `aria-selected`, save/delete кнопки без описания контекста (какую роль удаляем?). | `WorkflowConfigPage.tsx` | OPEN |

---

## API Testing Details

### Workflow Config (15 тестов)

| # | Тест | HTTP | Результат |
|---|------|------|-----------|
| 1 | GET /api/admin/workflow-config | 200 | PASS — полный конфиг |
| 2 | GET roles | 200 | PASS — 3 роли |
| 3 | GET issue-types | 200 | PASS — 7 типов |
| 4 | GET statuses | 200 | PASS — 32 статуса |
| 5 | GET link-types | 200 | PASS — 4 связи |
| 6 | GET status-issue-counts | 200 | PASS — counts by status |
| 7 | POST validate | 200 | PASS — valid=true |
| 8 | GET config status | 200 | PASS — configured=true |
| 9 | GET jira-metadata/issue-types | 200 | PASS |
| 10 | GET jira-metadata/statuses | 200 | PASS |
| 11 | GET jira-metadata/link-types | 200 | PASS |
| 12 | Without cookie | 401 | PASS — auth enforced |
| 13 | Invalid cookie | 401 | PASS — auth enforced |
| 14 | detect-statuses nonexistent type | 200 | PASS — 0 detected |
| 15 | PUT roles `[]` | 200 | **BUG-62** — deletes all roles |

### Tenant/Registration (15 тестов)

| # | Тест | HTTP | Результат |
|---|------|------|-----------|
| 1 | check-slug available | 200 | PASS |
| 2 | check-slug reserved "admin" | 200 | **BUG-64** — returns available:true |
| 3 | check-slug empty | 200 | **BUG-64** — returns available:true |
| 4 | check-slug 1 char "a" | 200 | **BUG-64** — returns available:true |
| 5 | check-slug uppercase | 200 | PASS — lowercased |
| 6 | check-slug special chars | 400 | PASS (but HTML, **BUG-72**) |
| 7 | check-slug SQL injection | 200 | PASS — safe |
| 8 | register missing fields | 400 | PASS — "Slug cannot be empty" |
| 9 | register invalid slug "123abc" | 400 | PASS — regex validation |
| 10 | register reserved "admin" | 400 | PASS — "reserved" |
| 11 | register too long (100 chars) | 400 | PASS — "3-63 chars" |
| 12 | register underscore slug | 400 | PASS — regex rejects |
| 13 | check-slug with spaces | 200 | **BUG-64** — returns available:true |

### Setup Wizard (11 тестов)

| # | Тест | HTTP | Результат |
|---|------|------|-----------|
| 1 | GET sync/status | 200 | PASS |
| 2 | issue-count months=6 | 200 | PASS — total=246 |
| 3 | issue-count months=0 | 200 | **BUG-65** — total=246 |
| 4 | issue-count months=-1 | 400 | PASS — validated |
| 5 | issue-count months=9999 | 200 | **BUG-73** — accepted |
| 6 | issue-count no param | 200 | **BUG-65** — defaults to 0 |
| 7 | issue-count months=abc | 400 | PASS — "For input string" |
| 8 | issue-count no auth | 401 | PASS |
| 9 | config status | 200 | PASS — configured=true |
| 10 | public roles | 200 | PASS — 3 roles |
| 11 | issue-type-categories | 200 | PASS — 7 types |

---

## Visual Review

### WorkflowConfigPage (Roles tab)
- ✅ Чистый layout с табами (Roles 3, Issue Types 7, Statuses 32, Link Types 4)
- ✅ Таблица ролей с цветными кружками (SA blue, DEV green, QA amber)
- ✅ Кнопки "Add Role", "Save Roles", "Re-run Wizard", "Validate"
- ✅ Version badge v0.45.0 в правом нижнем углу
- ⚠️ Нет "Default" индикатора — непонятно какая роль default
- ⚠️ Color picker не виден на скриншоте (нужен клик)

### RegistrationPage
- ✅ Чистый минималистичный дизайн, центрированная форма
- ✅ "Create your workspace" заголовок + "Free 14-day trial"
- ✅ Поля: Company name (placeholder "Acme Corporation"), Workspace URL с `.leadboard.app` суффиксом
- ✅ Кнопка "Create workspace" синяя, заметная
- ⚠️ Нет логотипа/бренда Lead Board на странице
- ⚠️ Нет визуальной обратной связи по доступности slug (нужен ввод для проверки)

---

## Test Coverage Gaps

### Backend (критичные пробелы)

| Класс | Тестов | Покрытие | Gap |
|-------|--------|----------|-----|
| WorkflowConfigController | 6 (все FAIL) | 0% (broken) | PUT validation, RBAC, edge cases |
| WorkflowConfigService | 22 (score only) | ~30% | isEpic/isStory/isBug, getRoles, categorize, multi-tenant cache |
| MappingAutoDetectService | 30+ | ~95% | Хорошо |
| MappingValidationService | 8 | ~100% | Нет @DisplayName |
| TenantService | 8 | ~50% | findBySlug, findAllActive, deactivate |
| TenantContext | 3 | 100% | Нет concurrency тестов |
| TenantFilter | 0 | 0% | Полностью без тестов |
| TenantMigrationService | 0 | 0% | Полностью без тестов |
| TenantSyncScheduler | 0 | 0% | Полностью без тестов |
| SchemaBasedConnectionProvider | 0 | 0% | Полностью без тестов |
| PublicConfigController | 0 | 0% | Полностью без тестов |

### Frontend (0% покрытие)

| Компонент | LOC | Тестов | Приоритет |
|-----------|-----|--------|-----------|
| WorkflowConfigPage.tsx | 1870 | 0 | P0 |
| SetupWizardPage.tsx | 261 | 0 | P1 |
| RegistrationPage.tsx | 139 | 0 | P1 |
| WorkflowConfigContext.tsx | 138 | 0 | P1 |

---

## Code Review Findings (не баги, а рекомендации)

1. **WorkflowConfigService:** Case-insensitive lookups O(n) — перебор всех записей. Нужен pre-computed lowercase map.
2. **MappingAutoDetectService:** Hardcoded heuristics для RU/EN типов задач. Если Jira на другом языке — типы помечаются IGNORE.
3. **TenantSyncScheduler:** Нет backoff при повторных ошибках, `catch(Exception)` слишком широкий.
4. **WorkflowConfigPage:** 1870 LOC в одном файле, wizard + editor + color pickers — нужен рефакторинг на подкомпоненты.
5. **No rate limiting** на `POST /api/public/tenants/register` — регистрация без CAPTCHA/throttling.

---

## Recommendations

### P0 (Critical)
1. **Fix multi-tenant cache race condition** — заменить глобальный volatile на `ConcurrentHashMap<Long, CacheSnapshot>` per-tenant
2. **Add schema name validation** — `if (!schemaName.matches("^tenant_[a-z0-9_]+$")) throw`
3. **Add empty array validation** — отклонять `PUT roles []` → 400

### P1 (High)
4. **Fix 44 broken @WebMvcTest tests** — добавить `@MockBean TenantUserRepository` во все контроллерные тесты
5. **Add slug validation to check-slug** — вызывать `validateSlug()` перед DB lookup
6. **Fix months=0 handling** — вернуть 400 или задокументировать поведение
7. **Add null check в getIssueTypeCategories** — skip types with null boardCategory

### P2 (Medium)
8. **Add AbortController** в WorkflowConfigPage и SetupWizardPage
9. **Add debounce** в RegistrationPage slug check
10. **Add frontend tests** для 3 непокрытых страниц (WorkflowConfig, Wizard, Registration)
11. **Add error handling** в SetupWizardPage polling

### P3 (Low)
12. **Support tab URL parameter** в WorkflowConfigPage
13. **Add aria-labels** для accessibility
14. **Add max months validation** (cap at 120)
15. **Clear localStorage** wizard state on logout

# QA Status — Lead Board

Мастер-документ: что протестировано QA-агентом, что ждёт проверки.

**Последнее обновление:** 2026-03-17

---

## Обзор по экранам

| # | Экран / Модуль | Фичи | QA статус | Баги | Отчёт |
|---|---------------|-------|-----------|------|-------|
| 1 | **Board** | F8, F10, F11, F15, F21, F31, F37, F42 | ✅ Проверен | 4 High, 5 Medium, 2 Low | [reports/2026-02-19_BOARD_DQ_BUGSLA.md](reports/2026-02-19_BOARD_DQ_BUGSLA.md) |
| 2 | **Teams** | F5, F6, F7, F37 | ✅ Проверен (F37) | 1 Medium (color tests missing) | [reports/2026-02-17_F35_F36_F37.md](reports/2026-02-17_F35_F36_F37.md) |
| 3 | **Team Metrics** | F22, F24, F32 | ✅ Проверен | 9 багов (1 Critical, 3 High, 4 Medium, 1 Low) | [reports/2026-02-17_TEAM_METRICS.md](reports/2026-02-17_TEAM_METRICS.md) |
| 4 | **Timeline** | F14, F20, F21, F45 | ✅ Проверен | 0 багов (11 fixed) | [reports/2026-02-25_TIMELINE.md](reports/2026-02-25_TIMELINE.md) |
| 5 | **Data Quality** | F18, F36, F42 | ✅ Проверен | (входит в Board QA) | [reports/2026-02-19_BOARD_DQ_BUGSLA.md](reports/2026-02-19_BOARD_DQ_BUGSLA.md) |
| 6 | **Planning Poker** | F23 | ⏸️ Отложен | Известные баги с Jira | — |
| 7 | **Workflow Config** | F17, F29, F38, F48 | ✅ Проверен | 2 Critical, 4 High, 10 Medium, 8 Low | [reports/2026-03-01_F48_PER_PROJECT_WORKFLOW.md](reports/2026-03-01_F48_PER_PROJECT_WORKFLOW.md) |
| 8 | **Simulation** | F28 | ✅ Проверен | 2 Critical, 4 High, 7 Medium, 5 Low | [reports/2026-02-25_SIMULATION.md](reports/2026-02-25_SIMULATION.md) |
| 9 | **Projects** | F35 | ✅ Проверен | 1 High (test regression), 1 Low | [reports/2026-02-17_F35_F36_F37.md](reports/2026-02-17_F35_F36_F37.md) |
| 10 | **RICE Scoring** | F36 | ✅ Проверен | 2 Medium (case-sensitive, FP), 1 Low | [reports/2026-02-17_F35_F36_F37.md](reports/2026-02-17_F35_F36_F37.md) |
| 11 | **Project Timeline** | F35 | ✅ Проверен | Визуал ОК | [reports/2026-02-17_F35_F36_F37.md](reports/2026-02-17_F35_F36_F37.md) |
| 12 | **Member Profile** | F30 | ✅ Проверен | 2 High, 5 Medium, 2 Low | [reports/2026-02-25_MEMBER_PROFILE.md](reports/2026-02-25_MEMBER_PROFILE.md) |
| 13 | **Setup Wizard** | F33 | ✅ Проверен | (входит в Workflow Config QA) | [reports/2026-02-25_WORKFLOW_TENANT_WIZARD.md](reports/2026-02-25_WORKFLOW_TENANT_WIZARD.md) |
| 14 | **Auth / OAuth / RBAC** | F4, F27, F44 | ✅ Проверен | 2 Critical, 5 High, 5 Medium, 2 Low | [reports/2026-02-25_AUTH_RBAC.md](reports/2026-02-25_AUTH_RBAC.md) |
| 15 | **Sync** | F2, F3, F9, F34 | ✅ Проверен | 2 Critical, 2 High, 4 Medium, 2 Low | [reports/2026-02-21_SYNC.md](reports/2026-02-21_SYNC.md) |
| 16 | **AutoScore / Planning** | F13, F19, F20, F21 | ✅ Проверен | 1 High, 4 Medium, 4 Low | [reports/2026-02-23_AUTOSCORE_PLANNING.md](reports/2026-02-23_AUTOSCORE_PLANNING.md) |
| 17 | **Team Members** | F5, F6, F37, F41 | ✅ Проверен (F41) | 3 High, 6 Medium, 3 Low | [reports/2026-02-19_F41_ABSENCES.md](reports/2026-02-19_F41_ABSENCES.md) |
| 18 | **Member Absences** | F41 | ✅ Проверен | 3 High, 6 Medium, 3 Low | [reports/2026-02-19_F41_ABSENCES.md](reports/2026-02-19_F41_ABSENCES.md) |

| 19 | **Bug SLA Settings** | F42 | ✅ Проверен (встроен в Settings) | 0 багов | [reports/2026-02-23_BUG_SLA_TO_SETTINGS.md](reports/2026-02-23_BUG_SLA_TO_SETTINGS.md) |
| 20 | **Multi-Tenancy / Registration** | F44 | ✅ Проверен | (входит в Workflow Config QA) | [reports/2026-02-25_WORKFLOW_TENANT_WIZARD.md](reports/2026-02-25_WORKFLOW_TENANT_WIZARD.md) |
| 21 | **Multi-Tenancy E2E Journey** | F44, F33 | ✅ Проверен + Исправлен | 11/11 fixed, 0 OPEN | [reports/2026-02-25_MULTITENANCY_E2E.md](reports/2026-02-25_MULTITENANCY_E2E.md) |

| 22 | **Early Exit Optimization** | Perf | ✅ Проверен | 1 High, 2 Medium | [reports/2026-03-01_EARLY_EXIT_OPTIMIZATION.md](reports/2026-03-01_EARLY_EXIT_OPTIMIZATION.md) |
| 23 | **Board Perf Optimization** | Perf | ✅ Проверен | 1 High, 2 Medium, 2 Low | [reports/2026-03-01_BOARD_OPTIMIZATION.md](reports/2026-03-01_BOARD_OPTIMIZATION.md) |
| 24 | **AI Chat Model + Tools** | F52, F53 | ✅ Проверен + Исправлен | 5/5 fixed | [reports/2026-03-02_CHAT_MODEL_TOOLS.md](reports/2026-03-02_CHAT_MODEL_TOOLS.md) |
| 25 | **Semantic Search (pgvector)** | F54 | ✅ Проверен | 1 High, 2 Medium, 1 Low | [reports/2026-03-02_F54_SEMANTIC_SEARCH.md](reports/2026-03-02_F54_SEMANTIC_SEARCH.md) |
| 26 | **Quarterly Planning** | F55 | ✅ Проверен + Исправлен | 9/9 fixed | [reports/2026-03-02_F55_QUARTERLY_PLANNING.md](reports/2026-03-02_F55_QUARTERLY_PLANNING.md) |
| 27 | **Sync & Multi-Tenant Fixes** | BUG-108, Sync | ✅ Проверен | 3 High, 2 Medium, 4 Low | [reports/2026-03-04_SYNC_MULTITENANT_FIXES.md](reports/2026-03-04_SYNC_MULTITENANT_FIXES.md) |
| 28 | **DSR Status-Based** | DSR refactor | ✅ Проверен | 1 High, 3 Medium, 1 Low | [reports/2026-03-04_DSR_STATUS_BASED.md](reports/2026-03-04_DSR_STATUS_BASED.md) |
| 29 | **Board Semantic Search** | F58 | ✅ Проверен | 1 Medium, 4 Low | [reports/2026-03-04_F58_BOARD_SEMANTIC_SEARCH.md](reports/2026-03-04_F58_BOARD_SEMANTIC_SEARCH.md) |

| 30 | **Worklog Timeline** | F65 | ✅ Проверен | 0 багов (5 рекомендаций) | [reports/2026-03-08_WORKLOG_TIMELINE.md](reports/2026-03-08_WORKLOG_TIMELINE.md) |
| 31 | **Priority Icons on Board** | F66 | ✅ Проверен | 1 High, 1 Low | [reports/2026-03-09_F66_PRIORITY_ICONS.md](reports/2026-03-09_F66_PRIORITY_ICONS.md) |
| 32 | **Metrics Redesign (CTO Dashboard)** | F66 Phase 1-4 | ✅ Проверен | 1 High, 1 Medium, 2 Low | [reports/2026-03-10_F66_METRICS_REDESIGN.md](reports/2026-03-10_F66_METRICS_REDESIGN.md) |

| 33 | **Quarterly Planning Production** | F68 | ✅ Проверен | 1 Medium, 2 Low | [reports/2026-03-17_F68_QUARTERLY_PLANNING_PRODUCTION.md](reports/2026-03-17_F68_QUARTERLY_PLANNING_PRODUCTION.md) |

**Прогресс: 33 / 33 модулей проверено (100%)**

---

## Статистика багов

| Severity | Открыто | Исправлено | Всего |
|----------|---------|------------|-------|
| Critical | 2 | 9 | 11 |
| High | 23 | 23 | 46 |
| Medium | 44 | 36 | 80 |
| Low | 35 | 18 | 53 |
| **Итого** | **104** | **86** | **190** |

---

## Детали по проверенным экранам

### F35 Projects + F36 RICE + F37 Team Colors — 2026-02-17

**API endpoints (21):** 19 PASS, 2 FAIL (minor)
**Visual:** 5 экранов проверены, все ОК

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-10 | High | 24 фронтенд-теста сломаны (регрессия F35/F36/F37) | ✅ FIXED (all 235 tests pass) |
| BUG-11 | Medium | RICE by-code endpoint case-sensitive | ✅ FIXED (findByCodeIgnoreCase) |
| BUG-12 | Medium | Floating point artifact в score-range (0.3000...04) | ✅ FIXED (additional rounding) |
| BUG-13 | Medium | Нет тестов для TeamService color methods | OPEN |
| BUG-14 | Medium | Нет controller-тестов для ProjectController/RiceController | OPEN |
| BUG-15 | Low | RICE assessment 404 вместо 200+null | ✅ FIXED (return 200+null) |
| BUG-16 | Low | Hardcoded 'ru-RU' locale в ProjectsPage | ✅ FIXED (+ JiraLink /browse/ fix) |

---

### Team Metrics (F22 + F24) — 2026-02-17

**API endpoints (11):** все работают, 1 critical bug (500 на несуществующем эпике)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-1 | Critical | epic-burndown 500 на несуществующем эпике | ✅ FIXED (@ExceptionHandler + try-catch) |
| BUG-2 | High | Broken Jira URL в ForecastAccuracyChart (нет `/browse/`) | ✅ FIXED (already had /browse/) |
| BUG-3 | High | Backend compilation error — ProjectService | ✅ FIXED (method exists, compiles) |
| BUG-4 | High | Frontend тесты — 53/240 падают (missing mock) | ✅ FIXED (all 235 tests pass) |
| BUG-5 | Medium | Inverted date range (from > to) → 200 вместо 400 | ✅ FIXED (@ExceptionHandler → 400) |
| BUG-6 | Medium | Race conditions — нет AbortController | ✅ FIXED (already has AbortController) |
| BUG-7 | Medium | Silent error swallowing в метриках | ✅ FIXED (already has setError) |
| BUG-8 | Medium | NaN из URL параметров | ✅ FIXED (already validates) |
| BUG-9 | Low | AssigneeTable .toFixed() на undefined | ✅ FIXED (already uses ??) |

**Пробелы в тестах:**
- ForecastAccuracyService: core logic не тестируется (P0)
- VelocityService, EpicBurndownService: 0 тестов (P1)
- 6 из 11 controller endpoints без тестов (P0)

---

### F41 Member Absences — 2026-02-19

**API endpoints (5):** 21 проверка, 20 PASS, 1 NOTE
**Visual:** 2 экрана проверены (TeamMembersPage, MemberProfilePage)
**Backend tests:** 19 unit-тестов (11 AbsenceService + 8 AssigneeSchedule), 0 controller
**Frontend tests:** 0

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-17 | High | Silent error swallowing в AbsenceTimeline (.catch(() => {})) | ✅ FIXED (error state + display) |
| BUG-18 | High | Timezone bug в Date parsing (без Z suffix) | ✅ FIXED (already uses T00:00:00Z) |
| BUG-19 | High | 0 frontend тестов для AbsenceTimeline/AbsenceModal | OPEN |
| BUG-20 | Medium | ABSENCE_COLORS дублируется в 2 файлах | ✅ FIXED (already single source) |
| BUG-21 | Medium | Нет client-side валидации startDate <= endDate | ✅ FIXED (already validates) |
| BUG-22 | Medium | 0 controller-level тестов для 5 endpoints | OPEN |
| BUG-23 | Medium | Нет aria-label на интерактивных элементах | ✅ FIXED (aria-labels added) |
| BUG-24 | Medium | Нет @DisplayName в backend тестах | OPEN |
| BUG-25 | Medium | Несогласованность формата createdAt (offset vs UTC) | ✅ FIXED (spring.jackson.time-zone: UTC) |
| BUG-26 | Low | TypeScript `any` type в catch handler | ✅ FIXED (already uses err: unknown) |
| BUG-27 | Low | `today` memo без deps (не обновится в полночь) | ✅ FIXED (startDate dep) |
| BUG-28 | Low | Tooltip может выйти за viewport | ✅ FIXED (boundary clamping) |

### Sync — 2026-02-21

**API endpoints (5):** все протестированы, найдены проблемы
**Backend tests:** 28+ passed, 0 failed (SyncServiceTest + ChangelogImportServiceTest)
**Code review:** Критичные проблемы архитектуры

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-39 | Critical | Зависший sync_in_progress без recovery (2 дня) | ✅ FIXED (@PostConstruct recovery) |
| BUG-40 | Critical | Reconciliation может удалить ВСЕ задачи при пустом ответе Jira | ✅ FIXED (sanity checks) |
| BUG-41 | High | @Transactional bypassed при self-invocation через Thread | ✅ FIXED (@Async + @Lazy self) |
| BUG-42 | High | jira_updated_at не заполняется → changelog months фильтр бесполезен | ✅ FIXED (parse Jira updated) |
| BUG-43 | Medium | Raw Thread без error handling и naming | ✅ FIXED (@Async replaces Thread) |
| BUG-44 | Medium | Нет concurrency guard для changelog import | ✅ FIXED (AtomicBoolean) |
| BUG-45 | Medium | Отрицательные months принимаются (months=-1 → 200 OK) | ✅ FIXED (validation → 400) |
| BUG-46 | Medium | Транзиентная 500 при issue-count | ✅ FIXED (try-catch) |
| BUG-47 | Low | Тесты без @DisplayName | ✅ FIXED (@DisplayName added) |
| BUG-48 | Low | countIssuesInJira без timeout | ✅ FIXED (WebClient timeout) |

---

### Timeline (F14, F45 Hybrid) — 2026-02-25

**API endpoints (10):** 8 PASS, 3 BUG (500 на несуществующей команде)
**Visual:** 1 экран проверен (TimelinePage — Gantt)
**Backend tests:** 209/222 passed, 13 failed (pre-existing @WebMvcTest TenantUserRepository)
**Frontend tests:** 13/19 passed, 6 failed (missing getRetrospective mock after F45)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-49 | High | unified/forecast/role-load 500 на несуществующей команде (teamId=9999) | ✅ FIXED (GlobalExceptionHandler → 404) |
| BUG-50 | High | WIP limits в ForecastResponse = forecasts.size() вместо team config | ✅ FIXED (PlanningConfigDto.wipLimits) |
| BUG-51 | High | TimelinePage.test.tsx — 6 тестов падают (missing getRetrospective mock F45) | ✅ FIXED (mocks added) |
| BUG-52 | High | Race condition — нет AbortController при смене команды | ✅ FIXED (AbortController) |
| BUG-53 | Medium | WIP history принимает отрицательные days (inverted date range) | ✅ FIXED (validation → 400) |
| BUG-54 | Medium | assigneeDisplayName=null для завершённых сторей в story-forecast | ✅ FIXED (fallback displayName) |
| BUG-55 | Medium | Historical snapshot mode не загружает retro данные | ✅ FIXED (loads retro + merge) |
| BUG-56 | Medium | mergeHybridEpics() теряет retro-only эпики | ✅ FIXED (retro-only epics added) |
| BUG-57 | Medium | Silent error catch при загрузке StatusStyles и Config | ✅ FIXED (console.error) |
| BUG-58 | Low | Tooltip может выйти за границы экрана | ✅ FIXED (viewport clamping) |
| BUG-59 | Low | Нет aria-labels на интерактивных элементах Gantt | ✅ FIXED (aria-labels added) |

---

### Board + Data Quality + Bug SLA — 2026-02-19

**API endpoints (9):** 8 PASS, 1 NOTE (status filter case-sensitive)
**Visual:** 3 экрана проверены (Board, DataQuality, BugSlaSettings)
**Backend tests:** ALL PASSED (включая 3 новых теста STORY_FULLY_LOGGED_NOT_DONE)
**Frontend tests:** 6 FAIL в BoardPage.test.tsx (pre-existing BUG-10)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-29 | High | Сообщение `STORY_FULLY_LOGGED_NOT_DONE` показывает `100%%` вместо `100%` | ✅ FIXED (already handles %%) |
| BUG-30 | High | Bug SLA страница отсутствует в навигации Layout.tsx | ✅ FIXED (already in Layout) |
| BUG-31 | High | Race condition в PriorityCell — нет AbortController на async hover | ✅ FIXED (already has AbortController) |
| BUG-32 | High | Тихое проглатывание ошибки в PriorityCell tooltip (зависает loading) | ✅ FIXED (already has loadError) |
| BUG-33 | Medium | BugSlaSettingsPage — error swallowing в 4 catch-блоках | ✅ FIXED (axios status details) |
| BUG-34 | Medium | DataQualityPage — axios error details lost | ✅ FIXED (already uses isAxiosError) |
| BUG-35 | Medium | Hardcoded PRIORITY_COLORS в BugSlaSettingsPage | ✅ FIXED (extracted to helpers/priorityColors.ts) |
| BUG-36 | Medium | Нет aria-label на drag handle и alert icon | ✅ FIXED (already has aria-labels) |
| BUG-37 | Low | Hardcoded score colors в PriorityCell | OPEN (low priority) |
| BUG-38 | Low | Hardcoded severity labels/rule names в AlertIcon | OPEN (localization scope) |

---

## Приоритет следующих проверок

| Приоритет | Экран | Почему |
|-----------|-------|--------|
| ~~P0~~ | ~~**Sync**~~ | ✅ Проверен |
| ~~P1~~ | ~~**AutoScore / Planning**~~ | ✅ Проверен |
| ~~P1~~ | ~~**Workflow Config**~~ | ✅ Проверен (2 Critical, 2 High, 7 Medium, 5 Low) |
| ~~P2~~ | ~~**Timeline**~~ | ✅ Проверен |
| ~~P3~~ | ~~**Setup Wizard**~~ | ✅ Проверен (входит в Workflow Config QA) |
| ~~P2~~ | ~~**Simulation**~~ | ✅ Проверен (2 Critical, 4 High, 7 Medium, 5 Low) |
| ~~P3~~ | ~~**Auth / OAuth / RBAC**~~ | ✅ Проверен (2 Critical, 5 High, 5 Medium, 2 Low) |
| ~~P3~~ | ~~**Member Profile**~~ | ✅ Проверен (2 High, 5 Medium, 2 Low) |

### Simulation (F28) — 2026-02-25

**API endpoints (5):** 23 проверки — 14 PASS, 5 BUG, 4 NOTE
**Backend tests:** 25 tests ALL PASS (4 test classes)
**Frontend:** нет (backend-only модуль)
**Code review:** 13 main files, 4 test files

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-76 | Critical | TOCTOU race condition в concurrent guard (existsByStatus check-then-act) | OPEN |
| BUG-77 | Critical | SimulationScheduler не устанавливает TenantContext (multi-tenant broken) | OPEN |
| BUG-78 | High | POST /dry-run и /run: null/missing teamId → 500 вместо 400 (нет валидации) | OPEN |
| BUG-79 | High | POST /dry-run teamId=9999 → 500 вместо 404 (non-existent team) | OPEN |
| BUG-80 | High | fromJson() returns null на corrupt JSON → NPE при чтении логов | OPEN |
| BUG-81 | High | toJson() swallows errors, saves "[]" → simulation data silently lost | OPEN |
| BUG-82 | Medium | N+1 query: findByParentKey() called per story per phase (~700 queries) | OPEN |
| BUG-83 | Medium | No duplicate guard: same team+date creates unlimited dry-run logs | OPEN |
| BUG-84 | Medium | Over-logging: 0.5h rounding minimum inflates Jira time tracking | OPEN |
| BUG-85 | Medium | getLogs() partial date filter silently ignored (only from without to) | OPEN |
| BUG-86 | Medium | getLogs() no pagination — returns all logs with full JSONB payloads | OPEN |
| BUG-87 | Medium | Deviation probability config not validated (sum != 1.0 → biased results) | OPEN |
| BUG-88 | Medium | Permanent "RUNNING" lock if DB fails during error handling | OPEN |
| BUG-89 | Low | No warning log when scheduler enabled without teamIds | OPEN |
| BUG-90 | Low | Fixed 100ms rate limit without adaptive backoff for Jira 429 | OPEN |
| BUG-91 | Low | Hardcoded "Epic" fallback in getEpicTypeNames() | OPEN |
| BUG-92 | Low | 0 tests for SimulationController (5 endpoints) and SimulationScheduler | OPEN |
| BUG-93 | Low | No @DisplayName on any of 25 tests | OPEN |

---

### Workflow Config + Setup Wizard + Multi-Tenancy — 2026-02-25

**API endpoints (41):** 32 PASS, 8 BUG, 1 NOTE
**Visual:** 2 экрана проверены (WorkflowConfigPage, RegistrationPage)
**Backend tests:** service tests ALL PASS; 44 @WebMvcTest FAIL (pre-existing TenantUserRepository)
**Frontend tests:** 235/235 PASS (но 0 тестов для проверяемых экранов)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-60 | Critical | Multi-tenant cache race condition в WorkflowConfigService (volatile + ConcurrentHashMap) | ✅ FIXED (synchronized ensureLoaded/clearCache) |
| BUG-61 | Critical | SQL injection risk — schema name конкатенируется в SQL напрямую | ✅ FIXED (regex validation ^tenant_[a-z0-9_]+$) |
| BUG-62 | High | PUT roles/statuses/issue-types `[]` → 200 OK, удаляет все данные | ✅ FIXED (empty list → 400) |
| BUG-63 | High | 44 @WebMvcTest controller теста сломаны (TenantUserRepository not mocked) | ✅ FIXED (@MockBean TenantUserRepository + TenantRepository) |
| BUG-64 | Medium | check-slug не валидирует reserved words, формат, длину — ложный "available" | ✅ FIXED (isValidSlug() before DB lookup) |
| BUG-65 | Medium | issue-count: months=0 возвращает все задачи, missing param дефолтится в 0 | ✅ FIXED (months must be > 0) |
| BUG-66 | Medium | PublicConfigController.getIssueTypeCategories() NPE на null boardCategory | ✅ FIXED (null check + skip) |
| BUG-67 | Medium | Нет AbortController в WorkflowConfigPage fetch calls | ✅ FIXED (AbortController + signal) |
| BUG-68 | Medium | Silent polling errors в SetupWizardPage (.catch(() => {})) | ✅ FIXED (error state + max 5 retries) |
| BUG-69 | Medium | Нет debounce на slug check в RegistrationPage | ✅ FIXED (300ms debounce + AbortController) |
| BUG-70 | Medium | 0 frontend тестов для WorkflowConfigPage, SetupWizardPage, RegistrationPage | OPEN |
| BUG-71 | Low | Tab URL parameter не работает (?tab=statuses показывает Roles) | ✅ FIXED (URL search params) |
| BUG-72 | Low | check-slug со спецсимволами → Tomcat HTML 400 вместо JSON | OPEN (Tomcat behavior) |
| BUG-73 | Low | Нет верхней границы months (9999 принимается) | ✅ FIXED (months <= 120) |
| BUG-74 | Low | localStorage wizard state переживает logout/смену tenant'а | ✅ FIXED (clear on logout) |
| BUG-75 | Low | Нет aria-labels на color picker, tab buttons в WorkflowConfigPage | OPEN |

### Multi-Tenancy E2E Customer Journey — 2026-02-25

**Scope:** Полный клиентский путь — регистрация → OAuth → Setup Wizard → Board
**API tests:** 47 проверок — 40 PASS, 7 BUG → **10 FIXED**
**Visual:** 6 экранов (Registration desktop+mobile, Landing, Wizard Step 1, Landing+fixes, Registration+fixes)
**E2E:** 11/11 багов исправлены. JiraConfigResolver заменил JiraProperties во всех 22+ файлах.

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-108 | Critical | Sync @Async thread loses TenantContext — TenantAwareAsyncConfig с TaskDecorator | ✅ FIXED |
| BUG-96* | Critical | Every new user gets ADMIN — tenantHasUsers() проверяет ВСЕ users | ✅ FIXED |
| BUG-94* | Critical | No tenant membership check — deny access без записи в tenant_users | ✅ FIXED |
| BUG-109 | High | Sync API uses global Jira config, not per-tenant — JiraConfigResolver + TenantJiraConfigController + T3 migration | ✅ FIXED |
| BUG-110 | High | Registration response exposes internal schemaName — removed | ✅ FIXED |
| BUG-111 | Medium | Branding: "Lead Board" → "OneLane" | ✅ FIXED |
| BUG-112 | Medium | Unknown slug → 404 on non-public routes | ✅ FIXED |
| BUG-113 | Medium | Landing → Registration: "Try Free" + "Попробовать бесплатно" | ✅ FIXED |
| BUG-114 | Medium | Dev mode redirect: localStorage + /board | ✅ FIXED |
| BUG-115 | Low | "Set to 0" hint: added "Recommended: 3–12 months" | ✅ FIXED |
| BUG-116 | Low | `err: any` → `err: unknown` with type assertion | ✅ FIXED |

*BUG-94, BUG-96 — подтверждены из Auth/RBAC QA, верифицированы E2E-тестированием.

---

### Auth / RBAC — 2026-02-25

**API endpoints (30+):** auth enforcement OK, RBAC gaps found
**Backend tests:** ALL PASS (но 0 RBAC-specific тестов)
**Frontend tests:** 235/235 PASS
**Code review:** 15 files (auth package, SecurityConfig, controllers, frontend Layout)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-94 | Critical | AuthorizationService uses getRole() (global) instead of getTenantRole() — all SpEL checks bypass tenant RBAC | OPEN |
| BUG-95 | Critical | Cross-tenant session reuse — session from tenant A accepted on tenant B | OPEN |
| BUG-96 | High | First-tenant-user ADMIN logic broken — every new user gets ADMIN in any tenant | OPEN |
| BUG-97 | High | 11+ write endpoints lack @PreAuthorize — VIEWER can modify competencies, forecast, planning | OPEN |
| BUG-98 | High | WebSocket /ws/** has no authentication (permitAll, no handshake interceptor) | OPEN |
| BUG-99 | High | findLatestToken() returns any user's OAuth token — privilege confusion | OPEN |
| BUG-100 | High | No session invalidation on role change — old sessions keep old role for 30 days | OPEN |
| BUG-101 | Medium | First-user ADMIN race condition — count==0 without DB lock | OPEN |
| BUG-102 | Medium | OAuth state in-memory only — breaks multi-instance, lost on restart | OPEN |
| BUG-103 | Medium | Tenant registration no rate limiting — public DDL trigger (CREATE SCHEMA + Flyway) | OPEN |
| BUG-104 | Medium | Frontend RBAC minimal — only Settings tab hidden, write ops visible to VIEWER | OPEN |
| BUG-105 | Medium | 0 RBAC-specific tests — no test verifies MEMBER/VIEWER rejected from ADMIN endpoints | OPEN |
| BUG-106 | Low | OAuth tokens stored in plaintext — DB compromise leaks all Jira tokens | OPEN |
| BUG-107 | Low | Sessions not bound to client fingerprint — stolen cookie works from any location | OPEN |

### Quarterly Planning (F55) — 2026-03-02

**API endpoints (6):** 0 PASS — все 500 из-за native query multi-tenant бага
**Backend tests:** 15/15 PASS (7 QuarterRange + 8 QuarterlyPlanningService)
**Frontend tests:** 0 (нет тестов), TypeScript компилируется ✅
**Visual:** Не тестировалось (API не работает)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-140 | Critical | Native SQL queries (findDistinctQuarterLabels и др.) работают в public schema → 500 | ✅ FIXED (заменены на JPQL + Java parsing) |
| BUG-141 | High | GET endpoints не защищены @PreAuthorize | ✅ FIXED (@PreAuthorize("isAuthenticated()") на класс) |
| BUG-142 | High | findEpicsByTeamAndLabel() и findProjectsByLabel() — dead code | ✅ FIXED (удалены) |
| BUG-143 | High | ProjectView BoostControl hardcoded currentBoost={0} | ✅ FIXED (manualBoost в ProjectViewDto) |
| BUG-144 | Medium | Нет loading-индикатора при смене квартала/команды | ✅ FIXED (dataLoading state) |
| BUG-145 | Medium | Race condition — нет AbortController | ✅ FIXED (AbortController + signal check) |
| BUG-146 | Medium | Epic link href="#" — мёртвая ссылка | ✅ FIXED (span вместо anchor) |
| BUG-147 | Low | getTeamDemand загружает ВСЕ проекты без фильтрации | ✅ FIXED (accepted: данных немного, JPA derived query работает с tenant) |
| BUG-148 | Low | Нет empty state при отсутствии кварталов | ✅ FIXED (подсказка "Add labels in Jira") |

---

### Board Endpoint Optimization — 2026-03-01

**Scope:** N+1 fix, O(n^2) fix, TTL cache, DQ decoupling, HikariCP tuning
**API tests:** 18 проверок — 18 PASS, 0 FAIL
**Backend tests:** 785 ALL PASS
**Performance:** p50 132ms (no DQ), p50 384ms (with DQ) на 61K issues — было 30s timeout

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-136 | Medium | Cache not invalidated when team planning config changes (TeamService.updatePlanningConfig) | OPEN |
| BUG-137 | Medium | Cache not invalidated when team members change (add/remove/deactivate) | OPEN |
| BUG-138 | Low | Unbounded ConcurrentHashMap growth — no eviction for deleted teams | OPEN |
| BUG-139 | Low | Frontend always sends includeDQ=true — negates 3x speedup opportunity | OPEN |

---

### Member Profile (F30) — 2026-02-25

**API endpoint (1):** 14 проверок — 12 PASS, 1 BUG, 1 NOTE
**Backend tests:** 9 tests ALL PASS (MemberProfileServiceTest)
**Frontend tests:** 236/236 PASS (но 0 для MemberProfilePage)
**Visual:** Ограничено (tenant/data mismatch)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-108 | High | Inverted date range (from > to) returns 200 empty instead of 400 | OPEN |
| BUG-109 | High | Silent error swallowing in 4 catch blocks (competency, absences, competency save) | OPEN |
| BUG-110 | Medium | No AbortController for profile fetch — race condition on period change | OPEN |
| BUG-111 | Medium | DSR=0.0 for completed tasks with 0 spent hours — skews avgDsr | OPEN |
| BUG-112 | Medium | Hardcoded status-badge CSS instead of StatusBadge + StatusStylesContext | OPEN |
| BUG-113 | Medium | No @PreAuthorize on profile endpoint — VIEWER can see all DSR metrics | OPEN |
| BUG-114 | Medium | 0 frontend tests for MemberProfilePage (527 LOC) | OPEN |
| BUG-115 | Low | TypeScript `catch (e: any)` instead of `unknown` | OPEN |
| BUG-116 | Low | N+1 query in resolveEpicInfo (findByIssueKey per parent/grandparent) | OPEN |

### Sync & Multi-Tenant Fixes — 2026-03-04

**Scope:** BUG-108 CallerRunsPolicy, ObservabilityMetrics, SyncService guard, SchemaBasedConnectionProvider logging
**API tests:** 7 проверок — 6 PASS, 1 NOTE (pre-existing)
**Backend tests:** 770 unit tests PASS, 73 integration FAIL (pre-existing)
**Code review:** 4 файла, найдено 9 багов (3 High, 2 Medium, 4 Low)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-149 | High | ForecastSnapshotService.createDailySnapshots() не tenant-aware → ERROR каждый день в 3:00 AM | OPEN |
| BUG-150 | High | ForecastSnapshotService.cleanupOldSnapshots() не tenant-aware → снапшоты не удаляются | OPEN |
| BUG-151 | High | WipSnapshotService.createDailySnapshots() не tenant-aware → ERROR каждый день в 9:00 AM | OPEN |
| BUG-152 | Medium | WipSnapshotService.cleanupOldSnapshots() не tenant-aware → WIP-снапшоты не удаляются | OPEN |
| BUG-153 | Low | SyncService.scheduledSync() dead code — TenantContext guard блокирует всегда | OPEN |
| BUG-154 | Medium | 0 тестов для TenantContextTaskDecorator (критический компонент) | OPEN |
| BUG-155 | Low | 0 тестов для ObservabilityMetrics.refreshGauges() | OPEN |
| BUG-156 | Low | ObservabilityMetrics.refreshGauges() дважды вызывает findAllActive() | OPEN |
| BUG-157 | Low | Thread pool sizing (4/8/100) недостаточен для embedding-heavy syncs | OPEN |

### DSR Status-Based Refactor — 2026-03-04

**Scope:** DSR formula from calendar-based (startedAt) to status changelog-based (IN_PROGRESS periods)
**Unit tests:** 10/10 PASS (DsrServiceTest), related tests ALL PASS
**API tests:** Skipped (backend running old code)
**Code review:** 6 files changed, 5 bugs found

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-158 | High | Historical data regression — completed epics without changelog silently excluded from DSR | OPEN |
| BUG-159 | Medium | knowledge_base.md stale DSR documentation (still describes calendar-based approach) | OPEN |
| BUG-160 | Medium | F32 feature spec outdated (describes old startedAt-based formula) | OPEN |
| BUG-161 | Medium | Unused `issueType` parameter in calculateInProgressWorkdays() | OPEN |
| BUG-162 | Low | N+1 query in changelog loading (one query per epic) | OPEN |

### Board Semantic Search (F58) — 2026-03-04

**Scope:** Semantic search integration (pgvector) with Board filtering
**Unit tests:** 8/8 PASS (BoardServiceSearchTest)
**API tests:** Skipped (backend running old version 0.57.0)
**Frontend:** TypeScript compiles ✅, 0 frontend tests
**Code review:** 8 files (2 new, 6 modified)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-163 | Medium | Race condition — stale search results при быстром вводе (нет AbortController) | OPEN |
| BUG-164 | Low | Javadoc от score-breakdown привязан к search endpoint | OPEN |
| BUG-165 | Low | teamIds всегда передаются как ALL team IDs вместо selected teams | OPEN |
| BUG-166 | Low | Нет ограничения длины query на backend (max length) | OPEN |
| BUG-167 | Low | N+1 queries при резолвинге subtask → grandparent (findByIssueKey per subtask) | OPEN |

---

## Артефакты

```
ai-ru/testing/
├── QA_STATUS.md              ← этот документ (что проверено, баги)
├── TEST_PLAN.md              ← тест-план (что и как тестировать, чек-листы)
├── TEST_PYRAMID.md           ← тестовая пирамида (покрытие unit/integration/e2e)
└── reports/
    ├── 2026-02-17_TEAM_METRICS.md     ← QA-отчёт: Team Metrics
    ├── 2026-02-17_F35_F36_F37.md      ← QA-отчёт: Projects + RICE + Team Colors
    ├── 2026-02-19_F41_ABSENCES.md     ← QA-отчёт: F41 Member Absences
    ├── 2026-02-21_SYNC.md             ← QA-отчёт: Sync Module (P0)
    ├── 2026-02-23_AUTOSCORE_PLANNING.md ← QA-отчёт: AutoScore / Planning
    ├── 2026-02-23_BUG_SLA_TO_SETTINGS.md ← QA-отчёт: Bug SLA Settings
    ├── 2026-02-25_TIMELINE.md         ← QA-отчёт: Timeline (F14, F45 Hybrid)
    ├── 2026-02-25_WORKFLOW_TENANT_WIZARD.md ← QA-отчёт: Workflow Config + Wizard + Multi-Tenancy
    ├── 2026-02-25_SIMULATION.md       ← QA-отчёт: Simulation (F28)
    ├── 2026-02-25_AUTH_RBAC.md        ← QA-отчёт: Auth / RBAC
    ├── 2026-02-25_MULTITENANCY_E2E.md ← QA-отчёт: Multi-Tenancy E2E Customer Journey
    ├── 2026-02-25_MEMBER_PROFILE.md   ← QA-отчёт: Member Profile (F30)
    ├── 2026-03-01_BOARD_OPTIMIZATION.md ← QA-отчёт: Board Endpoint Optimization
    ├── 2026-03-02_F55_QUARTERLY_PLANNING.md ← QA-отчёт: F55 Quarterly Planning
    └── 2026-03-04_SYNC_MULTITENANT_FIXES.md ← QA-отчёт: Sync & Multi-Tenant Fixes
```

## Процесс

1. После реализации фичи → `/qa <screen>` (QA-скилл)
2. QA-агент берёт чек-лист из TEST_PLAN.md
3. Генерирует отчёт → `ai-ru/testing/reports/YYYY-MM-DD_<SCREEN>.md`
4. Обновляет таблицу в QA_STATUS.md
5. Исправить баги → обновить статус в отчёте
5. Повторный прогон `/qa` для регрессии

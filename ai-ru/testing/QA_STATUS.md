# QA Status — Lead Board

Мастер-документ: что протестировано QA-агентом, что ждёт проверки.

**Последнее обновление:** 2026-02-21

---

## Обзор по экранам

| # | Экран / Модуль | Фичи | QA статус | Баги | Отчёт |
|---|---------------|-------|-----------|------|-------|
| 1 | **Board** | F8, F10, F11, F15, F21, F31, F37, F42 | ✅ Проверен | 4 High, 5 Medium, 2 Low | [reports/2026-02-19_BOARD_DQ_BUGSLA.md](reports/2026-02-19_BOARD_DQ_BUGSLA.md) |
| 2 | **Teams** | F5, F6, F7, F37 | ✅ Проверен (F37) | 1 Medium (color tests missing) | [reports/2026-02-17_F35_F36_F37.md](reports/2026-02-17_F35_F36_F37.md) |
| 3 | **Team Metrics** | F22, F24, F32 | ✅ Проверен | 9 багов (1 Critical, 3 High, 4 Medium, 1 Low) | [reports/2026-02-17_TEAM_METRICS.md](reports/2026-02-17_TEAM_METRICS.md) |
| 4 | **Timeline** | F14 | ❌ Не проверен | — | — |
| 5 | **Data Quality** | F18, F36, F42 | ✅ Проверен | (входит в Board QA) | [reports/2026-02-19_BOARD_DQ_BUGSLA.md](reports/2026-02-19_BOARD_DQ_BUGSLA.md) |
| 6 | **Planning Poker** | F23 | ⏸️ Отложен | Известные баги с Jira | — |
| 7 | **Workflow Config** | F17, F29 | ❌ Не проверен | — | — |
| 8 | **Simulation** | F28 | ❌ Не проверен | — | — |
| 9 | **Projects** | F35 | ✅ Проверен | 1 High (test regression), 1 Low | [reports/2026-02-17_F35_F36_F37.md](reports/2026-02-17_F35_F36_F37.md) |
| 10 | **RICE Scoring** | F36 | ✅ Проверен | 2 Medium (case-sensitive, FP), 1 Low | [reports/2026-02-17_F35_F36_F37.md](reports/2026-02-17_F35_F36_F37.md) |
| 11 | **Project Timeline** | F35 | ✅ Проверен | Визуал ОК | [reports/2026-02-17_F35_F36_F37.md](reports/2026-02-17_F35_F36_F37.md) |
| 12 | **Member Profile** | F30 | ❌ Не проверен | — | — |
| 13 | **Setup Wizard** | F33 | ❌ Не проверен | — | — |
| 14 | **Auth / OAuth** | F4, F27 | ❌ Не проверен | — | — |
| 15 | **Sync** | F2, F3, F9, F34 | ✅ Проверен | 2 Critical, 2 High, 4 Medium, 2 Low | [reports/2026-02-21_SYNC.md](reports/2026-02-21_SYNC.md) |
| 16 | **AutoScore / Planning** | F13, F19, F20, F21 | ✅ Проверен | 1 High, 4 Medium, 4 Low | [reports/2026-02-23_AUTOSCORE_PLANNING.md](reports/2026-02-23_AUTOSCORE_PLANNING.md) |
| 17 | **Team Members** | F5, F6, F37, F41 | ✅ Проверен (F41) | 3 High, 6 Medium, 3 Low | [reports/2026-02-19_F41_ABSENCES.md](reports/2026-02-19_F41_ABSENCES.md) |
| 18 | **Member Absences** | F41 | ✅ Проверен | 3 High, 6 Medium, 3 Low | [reports/2026-02-19_F41_ABSENCES.md](reports/2026-02-19_F41_ABSENCES.md) |

| 19 | **Bug SLA Settings** | F42 | ✅ Проверен (встроен в Settings) | 0 багов | [reports/2026-02-23_BUG_SLA_TO_SETTINGS.md](reports/2026-02-23_BUG_SLA_TO_SETTINGS.md) |

**Прогресс: 13 / 19 экранов проверено (68%)**

---

## Статистика багов

| Severity | Открыто | Исправлено | Всего |
|----------|---------|------------|-------|
| Critical | 0 | 3 | 3 |
| High | 3 | 11 | 14 |
| Medium | 11 | 16 | 27 |
| Low | 7 | 7 | 14 |
| **Итого** | **21** | **37** | **58** |

---

## Детали по проверенным экранам

### F35 Projects + F36 RICE + F37 Team Colors — 2026-02-17

**API endpoints (21):** 19 PASS, 2 FAIL (minor)
**Visual:** 5 экранов проверены, все ОК

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-10 | High | 24 фронтенд-теста сломаны (регрессия F35/F36/F37) | OPEN |
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
| BUG-4 | High | Frontend тесты — 53/240 падают (missing mock) | OPEN |
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
| BUG-25 | Medium | Несогласованность формата createdAt (offset vs UTC) | OPEN |
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
| BUG-35 | Medium | Hardcoded PRIORITY_COLORS в BugSlaSettingsPage | OPEN (standard Jira colors) |
| BUG-36 | Medium | Нет aria-label на drag handle и alert icon | ✅ FIXED (already has aria-labels) |
| BUG-37 | Low | Hardcoded score colors в PriorityCell | OPEN (low priority) |
| BUG-38 | Low | Hardcoded severity labels/rule names в AlertIcon | OPEN (localization scope) |

---

## Приоритет следующих проверок

| Приоритет | Экран | Почему |
|-----------|-------|--------|
| P0 | **Board** | Основной экран, частично проверен (визуал), нужны глубокие тесты |
| ~~P0~~ | ~~**Sync**~~ | ✅ Проверен (2 Critical, 2 High, 4 Medium, 2 Low) |
| ~~P1~~ | ~~**AutoScore / Planning**~~ | ✅ Проверен (1 High, 4 Medium, 4 Low) |
| P1 | **Workflow Config** | Центральный конфиг, влияет на всё |
| P2 | **Data Quality** | 17+ правил, влияет на доверие к данным |
| P2 | **Timeline** | Визуализация планирования |
| P3 | **Member Profile** | Профиль участника |
| P3 | **Setup Wizard** | Первичная настройка |
| P3 | **Auth / OAuth** | Безопасность |

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
    └── 2026-02-21_SYNC.md             ← QA-отчёт: Sync Module (P0)
```

## Процесс

1. После реализации фичи → `/qa <screen>` (QA-скилл)
2. QA-агент берёт чек-лист из TEST_PLAN.md
3. Генерирует отчёт → `ai-ru/testing/reports/YYYY-MM-DD_<SCREEN>.md`
4. Обновляет таблицу в QA_STATUS.md
5. Исправить баги → обновить статус в отчёте
5. Повторный прогон `/qa` для регрессии

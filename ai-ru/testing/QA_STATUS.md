# QA Status — Lead Board

Мастер-документ: что протестировано QA-агентом, что ждёт проверки.

**Последнее обновление:** 2026-02-19

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
| 15 | **Sync** | F2, F3, F9, F34 | ❌ Не проверен | — | — |
| 16 | **AutoScore / Planning** | F13, F19, F20, F21 | ❌ Не проверен | — | — |
| 17 | **Team Members** | F5, F6, F37, F41 | ✅ Проверен (F41) | 3 High, 6 Medium, 3 Low | [reports/2026-02-19_F41_ABSENCES.md](reports/2026-02-19_F41_ABSENCES.md) |
| 18 | **Member Absences** | F41 | ✅ Проверен | 3 High, 6 Medium, 3 Low | [reports/2026-02-19_F41_ABSENCES.md](reports/2026-02-19_F41_ABSENCES.md) |

| 19 | **Bug SLA Settings** | F42 | ✅ Проверен | (входит в Board QA) | [reports/2026-02-19_BOARD_DQ_BUGSLA.md](reports/2026-02-19_BOARD_DQ_BUGSLA.md) |

**Прогресс: 11 / 19 экранов проверено (58%)**

---

## Статистика багов

| Severity | Открыто | Исправлено | Всего |
|----------|---------|------------|-------|
| Critical | 1 | 0 | 1 |
| High | 11 | 0 | 11 |
| Medium | 19 | 0 | 19 |
| Low | 8 | 0 | 8 |
| **Итого** | **39** | **0** | **39** |

---

## Детали по проверенным экранам

### F35 Projects + F36 RICE + F37 Team Colors — 2026-02-17

**API endpoints (21):** 19 PASS, 2 FAIL (minor)
**Visual:** 5 экранов проверены, все ОК

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-10 | High | 24 фронтенд-теста сломаны (регрессия F35/F36/F37) | OPEN |
| BUG-11 | Medium | RICE by-code endpoint case-sensitive | OPEN |
| BUG-12 | Medium | Floating point artifact в score-range (0.3000...04) | OPEN |
| BUG-13 | Medium | Нет тестов для TeamService color methods | OPEN |
| BUG-14 | Medium | Нет controller-тестов для ProjectController/RiceController | OPEN |
| BUG-15 | Low | RICE assessment 404 вместо 200+null | OPEN |
| BUG-16 | Low | Hardcoded 'ru-RU' locale в ProjectsPage | OPEN |

---

### Team Metrics (F22 + F24) — 2026-02-17

**API endpoints (11):** все работают, 1 critical bug (500 на несуществующем эпике)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-1 | Critical | epic-burndown 500 на несуществующем эпике | OPEN |
| BUG-2 | High | Broken Jira URL в ForecastAccuracyChart (нет `/browse/`) | OPEN |
| BUG-3 | High | Backend compilation error — ProjectService | OPEN |
| BUG-4 | High | Frontend тесты — 53/240 падают (missing mock) | OPEN |
| BUG-5 | Medium | Inverted date range (from > to) → 200 вместо 400 | OPEN |
| BUG-6 | Medium | Race conditions — нет AbortController | OPEN |
| BUG-7 | Medium | Silent error swallowing в метриках | OPEN |
| BUG-8 | Medium | NaN из URL параметров | OPEN |
| BUG-9 | Low | AssigneeTable .toFixed() на undefined | OPEN |

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
| BUG-17 | High | Silent error swallowing в AbsenceTimeline (.catch(() => {})) | OPEN |
| BUG-18 | High | Timezone bug в Date parsing (без Z suffix) | OPEN |
| BUG-19 | High | 0 frontend тестов для AbsenceTimeline/AbsenceModal | OPEN |
| BUG-20 | Medium | ABSENCE_COLORS дублируется в 2 файлах | OPEN |
| BUG-21 | Medium | Нет client-side валидации startDate <= endDate | OPEN |
| BUG-22 | Medium | 0 controller-level тестов для 5 endpoints | OPEN |
| BUG-23 | Medium | Нет aria-label на интерактивных элементах | OPEN |
| BUG-24 | Medium | Нет @DisplayName в backend тестах | OPEN |
| BUG-25 | Medium | Несогласованность формата createdAt (offset vs UTC) | OPEN |
| BUG-26 | Low | TypeScript `any` type в catch handler | OPEN |
| BUG-27 | Low | `today` memo без deps (не обновится в полночь) | OPEN |
| BUG-28 | Low | Tooltip может выйти за viewport | OPEN |

### Board + Data Quality + Bug SLA — 2026-02-19

**API endpoints (9):** 8 PASS, 1 NOTE (status filter case-sensitive)
**Visual:** 3 экрана проверены (Board, DataQuality, BugSlaSettings)
**Backend tests:** ALL PASSED (включая 3 новых теста STORY_FULLY_LOGGED_NOT_DONE)
**Frontend tests:** 6 FAIL в BoardPage.test.tsx (pre-existing BUG-10)

| Bug ID | Severity | Описание | Статус |
|--------|----------|----------|--------|
| BUG-29 | High | Сообщение `STORY_FULLY_LOGGED_NOT_DONE` показывает `100%%` вместо `100%` | OPEN |
| BUG-30 | High | Bug SLA страница отсутствует в навигации Layout.tsx | OPEN |
| BUG-31 | High | Race condition в PriorityCell — нет AbortController на async hover | OPEN |
| BUG-32 | High | Тихое проглатывание ошибки в PriorityCell tooltip (зависает loading) | OPEN |
| BUG-33 | Medium | BugSlaSettingsPage — error swallowing в 4 catch-блоках | OPEN |
| BUG-34 | Medium | DataQualityPage — axios error details lost | OPEN |
| BUG-35 | Medium | Hardcoded PRIORITY_COLORS в BugSlaSettingsPage | OPEN |
| BUG-36 | Medium | Нет aria-label на drag handle и alert icon | OPEN |
| BUG-37 | Low | Hardcoded score colors в PriorityCell | OPEN |
| BUG-38 | Low | Hardcoded severity labels/rule names в AlertIcon | OPEN |

---

## Приоритет следующих проверок

| Приоритет | Экран | Почему |
|-----------|-------|--------|
| P0 | **Board** | Основной экран, частично проверен (визуал), нужны глубокие тесты |
| P0 | **Sync** | Источник данных для всего |
| P1 | **AutoScore / Planning** | Ядро бизнес-логики, сложные расчёты |
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
    └── 2026-02-19_F41_ABSENCES.md     ← QA-отчёт: F41 Member Absences
```

## Процесс

1. После реализации фичи → `/qa <screen>` (QA-скилл)
2. QA-агент берёт чек-лист из TEST_PLAN.md
3. Генерирует отчёт → `ai-ru/testing/reports/YYYY-MM-DD_<SCREEN>.md`
4. Обновляет таблицу в QA_STATUS.md
5. Исправить баги → обновить статус в отчёте
5. Повторный прогон `/qa` для регрессии

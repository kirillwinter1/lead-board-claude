# QA Status — Lead Board

Мастер-документ: что протестировано QA-агентом, что ждёт проверки.

**Последнее обновление:** 2026-02-17

---

## Обзор по экранам

| # | Экран / Модуль | Фичи | QA статус | Баги | Отчёт |
|---|---------------|-------|-----------|------|-------|
| 1 | **Board** | F8, F10, F11, F15, F21, F31 | ❌ Не проверен | — | — |
| 2 | **Teams** | F5, F6, F7 | ❌ Не проверен | — | — |
| 3 | **Team Metrics** | F22, F24, F32 | ✅ Проверен | 9 багов (1 Critical, 3 High, 4 Medium, 1 Low) | [reports/2026-02-17_TEAM_METRICS.md](reports/2026-02-17_TEAM_METRICS.md) |
| 4 | **Timeline** | F14 | ❌ Не проверен | — | — |
| 5 | **Data Quality** | F18 | ❌ Не проверен | — | — |
| 6 | **Planning Poker** | F23 | ❌ Не проверен | — | — |
| 7 | **Workflow Config** | F17, F29 | ❌ Не проверен | — | — |
| 8 | **Simulation** | F28 | ❌ Не проверен | — | — |
| 9 | **Projects** | F35 | ❌ Не проверен | — | — |
| 10 | **RICE Scoring** | F36 | ❌ Не проверен | — | — |
| 11 | **Member Profile** | F30 | ❌ Не проверен | — | — |
| 12 | **Setup Wizard** | F33 | ❌ Не проверен | — | — |
| 13 | **Auth / OAuth** | F4, F27 | ❌ Не проверен | — | — |
| 14 | **Sync** | F2, F3, F9, F34 | ❌ Не проверен | — | — |
| 15 | **AutoScore / Planning** | F13, F19, F20, F21 | ❌ Не проверен | — | — |

**Прогресс: 1 / 15 экранов проверено (7%)**

---

## Статистика багов

| Severity | Открыто | Исправлено | Всего |
|----------|---------|------------|-------|
| Critical | 1 | 0 | 1 |
| High | 3 | 0 | 3 |
| Medium | 4 | 0 | 4 |
| Low | 1 | 0 | 1 |
| **Итого** | **9** | **0** | **9** |

---

## Детали по проверенным экранам

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

## Приоритет следующих проверок

| Приоритет | Экран | Почему |
|-----------|-------|--------|
| P0 | **Board** | Основной экран продукта, 621 LOC в BoardService без тестов |
| P0 | **Sync** | Источник данных для всего, был FK-баг |
| P1 | **AutoScore / Planning** | Ядро бизнес-логики, сложные расчёты |
| P1 | **Projects** | Недавно реализовано (F35), не проверялось |
| P1 | **RICE Scoring** | Недавно реализовано (F36), не проверялось |
| P2 | **Data Quality** | 17 правил, влияет на доверие к данным |
| P2 | **Workflow Config** | Центральный конфиг, влияет на всё |
| P3 | **Timeline** | Визуализация, меньше бизнес-логики |
| P3 | **Teams** | CRUD, низкий риск |
| P3 | **Planning Poker** | Отложен (известные баги) |

---

## Артефакты

```
ai-ru/testing/
├── QA_STATUS.md              ← этот документ (что проверено, баги)
├── TEST_PLAN.md              ← тест-план (что и как тестировать, чек-листы)
├── TEST_PYRAMID.md           ← тестовая пирамида (покрытие unit/integration/e2e)
└── reports/
    └── 2026-02-17_TEAM_METRICS.md   ← QA-отчёт: Team Metrics
```

## Процесс

1. После реализации фичи → `/qa <screen>` (QA-скилл)
2. QA-агент берёт чек-лист из TEST_PLAN.md
3. Генерирует отчёт → `ai-ru/testing/reports/YYYY-MM-DD_<SCREEN>.md`
4. Обновляет таблицу в QA_STATUS.md
5. Исправить баги → обновить статус в отчёте
5. Повторный прогон `/qa` для регрессии

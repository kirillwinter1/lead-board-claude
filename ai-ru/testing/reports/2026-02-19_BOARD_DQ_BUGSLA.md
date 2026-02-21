# QA Report: Board + Data Quality + Bug SLA
**Дата:** 2026-02-19
**Тестировщик:** Claude QA Agent
**Версия:** 0.42.0

## Summary
- **Общий статус: PASS WITH ISSUES**
- Backend tests: ALL PASSED (включая новое правило STORY_FULLY_LOGGED_NOT_DONE)
- Frontend tests: 29 failed (4 файла: BoardPage 6, TeamMetrics 12, Poker 10 — pre-existing)
- API tests: 8/9 PASS, 1 NOTE (status filter case-sensitive)
- Visual: 3 экрана проверены, 1 баг навигации
- Frontend code review: 3 High, 5 Medium, 2 Low

---

## Тестированные экраны

### S1: Board (`/board`)
- Загрузка доски — 13 эпиков, все с прогрессом, командами, алертами ✅
- Фильтрация по команде (teamIds=2) — 6 эпиков ✅
- Фильтрация по статусу — работает (case-sensitive) ⚠️
- Поиск по ключу (query=LB-205) — находит ✅
- Score breakdown (LB-205) — возвращает 7 факторов ✅
- Score breakdown несуществующего — 404 ✅
- Без авторизации — 401 ✅
- Несуществующая команда (teamIds=999) — пустой список ✅
- Алерты на эпиках и сторях — 28 алертов, включая STORY_FULLY_LOGGED_NOT_DONE ✅

### S5: Data Quality (`/board/data-quality`)
- Список нарушений — 29 violations ✅
- Фильтрация по severity (ERROR/WARNING/INFO) ✅
- Правило STORY_FULLY_LOGGED_NOT_DONE — срабатывает на 3 сторях (LB-108, LB-216, LB-217) ✅
- Ссылки на Jira — корректные ✅

### Bug SLA Settings (`/board/bug-sla`)
- 5 приоритетов с SLA (Highest 24h — Lowest 672h) ✅
- Edit/Delete кнопки ✅
- "+ Add Priority" ✅
- API `GET /api/bug-sla` — корректный ответ ✅

---

## Bugs Found

### High

| Bug ID | Описание | Файл | Детали |
|--------|----------|------|--------|
| BUG-29 | Сообщение `STORY_FULLY_LOGGED_NOT_DONE` показывает `100%%` вместо `100%` | `DataQualityRule.java:159` | `%%` в шаблоне не проходит через `String.format` когда нет аргументов — `formatMessage()` возвращает raw template |
| BUG-30 | Bug SLA страница отсутствует в навигации Layout.tsx | `Layout.tsx` | Роут `/board/bug-sla` есть в App.tsx, но NavLink в Layout.tsx не добавлен |
| BUG-31 | Race condition в PriorityCell — нет AbortController на async hover | `PriorityCell.tsx:45-91` | Быстрое перемещение мыши = утечки памяти, stale state |
| BUG-32 | Тихое проглатывание ошибки в PriorityCell tooltip | `PriorityCell.tsx:84-90` | console.error вместо UI feedback; loading spinner зависает при ошибке |

### Medium

| Bug ID | Описание | Файл | Детали |
|--------|----------|------|--------|
| BUG-10 | BoardPage.test.tsx — 6 тестов сломаны (missing `getStatusStyles` mock) | `BoardPage.test.tsx` | Pre-existing регрессия, мок `../api/board` не включает `getStatusStyles` |
| BUG-33 | BugSlaSettingsPage — error swallowing в 4 catch-блоках | `BugSlaSettingsPage.tsx:61,88,106,119` | `setError(generic message)` без деталей ошибки |
| BUG-34 | DataQualityPage — axios error details lost | `DataQualityPage.tsx:195-199` | Network status/response body теряется |
| BUG-35 | Hardcoded PRIORITY_COLORS в BugSlaSettingsPage | `BugSlaSettingsPage.tsx:13-21` | Нарушает Design System: цвета должны из конфигурации |
| BUG-36 | Нет aria-label на drag handle и alert icon | `BoardRow.tsx:43-50`, `AlertIcon.tsx:74-76` | Accessibility: screen readers не могут идентифицировать элементы |

### Low

| Bug ID | Описание | Файл | Детали |
|--------|----------|------|--------|
| BUG-37 | Hardcoded score colors в PriorityCell | `PriorityCell.tsx:19-22` | Minor: design system violation |
| BUG-38 | Hardcoded severity labels/rule names в AlertIcon | `AlertIcon.tsx:24-65` | Russian translations hardcoded в компоненте вместо messages файла |

---

## Test Coverage

### Backend
- `DataQualityServiceTest` — покрывает все правила включая новое STORY_FULLY_LOGGED_NOT_DONE (3 теста)
- `BoardServiceTest` — покрывает основную логику агрегации
- `BugSlaServiceTest` — покрывает SLA breach и stale checks

### Frontend — GAPS
- `BoardPage.test.tsx` — 6 тестов СЛОМАНЫ (pre-existing)
- 0 тестов для `AlertIcon`, `PriorityCell`, `BoardRow`, `BoardTable`
- 0 тестов для `BugSlaSettingsPage`
- 0 тестов для `DataQualityPage`

---

## Visual Review

| Экран | Статус | Замечания |
|-------|--------|-----------|
| Board | ✅ OK | Все колонки видны, алерты отображаются, прогресс-бары корректны |
| Data Quality | ✅ OK | 29 violations, фильтры работают, severity badges корректны |
| Bug SLA | ✅ OK | Таблица приоритетов, Edit/Delete, v0.42.0 |

---

## Recommendations

1. **FIX BUG-29 (Quick):** Заменить `%%` на `%` в шаблоне `STORY_FULLY_LOGGED_NOT_DONE` (или всегда вызывать `String.format`)
2. **FIX BUG-30 (Quick):** Добавить NavLink "Bug SLA" в `Layout.tsx`
3. **FIX BUG-10 (Medium):** Обновить мок `../api/board` в `BoardPage.test.tsx` — добавить `getStatusStyles`
4. **Add AbortController** в `PriorityCell` для отмены fetch при unmount
5. **Извлечь hardcoded colors** из `BugSlaSettingsPage` и `PriorityCell`
6. **Написать тесты** для `AlertIcon`, `BugSlaSettingsPage`, `DataQualityPage`

# QA Report: Code Review Fixes
**Дата:** 2026-03-08
**Тестировщик:** Claude QA Agent
**Scope:** 12 frontend файлов с фиксами из REVIEW_REPORT.md

## Summary
- Общий статус: **PASS WITH ISSUES**
- Frontend build (tsc + vite): **PASS**
- Backend compilation: **PASS** (с WIP changes)
- Visual testing: **BLOCKED** (pre-existing DB issue: `relation "teams" does not exist`)
- Code review фиксов: **PASS** — все изменения корректны

## Verified Fixes

### H12. ProjectsPage hardcoded colors → PASS
- 8 hardcoded hex colors заменены на константы из `constants/colors.ts`
- Новые константы семантически именованы: `ERROR_TEXT`, `WARNING_BG`, `SEPARATOR`, etc.
- Не осталось hardcoded hex в файле (кроме тех что уже в colors.ts)

### M34. ProjectGanttView hardcoded colors → PASS
- `#36B37E` → `PROGRESS_COMPLETE`, `#0065FF` → `PROGRESS_IN_PROGRESS`
- Tooltip цвета вынесены в `TOOLTIP_*` константы
- Grid: `#ebecf0` → `CHART_GRID`
- ~30 замен, все корректны

### H15. WorkflowConfigPage `value: any` → PASS
- Все `value: any` заменены на proper union types (`string | number | boolean`, `string | null`, etc.)
- Добавлены type guards (`typeof value === 'string'`) где нужно
- 0 `any` осталось в файле

### H16. WorkflowConfigPage `catch (err: any)` → PASS
- 7 из 9 `catch` блоков используют `catch (err: unknown)` с type narrowing
- 1 блок использует `catch (err)` без аннотации (TypeScript default unknown) — OK
- Type narrowing через `(err as { response?: ... }).response?.data?.message`

### M18-M23. Index-based keys → PASS
- **DataQualityPage**: `key={i}` → `key={issueKey-rule-severity}` — stable composite key
- **ProjectsPage**: `key={i}` → `key={severity-message}` — stable
- **TimeInStatusChart**: 6 мест исправлены → `bar-{status}`, `dot-{status}`, `left-{value}` etc.
- **WorkflowConfigPage**: wizard steps, issue types, statuses — domain keys
- **ProjectGanttView**: headers → date ISO strings / label keys

### M20. ChatWidget streaming keys → PASS (PRIORITY FIX)
- Добавлено `id: string` в `ChatMessage` interface (`chat.ts`)
- `msgIdCounter = useRef(0)` — stable incrementing IDs
- Все messages создаются с `id: msg-${++counter}`
- `key={msg.id}` вместо `key={index}` — корректно для SSE streaming

### M28. window.alert() → PASS
- **TeamsPage**: 3 alert → `setFormError()` / `setError()`; inline `<div className="error">` в модалке
- **TeamMembersPage**: 3 alert → `setFormError()` / `setError()`; аналогичный UI
- `window.confirm()` оставлен для delete — приемлемо

### DsrBreakdownChart type fix → PASS
- `CustomYTickProps.x/y` изменены с `number` на `string | number` (совместимость с Recharts)

## Bugs Found

### Medium
- **PRE-EXISTING: Backend API 500 на всех авторизованных запросах**
  - Причина: `relation "teams" does not exist` — таблица не найдена в текущей schema
  - Не связано с review-фиксами, pre-existing DB/migration issue
  - Блокирует визуальное тестирование

### Low
- **Remaining `key={i}` in codebase** — ещё ~25 мест с index keys в файлах не затронутых ревью (TimelinePage, WorklogTimeline, AbsenceTimeline, SearchInput, skeletons, landing demo). Не регрессия, но технический долг.
- **ChatWidget `key={i}` в renderMarkdown** — используется для статичных markdown строк внутри одного сообщения. Не переупорядочиваются, риск минимальный. Можно оставить.

## Visual Review
- **BLOCKED** — backend не обслуживает авторизованные запросы из-за pre-existing DB issue
- Frontend build проходит без ошибок, все TypeScript типы корректны

## Test Coverage Gaps
- Нет unit-тестов на `constants/colors.ts` (не нужны — просто константы)
- Нет тестов на ChatWidget message ID generation
- WorkflowConfigPage type changes не покрыты тестами (wizard logic)

## Recommendations
1. Починить pre-existing DB migration issue (блокирует всё визуальное QA)
2. Добавить ESLint rule `no-restricted-syntax` для `key={i}` паттерна — предотвратит регрессию
3. Рассмотреть toast/notification system вместо inline errors в модалках (TeamsPage/TeamMembersPage)

# TASK: Replace window.alert/confirm with styled UI

**Priority:** Medium
**Review ID:** M28
**Files:**
- `TeamsPage.tsx:87,93,98,133` — 4× alert/confirm
- `TeamMembersPage.tsx:117,129,164` — 5× alert/confirm

## Проблема

`window.alert()` и `window.confirm()`:
- Блокируют main thread
- Не стилизуемые, выглядят как системные диалоги
- Недоступны для screen readers
- Не тестируемые

## Рекомендация

Заменить на inline error/success states (`setErrorMessage`, styled div) или toast notification system. Для confirm — использовать Modal компонент (уже есть в проекте).

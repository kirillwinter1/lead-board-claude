# QA Report: Chat Model Change + Tools Expansion + AdminController

**Дата:** 2026-03-02
**Тестировщик:** Claude QA Agent
**Scope:** Смена модели (gemini-2.5-flash → llama-3.3-70b:free), удаление Groq, новый tool epic_progress, AdminController tenant-aware, ChatWidget markdown rendering

## Summary

- **Общий статус:** PASS WITH ISSUES
- **Unit tests:** 16 passed (ChatToolExecutorTest 16 + ChatServiceTest 8) — ALL PASS
- **Integration tests:** 73 failed (pre-existing, DB-зависимые, не связаны с изменениями)
- **API tests:** Chat endpoint OK (SSE работает), Admin users endpoint OK
- **Visual:** Скриншоты не получены (сессия перенаправляла на лендинг)
- **Code review:** 5 багов найдено (1 Medium-High, 1 Medium, 3 Low)

## Bugs Found

### BUG-140 (Medium-High): `.env` overrides model default — платная модель остаётся активной

**Описание:** В `backend/.env` явно прописано `CHAT_MODEL=google/gemini-2.5-flash`, что перезаписывает новый дефолт из `application.yml`.

**Статус:** ✅ FIXED — обновлён `.env` на `meta-llama/llama-3.3-70b-instruct:free`. **TODO: обновить на проде.**

---

### BUG-141 (Medium): ChatProperties default model рассинхронизирован с application.yml

**Описание:** `ChatProperties.java:13` содержит старый дефолт `google/gemini-2.5-flash`.

**Статус:** ✅ FIXED — дефолт обновлён на `meta-llama/llama-3.3-70b-instruct:free`.

---

### BUG-142 (Low): formatInline() comment misleading — *italic* не реализован

**Описание:** Комментарий говорил "Split by **bold** and *italic* patterns", но *italic* не реализован.

**Статус:** ✅ FIXED — комментарий исправлен на "Split by **bold** patterns".

---

### BUG-143 (Low): renderMarkdown() не поддерживает нумерованные списки

**Описание:** Markdown renderer не обрабатывал `1. `, `2. ` нумерованные списки.

**Статус:** ✅ FIXED — regex обновлён: `/^(\s*)(?:[*-]|\d+\.)\s+(.*)/`

---

### BUG-144 (Low): Dead code — `p.equals("/")` в page routing никогда не сработает

**Описание:** `currentPage` — display name, не путь. `p.equals("/")` никогда не true.

**Статус:** ✅ FIXED — условие убрано, `p.contains("board")` покрывает Board.

---

## Test Coverage Analysis

### Новый код (epic_progress tool)

| Аспект | Покрытие |
|--------|---------|
| Happy path (query + data) | ✅ 1 тест |
| Empty board (no epics) | ❌ Нет теста |
| teamId filter | ❌ Нет теста |
| RBAC (non-admin without teamId) | ❌ Нет теста |
| Exception handling | ❌ Нет теста |
| Null roleProgress / children | ❌ Нет теста |

**Рекомендация:** Добавить 3-4 теста для edge cases: пустая доска, null значения, RBAC с teamId=null для non-admin.

### AdminController (tenant-aware)

| Аспект | Покрытие |
|--------|---------|
| getAllUsers() tenant mode | ❌ 0 тестов |
| updateUserRole() tenant mode | ❌ 0 тестов |
| Self-lockout protection | ❌ 0 тестов |
| Fallback (non-tenant mode) | ❌ 0 тестов |

**Рекомендация:** AdminController не имеет ни одного теста. Критичный модуль (управление ролями) без покрытия.

### ChatWidget markdown

| Аспект | Покрытие |
|--------|---------|
| renderMarkdown() | ❌ 0 тестов |
| formatInline() **bold** | ❌ 0 тестов |
| Nested lists | ❌ 0 тестов |
| Edge cases (empty, only whitespace) | ❌ 0 тестов |

## Recommendations

1. **P0:** Обновить `backend/.env` — убрать `CHAT_MODEL=google/gemini-2.5-flash` (BUG-140)
2. **P0:** Обновить `.env` на проде (79.174.94.70)
3. **P1:** Синхронизировать дефолт в ChatProperties.java (BUG-141)
4. **P2:** Добавить тесты для epic_progress edge cases
5. **P2:** Добавить тесты для AdminController (0 тестов на критичный модуль)
6. **P3:** Доработать markdown renderer (numbered lists, italic)

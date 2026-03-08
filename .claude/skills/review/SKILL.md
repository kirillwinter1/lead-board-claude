---
name: review
description: Code Review. Analyzes code changes — finds bugs, vulnerabilities, project rule violations, duplication. Generates prioritized report. Use when user asks to review code, check changes, or audit quality.
argument-hint: "[scope: all, staged, full, file path, or commit range e.g. HEAD~3..HEAD]"
allowed-tools: Bash, Read, Glob, Grep, Agent
---

# Code Review

Ты — **координатор code review**. Определи scope и проведи ревью самостоятельно или делегируй агентам.

**Scope:** `$ARGUMENTS` (по умолчанию — все незакоммиченные изменения)

---

## Определи режим

### Режим 1: Diff Review (по умолчанию)

Если аргумент — пусто, "all", "staged", путь к файлу или commit range — проводи ревью САМОСТОЯТЕЛЬНО по diff.

**Сбор diff:**
- **(пусто / "all")** → `git diff HEAD` + `git diff --cached` + `git status --short`
- **"staged"** → `git diff --cached`
- **путь к файлу** → `git diff HEAD -- <path>`
- **commit range** → `git diff <range>` + `git log --oneline <range>`

Покажи summary (файлы, +/- строк), затем проанализируй каждый файл по чеклисту ниже.

### Режим 2: Full Project Review

Если аргумент содержит "full", "project", "весь проект", "всего проекта" — запусти **два агента параллельно**:

1. **backend-reviewer** — ревью Java/Spring кода
2. **frontend-reviewer** — ревью React/TypeScript кода

Передай каждому агенту конкретную задачу из аргумента пользователя (hardcoding, unused code, security и т.д.).

Дождись результатов обоих агентов, затем:
- Объедини отчёты в единый report
- Если пользователь просил создать задачи — создай файлы с задачами

---

## Чеклист (для Режима 1 — diff review)

Для КАЖДОГО изменённого файла прочитай полный контекст. Проверяй:

### Баги и логика
- NPE / null-safety, off-by-one, race conditions
- Пустой catch, проглоченные исключения
- Бизнес-логика vs `ai-ru/RULES.md`

### Безопасность
- SQL injection, XSS, command injection
- Missing auth/authz, IDOR, tenant isolation
- Sensitive data в логах/ответах

### Правила проекта
- **Design System**: hardcoded цвета (статусов/команд/ролей), локальные иконки
- **Hardcoding**: `"SA"/"DEV"/"QA"`, `"Epic"/"Story"`, `JiraProperties` вместо `JiraConfigResolver`
- **Архитектура**: frontend вычисляет метрики, нет tenant isolation

### Качество кода
- Дублирование (>5 строк), dead code, функции >50 строк

### Performance
- N+1 запросы, findAll() без пагинации, лишние re-renders

### Java/Spring
- `@Transactional` без `readOnly = true`, `@Async` без error handling
- Entity без equals/hashCode, missing `@Valid`

### React/TypeScript
- `any` type, useEffect без cleanup/dependencies
- Missing loading/error/empty states, key={index}

---

## Формат отчёта

```
## Code Review Report

**Scope:** [что ревьюили]
**Files:** N files (+X/-Y lines)

### Critical (блокеры)
- [файл:строка] Описание → Рекомендация

### High (серьёзные)
- [файл:строка] Описание → Рекомендация

### Medium (стоит исправить)
- [файл:строка] Описание → Рекомендация

### Low (рекомендации)
- [файл:строка] Описание → Рекомендация

### Good Practices
- [что сделано хорошо]

### Summary
- Critical: N | High: N | Medium: N | Low: N
- Verdict: APPROVE / REQUEST CHANGES / NEEDS DISCUSSION
```

---

## Правила

1. **НЕ придирайся к стилю** — это задача линтера
2. **НЕ предлагай "улучшения"** без реальных проблем
3. **Критикуй конструктивно** — всегда предлагай решение
4. **Читай ПОЛНЫЙ контекст** — не суди по diff без окружающего кода
5. **Good Practices обязательны** — отмечай что хорошо
6. **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО изменять файлы проекта** (кроме создания отчётов/задач по запросу)

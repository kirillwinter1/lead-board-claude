# F53 — AI Chat Tools Expansion

**Статус:** ✅ Реализовано (v0.53.0)
**Дата:** 2026-03-02

## Описание

Расширение AI-чат-ассистента (F52): 7 новых инструментов (итого 12), смена модели на бесплатную Llama 3.3 70B, улучшения маршрутизации и рендеринга ответов.

## Что нового

### Смена модели

| Параметр | Было (F52) | Стало (F53) |
|----------|-----------|-------------|
| Модель | google/gemini-2.5-flash | meta-llama/llama-3.3-70b-instruct:free |
| Стоимость | Платная | Бесплатная |
| Провайдер | OpenRouter | OpenRouter |

### Новые инструменты (7 шт.)

| Tool | Описание | Параметры |
|------|----------|-----------|
| `task_search` | Поиск задач по ключу, статусу, типу, тексту. До 20 результатов | status?, teamId?, type?, query? |
| `bug_metrics` | Метрики багов: open/resolved, SLA compliance, по приоритету | teamId? |
| `project_list` | Список проектов с прогрессом, expected done, RICE score | — |
| `rice_ranking` | Рейтинг RICE score, top 20, сортировка по убыванию | — |
| `member_absences` | Отсутствия участников (отпуска, больничные) на 90 дней | teamId (required) |
| `bug_sla_settings` | Конфигурация Bug SLA: лимиты по приоритетам | — |
| `task_details` | Полная информация о задаче по Jira-ключу | issueKey (required) |
| `team_members` | Список участников команды с ролями и грейдами | teamId (required) |
| `epic_progress` | Прогресс эпиков: %, роли (SA/DEV/QA), оценки, стори | teamId?, query? |

### Существующие инструменты (из F52)

| Tool | Описание |
|------|----------|
| `board_summary` | Эпики и стори по статусам |
| `team_list` | Список команд |
| `team_metrics` | DSR, throughput, lead time, cycle time (30 дней) |
| `task_count` | Количество задач по фильтрам |
| `data_quality_summary` | Качество данных |

### Итого: 13 инструментов

### Улучшения фронтенда

- **Markdown rendering** — поддержка **bold**, нумерованных списков (`1.`, `2.`), маркированных списков (`-`, `*`)
- **Контекстная маршрутизация** — чат-виджет знает текущую страницу и добавляет её в system prompt

### AdminController (tenant-aware)

- `GET /api/admin/users` — загрузка пользователей из tenant_users (вместо глобального users)
- `PUT /api/admin/users/{id}/role` — обновление роли в tenant_users
- Защита от self-lockout (нельзя снять ADMIN с себя)
- Fallback на глобальных пользователей для legacy-режима (без тенанта)

## Архитектура

### Tool Execution Flow

```
User → ChatService → LLM (tool_calls) → ChatToolExecutor → Backend Services
                   ← LLM (final text) ← ChatToolExecutor (tool results)  ←
```

### RBAC

- ADMIN / PROJECT_MANAGER — все инструменты, все команды
- TEAM_LEAD / MEMBER — только свои команды (`AuthorizationService.getUserTeamIds()`)
- `checkTeamAccess(teamId)` — проверка перед каждым вызовом инструмента

## Конфигурация

```yaml
chat:
  enabled: ${CHAT_ENABLED:false}
  model: ${CHAT_MODEL:meta-llama/llama-3.3-70b-instruct:free}
  base-url: ${CHAT_BASE_URL:https://openrouter.ai/api/v1}
  api-key: ${CHAT_API_KEY:}
```

## Тесты

- **ChatToolExecutorTest** — 16 тестов: все 13 tools + RBAC + unknown tool + invalid args
- **ChatServiceTest** — 8 тестов: disabled, simple message, tool loop, session clear, history trim, max tool calls

## Файлы

### Backend (MODIFY)
- `chat/tools/ChatToolRegistry.java` — +8 инструментов (5→13)
- `chat/tools/ChatToolExecutor.java` — +8 обработчиков: task_search, bug_metrics, project_list, rice_ranking, member_absences, bug_sla_settings, task_details, team_members, epic_progress
- `chat/ChatProperties.java` — дефолт модели → llama-3.3-70b
- `chat/llm/OpenAiCompatibleLlmClient.java` — улучшения парсинга ответов
- `chat/ChatService.java` — system prompt с контекстом страницы
- `admin/AdminController.java` — tenant-aware user management

### Frontend (MODIFY)
- `components/chat/ChatWidget.tsx` — markdown rendering, page context routing

### Tests
- `ChatToolExecutorTest.java` — 8 новых тестов для новых tools

# F52 — AI Chat Assistant

**Статус:** ✅ Реализовано (v0.52.0)
**Дата:** 2026-03-01

## Описание

Встроенный AI-чат-ассистент, который отвечает на вопросы о данных LeadBoard, помогает с навигацией и использует инструменты (tools) для получения актуальной информации из системы.

## Архитектура

### Backend (chat package)

- **ChatProperties** — конфигурация: provider (groq), API key, model, лимиты
- **LLM абстракция** — `LlmClient` интерфейс + `OpenAiCompatibleLlmClient` (работает с OpenRouter, Groq, OpenAI и др.)
  - `chat()` — синхронный вызов с tool calling
  - `streamChat()` — SSE-стриминг финального ответа
- **ChatToolRegistry** — определения 5 инструментов
- **ChatToolExecutor** — исполнение инструментов через backend-сервисы
- **ChatService** — оркестратор: system prompt + tool loop + SSE стриминг
- **ChatController** — REST endpoint (POST /api/chat/message → SSE)

### Инструменты (Tools)

| Tool | Описание | Параметры |
|------|----------|-----------|
| `board_summary` | Эпики и стори по статусам | teamId? |
| `team_list` | Список команд | — |
| `team_metrics` | DSR, velocity, throughput (30 дней) | teamId (required) |
| `task_count` | Количество задач | status?, teamId?, type? |
| `data_quality_summary` | Качество данных | teamId? |

### RBAC

- ADMIN / PROJECT_MANAGER — видят все данные
- TEAM_LEAD / MEMBER — видят только свои команды
- Проверка через `AuthorizationService.getUserTeamIds()`

### Frontend

- **ChatWidget** — floating widget (bubble button → expandable panel)
- SSE-стриминг через `fetch()` + `ReadableStream`
- Tool call индикаторы в реальном времени
- Session-only история (in-memory)

### Rate Limiting

- `/api/chat/message` — 30 req/min per IP

## Конфигурация

```yaml
chat:
  enabled: ${CHAT_ENABLED:false}
  provider: ${CHAT_PROVIDER:openrouter}        # openrouter | groq | openai
  api-key: ${CHAT_API_KEY:}                    # ключ от провайдера
  model: ${CHAT_MODEL:google/gemini-2.5-flash} # бесплатная модель OpenRouter
  base-url: ${CHAT_BASE_URL:https://openrouter.ai/api/v1}
```

### Провайдеры

| Провайдер | Base URL | Бесплатные модели |
|-----------|----------|-------------------|
| OpenRouter | `https://openrouter.ai/api/v1` | Gemini 2.5 Flash, GPT-5 Nano, DeepSeek V3.2, Grok 4.1 Fast |
| Groq | `https://api.groq.com/openai/v1` | Llama 3.3 70B (ограниченно) |
| OpenAI | `https://api.openai.com/v1` | нет |

Все используют OpenAI-совместимый формат API.

## Knowledge Base

Загружается из `classpath:chat/knowledge_base.md` (~600 строк). Содержит описания всех экранов, формул, FAQ.

## Тесты

- **ChatToolExecutorTest** — 8 тестов: все 5 tools, RBAC, unknown tool, invalid args
- **ChatServiceTest** — 6 тестов: disabled, simple message, tool loop, session clear, history trim, max tool calls

## Файлы

### Backend (NEW)
- `chat/ChatProperties.java`
- `chat/ChatService.java`
- `chat/ChatController.java`
- `chat/dto/ChatMessageRequest.java`
- `chat/dto/ChatSseEvent.java`
- `chat/llm/LlmClient.java`
- `chat/llm/LlmMessage.java`
- `chat/llm/LlmToolCall.java`
- `chat/llm/LlmToolDefinition.java`
- `chat/llm/LlmResponse.java`
- `chat/llm/OpenAiCompatibleLlmClient.java`
- `chat/tools/ChatToolRegistry.java`
- `chat/tools/ChatToolExecutor.java`

### Backend (MODIFY)
- `application.yml` — chat config section
- `RateLimitFilter.java` — chat rate limit
- `build.gradle.kts` — version 0.52.0

### Frontend (NEW)
- `api/chat.ts`
- `components/chat/ChatWidget.tsx`
- `components/chat/ChatWidget.css`

### Frontend (MODIFY)
- `Layout.tsx` — ChatWidget integration
- `package.json` — version 0.52.0

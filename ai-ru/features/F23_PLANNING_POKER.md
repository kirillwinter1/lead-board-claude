# F23: Planning Poker

## Цель
Real-time Planning Poker для совместной оценки stories командой.

> Обновлено 2026-07-07: фича починена после QA (см. `ai-ru/testing/reports/2026-07-07_POKER.md`, BUG-174..188). Ниже описано актуальное поведение.

## Функционал

### Сессии
- Создание сессии для эпика команды (любой эпик не в Done-статусе по WorkflowConfig)
- Добавление stories: импорт существующих из Jira или создание новых (Jira-first)
- Состояния сессии: PREPARING → ACTIVE → COMPLETED
- Состояния story: PENDING → VOTING → REVEALED → COMPLETED
- Ведущий (facilitator) — создатель сессии; **все управляющие действия проверяются на сервере** по аутентифицированному аккаунту

### Голосование
- Оценка в **часах по ролям** (SA/DEV/QA из WorkflowConfig): карты 2, 4, 8, 12, 16, 24, 32, 40, «?» (−1)
- Валидация на сервере: −1 или 1..160 часов
- Голоса скрыты до reveal (значения не передаются в state для VOTING story)
- Финальную оценку по ролям устанавливает ведущий (0..160), только из состояния REVEALED
- При наличии Jira-ключа финальные оценки записываются в сабтаски Jira

### Real-time (WebSocket)
- Подключение: `/ws/poker/{roomCode}`
- Аутентификация и tenant — на handshake (`PokerHandshakeInterceptor`): session cookie → пользователь, tenant из auth-сессии (subdomain, если есть, обязан совпадать)
- Личность участника (accountId, displayName) и facilitator-статус определяются **сервером**, клиентский payload не является источником доверия
- Каждая WS-операция выполняется с установленным TenantContext (таблицы покера живут в tenant-схемах, V45)
- Reconnect на клиенте: exponential backoff 3s→30s

## API

| Endpoint | Описание |
|----------|----------|
| `GET /api/poker/eligible-epics/{teamId}` | Эпики команды не в Done (WorkflowConfig, без хардкода статусов) |
| `GET /api/poker/epic-stories/{epicKey}` | Stories эпика из Jira с ролями/оценками сабтасков |
| `POST /api/poker/sessions` | Создать сессию (создатель = facilitator) |
| `GET /api/poker/sessions/{id}` | Состояние сессии |
| `GET /api/poker/sessions/room/{roomCode}` | Сессия по коду комнаты |
| `GET /api/poker/sessions/team/{teamId}` | Сессии команды |
| `POST /api/poker/sessions/{id}/start` | Старт (facilitator) |
| `POST /api/poker/sessions/{id}/complete` | Завершение (facilitator) |
| `POST /api/poker/sessions/{id}/next` | Следующая story (facilitator) |
| `POST /api/poker/sessions/{id}/stories?createInJira=` | Добавить story (facilitator; Jira-first при createInJira=true) |
| `GET /api/poker/sessions/{id}/stories` | Stories сессии с голосами |
| `DELETE /api/poker/stories/{storyId}` | Удалить story (facilitator; в UI пока не выведено) |
| `POST /api/poker/stories/{storyId}/reveal` | Раскрыть голоса (facilitator, только VOTING → иначе 409) |
| `POST /api/poker/stories/{storyId}/final?updateJira=` | Финальная оценка (facilitator, только REVEALED → иначе 409) |
| `GET /api/poker/stories/{storyId}/votes` | Голоса story |

Голосование (VOTE) — только через WebSocket. Ошибки: 400 (валидация), 403 (не facilitator), 404 (нет ресурса), 405 (метод), 409 (нарушение стейт-машины).

## Файлы

**Backend (`poker/`):**
- `controller/PokerController.java` — REST + facilitator-гарды
- `service/PokerSessionService.java` — стейт-машина, голосование, участники (in-memory)
- `service/PokerJiraService.java` — Jira-интеграция (projectKey через `JiraConfigResolver`)
- `websocket/PokerHandshakeInterceptor.java` — auth + tenant на handshake
- `websocket/PokerWebSocketHandler.java` — WS-протокол, TenantContext-обёртка
- `entity/` — PokerSessionEntity, PokerStoryEntity, PokerVoteEntity

**Frontend:**
- `pages/PlanningPokerPage.tsx` — лобби (список сессий)
- `pages/PokerRoomPage.tsx` — комната голосования
- `hooks/usePokerWebSocket.ts` — WebSocket хук (backoff-reconnect)
- `api/poker.ts` — API клиент

## UI
- `/poker` — лобби: список сессий команды, создание, вход по коду
- `/poker/room/:roomCode` — комната: карты, участники, результаты; кнопки ведущего блокируются при отсутствии соединения

## Известные ограничения (tech debt)
- Состояние комнат in-memory: не переживает рестарт, не масштабируется на >1 инстанс (комнаты чистятся при уходе последнего участника)
- Удаление story не выведено в UI; удаления сессии нет
- Языковой микс: страницы покера на русском при английской навигации
- Финальная оценка не предзаполняется медианой голосов

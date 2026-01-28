# F23: Planning Poker

## Цель
Real-time Planning Poker для совместной оценки stories командой.

## Функционал

### Сессии
- Создание сессии для эпика (выбор из eligible эпиков)
- Добавление stories из Jira в сессию
- Состояния сессии: WAITING → VOTING → REVEALED → COMPLETED

### Голосование
- Шкала Фибоначчи: 0, 1, 2, 3, 5, 8, 13, 21, ?
- Все голоса скрыты до reveal
- После reveal — среднее, медиана, разброс
- Финальная оценка устанавливается ведущим

### Real-time (WebSocket)
- Подключение через WebSocket (`/ws/poker/{sessionId}`)
- Уведомления о голосах, reveal, смене story
- Список участников online

## API

| Endpoint | Описание |
|----------|----------|
| `POST /api/poker/sessions` | Создать сессию |
| `GET /api/poker/sessions/{id}` | Состояние сессии |
| `POST /api/poker/sessions/{id}/stories` | Добавить story |
| `POST /api/poker/stories/{id}/vote` | Проголосовать |
| `POST /api/poker/stories/{id}/reveal` | Раскрыть голоса |
| `POST /api/poker/stories/{id}/final` | Установить оценку |
| `GET /api/poker/eligible-epics` | Доступные эпики |
| `GET /api/poker/epics/{key}/stories` | Stories эпика |
| `DELETE /api/poker/sessions/{id}` | Удалить сессию |

## Файлы

**Backend:**
- `poker/PokerController.java` (281 LOC, 13 endpoints)
- `poker/PokerSessionService.java` (325 LOC)
- `poker/PokerWebSocketHandler.java` (280 LOC)
- `poker/entity/` — PokerSessionEntity, PokerStoryEntity, PokerVoteEntity

**Frontend:**
- `pages/PlanningPokerPage.tsx` — лобби (список сессий)
- `pages/PokerRoomPage.tsx` — комната голосования
- `hooks/usePokerWebSocket.ts` — WebSocket хук
- `api/poker.ts` — API клиент

## UI
- `/poker` — лобби: список активных сессий, создание новой
- `/poker/:id` — комната: карты голосования, список участников, результаты

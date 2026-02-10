# F7. Team Sync from Atlassian

## Обзор

Синхронизация команд и участников из Atlassian Teams API с сохранением локальных настроек.

## Atlassian API

| Endpoint | Назначение |
|----------|-----------|
| `GET /gateway/api/public/teams/v1/org/{orgId}/teams` | Список команд |
| `POST /gateway/api/public/teams/v1/org/{orgId}/teams/{id}/members` | Участники команды |
| `GET /rest/api/3/user?accountId={id}` | Информация о пользователе |

Аутентификация: Basic Auth (email:apiToken).

## Алгоритм синхронизации

1. **Получить все ACTIVE команды** из Atlassian
2. Для каждой команды:
   - Существует по `atlassianTeamId` → обновить name, jiraTeamValue, active=true
   - Не существует → создать новую с `atlassianTeamId`
3. **Синхронизация участников** каждой команды:
   - Существует по accountId → обновить displayName (остальное не трогать)
   - Не существует → создать с defaults (role=DEV, grade=MIDDLE, hoursPerDay=6.0)
   - Отсутствует в Atlassian → деактивировать (active=false)
4. **Деактивация команд** с `atlassianTeamId` не из списка Atlassian

## Маппинг полей

| Atlassian | OneLane | Стратегия |
|-----------|---------|-----------|
| `teamId` | `atlassianTeamId` | Прямое копирование |
| `displayName` (команды) | `name` + `jiraTeamValue` | Оба из displayName |
| `state` | Видимость | Только ACTIVE синхронизируются |
| `accountId` (участник) | `jiraAccountId` | Прямое копирование |
| User displayName | `displayName` | Через отдельный запрос getUser() |

## Сохранение локальных данных

При повторной синхронизации **НЕ перезаписываются:**
- `role` (SA/DEV/QA)
- `grade` (Junior/Middle/Senior)
- `hoursPerDay`

Обновляется только `displayName`.

## Защита от параллелизма

- `AtomicBoolean syncInProgress` — блокировка повторного запуска
- `lastSyncTime` — время последней успешной синхронизации
- `lastSyncError` — ошибка (если была)

## API

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/api/teams/sync/trigger` | Запуск синхронизации |
| GET | `/api/teams/sync/status` | Статус синхронизации |

## Принципы

- **Soft delete** — никогда не удаляет, только деактивирует
- **Non-destructive** — ручные настройки участников сохраняются
- **Graceful degradation** — ошибка по одной команде не останавливает синхронизацию остальных

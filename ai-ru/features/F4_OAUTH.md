# F4. OAuth 2.0 (Atlassian 3LO)

## Обзор

Аутентификация через Atlassian OAuth 2.0 (3-legged OAuth) с автоматическим обновлением токенов и stateless-сессиями.

## OAuth Flow

```
1. Frontend → GET /oauth/atlassian/authorize
2. Backend генерирует state (UUID), редиректит на Atlassian
3. Пользователь авторизуется в Atlassian
4. Atlassian → GET /oauth/atlassian/callback?code=...&state=...
5. Backend:
   a. Валидирует state (CSRF-защита)
   b. Обменивает code → access_token + refresh_token
   c. Запрашивает профиль: GET https://api.atlassian.com/me
   d. Запрашивает cloud_id: GET /oauth/token/accessible-resources
   e. Создаёт/обновляет UserEntity + OAuthTokenEntity
6. Редирект на frontend: /?auth=success
```

## Scopes

```
read:me, read:jira-user, read:jira-work, offline_access
```

## Управление токенами

- **Хранение:** БД (таблицы `users`, `oauth_tokens`)
- **Автообновление:** если до истечения < 5 минут и есть refresh_token → автоматический refresh через `POST /oauth/token`
- **Получение:** `OAuthService.getValidAccessToken()` — возвращает валидный токен или null

## Сессии

- **Stateless:** `SessionCreationPolicy.STATELESS` (нет серверных сессий)
- **Фильтр:** `LeadBoardAuthenticationFilter` на каждый запрос:
  1. Ищет последний `OAuthTokenEntity`
  2. Извлекает `UserEntity`
  3. Устанавливает `SecurityContext`

## Создание пользователя

- При первом логине создаётся `UserEntity` с ролью `MEMBER`
- Первый пользователь автоматически становится `ADMIN` (миграция V23)

## RBAC (роли)

| Роль | Права |
|------|-------|
| ADMIN | Полный доступ, управление пользователями, настройки |
| TEAM_LEAD | Управление своей командой, изменение приоритетов |
| MEMBER | Просмотр доски, участие в Poker |
| VIEWER | Только чтение |

## API Endpoints

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/oauth/atlassian/authorize` | Начало OAuth flow |
| GET | `/oauth/atlassian/callback` | Callback от Atlassian |
| GET | `/oauth/atlassian/status` | Статус аутентификации |
| POST | `/oauth/atlassian/logout` | Выход (удаление токенов) |

## Публичные endpoints (без аутентификации)

```
/oauth/atlassian/**, /api/health, /api/config, /ws/**
```

## Конфигурация

| Переменная | Описание |
|-----------|----------|
| `ATLASSIAN_CLIENT_ID` | Client ID приложения |
| `ATLASSIAN_CLIENT_SECRET` | Client Secret |
| `ATLASSIAN_REDIRECT_URI` | Callback URL |

## Известные ограничения

- State хранится in-memory (не подходит для multi-instance)
- Токены хранятся без шифрования в БД
- Single-tenant: `findLatestToken()` берёт последний токен

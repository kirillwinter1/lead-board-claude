# Инструкции для Claude

## Запуск проекта

### Перед запуском — освободить порты
```bash
lsof -ti:8080 -ti:5173 | xargs kill -9 2>/dev/null || true
```

### База данных PostgreSQL

**Вариант 1: Docker**
```bash
docker compose up -d
```

**Вариант 2: Локальный PostgreSQL**
```bash
psql -c "CREATE DATABASE leadboard"
psql -c "CREATE USER leadboard WITH PASSWORD 'leadboard'"
psql -c "GRANT ALL PRIVILEGES ON DATABASE leadboard TO leadboard"
psql -d leadboard -c "ALTER DATABASE leadboard OWNER TO leadboard"
```

### Запуск backend (порт 8080)
```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/backend
./gradlew bootRun
```

### Запуск frontend (порт 5173)
```bash
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend
npm run dev
```

## Сборка

### Backend
```bash
cd backend && ./gradlew build
```

### Frontend
```bash
cd frontend && npm run build
```

## Проверка работоспособности

```bash
curl http://localhost:8080/api/health   # Backend
curl http://localhost:5173/api/health   # Frontend (через proxy)
```

## Конфигурация (.env)

Файл `backend/.env` (не коммитить!):
```bash
# Jira
JIRA_BASE_URL=https://your-domain.atlassian.net
JIRA_EMAIL=your-email@example.com
JIRA_API_TOKEN=your-api-token
JIRA_PROJECT_KEY=YOUR_PROJECT
JIRA_SYNC_INTERVAL_SECONDS=300
JIRA_TEAM_FIELD_ID=customfield_12345  # ID поля Team в Epic

# Database (опционально, есть дефолты)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=leadboard
DB_USERNAME=leadboard
DB_PASSWORD=leadboard
```

## Важно

- **Всегда убивать процессы на портах перед запуском** — не допускать запуск на альтернативных портах
- **PostgreSQL должен быть запущен** перед стартом backend
- Backend запускать из директории `backend/`
- Frontend запускать из директории `frontend/`
- Credentials хранятся в `backend/.env` (не коммитить!)

## Структура проекта

```
lead-board-claude/
├── backend/          # Spring Boot (Java 21)
│   ├── .env          # Credentials (gitignore)
│   └── src/
├── frontend/         # React + Vite + TypeScript
│   └── src/
├── ai-ru/            # Документация на русском
└── docker-compose.yml
```

## Atlassian OAuth 2.0

### Настройка приложения в Atlassian Developer Console

1. Открыть https://developer.atlassian.com/console/myapps/
2. Создать или выбрать приложение
3. В **Permissions** добавить scopes:
   - **Jira API:** `read:jira-user`, `read:jira-work`
   - **User identity API:** `read:me`
4. В **Authorization** включить **Authorization code grants**
5. Добавить **Callback URL:** `http://localhost:8080/oauth/atlassian/callback`

### Важно
- `offline_access` не добавляется как scope в консоли — работает автоматически при Authorization code grants
- Scopes с пробелами в .env файле парсятся некорректно — лучше использовать дефолт из кода
- После авторизации токен сохраняется в БД и автообновляется

### Credentials в .env
```bash
ATLASSIAN_CLIENT_ID=your_client_id
ATLASSIAN_CLIENT_SECRET=your_client_secret
ATLASSIAN_REDIRECT_URI=http://localhost:8080/oauth/atlassian/callback
ATLASSIAN_SITE_BASE_URL=https://your-domain.atlassian.net
```

## Решённые проблемы

### Jira API 410 Gone
Atlassian изменил endpoint: `/rest/api/3/search` → `/rest/api/3/search/jql`

### Role mapping с кириллицей
YAML с кириллическими ключами работает нестабильно — добавлен fallback в коде через `String.contains()`

### Flyway PostgreSQL
Нужна отдельная зависимость с версией:
```kotlin
implementation("org.flywaydb:flyway-database-postgresql:10.10.0")
```

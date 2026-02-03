# Инструкции для Claude

## Запуск проекта

### Перед запуском — освободить порты
```bash
lsof -ti:8080 -ti:5173 | xargs kill -9 2>/dev/null || true
```

### База данных PostgreSQL

**Docker:**
```bash
docker compose up -d
```

**Локальный PostgreSQL:**
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

## Сборка и тесты

```bash
cd backend && ./gradlew build    # Backend сборка
cd backend && ./gradlew test     # Backend тесты
cd frontend && npm run build     # Frontend сборка
```

## Проверка работоспособности

```bash
curl http://localhost:8080/api/health
curl http://localhost:5173/api/health   # через proxy
```

## Конфигурация (.env)

Файл `backend/.env` (не коммитить!):
```bash
JIRA_BASE_URL=https://your-domain.atlassian.net
JIRA_EMAIL=your-email@example.com
JIRA_API_TOKEN=your-api-token
JIRA_PROJECT_KEY=YOUR_PROJECT
JIRA_SYNC_INTERVAL_SECONDS=300
JIRA_TEAM_FIELD_ID=customfield_12345
ATLASSIAN_CLIENT_ID=your_client_id
ATLASSIAN_CLIENT_SECRET=your_client_secret
ATLASSIAN_REDIRECT_URI=http://localhost:8080/oauth/atlassian/callback
ATLASSIAN_SITE_BASE_URL=https://your-domain.atlassian.net
DB_HOST=localhost
DB_PORT=5432
DB_NAME=leadboard
DB_USERNAME=leadboard
DB_PASSWORD=leadboard
```

## Документация (ai-ru/)

| Документ | Описание |
|----------|----------|
| [ARCHITECTURE.md](ai-ru/ARCHITECTURE.md) | Карта кодовой базы: пакеты, сервисы, entities, API, frontend |
| [FEATURES.md](ai-ru/FEATURES.md) | Индекс фич со статусами и ссылками на спецификации |
| [RULES.md](ai-ru/RULES.md) | Бизнес-правила + правила разработки |
| [TECH_DEBT.md](ai-ru/TECH_DEBT.md) | Технический долг и архитектурные проблемы |
| [JIRA_WORKFLOWS.md](ai-ru/JIRA_WORKFLOWS.md) | Jira workflows (Epic, Story, Subtask) |
| [JIRA_SETUP.md](ai-ru/JIRA_SETUP.md) | Настройка Jira Cloud (экраны, поля, схемы) |
| [TEST_PYRAMID.md](ai-ru/TEST_PYRAMID.md) | Тестовая пирамида и инструкции по тестам |
| [API_PLANNING.md](ai-ru/API_PLANNING.md) | API Planning документация |
| [ROADMAP_V2.md](ai-ru/ROADMAP_V2.md) | Роадмап F24-F29 |
| [features/](ai-ru/features/) | Детальные спецификации фич (F14-F23) |

## Структура проекта

```
lead-board-claude/
├── backend/          # Spring Boot (Java 21) — см. backend/CLAUDE.md
├── frontend/         # React + Vite + TypeScript — см. frontend/CLAUDE.md
├── ai-ru/            # Документация на русском
│   ├── features/     # Спецификации фич
│   └── archive/      # Устаревшие документы
└── docker-compose.yml
```

## Важно

- **Всегда убивать процессы на портах перед запуском**
- **PostgreSQL должен быть запущен** перед стартом backend
- Credentials хранятся в `backend/.env` (не коммитить!)
- Backend и frontend запускать из своих директорий

## Деплой на продакшн (onelane.ru)

### Сервер
- **IP:** 79.174.94.70
- **SSH:** `ssh root@79.174.94.70`
- **Проект на сервере:** `/opt/leadboard/`
- **Nginx конфиг:** `/etc/nginx/sites-available/onelane`

### Сборка образов (локально на Mac)
```bash
# Frontend
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend
docker build --platform linux/amd64 -t onelane-frontend:latest .

# Backend
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/backend
docker build --platform linux/amd64 -t onelane-backend:latest .
```

**ВАЖНО:** Флаг `--platform linux/amd64` обязателен (Mac = arm64, сервер = amd64)

### Загрузка образов на сервер
```bash
# Сохранить образ
docker save onelane-frontend:latest | gzip > /tmp/onelane-frontend.tar.gz
docker save onelane-backend:latest | gzip > /tmp/onelane-backend.tar.gz

# Загрузить на сервер
scp /tmp/onelane-frontend.tar.gz root@79.174.94.70:/root/
scp /tmp/onelane-backend.tar.gz root@79.174.94.70:/root/

# Загрузить образы на сервере
ssh root@79.174.94.70 "docker load < /root/onelane-frontend.tar.gz"
ssh root@79.174.94.70 "docker load < /root/onelane-backend.tar.gz"
```

### Запуск контейнеров
```bash
ssh root@79.174.94.70 "cd /opt/leadboard && docker compose -f docker-compose.prod.yml up -d"
```

### Проверка статуса
```bash
ssh root@79.174.94.70 "docker ps"
ssh root@79.174.94.70 "docker logs onelane-backend 2>&1 | tail -30"
ssh root@79.174.94.70 "docker logs onelane-frontend 2>&1 | tail -10"
```

### Перезапуск контейнеров
```bash
ssh root@79.174.94.70 "cd /opt/leadboard && docker compose -f docker-compose.prod.yml down && docker compose -f docker-compose.prod.yml up -d"
```

### Полный деплой (одной командой)
```bash
# Frontend only
cd /Users/kirillreshetov/IdeaProjects/lead-board-claude/frontend && \
docker build --platform linux/amd64 -t onelane-frontend:latest . && \
docker save onelane-frontend:latest | gzip > /tmp/onelane-frontend.tar.gz && \
scp /tmp/onelane-frontend.tar.gz root@79.174.94.70:/root/ && \
ssh root@79.174.94.70 "docker load < /root/onelane-frontend.tar.gz && cd /opt/leadboard && docker compose -f docker-compose.prod.yml up -d frontend"
```

### Структура на сервере
```
/opt/leadboard/
├── docker-compose.prod.yml   # Конфиг docker-compose
└── .env                      # Переменные окружения (НЕ используется, всё в docker-compose.prod.yml)

/etc/nginx/sites-available/onelane  # Nginx reverse proxy (SSL + proxy на localhost:3000)
```

### Сеть Docker
- Сеть: `leadboard_onelane-net`
- Backend алиас: `backend` (nginx в frontend ищет этот hostname)
- Frontend проксирует `/api/*` на `http://backend:8080`

### Порты
- `127.0.0.1:3000` — frontend (nginx внутри контейнера)
- `127.0.0.1:8080` — backend (Spring Boot)
- Внешний nginx проксирует 443 → localhost:3000

### Troubleshooting

**SSH зависает:**
- Сервер может быть перегружен сборкой Docker
- Подождать 1-2 минуты и попробовать снова

**Backend не стартует (Connection refused to localhost:5432):**
- Контейнер запущен без переменных окружения
- Использовать `docker compose` вместо `docker run`

**Frontend рестартится (host not found in upstream "backend"):**
- Контейнеры не в одной сети
- Backend должен иметь алиас `backend` в сети

**Сайт недоступен:**
```bash
# Проверить nginx на сервере
ssh root@79.174.94.70 "systemctl status nginx"
ssh root@79.174.94.70 "nginx -t"

# Проверить контейнеры
ssh root@79.174.94.70 "curl -s http://localhost:3000"
ssh root@79.174.94.70 "curl -s http://localhost:8080/api/health"
```

## Обязательные требования к фичам

- Тесты: JUnit5 для backend, покрывать основные сценарии
- Запускать `./gradlew test` перед коммитом, не коммитить с падающими тестами
- Обновлять документацию в ai-ru/
- Коммитить документацию вместе с кодом

## Решённые проблемы

- **Jira API 410 Gone:** `/rest/api/3/search` → `/rest/api/3/search/jql`
- **Role mapping с кириллицей:** fallback через `String.contains()`
- **Flyway PostgreSQL:** `org.flywaydb:flyway-database-postgresql:10.10.0`

## Atlassian OAuth 2.0

1. Создать приложение в https://developer.atlassian.com/console/myapps/
2. Permissions: `read:jira-user`, `read:jira-work`, `read:me`
3. Authorization code grants + Callback URL: `http://localhost:8080/oauth/atlassian/callback`
4. `offline_access` работает автоматически (не добавлять как scope)

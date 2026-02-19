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
| [TEST_PLAN.md](ai-ru/testing/TEST_PLAN.md) | Тест-план: стратегия, чек-листы по экранам, порядок |
| [TEST_PYRAMID.md](ai-ru/testing/TEST_PYRAMID.md) | Тестовая пирамида и инструкции по тестам |
| [QA_STATUS.md](ai-ru/testing/QA_STATUS.md) | QA статус по экранам, баги, отчёты |
| [API_PLANNING.md](ai-ru/API_PLANNING.md) | API Planning документация |
| [features/](ai-ru/features/) | Детальные спецификации фич (F1-F31) |
| [DEPLOY.md](DEPLOY.md) | Инструкции по деплою на продакшн |

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

## Деплой на продакшн

Подробные инструкции: **[DEPLOY.md](DEPLOY.md)** (сервер 79.174.94.70, Docker, nginx, troubleshooting)

## Нумерация фич

- **F-номера присваиваются только реализованным фичам**, строго по порядку (F1, F2, ... F28, F29, ...)
- Номера из ROADMAP_V2 и бэклога **не резервируются** заранее. Следующий номер = последний реализованный + 1
- Реализованная фича перемещается из `ai-ru/backlog/` в `ai-ru/features/` с новым F-номером
- В FEATURES.md: добавить в таблицу реализованных, пометить в бэклоге как `✅ Done → F{N}`

## Обязательные требования к фичам

- Тесты: JUnit5 для backend, покрывать основные сценарии
- Запускать `./gradlew test` перед коммитом, не коммитить с падающими тестами
- Обновлять документацию в ai-ru/
- Коммитить документацию вместе с кодом

## Design System (обязательные правила)

**Эти правила действуют для ВСЕХ фич без исключений.**

### Визуальные данные — только из конфигурации
1. **Иконки типов задач** — `getIssueIcon(type, getIssueTypeIconUrl(type))`. ЗАПРЕЩЕНО импортировать локальные иконки напрямую.
2. **Цвета статусов** — из `StatusStylesContext` / `StatusBadge`. ЗАПРЕЩЕНЫ hardcoded палитры `STATUS_COLORS`.
3. **Цвета команд** — `TeamBadge` или `team.color`. Никогда не показывать team name без цвета.
4. **Цвета фаз (ролей)** — `getRoleColor(code)` из `WorkflowConfigContext`. ЗАПРЕЩЕНЫ hardcoded цвета SA/DEV/QA.

### Переиспользование компонентов — не дублировать код
5. **Готовые компоненты**: `StatusBadge`, `TeamBadge`, `RiceScoreBadge`, `Modal`, `MultiSelectDropdown` — использовать как есть. Не создавать аналоги.
6. **Готовые хелперы**: `getIssueIcon()`, `getStatusStyles()`, `getIssueTypeIconUrl()`, `getRoleColor()` — не писать свои версии, расширять существующие.
7. **Перед созданием нового компонента** — проверить `components/`. Если есть похожий — расширить, а не создавать новый.

## Решённые проблемы

- **Jira API 410 Gone:** `/rest/api/3/search` → `/rest/api/3/search/jql`
- **Role mapping с кириллицей:** fallback через `String.contains()`
- **Flyway PostgreSQL:** `org.flywaydb:flyway-database-postgresql:10.10.0`

## Atlassian OAuth 2.0

1. Создать приложение в https://developer.atlassian.com/console/myapps/
2. Permissions: `read:jira-user`, `read:jira-work`, `read:me`
3. Authorization code grants + Callback URL: `http://localhost:8080/oauth/atlassian/callback`
4. `offline_access` работает автоматически (не добавлять как scope)

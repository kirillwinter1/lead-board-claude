# F2. Jira Integration MVP

## Обзор

REST-клиент для Jira Cloud API с двойной аутентификацией и поддержкой полной иерархии задач.

## Аутентификация

Два режима с автоматическим fallback:

1. **OAuth 2.0 (приоритет)** — Bearer-токен через Atlassian API gateway:
   `https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3/...`

2. **Basic Auth (fallback)** — Base64(email:apiToken), прямой доступ:
   `{JIRA_BASE_URL}/rest/api/3/...`

## Используемые API

| Endpoint | Метод | Назначение |
|----------|-------|------------|
| `/rest/api/3/search/jql` | GET | Поиск задач по JQL |
| `/rest/api/3/issue` | POST | Создание задач |
| `/rest/api/3/issue/{key}` | PUT | Обновление задач |
| `/rest/api/3/user` | GET | Информация о пользователе |
| `/gateway/api/public/teams/v1/org/{orgId}/teams` | GET | Список команд |
| `/gateway/api/public/teams/v1/org/{orgId}/teams/{id}/members` | POST | Участники команды |

## Получаемые поля

```
summary, status, issuetype, parent, project, timetracking,
priority, duedate, created, assignee, flagged, issuelinks
+ динамическое поле команды (JIRA_TEAM_FIELD_ID)
```

## Модель данных

```
JiraIssue
├── key, id, self
└── fields: JiraFields
    ├── summary, status, issuetype, parent, project
    ├── timetracking (originalEstimate, remaining, spent)
    ├── priority, duedate, created
    ├── assignee (accountId, displayName)
    ├── flagged (Impediment)
    ├── issuelinks[] (blocks / is blocked by)
    └── customFields (team field)
```

## Пагинация

**Cursor-based** (актуальная версия Jira API):
- Запрос: `nextPageToken` параметр
- Ответ: `isLast` (boolean) + `nextPageToken` (строка)
- Размер страницы: 50-100 задач

## Конфигурация

| Переменная | Описание |
|-----------|----------|
| `JIRA_BASE_URL` | URL инстанса Jira |
| `JIRA_EMAIL` | Email для Basic Auth |
| `JIRA_API_TOKEN` | API токен для Basic Auth |
| `JIRA_PROJECT_KEY` | Ключ проекта по умолчанию |
| `JIRA_TEAM_FIELD_ID` | ID кастомного поля команды |
| `JIRA_ORGANIZATION_ID` | ID организации Atlassian |
| `JIRA_SYNC_INTERVAL_SECONDS` | Интервал синхронизации (по умолчанию 300) |

## Технические детали

- **WebClient buffer:** 16MB (для больших ответов Jira)
- **Маппинг ролей подзадач:** поддержка кириллицы с fallback через `String.contains()`
  - Аналитика → SA, Разработка → DEV, Тестирование → QA

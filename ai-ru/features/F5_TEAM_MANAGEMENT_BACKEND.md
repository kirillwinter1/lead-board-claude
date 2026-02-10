# F5. Team Management Backend

## Обзор

CRUD-операции для команд и участников с planning config, soft delete и валидацией.

## Entities

### TeamEntity (`teams`)

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | BIGSERIAL | PK |
| `name` | VARCHAR(255) | Название команды (уникально среди активных) |
| `jiraTeamValue` | VARCHAR(255) | Значение поля Team в Jira |
| `atlassianTeamId` | VARCHAR(100) | ID команды в Atlassian |
| `active` | BOOLEAN | Soft delete (по умолчанию true) |
| `planningConfig` | JSONB | Конфигурация планирования |
| `createdAt`, `updatedAt` | TIMESTAMP | Временные метки |

### TeamMemberEntity (`team_members`)

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | BIGSERIAL | PK |
| `teamId` | BIGINT FK | Ссылка на команду |
| `jiraAccountId` | VARCHAR(255) | Atlassian account ID |
| `displayName` | VARCHAR(255) | Имя участника |
| `role` | ENUM | SA / DEV / QA (по умолчанию DEV) |
| `grade` | ENUM | JUNIOR / MIDDLE / SENIOR (по умолчанию MIDDLE) |
| `hoursPerDay` | DECIMAL(3,1) | Часов в день (0-12, по умолчанию 6.0) |
| `active` | BOOLEAN | Soft delete |

**Constraint:** unique (team_id, jira_account_id) WHERE active=TRUE

## Planning Config (JSONB)

```json
{
  "gradeCoefficients": { "senior": 0.8, "middle": 1.0, "junior": 1.5 },
  "riskBuffer": 0.2,
  "wipLimits": { "team": 6, "sa": 2, "dev": 3, "qa": 2 },
  "storyDuration": { "sa": 2, "dev": 2, "qa": 2 }
}
```

- **gradeCoefficients** — множители производительности (Senior быстрее, Junior медленнее)
- **riskBuffer** — буфер на риски (20% по умолчанию)
- **wipLimits** — рекомендательные WIP-лимиты
- **storyDuration** — средняя длительность story по ролям (для pipeline offset)

## CRUD операции

| Операция | Метод | Логика |
|----------|-------|--------|
| Список команд | `getAllTeams()` | Только active=true, с количеством участников |
| Создание команды | `createTeam()` | Валидация уникальности имени |
| Обновление команды | `updateTeam()` | Валидация имени если изменилось |
| Деактивация команды | `deactivateTeam()` | Soft delete (active=false) |
| Участники команды | `getTeamMembers()` | Только active=true |
| Добавление участника | `addTeamMember()` | Валидация уникальности accountId |
| Обновление участника | `updateTeamMember()` | Обновление только non-null полей |
| Деактивация участника | `deactivateTeamMember()` | Soft delete |

## API Endpoints

| Метод | Путь | Доступ |
|-------|------|--------|
| GET | `/api/teams` | Все |
| GET | `/api/teams/:id` | Все |
| POST | `/api/teams` | Admin |
| PUT | `/api/teams/:id` | Team manager |
| DELETE | `/api/teams/:id` | Admin |
| GET | `/api/teams/:id/members` | Все |
| POST | `/api/teams/:id/members` | Team manager |
| PUT | `/api/teams/:id/members/:mid` | Team manager |
| POST | `/api/teams/:id/members/:mid/deactivate` | Team manager |
| GET | `/api/teams/:id/planning-config` | Все |
| PUT | `/api/teams/:id/planning-config` | Team manager |

## Миграции

- `V3__create_team_tables.sql` — таблицы teams, team_members
- `V5__add_atlassian_team_id.sql` — поле atlassianTeamId
- `V10__add_team_planning_config.sql` — JSONB planning_config

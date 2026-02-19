# F37: Team Colors

**Дата:** 2026-02-17
**Статус:** Реализована
**Версия:** 0.37.0

## Описание

Accent-цвет для каждой команды — визуальная дифференциация команд на всех экранах приложения. Палитра из 12 предустановленных Atlassian-like цветов с автоматическим назначением при создании/sync и ручным выбором в UI.

## Изменения

### Backend

#### База данных

**V38 — add_team_color:**
- `ALTER TABLE teams ADD COLUMN color VARCHAR(7)` — hex-цвет (`#0052CC`)
- Nullable: существующие команды получают цвет лениво при первом `getAllTeams()`

#### Пакет `com.leadboard.team`

| Файл | Изменение |
|------|-----------|
| `TeamEntity.java` | Поле `color` (`@Column(length = 7)`) + getter/setter |
| `TeamDto.java` | Поле `color` в record, маппинг из entity |
| `CreateTeamRequest.java` | Optional `color` с `@Pattern(regexp = "^#[0-9a-fA-F]{6}$")` |
| `UpdateTeamRequest.java` | Optional `color` с `@Pattern` |
| `TeamService.java` | Палитра `TEAM_PALETTE[12]`, `nextAutoColor()`, `assignMissingColors()` |
| `TeamSyncService.java` | Авто-назначение цвета при sync (`teamService.nextAutoColor()`) |

#### TeamService — ключевая логика

- **Палитра (12 цветов):**
  ```
  #0052CC #00875A #FF5630 #6554C0
  #FF991F #00B8D9 #36B37E #E74C3C
  #8777D9 #2C3E50 #F39C12 #1ABC9C
  ```
- **Auto-assign:** `TEAM_PALETTE[count % 12]` — детерминированное назначение по числу активных команд
- **Lazy fill:** `assignMissingColors()` — при первом `getAllTeams()` заполняет цвет для всех команд без цвета
- **createTeam():** используется `request.color()` если передан, иначе `nextAutoColor()`
- **updateTeam():** `request.color()` обновляет цвет

#### Пакет `com.leadboard.board`

| Файл | Изменение |
|------|-----------|
| `BoardNode.java` | Поле `teamColor` + getter/setter |
| `BoardService.java` | `Map<Long, String> teamColors` из `TeamRepository`, передача в `mapToNode()`, `node.setTeamColor()` |

### Frontend

#### Новые файлы

| Файл | Назначение |
|------|-----------|
| `constants/teamColors.ts` | `TEAM_PALETTE` — 12 цветов (синхронизирована с backend) |
| `components/TeamBadge.tsx` | Цветной бейдж команды: `border-left: 3px solid {color}`, фон `{color}14` (8% opacity), текст — цвет команды |

#### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `api/teams.ts` | `color: string \| null` в `Team`, `color?: string` в `CreateTeamRequest`, `UpdateTeamRequest` |
| `components/board/types.ts` | `teamColor: string \| null` в `BoardNode` |
| `components/board/BoardRow.tsx` | `<TeamBadge>` вместо plain text в ячейке Team |
| `components/board/FilterPanel.tsx` | Проброс `teamColorMap` в `MultiSelectDropdown` |
| `components/MultiSelectDropdown.tsx` | Optional prop `colorMap` — цветные точки (8×8px) перед названием |
| `hooks/useBoardFilters.ts` | `teamColorMap: Map<string, string>` — собирается из board данных |
| `pages/BoardPage.tsx` | Проброс `teamColorMap` из `useBoardFilters` → `FilterPanel` |
| `pages/TeamsPage.tsx` | Color picker: inline палитра (12 кружков) в таблице + в модалке create/edit |
| `pages/TeamMembersPage.tsx` | Цветная рамка (`border: 2px solid {color}`) на аватарах участников |

## Визуальная интеграция

### Board (борд)
- **Ячейка Team:** цветной бейдж (`TeamBadge`) с left-border и полупрозрачным фоном
- **Фильтр Team:** цветная точка перед названием каждой команды в dropdown

### TeamsPage (управление командами)
- **Таблица:** цветной кружок (16×16) перед именем команды, клик → inline палитра (6×2 grid)
- **Модалка create/edit:** секция "Team Color" с палитрой (12 кнопок), подпись "Auto-assigned if not selected"

### TeamMembersPage (участники)
- **Бейдж команды:** цветной кружок рядом с названием
- **Аватары:** цветная рамка (`border: 2px solid {team.color}`)
- **Overflow counter:** фон и border в цвете команды

## API

Изменения в существующих endpoints:

| Endpoint | Изменение |
|----------|-----------|
| `GET /api/teams` | `color` в каждом объекте Team |
| `POST /api/teams` | Принимает optional `color`, авто-назначение если не передан |
| `PUT /api/teams/{id}` | Принимает optional `color` для смены |
| `GET /api/board` | `teamColor` в каждом BoardNode |

## Файлы

### Backend (7 изменённых + 1 новый)
- `backend/src/main/resources/db/migration/V38__add_team_color.sql` — **NEW**
- `backend/src/main/java/com/leadboard/team/TeamEntity.java`
- `backend/src/main/java/com/leadboard/team/TeamDto.java`
- `backend/src/main/java/com/leadboard/team/CreateTeamRequest.java`
- `backend/src/main/java/com/leadboard/team/UpdateTeamRequest.java`
- `backend/src/main/java/com/leadboard/team/TeamService.java`
- `backend/src/main/java/com/leadboard/team/TeamSyncService.java`
- `backend/src/main/java/com/leadboard/board/BoardNode.java`
- `backend/src/main/java/com/leadboard/board/BoardService.java`

### Frontend (8 изменённых + 2 новых)
- `frontend/src/constants/teamColors.ts` — **NEW**
- `frontend/src/components/TeamBadge.tsx` — **NEW**
- `frontend/src/api/teams.ts`
- `frontend/src/components/board/types.ts`
- `frontend/src/components/board/BoardRow.tsx`
- `frontend/src/components/board/FilterPanel.tsx`
- `frontend/src/components/MultiSelectDropdown.tsx`
- `frontend/src/hooks/useBoardFilters.ts`
- `frontend/src/pages/BoardPage.tsx`
- `frontend/src/pages/TeamsPage.tsx`
- `frontend/src/pages/TeamMembersPage.tsx`

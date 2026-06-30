# F69: Quarterly Planning Redesign

**Версия:** 0.69.0
**Дата:** 2026-05-15

## Контекст

Текущая страница Quarterly Planning (`QuarterlyPlanningLivePage` из F68) функционально полная, но «выбивается из общей концепции» и неочевидна пользователю. Главный сценарий — **решить, что взять в квартал** — был реализован как дашборд с тремя табами (Projects / Readiness / Teams), что заставляло переключаться между экранами и не давало прямого механизма принятия решения. Кроме того, страница использовала много кастомных CSS-классов (`.qpp-*`) вместо компонентов дизайн-системы.

Редизайн превращает страницу в **инструмент принятия решений**: канбан-доска, где пользователь физически переносит эпики из бэклога в квартал, видя в реальном времени, помещаются ли они в ёмкость команд. Изменения записываются в Jira как quarter label.

## Принципиальные решения

| Решение | Выбор | Обоснование |
|---|---|---|
| Главный сценарий | Решить, что взять в квартал | Активное принятие решения, не пассивный дашборд |
| Механизм фиксации | Писать quarter label в Jira напрямую из UI | Jira — single source of truth |
| Единица планирования | **Эпик** (проект — группировка) | Проект может попасть в квартал частично |
| Метафора UI | **Канбан Backlog → В квартале** | Прямая визуальная метафора «решение = движение» |
| Поведение при перегрузе | Предупреждать, но пускать | Перегруз бывает осознанным (контракторы, выходы) |
| Эпики без оценок/команд | Показывать в Backlog с пометкой; в квартал пускать, но НЕ учитывать в capacity | Не блокируем планирование, явно показываем риск |
| Manual boost | На уровне эпика | Эпик — единица планирования, boost логично там же |
| Синхронизация с Jira | Refresh при фокусе вкладки + кнопка Refresh; БЕЗ автополлинга и WebSocket | Квартальное планирование не требует real-time актуальности |

## Структура UI

### Header (sticky)

- Селектор квартала
- Capacity-bars по командам: зелёный <80%, жёлтый 80–100%, красный >100%
- Кнопка «Опубликовать → Jira (N)» — появляется при наличии несохранённых изменений

### Две колонки

**Backlog (слева):**
- Сгруппирован по проекту (collapsible)
- Эпики отсортированы по `priorityScore` (RICE + boost)
- Фильтры над колонкой: поиск, проект, команда

**В квартале (справа):**
- Переключатель группировки: по команде / по проекту
- Секция «Не назначены» для эпиков без команд
- Counter: «N эпиков, N чел-дней»

### Карточка эпика

- Иконка типа (`getIssueIcon` + `getIssueTypeIconUrl`)
- Имя эпика (ссылка на Jira)
- RICE score (`RiceScoreBadge`)
- Проект (ссылка на ProjectPage)
- Команды (`TeamBadge` × N) с днями
- Boost-чип (`+15` / `-10`, если ≠ 0) — inline edit
- Warning-бейджи: «нет оценки», «нет команды», «перегруз FE»
- Кнопка «→ взять» / «← вернуть»

## Что убрано из старой страницы

- 3 таба (Projects / Readiness / Teams)
- Step cards (Projects → Readiness → Team Impact)
- 4 KPI-карточки (Projects in quarter / Ready to plan / Epics coverage / Teams involved)
- 700+ строк legacy `.qpp-*` CSS
- Компоненты `ProjectsTab`, `ReadinessTab`, `TeamsTab`, `SummaryMetricCard`

## Изменения

### Backend

- 3 новых endpoint в `QuarterlyPlanningController`:
  - `GET /api/quarterly-planning/quarters/{quarter}/epics` — список эпиков с `priorityScore`, `demandByRole`, флагами `hasEstimate`/`hasTeamMapping`, `overloadedTeams`
  - `POST /api/quarterly-planning/epics/{epicKey}/quarter` — body `{ quarter: "2026Q2" | null }`, пишет/удаляет label в Jira атомарно
  - `POST /api/quarterly-planning/epics/{epicKey}/boost` — body `{ boost: -50..50 }`
- Авторизация на epic-уровневых mutate endpoints (`/epics/{key}/quarter`, `/epics/{key}/boost`): ROLE_ADMIN, ROLE_PROJECT_MANAGER или ROLE_TEAM_LEAD — TEAM_LEAD является основным пользователем доски планирования и должен иметь возможность публиковать изменения. Project-уровневые операции (`/projects/{key}/desired-quarter`, `/projects/{key}/boost`) остаются ADMIN/PROJECT_MANAGER (PM-решение, F70).
- `JiraClient.updateLabels(issueKey, labels)` — `PUT /rest/api/3/issue/{key}` через JiraConfigResolver
- 4 новых метода в `QuarterlyPlanningService`: `getEpicsForQuarter`, `assignEpicToQuarter`, `removeEpicFromQuarter`, `setEpicBoost`
- 2 новых DTO: `PlanningEpicDto`, `QuarterlyEpicsResponse`
- Миграция БД не понадобилась — `manualBoost` уже присутствует на уровне `JiraIssueEntity` (с V47)
- 25 новых тестов: 19 в `QuarterlyPlanningServiceTest`, 6 в новом `QuarterlyPlanningControllerTest`

### Frontend

- Полный rewrite `QuarterlyPlanningPage.tsx`: с tabbed view (Projects/Readiness/Teams) на канбан Backlog ↔ В квартале
- 5 новых компонентов в `frontend/src/components/planning/`:
  - `EpicCard.tsx` — карточка эпика с иконкой типа, RICE-бейджем, командами, ролями, boost-чипом, warning-бейджами, action-кнопкой
  - `CapacityBars.tsx` — sticky-bars по командам с цветовой утилизацией (зелёный/жёлтый/красный)
  - `BacklogColumn.tsx` — левая колонка, поиск + фильтры (проект/команда), группировка по проекту, сортировка по priorityScore
  - `InQuarterColumn.tsx` — правая колонка, toggle группировки project/team, секция «Не назначены» для эпиков без команд
  - `PublishToJiraModal.tsx` — Modal со списком pending changes (add/remove/move/boost), последовательная публикация с retry
- API расширен: `getEpicsForQuarter`, `assignEpicToQuarter`, `setEpicBoost` в `frontend/src/api/metrics.ts`
- Optimistic updates с baseline-diff для надёжного publish

### Дизайн-система

Переиспользовано без изменений: `TeamBadge`, `RiceScoreBadge`, `Modal`, `MultiSelectDropdown`, `SingleSelectDropdown`, `SearchInput`, `getIssueIcon` + `getIssueTypeIconUrl`, `getRoleColor`.

Цвета через `constants/colors.ts`: `TEXT_*`, `ERROR_TEXT`, `LINK_COLOR`, `BG_SUBTLE`, `BORDER_*`, `DSR_GREEN/YELLOW/RED`, `getUtilizationColor()`.

## API Endpoints

- `GET /api/quarterly-planning/quarters/{quarter}/epics` — список эпиков квартала с priorityScore, capacity-вкладом, флагами hasEstimate/hasTeamMapping, overloadedTeams
- `POST /api/quarterly-planning/epics/{epicKey}/quarter` — установить/снять quarter label в Jira атомарно
- `POST /api/quarterly-planning/epics/{epicKey}/boost` — установить manual boost на эпик (−50..50)

## Конфигурация

Новые env-переменные не требуются. Использует существующий `JiraConfigResolver` и Jira API credentials из конфигурации тенанта.

## Поведение edge cases

| Случай | Поведение |
|---|---|
| Эпик без оценки | В Backlog с бейджем «нет оценки»; можно взять в квартал, не учитывается в capacity |
| Эпик без team mapping | Бейдж «нет команды», не учитывается в capacity; попадает в секцию «Не назначены» |
| Эпик перегружает команду | Красный border-left + бейдж «перегрузка FE», capacity-bar красный, кнопка «взять» работает |
| Jira API упал при публикации | Modal показывает ошибку, локальное состояние не применяется, пользователь может повторить |
| Boost вне диапазона | Backend валидация `boost ∈ [-50, 50]`, UI ограничивает input |
| Sync с Jira | Refresh при фокусе вкладки + кнопка Refresh; reconcile через refetch после publish |

## Технический долг (pre-existing)

Atlassian tonal hex для warning-бейджей (`#E3FCEF`, `#BF2600` и др.) — pre-existing pattern в проекте, следует вынести в общий design-token в отдельной follow-up задаче.

## Связанные фичи

- **F55** — Quarterly Capacity Planning (базовая логика capacity/demand/RICE)
- **F67** — Quarter Label & Filter (quarter labels, quarter inheritance от parent project)
- **F68** — Quarterly Planning Production UI (предыдущая версия страницы, которую заменяет этот редизайн)

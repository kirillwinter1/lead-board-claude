# F70: Customer-Driven Quarter Planning

**Статус:** Реализовано
**Дата:** 2026-05-16
**Базируется на:** F67 (quarter labels), F69 (kanban квартального планирования)

## Контекст

Текущая модель смешивает в одной странице интересы заказчика (PM) и тимлида:
- **Заказчик** оперирует **проектами** — хочет, чтобы его проекты взяли в работу и довели до результата
- **Тимлид** оперирует **эпиками** — должен не вылезти за capacity, выбрать что реально влезает
- При этом существуют **эпики без проекта** (техдолг, мелкие доработки) — first-class случай, который сейчас плохо обрабатывается

После F69 страница Quarterly Planning стала тимлидской, но не хватает явного сценария **PM ↔ тимлид**: «заказчик заявляет желаемый квартал → тимлид подтверждает вместимостью».

## Принципиальная модель

Две независимые метки на разных уровнях:

| Уровень | Метка | Семантика | Кто ставит |
|---|---|---|---|
| Проект | `desired_quarter` (label на project-issue в Jira) | «Заказчик хочет, чтобы эту работу сделали в этом квартале» | PM проекта (ROLE_ADMIN или ROLE_PROJECT_MANAGER) |
| Эпик | `committed_quarter` (label на epic-issue в Jira) | «Тимлид подтверждает: этот конкретный эпик влезает в этот квартал моей команды» | Тимлид (ROLE_ADMIN или ROLE_PROJECT_MANAGER) |

Эти метки — **независимы**. Расхождение видимо обеим сторонам.

### Конфликт desired vs committed

Если эпик имеет `committed_quarter`, отличный от `desired_quarter` своего проекта:
- На странице Projects (карточка проекта): бейдж «committed: Q3 — желали Q2 (3/12 эпиков)»
- На странице Quarterly Planning (карточка эпика): бейдж «PM желает Q2» (если committed=Q2 — бейджа нет, всё совпало)
- Переговоры решаются оффлайн (прямой разговор PM↔тимлид)

### Наследование (изменение F67)

**Текущее поведение F67:** эпик без `quarter_label` наследует от parent project.

**Новое поведение F70:**
- Для `resolveQuarterLabel()` в `getEpicsForQuarter()` (тимлидский экран): **наследование отключено**. Эпик «в квартале» только если у него явно установлен `committed_quarter`.
- Для Board/Projects фильтра (F67): **наследование сохраняется**. Эпик показывается в фильтре `quarter=Q2`, если у него или у его проекта label `2026Q2`. Это сохраняет user-facing поведение F67.
- Backend: `resolveQuarterLabel` остаётся как читалка фильтра, но в planning-service добавляется `resolveCommittedQuarter` (только direct epic label).

## Сценарий 1 — Заказчик (Projects)

### UX

На странице Projects, для каждой карточки проекта PM (assignee/owner) видит:

```
┌───────────────────────────────────────────┐
│ ◆ Project Auth                            │
│ ────────────────────────────────────────  │
│ Owner: @ivan · RICE 78                    │
│                                            │
│ Desired quarter: [2026Q2 ▾] [✏]          │
│ Commitment: ███████░░░ 8/12 эпиков        │
│   ✓ Backend (3/3)                         │
│   ✓ Frontend (4/4)                        │
│   ⚠ QA (1/3) — 2 эпика → Q3              │
│   ✗ Design (0/2)                          │
│                                            │
│ [Открыть] [Изменить boost]                │
└───────────────────────────────────────────┘
```

- **Desired quarter dropdown** — список доступных кварталов (как в F69). Действие на клик: API call → label в Jira на project-issue + локальная БД.
- **Commitment view** — агрегат: для каждой команды, упомянутой в team_mapping эпиков, показать сколько эпиков подтверждены в desired_quarter / переехали в другой квартал / без committed_quarter.
- **Цвета:** ✓ зелёный (все эпики committed_quarter == desired), ⚠ жёлтый (частично), ✗ красный (никто не взял).

### Кто может менять desired_quarter

- ROLE_ADMIN — любой проект
- ROLE_PROJECT_MANAGER — любой проект (как и существующее `updateProjectBoost`)
- В будущем: ограничить до `assignee` проекта (отдельная задача — нужна модель ownership на проекте)

### Авторизация

`@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")` на mutate endpoint.

## Сценарий 2 — Тимлид (Quarterly Planning)

### UX — изменения в F69-странице

**Backlog (левая колонка) — новая фильтрация:**
- По умолчанию показываются:
  1. Эпики из проектов с `desired_quarter == выбранный квартал`
  2. Standalone эпики (без parent project) — техдолг, мелочи
- Toggle «Показать всё» в шапке колонки — снимает фильтр (показывает все эпики, как в текущей F69)
- Сортировка внутри: standalone эпики собраны в отдельную группу `Standalone (без проекта)` внизу

**Карточка эпика — новый бейдж:**
- Если эпик принадлежит проекту с `desired_quarter`: показать `PM желает Q2` (если desired != committed) или скрыть (если совпадают)
- Иконка/цвет: info-стиль (синий бейдж)

**Empty state:** «На команду в Q2 ничего не запланировано. Включи toggle “Показать всё”, чтобы увидеть остальные эпики.»

### Что не меняется в F69

- Drag/move эпиков → / ←
- Capacity-bars (с предупреждением о перегрузе)
- Boost-editor на эпике
- Modal публикации в Jira

## Backend изменения

### Новый endpoint

```
POST /api/projects/{projectKey}/desired-quarter
  Body: { quarter: "2026Q2" | null }
  Action: 
    1) Найти project-issue в БД (workflowConfigService.isProject или boardCategory == "PROJECT")
    2) Удалить старые \d{4}Q[1-4] labels с project.labels
    3) Добавить новый desired quarter (если не null)
    4) jiraClient.updateLabels(projectKey, newLabels) — через EpicLabelPersistenceService паттерн (REQUIRES_NEW)
    5) Save локально
  Response: ProjectQuarterCommitmentDto {
    projectKey,
    desiredQuarter,
    commitmentByTeam: {
      teamId, teamName, teamColor,
      totalEpics, committedEpics, otherQuarterEpics, uncommittedEpics
    }[]
  }
  Auth: ROLE_ADMIN или ROLE_PROJECT_MANAGER
```

### Изменения существующих endpoints

**`GET /api/quarterly-planning/quarters/{quarter}/epics`** — добавить поведение фильтрации:
- Query param `?onlyDesired=true` (default `true`)
- Если `true`: возвращать только эпики из проектов с `desired_quarter == quarter` ∪ standalone эпики (без parent project)
- Если `false`: текущее поведение (все эпики)

**`PlanningEpicDto`** — добавить поля:
- `projectDesiredQuarter: string | null` — desired_quarter проекта эпика (или null если standalone)
- `isStandalone: boolean` — true если parent project отсутствует

**`GET /api/projects`** (если есть) — добавить в DTO:
- `desiredQuarter: string | null`
- `commitmentByTeam: ProjectQuarterCommitmentDto[]` — может быть дорогим, либо отдельным endpoint'ом

**Новый endpoint для получения commitment:**
```
GET /api/projects/{projectKey}/quarter-commitment
  Response: ProjectQuarterCommitmentDto
  Использовать на странице Projects для рендеринга commitment view.
```

### Сервисный слой

- `ProjectQuarterService` (новый сервис в `com.leadboard.planning` или `com.leadboard.project`):
  - `setDesiredQuarter(projectKey, quarter)` — паттерн как `assignEpicToQuarter`, через `EpicLabelPersistenceService` (либо новый `ProjectLabelPersistenceService` если нужна типовая защита)
  - `getProjectCommitment(projectKey)` — агрегирует данные о committed_quarter эпиков проекта по командам
- `QuarterlyPlanningService.getEpicsForQuarter(quarter, onlyDesired)` — реализовать фильтрацию

### Resolution logic

```java
// Для тимлидского экрана:
resolveCommittedQuarter(epic) {
    return epic.labels.stream()
        .filter(l -> QUARTER_PATTERN.matches(l))
        .findFirst().orElse(null);
    // НЕТ fallback на parent.label
}

// Для проекта (desired):
resolveDesiredQuarter(project) {
    return project.labels.stream()
        .filter(l -> QUARTER_PATTERN.matches(l))
        .findFirst().orElse(null);
}

// Для F67 фильтра (Board/Projects) — оставить как есть:
resolveQuarterLabelForFilter(issue, ...) {
    return committed != null ? committed : parentProject.desired;
}
```

## Frontend изменения

### Projects page

Новые компоненты:
- `frontend/src/components/projects/DesiredQuarterPicker.tsx` — dropdown selector + sync с API
- `frontend/src/components/projects/ProjectCommitmentView.tsx` — список команд с прогресс-баром и счётчиками

### Quarterly Planning page (F69)

Минорные правки:
- В шапке Backlog колонки: toggle `[Только заявленные на квартал | Показать всё]` — default ON
- В `EpicCard`: новый warning-бейдж (info-style) `PM желает 2026Q2`, показывается если `projectDesiredQuarter != null && projectDesiredQuarter != currentQuarter`
- Empty state: новый текст когда фильтр включен и ничего не найдено
- API: дополнительный параметр `onlyDesired` в `getEpicsForQuarter`

### API client extension

- `frontend/src/api/projects.ts`: `setDesiredQuarter(projectKey, quarter)`, `getProjectCommitment(projectKey)`
- `frontend/src/api/quarterlyPlanning.ts`: дополнить `getEpicsForQuarter(quarter, onlyDesired?)`

## Edge cases

| Кейс | Поведение |
|---|---|
| Эпик не принадлежит проекту (standalone) | Всегда в backlog тимлида, независимо от фильтра. Бейдж `Standalone`. PM-желания нет. |
| Проект без desired_quarter | Эпики проекта не попадают в фильтрованный backlog (если toggle ON). С toggle OFF — видны как обычно. |
| desired_quarter изменился задним числом | Сам по себе не меняет committed_quarter эпиков. PM видит в commitment view расхождение. |
| Эпик в проекте, но committed_quarter == null | На странице PM: показывается как «uncommitted» в commitment view. На странице тимлида: появляется в backlog при desired_quarter == selected. |
| Эпик committed в Q2, проект desired Q1 | На карточке эпика: бейдж `PM желает Q1`. На странице PM: команда «частично» взяла, эпик «уехал в другой квартал». |
| Удаление desired_quarter (null) | Все эпики проекта по-прежнему committed (их label не трогаем). PM просто снял «заявку». |

## Авторизация (сводно)

| Действие | Роли |
|---|---|
| `POST /projects/{key}/desired-quarter` | ROLE_ADMIN, ROLE_PROJECT_MANAGER |
| `POST /epics/{key}/quarter` (committed) | ROLE_ADMIN, ROLE_PROJECT_MANAGER (как сейчас в F69) |
| `GET /projects/{key}/quarter-commitment` | authenticated |
| `GET /quarters/{q}/epics?onlyDesired=...` | authenticated (как сейчас в F69) |

## Поэтапная реализация

1. **Phase 1 — Backend** (1 итерация)
   - `ProjectQuarterService` + endpoints (`POST .../desired-quarter`, `GET .../quarter-commitment`)
   - Расширение `PlanningEpicDto` и `getEpicsForQuarter(onlyDesired)`
   - Изменение `resolveQuarterLabel` логики (committed vs filter)
   - Тесты

2. **Phase 2 — Frontend Quarterly Planning** (тимлидский флоу)
   - Toggle `onlyDesired`, бейдж `PM желает …`, empty state
   - Минимальные правки EpicCard и QuarterlyPlanningPage

3. **Phase 3 — Frontend Projects** (PM флоу)
   - `DesiredQuarterPicker` + `ProjectCommitmentView` на странице Projects
   - Возможно — карточка проекта с раскрываемой секцией commitment

4. **Phase 4 — Polish и follow-up** (отдельно)
   - Notify тимлида когда PM поставил desired_quarter на проект его команды (BF Notifications)
   - История изменений desired_quarter

## Verification

1. **API:**
   - `POST /projects/PROJ-1/desired-quarter` с `{"quarter":"2026Q2"}` → label `2026Q2` на project-issue в Jira; response: commitment view с актуальными данными по командам
   - `GET /quarters/2026Q2/epics?onlyDesired=true` → возвращает только эпики из проектов с `desired_quarter=2026Q2` + standalone
   - `GET /quarters/2026Q2/epics?onlyDesired=false` → текущее поведение (все эпики)
   - `GET /projects/PROJ-1/quarter-commitment` → правильно агрегирует epic.committed_quarter по командам

2. **UI Projects:**
   - PM устанавливает desired_quarter → видит в commitment секции команды и сколько эпиков взято
   - PM меняет desired_quarter → commitment view пересчитывается

3. **UI Quarterly Planning:**
   - С toggle ON и desired_quarter=Q2: backlog показывает эпики из проектов c desired Q2 + standalone
   - С toggle OFF: backlog показывает все эпики (как F69)
   - На карточке эпика-из-Q2-проекта который committed в Q3: бейдж `PM желает Q2`

4. **Реальный кейс на dev:**
   - PM ставит desired_quarter на проект → эпики проекта появляются в backlog тимлида (с toggle ON)
   - Тимлид двигает эпик в Q3 (committed_quarter=Q3) → на странице Projects commitment view показывает «1 эпик переехал в Q3»

5. **Тесты:**
   - `ProjectQuarterServiceTest` — setDesiredQuarter, getProjectCommitment
   - `QuarterlyPlanningServiceTest` — getEpicsForQuarter(onlyDesired=true) фильтрует корректно, standalone эпики проходят независимо
   - `ProjectQuarterControllerTest` — авторизация, валидация
   - Frontend: DesiredQuarterPicker, ProjectCommitmentView, фильтр backlog

## Связь с другими фичами

- **F67** — quarter labels (база). F70 расширяет: добавляет project-level семантику
- **F69** — Quarterly Planning канбан. F70 модифицирует backlog-фильтрацию и добавляет бейдж
- **BF Default Team Filter** — связано: тимлид по умолчанию видит свою команду. Без него тимлид-флоу всё ещё работает (тимлид сам выбирает фильтр)
- **BF Notifications** — будущее: уведомление тимлиду когда PM поставил desired_quarter на проект его команды

## Open follow-up (вне F70)

- Default team filter (BF, уже зафиксирован в backlog)
- Notifications PM↔тимлид при изменениях desired/committed
- Project ownership model (сейчас любой ROLE_PM может править — нужно сузить до assignee)
- Audit log изменений desired_quarter
- Bulk-операции (PM ставит desired сразу нескольким проектам)

## Реализация

### Backend

**Новые endpoints в `QuarterlyPlanningController`:**
- `POST /api/quarterly-planning/projects/{key}/desired-quarter` (auth: `ADMIN` или `PROJECT_MANAGER`) — записывает label в Jira, зеркалит в БД через транзакцию `REQUIRES_NEW`
- `GET /api/quarterly-planning/projects/{key}/quarter-commitment` — агрегат по командам (committed / в другом квартале / без коммита)
- `GET /api/quarterly-planning/quarters/{q}/epics?onlyDesired={true|false}` — расширение F69 endpoint, `onlyDesired` по умолчанию `true`

**Расширение `PlanningEpicDto`:**
- Добавлены поля `projectDesiredQuarter` и `isStandalone`

**Новый сервис `ProjectLabelPersistenceService`:**
- Паттерн `REQUIRES_NEW`, аналогичен `EpicLabelPersistenceService` из F69

**Новое исключение `ProjectNotFoundException`:**
- HTTP 404, обработчик добавлен в `GlobalExceptionHandler`

**Изменения в `QuarterlyPlanningService`:**
- Новые методы: `setProjectDesiredQuarter`, `getProjectCommitment`, `findProjectOrThrow`, `resolveCommittedQuarter`, `resolveDesiredQuarter`
- Метод `getEpicsForQuarter` рефакторен под параметр `onlyDesired`
- Исправление L1 cache: `project.setLabels(newLabels)` в outer session перед return (чтобы избежать desync после REQUIRES_NEW write)

**Тесты:**
- 22 новых теста: Service +14, Controller +5, ControllerSecurity +2, ProjectLabelPersistenceService +3, regression +1

### Frontend

**Новые компоненты в `frontend/src/components/projects/`:**
- `DesiredQuarterPicker.tsx` — dropdown selector для PM
- `ProjectCommitmentView.tsx` — список команд с прогресс-баром и статус-иконками (✓/⚠/✗/—)

**Изменения в QuarterlyPlanningPage (F69-страница):**
- Toggle «Только заявленные на квартал» (default ON)
- Новые бейджи на `EpicCard`: `PM желает Q2` (info-tone) и `Standalone` (neutral-tone)
- Добавлен `neutral` tone в `WarningBadge` через токены `BG_SUBTLE / TEXT_SECONDARY / BORDER_DEFAULT`

**Изменения в ProjectsPage:**
- `useAuth` gate: `canEditDesiredQuarter = isAdmin() || isProjectManager()`
- Lazy-загрузка `commitment` для каждой раскрытой карточки проекта
- Expand-секция «Quarter & Priority» с `DesiredQuarterPicker` + RICE Scoring + `ProjectCommitmentView`

**API client (`frontend/src/api/metrics.ts`):**
- Новые типы: `TeamCommitmentDto`, `ProjectQuarterCommitmentDto`
- Новые методы: `setProjectDesiredQuarter`, `getProjectCommitment`
- `getEpicsForQuarter` принимает опциональный `onlyDesired`

### Семантика — два независимых quarter label

| Уровень | Label | Семантика | Кто ставит |
|---------|-------|-----------|------------|
| Проект (`project.labels`) | `desired_quarter` | PM хочет, чтобы работу сделали в этом квартале | ADMIN / PROJECT_MANAGER |
| Эпик (`epic.labels`) | `committed_quarter` | Тимлид подтверждает: эпик влезает в квартал команды | ADMIN / PROJECT_MANAGER |

Расхождение видимо обеим сторонам: PM — в commitment view, тимлид — в бейдже «PM желает Q2». Standalone эпики (без parent project) — first-class объект, всегда видимы в backlog тимлида.

### Изменение F67 наследования

- Старый `resolveQuarterLabel` (с наследованием от parent project) — оставлен для Board/Projects фильтра (backward compat F67)
- Новый `resolveCommittedQuarter` (без наследования, прямой label эпика) — используется в тимлидском экране F69/F70

### Дизайн-система

- Все цвета через `constants/colors.ts` — никаких хардкод hex
- Переиспользованы: `SingleSelectDropdown`, `TeamBadge`, `RiceScoreBadge`, `Modal`, `MultiSelectDropdown`
- `useAuth().isAdmin()` / `isProjectManager()` для UI-gate

### Известные follow-up (не блокируют release)

- `getProjectCommitment` — O(N×epics_total) при раскрытии всех проектов на ProjectsPage (TODO в коде)
- Unmount-guard на in-flight promises (pre-existing pattern в codebase)
- `onlyDesired` toggle не персистится в URL/localStorage между перезагрузками
- `desiredQuarter == null` bucket семантика — все committed эпики попадают в «другой квартал»; семантически их можно было бы скрывать

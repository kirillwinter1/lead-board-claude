# F21. Unified Planning Algorithm

**Статус:** 🚧 В разработке (Phase 1-4 завершены)
**Дата начала:** 2026-01-25

## Цель

Единый алгоритм планирования, который:
1. Планирует стори по приоритету эпиков (верхний эпик закрываем первым)
2. Использует pipeline SA→DEV→QA внутри каждой стори
3. Назначает конкретных людей на фазы
4. Учитывает capacity и календарь
5. Заменяет два текущих алгоритма (ForecastService + StoryForecastService)

## Ключевые правила

| # | Правило | Описание |
|---|---------|----------|
| 1 | Приоритет эпиков | Эпики планируются по AutoScore DESC. Верхний эпик закрываем первым |
| 2 | Pipeline внутри стори | SA → DEV → QA строго последовательно. DEV ждёт завершения SA, QA ждёт DEV |
| 3 | Один человек на фазу | Одна фаза стори = один исполнитель. Нельзя 2 SA на одной стори |
| 4 | Параллелизм между сторями | Несколько SA могут работать над разными сторями одновременно |
| 5 | Дробление рабочего дня | Если осталось 3ч по стори, человек берёт ещё 5ч другой стори в тот же день |
| 6 | Переход между эпиками | Когда роль закончила работу по эпику, она берёт следующий по приоритету |
| 7 | Dependencies | Blocked story ждёт ПОЛНОГО завершения blocker (SA+DEV+QA) |
| 8 | Auto-assign | Алгоритм сам назначает исполнителей (Jira assignee игнорируется) |
| 9 | Оценки из subtasks | Время на фазы берём из subtasks. Rough estimate только для эпиков в Planned без subtasks |
| 10 | Risk buffer | 20% буфер применяется к оценкам |
| 11 | Стори без оценок | Не планируем, показываем warning на Timeline |

## Алгоритм

### Входные данные

```
Input:
  - teamId: Long
  - epics: List<Epic> — эпики команды, отсортированные по AutoScore DESC
  - teamMembers: List<TeamMember> — члены команды с ролями и capacity
  - workCalendar: WorkCalendar — рабочие дни, праздники
  - riskBuffer: BigDecimal — коэффициент буфера (default 0.20)
```

### Выходные данные

```
Output:
  - plannedEpics: List<PlannedEpic>
      - epicKey: String
      - startDate: LocalDate
      - endDate: LocalDate
      - stories: List<PlannedStory>
          - storyKey: String
          - summary: String
          - startDate: LocalDate
          - endDate: LocalDate
          - phases: PlannedPhases
              - sa: PhaseSchedule (assignee, startDate, endDate, hours)
              - dev: PhaseSchedule
              - qa: PhaseSchedule
          - warnings: List<Warning>
  - warnings: List<Warning> — глобальные предупреждения
  - assigneeUtilization: Map<String, AssigneeStats>
```

### Псевдокод

```python
def unified_planning(team_id):
    # 1. Подготовка данных
    epics = get_epics_sorted_by_autoscore(team_id)
    members = get_team_members(team_id)
    config = get_planning_config(team_id)

    # 2. Создаём schedules для каждого члена команды
    # key: accountId, value: {role, effectiveHoursPerDay, timeline: [(date, availableHours)]}
    assignee_schedules = {}
    for member in members:
        effective_hours = member.hours_per_day / grade_coefficient(member.grade)
        assignee_schedules[member.account_id] = AssigneeSchedule(
            role=member.role,
            effective_hours_per_day=effective_hours,
            availability={}  # date -> remaining hours
        )

    # 3. Для каждого эпика собираем стори с оценками
    all_stories = []
    warnings = []

    for epic in epics:
        stories = get_stories_sorted(epic)  # AutoScore + topological sort

        for story in stories:
            phases = extract_phases(story, epic, config)  # SA/DEV/QA hours

            if phases.total_hours == 0:
                warnings.append(Warning(story.key, "NO_ESTIMATE"))
                continue

            # Применяем risk buffer
            phases = apply_risk_buffer(phases, config.risk_buffer)

            all_stories.append(StoryToSchedule(
                epic_key=epic.key,
                epic_order=epic.order,
                story_key=story.key,
                story_order=story.order,
                dependencies=story.is_blocked_by,
                phases=phases
            ))

    # 4. Планируем фазы
    scheduled_stories = {}  # story_key -> PlannedStory
    story_end_dates = {}    # story_key -> end_date (для dependencies)

    for story in all_stories:
        planned = schedule_story(
            story,
            assignee_schedules,
            story_end_dates,
            work_calendar
        )
        scheduled_stories[story.story_key] = planned
        story_end_dates[story.story_key] = planned.end_date

    # 5. Группируем по эпикам
    return group_by_epics(scheduled_stories, warnings)


def schedule_story(story, assignee_schedules, story_end_dates, calendar):
    """Планирует одну стори с учётом dependencies и availability."""

    # Определяем earliest start (после dependencies)
    earliest_start = today()
    for blocker_key in story.dependencies:
        if blocker_key in story_end_dates:
            blocker_end = story_end_dates[blocker_key]
            earliest_start = max(earliest_start, next_workday(blocker_end))

    phases_scheduled = {}
    current_date = earliest_start

    # Планируем SA → DEV → QA последовательно
    for phase in ['SA', 'DEV', 'QA']:
        hours_needed = story.phases[phase]

        if hours_needed <= 0:
            phases_scheduled[phase] = None
            continue

        # Находим первого свободного человека нужной роли
        assignee = find_earliest_available(
            assignee_schedules,
            role=phase,
            after_date=current_date
        )

        if assignee is None:
            # Нет людей с этой ролью — warning
            phases_scheduled[phase] = PhaseSchedule(
                assignee=None,
                start_date=current_date,
                end_date=None,
                hours=hours_needed,
                warning="NO_CAPACITY"
            )
            continue

        # Планируем работу с учётом дробления дней
        phase_schedule = allocate_hours(
            assignee_schedules[assignee],
            hours_needed,
            start_after=current_date,
            calendar=calendar
        )

        phases_scheduled[phase] = phase_schedule
        current_date = next_workday(phase_schedule.end_date)

    return PlannedStory(
        story_key=story.story_key,
        start_date=phases_scheduled['SA'].start_date if phases_scheduled['SA'] else None,
        end_date=phases_scheduled['QA'].end_date if phases_scheduled['QA'] else
                 phases_scheduled['DEV'].end_date if phases_scheduled['DEV'] else
                 phases_scheduled['SA'].end_date,
        phases=phases_scheduled
    )


def allocate_hours(assignee_schedule, hours_needed, start_after, calendar):
    """
    Распределяет часы на assignee с учётом дробления дней.

    Пример: нужно 12 часов, capacity 8ч/день
    - День 1: 8 часов (полный день)
    - День 2: 4 часа (частичный день, остаётся 4ч для другой работы)
    """
    remaining_hours = hours_needed
    current_date = start_after
    start_date = None

    while remaining_hours > 0:
        # Пропускаем выходные
        current_date = calendar.next_workday(current_date)

        # Сколько часов доступно у assignee в этот день
        available = assignee_schedule.get_available_hours(current_date)

        if available <= 0:
            current_date = next_day(current_date)
            continue

        if start_date is None:
            start_date = current_date

        # Берём минимум из (нужно, доступно)
        hours_to_use = min(remaining_hours, available)

        # Резервируем часы
        assignee_schedule.reserve_hours(current_date, hours_to_use)

        remaining_hours -= hours_to_use

        if remaining_hours > 0:
            current_date = next_day(current_date)

    return PhaseSchedule(
        assignee=assignee_schedule.account_id,
        assignee_name=assignee_schedule.display_name,
        start_date=start_date,
        end_date=current_date,
        hours=hours_needed
    )


def find_earliest_available(assignee_schedules, role, after_date):
    """Находит человека с нужной ролью, который освободится раньше всех."""

    candidates = [
        (account_id, schedule)
        for account_id, schedule in assignee_schedules.items()
        if schedule.role == role
    ]

    if not candidates:
        return None

    # Находим того, кто раньше всех сможет начать работу после after_date
    best = None
    best_date = None

    for account_id, schedule in candidates:
        available_date = schedule.find_first_available_slot(after_date)
        if best_date is None or available_date < best_date:
            best = account_id
            best_date = available_date

    return best
```

### Извлечение оценок по фазам

```python
def extract_phases(story, epic, config):
    """
    Извлекает часы по фазам из subtasks.

    Правила:
    1. Если эпик в статусе Planned и нет subtasks с оценками → rough estimate эпика
    2. Иначе → агрегируем subtasks по типу (SA/DEV/QA)
    """

    subtasks = get_subtasks(story.key)

    # Агрегируем по ролям
    sa_hours = 0
    dev_hours = 0
    qa_hours = 0

    for subtask in subtasks:
        if is_done(subtask.status):
            continue  # Пропускаем завершённые

        remaining = max(0, subtask.estimate - subtask.time_spent)
        role = determine_role(subtask.type)  # SA/DEV/QA

        if role == 'SA':
            sa_hours += remaining
        elif role == 'DEV':
            dev_hours += remaining
        else:
            qa_hours += remaining

    # Если нет оценок и эпик в Planned — используем rough estimate
    if sa_hours == 0 and dev_hours == 0 and qa_hours == 0:
        if is_planned_status(epic.status) and has_rough_estimate(epic):
            # Распределяем rough estimate эпика пропорционально на стори
            # (или используем rough estimate стори если есть)
            return PhaseHours(
                sa=story.rough_sa_hours or 0,
                dev=story.rough_dev_hours or 0,
                qa=story.rough_qa_hours or 0
            )

    return PhaseHours(sa=sa_hours, dev=dev_hours, qa=qa_hours)
```

## Структуры данных

### Input DTOs

```java
// Конфигурация планирования (из TeamService)
record PlanningConfig(
    BigDecimal riskBuffer,          // 0.20
    GradeCoefficients gradeCoeffs,  // senior: 0.8, middle: 1.0, junior: 1.5
    StatusMapping statusMapping
)

// Член команды
record TeamMemberInfo(
    String accountId,
    String displayName,
    Role role,           // SA, DEV, QA
    Grade grade,         // SENIOR, MIDDLE, JUNIOR
    BigDecimal hoursPerDay
)
```

### Output DTOs

```java
// Полный результат планирования
record UnifiedPlanningResult(
    Long teamId,
    LocalDate planningDate,
    List<PlannedEpic> epics,
    List<PlanningWarning> warnings,
    Map<String, AssigneeUtilization> assigneeUtilization
)

// Запланированный эпик
record PlannedEpic(
    String epicKey,
    String summary,
    BigDecimal autoScore,
    LocalDate startDate,
    LocalDate endDate,
    List<PlannedStory> stories,
    // Агрегированные данные по фазам
    PhaseAggregation phases  // total SA/DEV/QA hours, dates
)

// Запланированная стори
record PlannedStory(
    String storyKey,
    String summary,
    BigDecimal autoScore,
    LocalDate startDate,
    LocalDate endDate,
    PlannedPhases phases,
    List<String> blockedBy,
    List<PlanningWarning> warnings
)

// Фазы стори
record PlannedPhases(
    PhaseSchedule sa,
    PhaseSchedule dev,
    PhaseSchedule qa
)

// Расписание одной фазы
record PhaseSchedule(
    String assigneeAccountId,
    String assigneeDisplayName,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal hours,
    boolean noCapacity  // true если нет людей с этой ролью
)

// Предупреждение
record PlanningWarning(
    String issueKey,
    WarningType type,    // NO_ESTIMATE, NO_CAPACITY, CIRCULAR_DEPENDENCY
    String message
)

// Утилизация исполнителя
record AssigneeUtilization(
    String displayName,
    Role role,
    BigDecimal totalHoursAssigned,
    BigDecimal effectiveHoursPerDay,
    Map<LocalDate, BigDecimal> dailyLoad  // для визуализации
)
```

## API

### Endpoint

```
GET /api/planning/unified?teamId={teamId}
```

### Response

```json
{
  "teamId": 3,
  "planningDate": "2026-01-25",
  "epics": [
    {
      "epicKey": "LB-95",
      "summary": "User Authentication",
      "autoScore": 85.0,
      "startDate": "2026-01-27",
      "endDate": "2026-02-15",
      "stories": [
        {
          "storyKey": "LB-210",
          "summary": "Login form",
          "autoScore": 76.0,
          "startDate": "2026-01-27",
          "endDate": "2026-02-03",
          "phases": {
            "sa": {
              "assigneeAccountId": "user-1",
              "assigneeDisplayName": "Anna SA",
              "startDate": "2026-01-27",
              "endDate": "2026-01-28",
              "hours": 12.0
            },
            "dev": {
              "assigneeAccountId": "user-2",
              "assigneeDisplayName": "Bob DEV",
              "startDate": "2026-01-29",
              "endDate": "2026-02-01",
              "hours": 24.0
            },
            "qa": {
              "assigneeAccountId": "user-3",
              "assigneeDisplayName": "Carol QA",
              "startDate": "2026-02-02",
              "endDate": "2026-02-03",
              "hours": 8.0
            }
          },
          "blockedBy": [],
          "warnings": []
        }
      ]
    }
  ],
  "warnings": [
    {
      "issueKey": "LB-215",
      "type": "NO_ESTIMATE",
      "message": "Story has no subtasks with estimates"
    }
  ],
  "assigneeUtilization": {
    "user-1": {
      "displayName": "Anna SA",
      "role": "SA",
      "totalHoursAssigned": 48.0,
      "effectiveHoursPerDay": 7.5
    }
  }
}
```

## Timeline визуализация

### Уровни отображения

```
Epic 1 (LB-95) ▾
├── [==============EPIC BAR================]     ← Общий бар эпика
│
├── Story LB-210 "Login form"
│   └── [SA]───[DEV]───────[QA]                  ← Фазы стори (разные цвета)
│       Anna    Bob        Carol
│
├── Story LB-211 "Logout"
│   └── [SA]─[DEV]──[QA]
│       Anna  Bob   Carol
│
└── Story LB-215 "Password reset" ⚠️ NO_ESTIMATE
    └── (не запланирована)

Epic 2 (LB-100) ▾
├── ...
```

### Цветовая схема фаз

- **SA**: синий (#3b82f6)
- **DEV**: зелёный (#22c55e)
- **QA**: фиолетовый (#8b5cf6)

### Tooltip для фазы

```
SA Phase - LB-210
Assignee: Anna SA
Start: Jan 27, 2026
End: Jan 28, 2026
Hours: 12.0h
```

## Edge Cases

### 1. Стори без оценок
- Не планируем
- Показываем warning на Timeline
- Стори отображается серым без дат

### 2. Нет людей с нужной ролью
- Фаза показывается с `noCapacity: true`
- Warning: "No SA capacity in team"
- Следующие фазы не могут начаться

### 3. Circular dependencies
- Детектим циклы
- Warning для всех сторей в цикле
- Планируем по AutoScore игнорируя циклические deps

### 4. Эпик в Planned без subtasks
- Используем rough estimate эпика
- Распределяем пропорционально по сторям (или равномерно)

### 5. Все стори эпика без оценок
- Эпик показывается без дат
- Warning: "Epic has no estimated stories"

## План реализации

### Phase 1: Новый сервис ✅
- [x] Создать `UnifiedPlanningService`
- [x] Реализовать `AssigneeSchedule` с дроблением дней
- [x] Реализовать основной алгоритм планирования
- [x] Unit тесты (7 тестов)

### Phase 2: API & DTOs ✅
- [x] Создать output DTOs (`UnifiedPlanningResult.java`)
- [x] Добавить endpoint `/api/planning/unified`
- [x] Controller тесты

### Phase 3: Рефакторинг существующих сервисов ✅
- [x] `ForecastService` → делегирует к `UnifiedPlanningService`
- [x] `StoryForecastService` → тесты disabled (сервис будет удалён)
- [x] Обновить `BoardService` → использует `UnifiedPlanningService`
- [x] Обновить `ForecastServiceTest` → тесты конвертации

### Phase 4: Frontend ✅
- [x] Обновить Timeline для отображения фаз (SA/DEV/QA)
- [x] Добавить warnings (NO_ESTIMATE, blocked stories)
- [x] Обновить tooltips (таблица фаз с assignee, датами, часами)
- [x] Добавить легенду для фаз
- [x] Использовать `/api/planning/unified` endpoint

### Phase 5: Тестирование & Документация
- [ ] E2E тестирование
- [ ] Обновить FEATURES.md
- [ ] Performance тестирование

## Изменения в WIP лимитах

С версии F21 **WIP лимиты НЕ влияют на алгоритм планирования**.

### Что было (F15, F16)
- WIP лимиты ограничивали количество активных эпиков
- Эпики за пределами WIP становились в очередь
- Capacity делился между эпиками в WIP

### Что стало (F21)
- Все эпики планируются на основе реальной capacity
- Нет искусственных ограничений
- WIP лимиты сохранены как **рекомендательные значения**

### Что осталось
| Компонент | Статус | Назначение |
|-----------|--------|------------|
| `PlanningConfigDto.WipLimits` | Сохранён | Рекомендательные значения для UI |
| `WipSnapshotService` | Сохранён | Историческая статистика |
| `ForecastResponse.WipStatus` | Сохранён | Обратная совместимость API |
| `EpicForecast.isWithinWip` | Всегда `true` | Legacy field |
| `EpicForecast.queuePosition` | Всегда `null` | Legacy field |

## Связанные фичи

- **F19. Story AutoScore** — используется для сортировки
- **F13. Epic Autoplanning** — заменён UnifiedPlanningService
- **F20. Story Forecast** — заменён UnifiedPlanningService
- **F15. WIP Limits** — deprecate for planning (рекомендательные)
- **F14. Timeline** — будет обновлён для новой модели

# F16. Pipeline WIP + Визуализация сторей

## Цель

Улучшить планирование и визуализацию на Timeline:
1. **Pipeline WIP** — роли не простаивают, SA может начать новый эпик пока DEV работает над предыдущим
2. **Визуализация сторей** — показать child issues эпика как сегменты на Gantt
3. **Tooltips везде** — максимум информации при наведении

---

## Проблема 1: Простой ролей при WIP=1

### Текущее поведение (неправильное)
```
WIP = 1

Epic 1: [SA]──────[DEV]──────────────[QA]────
Epic 2:                                      [SA]──[DEV]──[QA]
                                             ↑
                                    SA простаивает!
```

### Целевое поведение (Pipeline)
```
WIP = 1 (ограничивает эпики НА КАЖДОЙ ФАЗЕ)

Epic 1: [SA]──────[DEV]──────────────[QA]────
Epic 2:      [SA]──────[DEV]──────────────[QA]
             ↑
      SA начинает сразу после завершения SA Epic 1
```

**Принцип:** Каждая роль имеет свой независимый WIP лимит. SA может работать над Epic 2, пока DEV на Epic 1.

---

## Проблема 2: Нет видимости по сторям

### Текущее
```
Epic 1: ████████████████████████████
```
Не видно что внутри, какие стори, их статус.

### Целевое
```
Epic 1: [Story1|Story2|Story3|Story4|Story5]
         Done   Done   WIP    To Do  To Do
```
Видно прогресс по сторям, можно навести и получить детали.

---

## Scope

### Этап 1: Pipeline WIP

**Backend:**
- [ ] Изменить алгоритм ForecastService:
  - SA WIP лимит работает независимо от DEV/QA
  - DEV может начать только когда SA завершит свою часть эпика
  - Роли не ждут завершения всего эпика, только своей фазы
- [ ] Обновить расчёт дат начала фаз

**Frontend:**
- [ ] Обновить отображение pipeline в Timeline
- [ ] Показать перекрытие фаз разных эпиков

### Этап 2: Загрузка сторей из Jira

**Backend:**
- [ ] Расширить JiraSyncService для загрузки child issues
- [ ] Модель StoryEntity (или расширить JiraIssueEntity)
- [ ] Поля: key, summary, status, estimate, timeSpent, parentEpicKey
- [ ] API: GET /api/epics/{epicKey}/stories

**Модель данных:**
```java
public class StoryInfo {
    String storyKey;
    String summary;
    String status;          // To Do, In Progress, Done
    String assignee;
    LocalDate startDate;    // Когда перешла в In Progress
    Long estimateSeconds;
    Long timeSpentSeconds;
    String phase;           // SA, DEV, QA (по label или component)
}
```

### Этап 3: Визуализация сторей на Gantt

**Frontend:**
- [ ] Компонент StorySegments внутри EpicBar
- [ ] Сегменты пропорциональны estimate
- [ ] Цвет по статусу: серый (To Do), синий (WIP), зелёный (Done)
- [ ] Tooltip при наведении на сторю

**Tooltip сторей:**
```
┌──────────────────────────────────┐
│ PROJ-456: Implement login form   │
│ ─────────────────────────────────│
│ Status: In Progress              │
│ Assignee: John Doe               │
│ Estimate: 8h                     │
│ Logged: 4h (50%)                 │
│ Started: Jan 20                  │
│ Phase: DEV                       │
└──────────────────────────────────┘
```

### Этап 4: Tooltips везде

**Элементы с tooltips:**
- [ ] WIP badges (SA: 2/3) → "SA phase: 2 epics active, limit 3"
- [ ] Summary stats → "3 epics on track - completing before due date"
- [ ] Queue badge (#2) → "Position 2 in queue, waiting for Epic X to complete"
- [ ] Phase bars → "DEV phase: Jan 20 - Feb 15 (20 work days)"
- [ ] Due date line → "Due: Feb 28 (5 days remaining)"
- [ ] Today line → "Today: Jan 24"
- [ ] Legend items → описание каждого цвета/элемента

---

## Алгоритм Pipeline WIP

### Текущий (упрощённо)
```
for each epic (sorted by priority):
    if active_epics < team_wip:
        start_epic()
    else:
        queue_epic()
```

### Новый (Pipeline)
```
for each epic (sorted by priority):
    # SA фаза
    if sa_active < sa_wip_limit:
        sa_start = max(today, prev_epic_sa_end)
    else:
        sa_start = wait_for_sa_slot()

    # DEV фаза (начинается после SA этого эпика)
    if dev_active < dev_wip_limit:
        dev_start = max(sa_end + pipeline_offset, prev_epic_dev_end)
    else:
        dev_start = wait_for_dev_slot()

    # QA фаза (начинается после DEV этого эпика)
    if qa_active < qa_wip_limit:
        qa_start = max(dev_end + pipeline_offset, prev_epic_qa_end)
    else:
        qa_start = wait_for_qa_slot()
```

**Ключевое отличие:** Фазы разных эпиков могут перекрываться, но количество эпиков на каждой фазе ограничено.

---

## UI изменения

### Story Segments
```
┌─────────────────────────────────────────────────┐
│ Epic Bar                                        │
│ [S1][S2 ][S3   ][S4][S5    ][S6]               │
│  ✓   ✓    ●     ○   ○       ○                  │
└─────────────────────────────────────────────────┘

✓ = Done (green)
● = In Progress (blue)
○ = To Do (gray)
```

### Expanded View с перекрытием фаз
```
Epic 1: [SA]────[DEV]────────────[QA]────
Epic 2:    [SA]────[DEV]────────────[QA]
Epic 3:       [SA]────[DEV]────────────[QA]
              ↑
         SA работает над Epic 3,
         пока DEV на Epic 1, QA ещё не начал
```

---

## Зависимости

- F15 WIP Limits (завершён)
- F13 Auto Planning (завершён)
- Jira Sync (для загрузки сторей)

---

## Оценка трудозатрат

| Этап | Backend | Frontend | Тесты |
|------|---------|----------|-------|
| 1. Pipeline WIP | 4ч | 2ч | 2ч |
| 2. Загрузка сторей | 4ч | - | 1ч |
| 3. Визуализация сторей | - | 6ч | 1ч |
| 4. Tooltips | - | 4ч | - |
| **Итого** | **8ч** | **12ч** | **4ч** |

---

## Риски

1. **Производительность** — много сторей могут замедлить рендеринг
2. **Jira API лимиты** — загрузка сторей увеличит количество запросов
3. **UX сложность** — не перегрузить интерфейс информацией

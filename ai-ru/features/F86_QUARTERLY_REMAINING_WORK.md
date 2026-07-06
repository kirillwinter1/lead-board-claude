# F86: Остаток работы по эпикам в квартальном планировании

**Статус:** ✅ Реализовано (+ полировка 2026-07-06, см. «Финальная семантика» внизу)
**Дата:** 2026-07-03
**Базируется на:** F70 (customer-driven quarter planning), F21 (Unified Planning автопланер), F20 (Story Forecast)

## Контекст

На странице Quarterly Planning (`/quarterly-planning`) тимлид раскладывает эпики по кварталам. После F70 фильтр «Only requested for this quarter» (`onlyDesired`) показывает только эпики, чьи проекты PM «пожелал» на выбранный квартал, плюс standalone.

Проблема: эпики с **незавершённой работой**, которые не пожеланы PM-ом на новый квартал, при включённом фильтре исчезают из виду — их легко потерять при планировании. Пример: LB-302 (CRM Integration, проект LB-301) активен, но не закоммичен ни в какой квартал и проект не пожелан на 2026Q3 → скрыт.

Тимлиду нужно:
1. Видеть такие эпики при планировании нового квартала, даже с включённым фильтром.
2. Понимать по каждому такому эпику **сколько работы осталось сейчас** и **сколько останется к началу выбранного квартала** (автопланер часть работы «сожжёт» между сегодня и стартом квартала). Именно этот остаток и нужно планировать в новый квартал.

## Термины

| Термин | Определение |
|---|---|
| `committed_quarter` | Метка эпика (F70): тимлид подтвердил эпик в конкретный квартал |
| `desired_quarter` | Метка проекта (F70): PM хочет проект в квартал |
| **needs-planning эпик** | Активный (не Done) эпик, НЕ находящийся в выбранном квартале и НЕ закоммиченный в будущий квартал: либо без коммита вообще, либо хвост из прошлого квартала |
| **Остаток сейчас** | Незавершённая работа по эпику на сегодня, чел-дни по ролям (из автопланера) |
| **Остаток на старте квартала** | Часть работы эпика, которую автопланер ставит на/после даты старта выбранного квартала (не успевает закрыть до старта) |

## Определение needs-planning (client-side)

Метки кварталов `YYYYQn` сортируются лексикографически по хронологии (уже используется в `plannableQuarters`).

```
needsPlanning(e, quarter) =
  !e.inQuarter && (e.quarterLabel == null || e.quarterLabel < quarter)
```

- `quarterLabel == null` → эпик нигде не запланирован (LB-302).
- `quarterLabel < quarter` → хвост из прошлого квартала (carryover).
- `quarterLabel > quarter` (будущий) → НЕ needs-planning (уже запланирован вперёд).
- `inQuarter == true` → уже в выбранном квартале, не трогаем.

## Часть 1 — Фильтр Backlog (frontend)

`onlyDesired` — backlog-only, client-side фильтр (см. фикс в `fix/quarterly-planning-only-requested-reset`). Расширяем условие: needs-planning эпики проходят фильтр всегда.

```ts
backlogEpics = epics.filter(e =>
  !e.inQuarter
  && epicMatchesTeamFilter(e)
  && (!onlyDesired || e.isStandalone || e.projectDesiredQuarter === quarter || needsPlanning(e, quarter))
)
```

Колонка InQuarter фильтром не затрагивается (гарантия из фикса).

## Часть 2 — Остаток работы (backend + frontend)

### Backend

Автопланер (`UnifiedPlanningService.calculatePlan(teamId)`, кэш 60с) выдаёт `UnifiedPlanningResult`:
- `PlannedEpic.phaseAggregation`: `Map<roleCode, {hours, startDate, endDate}>` — остаток по роли с датами.
- `PlannedEpic.stories[].phases`: `Map<roleCode, PhaseSchedule{startDate, endDate, hours, noCapacity}>` — по фазам с датами.

Экран single-team (Team — одиночный выбор), автопланер per-team → одного прогона хватает на все эпики команды.

**Новый метод** `QuarterlyPlanningService.getRemainingForQuarter(Long teamId, String quarterLabel)`:

1. Дата старта квартала `Ds` из `quarterLabel`: `2026Q3` → `LocalDate.of(2026, (q-1)*3+1, 1)`.
2. `result = unifiedPlanningService.calculatePlan(teamId)`.
3. Для каждого `PlannedEpic pe`:
   - `remainingNowByRole[role]` = `phaseAggregation[role].hours` → чел-дни (`/8`, HALF_UP, 1 знак).
   - `remainingAtQuarterStartByRole[role]` = сумма по фазам (story-level `phases`, для rough-эпиков без историй — по `phaseAggregation`) часов, попадающих **на/после `Ds`**, с пропорцией для фаз, пересекающих границу:
     - `phase.endDate < Ds` → 0 (закрыто до старта);
     - `phase.startDate >= Ds` → все `hours`;
     - фаза пересекает `Ds` → `hours * workdays(max(start,Ds)..end) / workdays(start..end)` (рабочие дни через `WorkCalendarService`; fallback — календарные дни);
     - `start/end == null` (напр. `noCapacity`) → считаем полностью остатком (консервативно).
   - `hasEstimate` = есть ли хоть один `phaseAggregation` с `hours > 0`.
4. Возврат: `QuarterlyRemainingResponse{ quarter, teamId, Map<epicKey, EpicRemainingDto> }`.

**DTO** `EpicRemainingDto`:
```
record EpicRemainingDto(
  String epicKey,
  Map<String, BigDecimal> remainingNowByRole,           // {SA: 5.0, DEV: 8.0, QA: 2.0}
  Map<String, BigDecimal> remainingAtQuarterStartByRole, // {SA: 3.0, DEV: 5.0, QA: 1.0}
  BigDecimal remainingNowDays,                            // Σ
  BigDecimal remainingAtQuarterStartDays,                // Σ
  boolean hasEstimate
)
```

**Endpoint** (`QuarterlyPlanningController`):
```
GET /api/quarterly-planning/remaining?teamId={id}&quarter={YYYYQn}
→ QuarterlyRemainingResponse
```
Права — как у остальных quarterly-planning endpoint-ов.

### Frontend

- `api/quarterlyPlanning.ts`: `getRemainingForQuarter(quarter, teamId)`; типы `EpicRemainingDto`, `QuarterlyRemainingResponse`.
- `QuarterlyPlanningPage.tsx`:
  - Хелпер `needsPlanning(epic, quarter)`.
  - Отдельный `useEffect`: при смене `quarter`/`teamFilter` догружает remaining, кладёт в `Map<epicKey, EpicRemainingDto>` (state). Грузится лениво, отдельно от основного списка — доска рендерится сразу, числа подтягиваются.
  - Расширить фильтр `backlogEpics` (Часть 1).
  - Пробросить remaining-map и `needsPlanning` флаг в `BacklogColumn` → `EpicCard`.
- `EpicCard.tsx` (режим backlog, только needs-planning эпики):
  - Маркер-бейдж **«Осталась работа»** (амбер: `WARNING_BG/WARNING_TEXT/WARNING_BORDER` — уже импортированы).
  - Две строки остатка по ролям (чипы ролей через `getRoleColor`, как существующий demand-ряд):
    - `Осталось сейчас: SA 5 · DEV 8 · QA 2 · Σ 15д`
    - `На старте 2026Q3: SA 3 · DEV 5 · QA 1 · Σ 9д`
  - Если `hasEstimate == false` или данных нет — вместо чисел «нет оценки».
  - Существующий demand-ряд остаётся без изменений.

## Что НЕ входит (следующие шаги)

- Кнопка «Запланировать остаток автопланером» (авто-простановка `committed_quarter` под capacity).
- Полное авто-заполнение квартала.
- Разбор «остатка на старте квартала» на per-day burndown-график.

## Тесты

**Backend (JUnit5):**
- `getRemainingForQuarter`: remainingNow == сумма phaseAggregation; проверка пропорции для фазы, пересекающей `Ds`.
- Фаза целиком до `Ds` → 0; целиком после → все часы.
- Эпик без оценки → `hasEstimate=false`, нули.
- Парсинг quarterLabel → корректная `Ds` (Q1→Jan, Q2→Apr, Q3→Jul, Q4→Oct).

**Frontend:**
- Сборка `npm run build` без ошибок типов.
- (Если есть тестовая инфра) unit на `needsPlanning`: null → true; прошлый квартал → true; текущий → false (inQuarter); будущий → false.

## Версия

F86 → **0.86.0** (`backend/build.gradle.kts`, `frontend/package.json`). Обновить `ai-ru/FEATURES.md`.

---

## Финальная семантика (полировка 2026-07-06)

Исходная реализация выше уточнена серией правок по фидбеку. Актуальное поведение:

### Единая шкала чисел — без риск-буфера
- **Спрос = сырая предоценка из Jira.** `QuarterlyPlanningService.computeEpicDemand()` больше НЕ умножает на `(1 + riskBuffer)` — карточки, суммы групп, capacity-математика и детекция перегруза считают по тем же числам, что и Board. Запас на риски выражен на стороне capacity: `CapacityBars` красит >80% жёлтым, >100% красным.
- **Остаток раз-буферивается.** Автопланер планирует часы с буфером — `getRemainingForQuarter` делит их обратно на `(1 + riskBuffer команды)`, чтобы остаток нетронутого эпика равнялся его оценке (тест `remainingIsUnbufferedByTeamRiskBuffer`).
- `planning_config.riskBuffer` продолжает работать в `UnifiedPlanningService` (прогноз расписания).

### Одна строка чисел на карточку — фаза решает
- Эпик **в фазе предоценки** (серверный флаг `PlanningEpicDto.estimateEditable` = категория статуса NEW/REQUIREMENTS/TODO, то же правило, что `epicInTodo` на Board): редактируемые чипы предоценки, все роли пайплайна, у пустых — карандаш ✎. Бейдж «нет оценки» не показывается — чипы говорят сами.
- Эпик **в работе**: предоценка (устаревшая прикидка) скрыта; показывается только «Осталось сейчас: …» — живой остаток по подзадачам.
- Строка «На старте {квартал}» рендерится только когда отличается от «Осталось сейчас» (актуально при планировании будущего квартала).
- Бейдж **«Осталась работа»** — только при посчитанном остатке > 0; без оценки — одиночный бейдж «нет оценки».

### Inline-предоценка (как на Board)
- `PlanningRoleChip` — тот же паттерн, что `EpicRoleChip`: клик → инпут → Enter/blur сохраняет через `PATCH /api/epics/{key}/rough-estimate/{role}`, Escape отменяет. Работает в обеих колонках.
- Своя компактная геометрия `.planning-role-chip` (58×30, горизонтальная), стили — общие классы `epic-role-chip` из BoardPage.css. Read-only чипы сохраняют штриховку rough-only.
- После сохранения demand/Σ/тоталы/capacity обновляются из ответа PATCH без перезагрузки.

### Прочее
- Статус эпика на карточке через `StatusBadge` (`PlanningEpicDto.status`); страница поставляет `StatusStylesContext`. Локальный override `max-width: none` — глобальная утечка `.status-badge { max-width: 130px }` из TimelinePage.css (кандидат в TECH_DEBT).
- Все чел-дни на карточках — **целые** (`Math.round`), Σ строки = сумма округлённых чипов.
- С карточек убраны: бейджи команд (страница отфильтрована по одной команде), Standalone (дублирует группу «Без проекта»), «перегруз» + красные рамки (сигнал уровня команды — в capacity-барах), строка «Project:» под заголовком группы проекта.

### Известный край
Эпик в работе, закоммиченный в квартал, в колонке «В квартале» показывает карточку без чисел (remaining-map туда не проброшен). В текущих данных не встречается; починка — проброс `remainingByEpic` в `InQuarterColumn` тем же паттерном.

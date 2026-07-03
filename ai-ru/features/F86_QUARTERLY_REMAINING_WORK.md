# F86: Остаток работы по эпикам в квартальном планировании

**Статус:** 🚧 В разработке
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

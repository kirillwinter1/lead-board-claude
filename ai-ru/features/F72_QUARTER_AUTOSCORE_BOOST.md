# F72 — Бонус в AutoScore за включение в текущий квартал

**Дата:** 2026-05-25
**Версия:** 0.72.0
**Статус:** Реализовано

## Проблема

`AutoScoreCalculator` ранжирует эпики по 9 факторам (Status, Progress, Due Date, Priority, Size, Age, RICE, Alignment, Flagged). Но факта «эпик закоммичен в текущий квартал» среди них нет.

Команда сознательно решает, что именно идёт в квартал (через Jira-label `YYYYQN`, поддерживается в `JiraIssueEntity.getQuarterLabel()` и используется `QuarterlyPlanningService`). После этого решения такой эпик должен подниматься в рекомендованной позиции на доске — но сейчас AutoScore этого не знает.

Пример из текущего UI: `LB-9` с label `2026Q2`, Total Score `31.6`, recommended position 6. Никакой строки про квартал в breakdown нет — пользователь не понимает, учитывается ли его квартальное планирование.

## Решение

Новый фактор `quarter` в `AutoScoreCalculator`: **+10** баллов, если quarter-label эпика равен метке текущего квартала, иначе **0**.

### Бонус даётся только за текущий квартал

- Прошлые кварталы → 0 (никакого штрафа: лейбл могли просто не обновить).
- Будущие кварталы → 0 (бэклог квартала, ещё не активен).
- Без quarter-label → 0.

Текущий квартал вычисляется из `LocalDate.now()`: `year + "Q" + ((month - 1) / 3 + 1)` → `2026Q2` для мая 2026.

### Вес = 10

В одной лиге с `Progress` (10) и максимумом `Alignment` (10). Не перебивает топ-факторы Status (+30) и Due Date (+25), но заметно сдвигает квартальный эпик вверх по сравнению с не-квартальным.

### Helper для метки текущего квартала

Вычисление метки выносится в `public static String getCurrentQuarterLabel(LocalDate today)` в `QuarterlyPlanningService` (там уже живёт паттерн `QUARTER_LABEL_PATTERN`). Формула: `today.getYear() + "Q" + ((today.getMonthValue() - 1) / 3 + 1)`.

Метод **static** — `AutoScoreCalculator` вызывает его напрямую (`QuarterlyPlanningService.getCurrentQuarterLabel(LocalDate.now())`), без инъекции бина. Это исключает риск циклической зависимости в Spring DI и не меняет конструктор `AutoScoreCalculator`.

## Затронутые файлы

**Backend:**
- `backend/src/main/java/com/leadboard/planning/QuarterlyPlanningService.java` — публичный метод `getCurrentQuarterLabel()` (использует `LocalDate.now()` через инжектируемый `Clock` или `LocalDate.now()` напрямую; см. секцию «Тестируемость текущей даты»).
- `backend/src/main/java/com/leadboard/planning/AutoScoreCalculator.java` —
  - константа `WEIGHT_QUARTER = new BigDecimal("10")`;
  - метод `calculateQuarterBoost(JiraIssueEntity epic)` (вызывает `QuarterlyPlanningService.getCurrentQuarterLabel(LocalDate.now())`, без инъекции бина);
  - вызов `factors.put("quarter", calculateQuarterBoost(epic));` в `calculateFactors()`;
  - обновлённый JavaDoc вверху класса (добавить строку про Quarter в список факторов).

**Frontend:**
- `frontend/src/components/board/PriorityCell.tsx` — в `factorLabels` добавить `quarter: 'Quarter'`. Сортировка факторов по `Math.abs(value)` уже корректна — Quarter (+10) встанет между Status/DueDate/Priority и RICE/Progress/Size/Age.

**Тесты (backend):**
- `AutoScoreCalculatorTest`:
  - `shouldGiveQuarterBoostForCurrentQuarterLabel` — эпик с label текущего квартала → `factors["quarter"]` == 10.
  - `shouldNotGiveQuarterBoostForPastQuarter` — label `2024Q1` (заведомо прошлый) → 0.
  - `shouldNotGiveQuarterBoostForFutureQuarter` — label `2099Q4` (заведомо будущий) → 0.
  - `shouldNotGiveQuarterBoostWithoutQuarterLabel` — пустой/нерелевантный label → 0.
  - `shouldIncludeQuarterBoostInTotalScore` — `calculate()` суммирует фактор корректно.

**Документация:**
- `ai-ru/FEATURES.md` — запись `| F72 | Current Quarter Boost in AutoScore | 2026-05-25 | [features/F72](features/F72_QUARTER_AUTOSCORE_BOOST.md) |`.

**Версии:**
- `backend/build.gradle.kts` → `version = "0.72.0"`
- `frontend/package.json` → `"version": "0.72.0"`

## Тестируемость текущей даты

`AutoScoreCalculator` уже использует `LocalDate.now()` напрямую в `calculateDueDateScore`/`calculateAgeScore`. Сохраняем тот же паттерн в `calculateQuarterBoost`, чтобы не вводить `Clock` ради одной фичи.

Тестируемость обеспечена сигнатурой helper'а: `QuarterlyPlanningService.getCurrentQuarterLabel(LocalDate today)` принимает дату параметром. AutoScoreCalculator вызывает его как `getCurrentQuarterLabel(LocalDate.now())`. Тесты:

- **Current quarter** — вычисляют ожидаемый label тем же helper'ом с `LocalDate.now()`, ассертят `factors["quarter"] == 10`. Тест не ломается при переходе квартала — обе стороны используют одну формулу.
- **Past / future / no label** — статические лейблы (`"1999Q1"`, `"2999Q4"`, `null`) гарантированно не равны текущему кварталу, не зависят от даты.

## Edge cases

| Случай | Поведение |
|--------|-----------|
| Эпик с несколькими quarter-label (`2026Q1`, `2026Q2`) | `getQuarterLabel()` возвращает первый matching — поведение наследуется. Если первый — текущий, бонус есть; если первый — прошлый, бонуса нет. Сейчас в проекте такие случаи не встречаются. |
| Label регистре отличающемся (`2026q2`) | `getQuarterLabel()` использует regex `\d{4}Q[1-4]` — `q` в нижнем регистре не матчит. Считаем как «нет квартала». Не меняем существующий regex в рамках этой фичи. |
| Эпик закрыт (Done), label текущего квартала | Done эпики уже исключены из `recalculateAll` (F71). Фактор не вычисляется. |
| Граница смены квартала (1 апреля 00:00) | `LocalDate.now()` использует системную TZ. Эпики с `2026Q1` мгновенно теряют бонус, с `2026Q2` — получают. Это ожидаемо, отдельной обработки не требует. |
| Quarter-label у Project (родителя эпика) | Не наследуется. Бонус только за label на самом эпике (так же, как `resolveCommittedQuarter` сейчас работает). Изменение поведения наследования — отдельная задача. |

## Что осознанно НЕ делаем

- **Штраф за прошлые кварталы.** Лейбл могли не обновить — топить за это эпик нельзя.
- **Конфигурируемый вес через Admin UI.** Все остальные веса — константы; не вводим один особенный сегодня.
- **Наследование label от родителя-проекта.** Расширение `resolveCommittedQuarter` в отдельной фиче.
- **Бонус «следующий квартал».** Только текущий — соответствует выбору пользователя при брейнсторминге.
- **Инъекция `Clock` бина.** Out of scope; используем параметр `today` для тестируемости.

## Спецификация и план

- Spec: `docs/superpowers/specs/2026-05-25-quarter-autoscore-boost-design.md` (копия этого документа для брейнсторминг-флоу)
- Plan: будет создан через `writing-plans` после approve спеки

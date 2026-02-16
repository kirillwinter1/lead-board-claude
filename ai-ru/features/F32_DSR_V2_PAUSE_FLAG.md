# F32: DSR v2 — Pause Flag + Subtask End Point

## Статус: ✅ Done (2026-02-15)

## Проблема

Старая формула DSR использовала `epic.doneAt` как конечную точку, что включало время E2E-тестирования и приёмки заказчиком — этапы, которые команда не контролирует. Также не учитывались паузы (ожидание заказчика, внешние блокеры).

## Решение

### Новая формула DSR

```
DSR = (working_days - flagged_days) / estimate_days
```

- `working_days` — рабочие дни от `epic.started_at` до `endDate`
- `endDate`: завершённые эпики = `max(subtask.done_at)`, in-progress = `today`
- `flagged_days` — рабочие дни под флагом (паузы)
- `estimate_days` = `sum(subtask.original_estimate_seconds) / (8 * 3600)`

### Ключевые изменения

1. **Pause Flag** — флаг на эпике останавливает таймер DSR. Каждое включение/выключение записывается в `flag_changelog`.
2. **Subtask End Point** — для завершённых эпиков конечная точка = `max(subtask.done_at)`, а не `epic.done_at`. Это исключает E2E/приёмку.
3. **Live DSR** — DSR считается для in-progress эпиков (endDate = today). Если DSR > 1.0 — команда отстаёт.
4. **AutoScore штраф** — flagged эпик получает -100 к AutoScore, что опускает его ниже активных эпиков на Board.

## Файлы

### Новые
| Файл | Описание |
|------|----------|
| `V29__create_flag_changelog.sql` | Таблица `flag_changelog` (issue_key, flagged_at, unflagged_at) |
| `FlagChangelogEntity.java` | JPA entity для flag_changelog |
| `FlagChangelogRepository.java` | Repository с `findOpenByIssueKey()` |
| `FlagChangelogService.java` | Детекция изменений флага + расчёт flagged workdays |
| `FlagChangelogServiceTest.java` | 6 тестов |

### Изменённые
| Файл | Изменение |
|------|-----------|
| `EpicDsr.java` | +3 поля: `inProgress`, `calendarWorkingDays`, `flaggedDays`, `effectiveWorkingDays` |
| `DsrService.java` | Новая формула, in-progress эпики, subtask endpoint, flagged days |
| `JiraIssueRepository.java` | +query `findEpicsForDsr()` (startedAt != null, completed OR in-progress) |
| `SyncService.java` | +FlagChangelogService, детекция изменения флага при sync |
| `DataQualityRule.java` | +`SUBTASK_TIME_LOGGED_WHILE_EPIC_FLAGGED` |
| `DataQualityService.java` | Проверка time logged при flagged epic |
| `AutoScoreCalculator.java` | +фактор `flagged` (-100 штраф за приостановку) |
| `DsrServiceTest.java` | 9 тестов (4 обновлены + 5 новых) |
| `SyncServiceTest.java` | +FlagChangelogService mock |
| `metrics.ts` | Обновлён интерфейс EpicDsr |
| `DsrGauge.tsx` | Обновлён tooltip с формулой |
| `TeamMetricsPage.tsx` | +DSR Epic Breakdown таблица с Live/Done бейджами |
| `TeamMetricsPage.css` | +стили badge-live, badge-done |

## API

`GET /api/metrics/dsr?teamId=1&from=2025-01-01&to=2026-12-31`

Ответ (EpicDsr):
```json
{
  "epicKey": "PROJ-42",
  "summary": "New Feature",
  "inProgress": true,
  "calendarWorkingDays": 15,
  "flaggedDays": 3,
  "effectiveWorkingDays": 12,
  "estimateDays": 10.0,
  "forecastDays": null,
  "dsrActual": 1.20,
  "dsrForecast": null
}
```

## Data Quality

Новое правило `SUBTASK_TIME_LOGGED_WHILE_EPIC_FLAGGED` (WARNING): предупреждает когда на сабтаске есть залогированное время, а родительский эпик под флагом (паузой).

## Тесты

### DsrServiceTest (9 тестов)
- `calculateDsr_noEpics_returnsEmpty`
- `calculateDsr_withEpics_calculatesCorrectly`
- `calculateDsr_epicWithoutStartDate_usesJiraCreatedAt`
- `calculateDsr_epicWithSubtaskEstimates_sumsSubtasks`
- `calculateDsr_epicWithNoEstimate_dsrActualNull`
- `calculateDsr_withFlaggedDays_subtractsFromWorkingDays` ✨
- `calculateDsr_usesMaxSubtaskDoneAt_notEpicDoneAt` ✨
- `calculateDsr_noSubtasksDoneAt_fallsBackToEpicDoneAt` ✨
- `calculateDsr_inProgressEpic_usesTodayAsEndDate` ✨
- `calculateDsr_mixedCompletedAndInProgress` ✨

### FlagChangelogServiceTest (6 тестов)
- `detectFlagChange_flagged_createsEntry`
- `detectFlagChange_unflagged_closesEntry`
- `detectFlagChange_noChange_doesNotSave`
- `calculateFlaggedWorkdays_noEntries_returnsZero`
- `calculateFlaggedWorkdays_partialOverlap`
- `calculateFlaggedWorkdays_multiplePeriods`
- `calculateFlaggedWorkdays_openEntry_usesToday`

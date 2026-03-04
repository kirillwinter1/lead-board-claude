# F32: DSR v2 — Pause Flag + Subtask End Point

## Статус: ✅ Done (2026-02-15)

## Проблема

Старая формула DSR использовала `epic.doneAt` как конечную точку, что включало время E2E-тестирования и приёмки заказчиком — этапы, которые команда не контролирует. Также не учитывались паузы (ожидание заказчика, внешние блокеры).

## Решение

### Формула DSR (v3 — status-based)

```
DSR = (дни_в_работе − дни_паузы) / оценка_в_днях
```

- `дни_в_работе` — сумма рабочих дней во всех периодах IN_PROGRESS (из `status_changelog`)
- `дни_паузы` — рабочие дни под флагом Jira, только внутри периодов IN_PROGRESS
- `оценка` = `sum(subtask.original_estimate_seconds) / (8 * 3600)`, fallback: rough estimates
- Fallback для эпиков без changelog: `startedAt` → `doneAt` (историческая совместимость)

### Ключевые изменения

1. **Pause Flag** — флаг на эпике останавливает таймер DSR. Каждое включение/выключение записывается в `flag_changelog`.
2. **Status-based подсчёт** — дни в работе считаются из status_changelog (периоды IN_PROGRESS), а не от startedAt. Это корректно обрабатывает откаты статуса (epic возвращён в Planned → дни в Planned не считаются).
3. **Множественные периоды** — epic может несколько раз входить/выходить из IN_PROGRESS, все периоды суммируются.
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
| `EpicDsr.java` | Поля: `inProgressWorkdays`, `flaggedDays`, `effectiveWorkingDays` (status-based) |
| `DsrService.java` | Status-based формула: changelog IN_PROGRESS периоды + fallback на startedAt |
| `JiraIssueRepository.java` | `findEpicsForDsr()` без фильтра startedAt, сортировка по issueKey |
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

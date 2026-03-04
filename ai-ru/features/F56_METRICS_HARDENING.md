# F56: Metrics Hardening

**Статус:** Реализована
**Версия:** 0.56.0
**Дата:** 2026-03-03

## Описание

Комплексное исправление 15 проблем на экране Team Metrics: точность данных, визуальная консистентность, понятность метрик.

## Исправления

### Critical (5)

1. **Lead Time всегда 0** — null lead/cycle time в by-assignee теперь возвращает `null` вместо `BigDecimal.ZERO`. Frontend показывает "—" вместо "0.0 days".

2. **Отрицательные cycle times** — SQL фильтр `AND (started_at IS NULL OR done_at >= started_at)` в `getExtendedMetricsByAssignee` исключает некорректные данные.

3. **Time-in-Status теряет 40-100% данных** — порог снижен с 300с до 60с. Показываются ВСЕ статусы из changelog (не только pipeline). Исключены NEW/DONE/TODO. Сортировка: pipeline statuses first, затем по количеству переходов.

4. **Velocity 280% spike** — `getLoggedHoursByWeek()` переписан: time_spent распределяется пропорционально от `started_at` до `done_at`. Если `started_at` null — fallback на неделю `done_at`.

5. **DSR включает planned epics** — фильтр после `findEpicsForDsr()`: completed epics всегда включены, незавершённые — только если `isEpicInProgress(status)` = true.

### High (6)

6. **Throughput без BUG** — добавлено поле `bugs` в `PeriodThroughput`, `totalBugs` в `ThroughputResponse`. Frontend показывает bugs в subtitle.

7. **AssigneeTable: Velocity колонка дублирует DSR** — колонка Velocity удалена. DSR thresholds выровнены с DsrGauge: ≤1.1 green, ≤1.5 orange, >1.5 red.

8. **DsrGauge: несогласованные цвета** — `#00875a` → `#36B37E`, `#ff991f` → `#FFAB00`, `#de350b` → `#FF5630`.

9. **DSR table: несогласованные цвета** — те же замены цветов.

10. **"On-Time Rate" misleading** — переименовано в "Within Estimate" с уточнённым tooltip.

11. **Forecast Accuracy: направление ratio непонятно** — добавлена подсказка "> 1 = быстрее плана" и контрастное примечание с DSR.

### Medium (4)

12-15. **TS types для nullable полей** — `avgLeadTimeDays` и `avgCycleTimeDays` теперь `number | null`.

## Затронутые файлы

### Backend
- `TeamMetricsService.java` — null handling, time-in-status rewrite, BUG throughput
- `MetricsQueryRepository.java` — negative cycle time filter
- `StatusChangelogRepository.java` — threshold 300→60
- `VelocityService.java` — proportional distribution rewrite
- `DsrService.java` — planned epic exclusion
- `PeriodThroughput.java` — +bugs field
- `ThroughputResponse.java` — +totalBugs field

### Frontend
- `metrics.ts` — updated types
- `AssigneeTable.tsx` — null handling, removed Velocity column, aligned DSR thresholds
- `DsrGauge.tsx` — consistent colors
- `TeamMetricsPage.tsx` — DSR table colors, "Within Estimate", bugs subtitle
- `ForecastAccuracyChart.tsx` — direction hint

### Tests
- `TeamMetricsServiceTest.java` — null lead time, BUG throughput, time-in-status with all statuses
- `VelocityServiceTest.java` (new) — proportional distribution, single-week, null fallback
- `DsrServiceTest.java` — planned epic excluded, completed always included

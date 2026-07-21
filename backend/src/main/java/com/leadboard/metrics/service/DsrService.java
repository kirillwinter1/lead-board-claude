package com.leadboard.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.entity.WorkflowRoleEntity;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.EpicDsr;
import com.leadboard.metrics.dto.DsrResponse;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.leadboard.metrics.dto.MonthlyDsrResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Delivery Speed Ratio (DSR) Service.
 *
 * DSR = (in_progress_workdays - flagged_days) / estimate_days
 *
 * Uses status changelog to calculate actual time spent in IN_PROGRESS statuses,
 * instead of calendar-based startedAt approach.
 *
 * Interpretation:
 * - 1.0 = baseline speed
 * - < 1.0 = completed faster than estimated
 * - > 1.0 = completed slower than estimated
 */
@Service
public class DsrService {

    private static final Logger log = LoggerFactory.getLogger(DsrService.class);
    private static final BigDecimal ON_TIME_THRESHOLD = new BigDecimal("1.1");

    private final JiraIssueRepository issueRepository;
    private final WorkCalendarService workCalendarService;
    private final ForecastSnapshotRepository snapshotRepository;
    private final WorkflowConfigService workflowConfigService;
    private final FlagChangelogService flagChangelogService;
    private final StatusChangelogRepository statusChangelogRepository;
    private final ObjectMapper objectMapper;

    public DsrService(
            JiraIssueRepository issueRepository,
            WorkCalendarService workCalendarService,
            ForecastSnapshotRepository snapshotRepository,
            WorkflowConfigService workflowConfigService,
            FlagChangelogService flagChangelogService,
            StatusChangelogRepository statusChangelogRepository,
            ObjectMapper objectMapper
    ) {
        this.issueRepository = issueRepository;
        this.workCalendarService = workCalendarService;
        this.snapshotRepository = snapshotRepository;
        this.workflowConfigService = workflowConfigService;
        this.flagChangelogService = flagChangelogService;
        this.statusChangelogRepository = statusChangelogRepository;
        this.objectMapper = objectMapper;
    }

    public DsrResponse calculateDsr(Long teamId, LocalDate from, LocalDate to) {
        // Single-period DSR intentionally includes still-open epics (lifetime in-progress
        // days), so their ongoing delivery speed is visible on the dashboard.
        return calculateDsr(teamId, from, to, false);
    }

    /**
     * @param excludeOpenEpicsOutsideWindow when true, still-open epics (doneAt IS NULL,
     *        which the findEpicsForDsr query returns for EVERY window) are only counted if
     *        their in-progress activity actually intersects [from, to]. Used by the monthly
     *        trend so an open epic does not pollute months before it even started.
     */
    private DsrResponse calculateDsr(Long teamId, LocalDate from, LocalDate to,
                                     boolean excludeOpenEpicsOutsideWindow) {
        List<JiraIssueEntity> epics = issueRepository.findEpicsForDsr(
                teamId,
                from.atStartOfDay().atOffset(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        );

        if (excludeOpenEpicsOutsideWindow) {
            epics = epics.stream()
                    .filter(epic -> epic.getDoneAt() != null || isInProgressDuringWindow(epic, from, to))
                    .collect(Collectors.toList());
        }

        log.info("DSR: Found {} epics for team {} between {} and {}",
                epics.size(), teamId, from, to);

        if (epics.isEmpty()) {
            return new DsrResponse(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, List.of());
        }

        // Load forecast snapshots
        LocalDate snapshotFrom = from.minusDays(180);
        List<ForecastSnapshotEntity> snapshots = snapshotRepository.findByTeamIdAndDateRange(teamId, snapshotFrom, to);
        Map<LocalDate, UnifiedPlanningResult> snapshotsByDate = parseSnapshots(snapshots);

        // Batch-load all stories (children of epics) and their subtasks to avoid N+1
        List<String> epicKeys = epics.stream().map(JiraIssueEntity::getIssueKey).toList();
        Map<String, List<JiraIssueEntity>> storiesByEpicKey = issueRepository.findByParentKeyIn(epicKeys).stream()
                .collect(Collectors.groupingBy(JiraIssueEntity::getParentKey));
        List<String> allStoryKeys = storiesByEpicKey.values().stream()
                .flatMap(List::stream).map(JiraIssueEntity::getIssueKey).toList();
        Map<String, List<JiraIssueEntity>> subtasksByStoryKey = allStoryKeys.isEmpty()
                ? Map.of()
                : issueRepository.findByParentKeyIn(allStoryKeys).stream()
                        .collect(Collectors.groupingBy(JiraIssueEntity::getParentKey));

        List<EpicDsr> epicDsrs = new ArrayList<>();
        BigDecimal totalDsrActual = BigDecimal.ZERO;
        BigDecimal totalDsrForecast = BigDecimal.ZERO;
        int dsrActualCount = 0;
        int dsrForecastCount = 0;
        int onTimeCount = 0;

        for (JiraIssueEntity epic : epics) {
            EpicDsr dsr = calculateEpicDsr(epic, snapshotsByDate, storiesByEpicKey, subtasksByStoryKey);
            if (dsr != null) {
                epicDsrs.add(dsr);
                if (dsr.dsrActual() != null) {
                    totalDsrActual = totalDsrActual.add(dsr.dsrActual());
                    dsrActualCount++;
                    if (dsr.dsrActual().compareTo(ON_TIME_THRESHOLD) <= 0) {
                        onTimeCount++;
                    }
                }
                if (dsr.dsrForecast() != null) {
                    totalDsrForecast = totalDsrForecast.add(dsr.dsrForecast());
                    dsrForecastCount++;
                }
            }
        }

        int totalEpics = epicDsrs.size();
        BigDecimal avgDsrActual = dsrActualCount > 0
                ? totalDsrActual.divide(BigDecimal.valueOf(dsrActualCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgDsrForecast = dsrForecastCount > 0
                ? totalDsrForecast.divide(BigDecimal.valueOf(dsrForecastCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal onTimeRate = totalEpics > 0
                ? BigDecimal.valueOf(onTimeCount * 100.0 / totalEpics).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new DsrResponse(avgDsrActual, avgDsrForecast, totalEpics, onTimeCount, onTimeRate, epicDsrs);
    }

    private EpicDsr calculateEpicDsr(JiraIssueEntity epic, Map<LocalDate, UnifiedPlanningResult> snapshotsByDate,
                                     Map<String, List<JiraIssueEntity>> storiesByEpicKey,
                                     Map<String, List<JiraIssueEntity>> subtasksByStoryKey) {
        InProgressResult inProgressResult = calculateInProgressWorkdays(epic.getIssueKey(), epic);

        if (inProgressResult.totalWorkdays <= 0) {
            return null; // No time in IN_PROGRESS → exclude
        }

        int flaggedDays = calculateFlaggedDaysInPeriods(epic.getIssueKey(), inProgressResult.periods);
        int effectiveWorkingDays = Math.max(inProgressResult.totalWorkdays - flaggedDays, 1);

        BigDecimal estimateDays = calculateEstimateDays(epic, storiesByEpicKey, subtasksByStoryKey);
        BigDecimal forecastDays = calculateForecastDays(epic, snapshotsByDate);

        BigDecimal dsrActual = estimateDays != null && estimateDays.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(effectiveWorkingDays).divide(estimateDays, 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal dsrForecast = forecastDays != null && forecastDays.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(effectiveWorkingDays).divide(forecastDays, 2, RoundingMode.HALF_UP)
                : null;

        return new EpicDsr(
                epic.getIssueKey(),
                epic.getSummary(),
                epic.getStatus(),
                epic.getIssueType(),
                inProgressResult.totalWorkdays,
                flaggedDays,
                effectiveWorkingDays,
                estimateDays,
                forecastDays,
                dsrActual,
                dsrForecast
        );
    }

    /**
     * Calculates total workdays spent in IN_PROGRESS statuses by analyzing status changelog.
     * Falls back to startedAt→doneAt for epics with no changelog (historical data).
     */
    InProgressResult calculateInProgressWorkdays(String issueKey, JiraIssueEntity epic) {
        List<StatusChangelogEntity> changelog = statusChangelogRepository
                .findByIssueKeyOrderByTransitionedAtAsc(issueKey);

        List<DatePeriod> periods = new ArrayList<>();

        if (changelog.isEmpty()) {
            // Fallback for historical epics without changelog
            LocalDate start = epic.getStartedAt() != null
                    ? epic.getStartedAt().toLocalDate()
                    : (epic.getJiraCreatedAt() != null ? epic.getJiraCreatedAt().toLocalDate() : null);
            if (start != null) {
                LocalDate end = epic.getDoneAt() != null ? epic.getDoneAt().toLocalDate() : LocalDate.now();
                periods.add(new DatePeriod(start, end));
            }
        } else {
            LocalDate periodStart = null;
            for (StatusChangelogEntity entry : changelog) {
                boolean toInProgress = workflowConfigService.isEpicInProgress(entry.getToStatus());
                boolean fromInProgress = entry.getFromStatus() != null
                        && workflowConfigService.isEpicInProgress(entry.getFromStatus());

                if (toInProgress && periodStart == null) {
                    periodStart = entry.getTransitionedAt().toLocalDate();
                } else if (fromInProgress && !toInProgress && periodStart != null) {
                    LocalDate periodEnd = entry.getTransitionedAt().toLocalDate();
                    periods.add(new DatePeriod(periodStart, periodEnd));
                    periodStart = null;
                }
            }

            // Open period (still in progress) → end = today
            if (periodStart != null) {
                periods.add(new DatePeriod(periodStart, LocalDate.now()));
            }
        }

        int totalWorkdays = 0;
        LocalDate prevTo = null;
        for (DatePeriod period : periods) {
            int workdays = workCalendarService.countWorkdays(period.from, period.to);
            // countWorkdays is inclusive of both endpoints; when a pause and resume happen
            // on the same workday, that boundary day is the `to` of one period and the
            // `from` of the next, so it would be counted twice. Drop the shared day once —
            // but only when it is actually a workday: on a weekend/holiday boundary
            // countWorkdays already counted it zero times in each period, so subtracting
            // would under-count the in-progress span by a day.
            if (prevTo != null && period.from.equals(prevTo) && workCalendarService.isWorkday(prevTo)) {
                workdays -= 1;
            }
            totalWorkdays += workdays;
            prevTo = period.to;
        }

        return new InProgressResult(totalWorkdays, periods);
    }

    /**
     * True if the epic's IN_PROGRESS activity overlaps the [from, to] window (inclusive).
     * For a still-open epic the last period runs to today, so a past month that ended
     * before the epic entered progress yields no overlap and the epic is excluded.
     */
    private boolean isInProgressDuringWindow(JiraIssueEntity epic, LocalDate from, LocalDate to) {
        InProgressResult result = calculateInProgressWorkdays(epic.getIssueKey(), epic);
        for (DatePeriod period : result.periods()) {
            boolean overlaps = !period.from().isAfter(to) && !period.to().isBefore(from);
            if (overlaps) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculates flagged workdays only within IN_PROGRESS periods.
     */
    int calculateFlaggedDaysInPeriods(String issueKey, List<DatePeriod> periods) {
        int total = 0;
        for (DatePeriod period : periods) {
            total += flagChangelogService.calculateFlaggedWorkdays(issueKey, period.from, period.to);
        }
        return total;
    }

    private BigDecimal calculateEstimateDays(JiraIssueEntity epic,
                                             Map<String, List<JiraIssueEntity>> storiesByEpicKey,
                                             Map<String, List<JiraIssueEntity>> subtasksByStoryKey) {
        // 1. Try subtask estimates (Epic → Story → Subtask) using pre-loaded data
        List<JiraIssueEntity> stories = storiesByEpicKey.getOrDefault(epic.getIssueKey(), List.of());
        if (!stories.isEmpty()) {
            long totalEstimateSeconds = stories.stream()
                    .flatMap(story -> subtasksByStoryKey.getOrDefault(story.getIssueKey(), List.of()).stream())
                    .mapToLong(st -> st.getOriginalEstimateSeconds() != null ? st.getOriginalEstimateSeconds() : 0)
                    .sum();

            if (totalEstimateSeconds > 0) {
                return BigDecimal.valueOf(totalEstimateSeconds)
                        .divide(BigDecimal.valueOf(3600 * 8), 2, RoundingMode.HALF_UP);
            }
        }

        // 2. Fallback to rough estimates on epic (dynamic roles from workflow config)
        BigDecimal total = BigDecimal.ZERO;
        boolean hasAny = false;
        for (WorkflowRoleEntity role : workflowConfigService.getRolesInPipelineOrder()) {
            BigDecimal estimate = epic.getRoughEstimate(role.getCode());
            if (estimate != null) {
                total = total.add(estimate);
                hasAny = true;
            }
        }
        if (hasAny && total.compareTo(BigDecimal.ZERO) > 0) return total;

        return null;
    }

    private BigDecimal calculateForecastDays(JiraIssueEntity epic, Map<LocalDate, UnifiedPlanningResult> snapshotsByDate) {
        for (Map.Entry<LocalDate, UnifiedPlanningResult> entry : snapshotsByDate.entrySet()) {
            UnifiedPlanningResult plan = entry.getValue();
            Optional<UnifiedPlanningResult.PlannedEpic> plannedEpic = plan.epics().stream()
                    .filter(e -> e.epicKey().equals(epic.getIssueKey()))
                    .findFirst();

            if (plannedEpic.isPresent()) {
                UnifiedPlanningResult.PlannedEpic pe = plannedEpic.get();
                if (pe.startDate() != null && pe.endDate() != null) {
                    int forecastWorkdays = workCalendarService.countWorkdays(pe.startDate(), pe.endDate());
                    if (forecastWorkdays > 0) {
                        return BigDecimal.valueOf(forecastWorkdays);
                    }
                }
            }
        }
        return null;
    }

    private Map<LocalDate, UnifiedPlanningResult> parseSnapshots(List<ForecastSnapshotEntity> snapshots) {
        Map<LocalDate, UnifiedPlanningResult> result = new LinkedHashMap<>();
        for (ForecastSnapshotEntity snapshot : snapshots) {
            try {
                UnifiedPlanningResult plan = objectMapper.readValue(
                        snapshot.getUnifiedPlanningJson(), UnifiedPlanningResult.class
                );
                result.put(snapshot.getSnapshotDate(), plan);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse snapshot for date {}: {}", snapshot.getSnapshotDate(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * Calculate monthly DSR trend over the last N months.
     * For each month, calls calculateDsr with that month's date range.
     */
    public MonthlyDsrResponse calculateMonthlyDsr(Long teamId, int months) {
        if (months < 1) months = 1;
        if (months > 24) months = 24;

        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(months - 1);

        List<MonthlyDsrResponse.MonthlyDsrPoint> points = new ArrayList<>();

        for (YearMonth ym = start; !ym.isAfter(current); ym = ym.plusMonths(1)) {
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();

            // Trend semantics = "this month": an open epic must only count in months where
            // its in-progress activity actually falls, not in every historical month.
            DsrResponse dsr = calculateDsr(teamId, from, to, true);

            points.add(new MonthlyDsrResponse.MonthlyDsrPoint(
                    ym.toString(), // "2025-01"
                    dsr.totalEpics() > 0 ? dsr.avgDsrActual() : null,
                    dsr.totalEpics() > 0 ? dsr.avgDsrForecast() : null,
                    dsr.totalEpics(),
                    dsr.onTimeCount(),
                    dsr.onTimeRate()
            ));
        }

        return new MonthlyDsrResponse(teamId, points);
    }

    // Internal data structures
    record DatePeriod(LocalDate from, LocalDate to) {}

    record InProgressResult(int totalWorkdays, List<DatePeriod> periods) {}
}

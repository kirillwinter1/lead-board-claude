package com.leadboard.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.entity.WorkflowRoleEntity;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.EpicDsr;
import com.leadboard.metrics.dto.DsrResponse;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Delivery Speed Ratio (DSR) Service.
 *
 * DSR = (working_days - flagged_days) / estimate_days
 *
 * Interpretation:
 * - 1.0 = baseline speed
 * - < 1.0 = completed faster than estimated
 * - > 1.0 = completed slower than estimated
 *
 * Supports both completed and in-progress epics (live DSR).
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
    private final ObjectMapper objectMapper;

    public DsrService(
            JiraIssueRepository issueRepository,
            WorkCalendarService workCalendarService,
            ForecastSnapshotRepository snapshotRepository,
            WorkflowConfigService workflowConfigService,
            FlagChangelogService flagChangelogService
    ) {
        this.issueRepository = issueRepository;
        this.workCalendarService = workCalendarService;
        this.snapshotRepository = snapshotRepository;
        this.workflowConfigService = workflowConfigService;
        this.flagChangelogService = flagChangelogService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public DsrResponse calculateDsr(Long teamId, LocalDate from, LocalDate to) {
        List<JiraIssueEntity> epics = issueRepository.findEpicsForDsr(
                teamId,
                from.atStartOfDay().atOffset(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        );

        log.info("DSR: Found {} epics (completed + in-progress) for team {} between {} and {}",
                epics.size(), teamId, from, to);

        if (epics.isEmpty()) {
            return new DsrResponse(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, List.of());
        }

        // Load forecast snapshots
        LocalDate snapshotFrom = from.minusDays(180);
        List<ForecastSnapshotEntity> snapshots = snapshotRepository.findByTeamIdAndDateRange(teamId, snapshotFrom, to);
        Map<LocalDate, UnifiedPlanningResult> snapshotsByDate = parseSnapshots(snapshots);

        List<EpicDsr> epicDsrs = new ArrayList<>();
        BigDecimal totalDsrActual = BigDecimal.ZERO;
        BigDecimal totalDsrForecast = BigDecimal.ZERO;
        int dsrActualCount = 0;
        int dsrForecastCount = 0;
        int onTimeCount = 0;

        for (JiraIssueEntity epic : epics) {
            EpicDsr dsr = calculateEpicDsr(epic, snapshotsByDate);
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

    private EpicDsr calculateEpicDsr(JiraIssueEntity epic, Map<LocalDate, UnifiedPlanningResult> snapshotsByDate) {
        boolean inProgress = epic.getDoneAt() == null;

        LocalDate startDate = epic.getStartedAt() != null
                ? epic.getStartedAt().toLocalDate()
                : (epic.getJiraCreatedAt() != null ? epic.getJiraCreatedAt().toLocalDate() : null);

        if (startDate == null) return null;

        // Determine end date
        LocalDate endDate;
        if (inProgress) {
            endDate = LocalDate.now();
        } else {
            endDate = calculateEndDateFromSubtasks(epic);
        }

        int calendarWorkingDays = workCalendarService.countWorkdays(startDate, endDate);
        if (calendarWorkingDays <= 0) calendarWorkingDays = 1;

        int flaggedDays = flagChangelogService.calculateFlaggedWorkdays(epic.getIssueKey(), startDate, endDate);
        int effectiveWorkingDays = Math.max(calendarWorkingDays - flaggedDays, 1);

        // Calculate estimateDays from subtasks
        BigDecimal estimateDays = calculateEstimateDays(epic);

        // Calculate forecastDays from snapshots
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
                inProgress,
                calendarWorkingDays,
                flaggedDays,
                effectiveWorkingDays,
                estimateDays,
                forecastDays,
                dsrActual,
                dsrForecast
        );
    }

    /**
     * For completed epics, endDate = max(subtask.doneAt), fallback to epic.doneAt.
     */
    private LocalDate calculateEndDateFromSubtasks(JiraIssueEntity epic) {
        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epic.getIssueKey());
        if (!stories.isEmpty()) {
            List<String> storyKeys = stories.stream().map(JiraIssueEntity::getIssueKey).toList();
            List<JiraIssueEntity> subtasks = issueRepository.findByParentKeyIn(storyKeys);

            OffsetDateTime maxDoneAt = subtasks.stream()
                    .map(JiraIssueEntity::getDoneAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            if (maxDoneAt != null) {
                return maxDoneAt.toLocalDate();
            }
        }

        // Fallback to epic's own doneAt
        return epic.getDoneAt() != null ? epic.getDoneAt().toLocalDate() : LocalDate.now();
    }

    private BigDecimal calculateEstimateDays(JiraIssueEntity epic) {
        // 1. Try subtask estimates (Epic → Story → Subtask)
        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epic.getIssueKey());
        if (!stories.isEmpty()) {
            List<String> storyKeys = stories.stream().map(JiraIssueEntity::getIssueKey).toList();
            List<JiraIssueEntity> subtasks = issueRepository.findByParentKeyIn(storyKeys);
            long totalEstimateSeconds = subtasks.stream()
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
}

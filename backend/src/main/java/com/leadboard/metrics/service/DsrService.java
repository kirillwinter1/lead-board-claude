package com.leadboard.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leadboard.calendar.WorkCalendarService;
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
import java.time.ZoneOffset;
import java.util.*;

/**
 * Delivery Speed Ratio (DSR) Service.
 *
 * DSR = actual working days / current estimate (in days)
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
    private final ObjectMapper objectMapper;

    public DsrService(
            JiraIssueRepository issueRepository,
            WorkCalendarService workCalendarService,
            ForecastSnapshotRepository snapshotRepository
    ) {
        this.issueRepository = issueRepository;
        this.workCalendarService = workCalendarService;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public DsrResponse calculateDsr(Long teamId, LocalDate from, LocalDate to) {
        List<JiraIssueEntity> completedEpics = issueRepository.findCompletedEpicsInPeriod(
                teamId,
                from.atStartOfDay().atOffset(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        );

        log.info("DSR: Found {} completed epics for team {} between {} and {}",
                completedEpics.size(), teamId, from, to);

        if (completedEpics.isEmpty()) {
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

        for (JiraIssueEntity epic : completedEpics) {
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
        if (epic.getDoneAt() == null) return null;

        LocalDate doneDate = epic.getDoneAt().toLocalDate();
        LocalDate startDate = epic.getStartedAt() != null
                ? epic.getStartedAt().toLocalDate()
                : (epic.getJiraCreatedAt() != null ? epic.getJiraCreatedAt().toLocalDate() : null);

        if (startDate == null) return null;

        int workingDays = workCalendarService.countWorkdays(startDate, doneDate);
        if (workingDays <= 0) workingDays = 1;

        // Calculate estimateDays from subtasks
        BigDecimal estimateDays = calculateEstimateDays(epic);

        // Calculate forecastDays from snapshots
        BigDecimal forecastDays = calculateForecastDays(epic, snapshotsByDate);

        BigDecimal dsrActual = estimateDays != null && estimateDays.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(workingDays).divide(estimateDays, 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal dsrForecast = forecastDays != null && forecastDays.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(workingDays).divide(forecastDays, 2, RoundingMode.HALF_UP)
                : null;

        return new EpicDsr(
                epic.getIssueKey(),
                epic.getSummary(),
                workingDays,
                estimateDays,
                forecastDays,
                dsrActual,
                dsrForecast
        );
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

        // 2. Fallback to rough estimates on epic
        BigDecimal sa = epic.getRoughEstimateSaDays();
        BigDecimal dev = epic.getRoughEstimateDevDays();
        BigDecimal qa = epic.getRoughEstimateQaDays();
        if (sa != null || dev != null || qa != null) {
            BigDecimal total = BigDecimal.ZERO;
            if (sa != null) total = total.add(sa);
            if (dev != null) total = total.add(dev);
            if (qa != null) total = total.add(qa);
            if (total.compareTo(BigDecimal.ZERO) > 0) return total;
        }

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

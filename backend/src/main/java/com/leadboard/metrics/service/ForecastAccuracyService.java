package com.leadboard.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.ForecastAccuracyResponse;
import com.leadboard.metrics.dto.ForecastAccuracyResponse.EpicAccuracy;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating forecast accuracy metrics.
 * Compares planned completion dates (from snapshots) with actual completion dates.
 */
@Service
public class ForecastAccuracyService {

    private static final Logger log = LoggerFactory.getLogger(ForecastAccuracyService.class);

    private final ForecastSnapshotRepository snapshotRepository;
    private final JiraIssueRepository issueRepository;
    private final ObjectMapper objectMapper;

    public ForecastAccuracyService(
            ForecastSnapshotRepository snapshotRepository,
            JiraIssueRepository issueRepository
    ) {
        this.snapshotRepository = snapshotRepository;
        this.issueRepository = issueRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Calculate forecast accuracy for completed epics in a period.
     */
    public ForecastAccuracyResponse calculateAccuracy(Long teamId, LocalDate from, LocalDate to) {
        // Get all completed epics in the period
        List<JiraIssueEntity> completedEpics = issueRepository.findCompletedEpicsInPeriod(
                teamId, from.atStartOfDay().atOffset(java.time.ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC)
        );

        log.info("Found {} completed epics for team {} between {} and {}",
                completedEpics.size(), teamId, from, to);

        if (completedEpics.isEmpty()) {
            return new ForecastAccuracyResponse(
                    teamId, from, to,
                    BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                    0, 0, 0, 0,
                    List.of()
            );
        }

        // Get all snapshots for the team in the relevant period
        // We need snapshots from before the epics started to get their initial forecasts
        LocalDate snapshotFrom = from.minusDays(180); // Look back for initial forecasts
        List<ForecastSnapshotEntity> snapshots = snapshotRepository.findByTeamIdAndDateRange(
                teamId, snapshotFrom, to
        );

        // Parse snapshots into a map by date for quick lookup
        Map<LocalDate, UnifiedPlanningResult> snapshotsByDate = new LinkedHashMap<>();
        for (ForecastSnapshotEntity snapshot : snapshots) {
            try {
                UnifiedPlanningResult plan = objectMapper.readValue(
                        snapshot.getUnifiedPlanningJson(), UnifiedPlanningResult.class
                );
                snapshotsByDate.put(snapshot.getSnapshotDate(), plan);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse snapshot for date {}: {}", snapshot.getSnapshotDate(), e.getMessage());
            }
        }

        // Calculate accuracy for each epic
        List<EpicAccuracy> epicAccuracies = new ArrayList<>();
        int onTimeCount = 0;
        int lateCount = 0;
        int earlyCount = 0;
        BigDecimal totalAccuracyRatio = BigDecimal.ZERO;
        int totalScheduleVariance = 0;

        for (JiraIssueEntity epic : completedEpics) {
            EpicAccuracy accuracy = calculateEpicAccuracy(epic, snapshotsByDate);
            if (accuracy != null) {
                epicAccuracies.add(accuracy);
                totalAccuracyRatio = totalAccuracyRatio.add(accuracy.accuracyRatio());
                totalScheduleVariance += accuracy.scheduleVariance();

                switch (accuracy.status()) {
                    case "ON_TIME" -> onTimeCount++;
                    case "LATE" -> lateCount++;
                    case "EARLY" -> earlyCount++;
                }
            }
        }

        int totalCompleted = epicAccuracies.size();
        BigDecimal avgAccuracyRatio = totalCompleted > 0
                ? totalAccuracyRatio.divide(BigDecimal.valueOf(totalCompleted), 2, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        BigDecimal onTimeDeliveryRate = totalCompleted > 0
                ? BigDecimal.valueOf((onTimeCount + earlyCount) * 100.0 / totalCompleted)
                        .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgScheduleVariance = totalCompleted > 0
                ? BigDecimal.valueOf((double) totalScheduleVariance / totalCompleted)
                        .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Sort by completion date descending
        epicAccuracies.sort((a, b) -> b.actualEnd().compareTo(a.actualEnd()));

        return new ForecastAccuracyResponse(
                teamId, from, to,
                avgAccuracyRatio,
                onTimeDeliveryRate,
                avgScheduleVariance,
                totalCompleted,
                onTimeCount,
                lateCount,
                earlyCount,
                epicAccuracies
        );
    }

    /**
     * Calculate accuracy for a single epic.
     */
    private EpicAccuracy calculateEpicAccuracy(
            JiraIssueEntity epic,
            Map<LocalDate, UnifiedPlanningResult> snapshotsByDate
    ) {
        LocalDate actualEnd = epic.getDoneAt() != null
                ? epic.getDoneAt().toLocalDate()
                : null;

        if (actualEnd == null) {
            return null;
        }

        LocalDate actualStart = epic.getStartedAt() != null
                ? epic.getStartedAt().toLocalDate()
                : (epic.getJiraCreatedAt() != null ? epic.getJiraCreatedAt().toLocalDate() : null);

        if (actualStart == null) {
            return null;
        }

        // Find the first snapshot that contains this epic to get initial forecast
        LocalDate plannedStart = null;
        LocalDate plannedEnd = null;

        for (Map.Entry<LocalDate, UnifiedPlanningResult> entry : snapshotsByDate.entrySet()) {
            LocalDate snapshotDate = entry.getKey();
            UnifiedPlanningResult plan = entry.getValue();

            // Look for this epic in the snapshot
            Optional<UnifiedPlanningResult.PlannedEpic> plannedEpic = plan.epics().stream()
                    .filter(e -> e.epicKey().equals(epic.getIssueKey()))
                    .findFirst();

            if (plannedEpic.isPresent()) {
                UnifiedPlanningResult.PlannedEpic pe = plannedEpic.get();

                // Use the first snapshot where we found the epic (earliest forecast)
                if (plannedEnd == null && pe.endDate() != null) {
                    plannedStart = pe.startDate();
                    plannedEnd = pe.endDate();
                    break;
                }
            }
        }

        // If no snapshot found, try to use epic's own dates as fallback
        if (plannedEnd == null) {
            log.debug("No snapshot found for epic {}, skipping accuracy calculation", epic.getIssueKey());
            return null;
        }

        // Calculate metrics
        int plannedDays = (int) ChronoUnit.DAYS.between(
                plannedStart != null ? plannedStart : actualStart,
                plannedEnd
        );
        int actualDays = (int) ChronoUnit.DAYS.between(actualStart, actualEnd);

        // Prevent division by zero
        if (actualDays <= 0) actualDays = 1;
        if (plannedDays <= 0) plannedDays = 1;

        BigDecimal accuracyRatio = BigDecimal.valueOf(plannedDays)
                .divide(BigDecimal.valueOf(actualDays), 2, RoundingMode.HALF_UP);

        int scheduleVariance = (int) ChronoUnit.DAYS.between(plannedEnd, actualEnd);

        String status;
        if (scheduleVariance <= 0) {
            status = scheduleVariance < 0 ? "EARLY" : "ON_TIME";
        } else {
            status = "LATE";
        }

        return new EpicAccuracy(
                epic.getIssueKey(),
                epic.getSummary(),
                plannedStart,
                plannedEnd,
                actualStart,
                actualEnd,
                plannedDays,
                actualDays,
                accuracyRatio,
                scheduleVariance,
                status
        );
    }
}

package com.leadboard.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.ForecastAccuracyResponse;
import com.leadboard.metrics.dto.ForecastAccuracyResponse.EpicAccuracy;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Service for calculating forecast accuracy metrics.
 * Compares planned completion dates (from snapshots) with actual completion dates.
 * Uses working days (via WorkCalendarService) and status changelog for actual start/end.
 */
@Service
public class ForecastAccuracyService {

    private static final Logger log = LoggerFactory.getLogger(ForecastAccuracyService.class);

    private final ForecastSnapshotRepository snapshotRepository;
    private final JiraIssueRepository issueRepository;
    private final WorkCalendarService workCalendarService;
    private final StatusChangelogRepository statusChangelogRepository;
    private final WorkflowConfigService workflowConfigService;
    private final ObjectMapper objectMapper;

    public ForecastAccuracyService(
            ForecastSnapshotRepository snapshotRepository,
            JiraIssueRepository issueRepository,
            WorkCalendarService workCalendarService,
            StatusChangelogRepository statusChangelogRepository,
            WorkflowConfigService workflowConfigService
    ) {
        this.snapshotRepository = snapshotRepository;
        this.issueRepository = issueRepository;
        this.workCalendarService = workCalendarService;
        this.statusChangelogRepository = statusChangelogRepository;
        this.workflowConfigService = workflowConfigService;
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
        LocalDate snapshotFrom = from.minusDays(180);
        List<ForecastSnapshotEntity> snapshots = snapshotRepository.findByTeamIdAndDateRange(
                teamId, snapshotFrom, to
        );

        // Parse snapshots into a list of (date, plan) pairs preserving order
        List<SnapshotEntry> snapshotEntries = new ArrayList<>();
        for (ForecastSnapshotEntity snapshot : snapshots) {
            try {
                UnifiedPlanningResult plan = objectMapper.readValue(
                        snapshot.getUnifiedPlanningJson(), UnifiedPlanningResult.class
                );
                snapshotEntries.add(new SnapshotEntry(snapshot.getSnapshotDate(), plan));
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
            EpicAccuracy accuracy = calculateEpicAccuracy(epic, snapshotEntries);
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
            List<SnapshotEntry> snapshotEntries
    ) {
        // Determine actual start/end from status changelog
        List<StatusChangelogEntity> changelog = statusChangelogRepository
                .findByIssueKeyOrderByTransitionedAtAsc(epic.getIssueKey());

        LocalDate actualStart = findActualStart(changelog, epic.getIssueType());
        LocalDate actualEnd = findActualEnd(changelog, epic.getIssueType());

        // Fallback to entity dates
        if (actualEnd == null) {
            actualEnd = epic.getDoneAt() != null ? epic.getDoneAt().toLocalDate() : null;
        }
        if (actualEnd == null) {
            return null;
        }

        if (actualStart == null) {
            actualStart = epic.getStartedAt() != null
                    ? epic.getStartedAt().toLocalDate()
                    : (epic.getJiraCreatedAt() != null ? epic.getJiraCreatedAt().toLocalDate() : null);
        }
        if (actualStart == null) {
            return null;
        }

        // Find the first snapshot that contains this epic to get initial forecast
        LocalDate plannedStart = null;
        LocalDate plannedEnd = null;
        long initialEstimateHours = 0;
        long developingEstimateHours = 0;

        // Track first appearance and closest-to-developing-start snapshots
        Long firstAppearanceEstimateSeconds = null;
        Long developingEstimateSeconds = null;
        long closestDiff = Long.MAX_VALUE;

        for (SnapshotEntry entry : snapshotEntries) {
            Optional<UnifiedPlanningResult.PlannedEpic> plannedEpic = entry.plan.epics().stream()
                    .filter(e -> e.epicKey().equals(epic.getIssueKey()))
                    .findFirst();

            if (plannedEpic.isPresent()) {
                UnifiedPlanningResult.PlannedEpic pe = plannedEpic.get();

                // Use the first snapshot where we found the epic (earliest forecast)
                if (plannedEnd == null && pe.endDate() != null) {
                    plannedStart = pe.startDate();
                    plannedEnd = pe.endDate();
                }

                // Track initial estimate (first appearance)
                if (firstAppearanceEstimateSeconds == null && pe.totalEstimateSeconds() != null) {
                    firstAppearanceEstimateSeconds = pe.totalEstimateSeconds();
                }

                // Track estimate closest to actualStart (developing entry)
                if (pe.totalEstimateSeconds() != null) {
                    long diff = Math.abs(entry.date.toEpochDay() - actualStart.toEpochDay());
                    if (diff < closestDiff) {
                        closestDiff = diff;
                        developingEstimateSeconds = pe.totalEstimateSeconds();
                    }
                }
            }
        }

        if (plannedEnd == null) {
            log.debug("No snapshot found for epic {}, skipping accuracy calculation", epic.getIssueKey());
            return null;
        }

        // Convert estimates
        if (firstAppearanceEstimateSeconds != null) {
            initialEstimateHours = firstAppearanceEstimateSeconds / 3600;
        }
        if (developingEstimateSeconds != null) {
            developingEstimateHours = developingEstimateSeconds / 3600;
        }

        // Calculate metrics using working days
        LocalDate effectivePlannedStart = plannedStart != null ? plannedStart : actualStart;
        int plannedDays = workCalendarService.countWorkdays(effectivePlannedStart, plannedEnd);
        int actualDays = workCalendarService.countWorkdays(actualStart, actualEnd);

        // Prevent division by zero
        if (actualDays <= 0) actualDays = 1;
        if (plannedDays <= 0) plannedDays = 1;

        BigDecimal accuracyRatio = BigDecimal.valueOf(plannedDays)
                .divide(BigDecimal.valueOf(actualDays), 2, RoundingMode.HALF_UP);

        // Schedule variance in working days (with sign)
        int scheduleVariance;
        if (!actualEnd.isAfter(plannedEnd)) {
            // Early or on time: negative or zero
            scheduleVariance = -workCalendarService.countWorkdays(actualEnd, plannedEnd);
            if (actualEnd.isEqual(plannedEnd)) {
                scheduleVariance = 0;
            }
        } else {
            // Late: positive
            scheduleVariance = workCalendarService.countWorkdays(plannedEnd, actualEnd);
        }

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
                status,
                initialEstimateHours,
                developingEstimateHours
        );
    }

    /**
     * Find actual start date: first transition to an in-progress status.
     */
    private LocalDate findActualStart(List<StatusChangelogEntity> changelog, String issueType) {
        for (StatusChangelogEntity entry : changelog) {
            if (workflowConfigService.isInProgress(entry.getToStatus(), issueType)) {
                return entry.getTransitionedAt().toLocalDate();
            }
        }
        return null;
    }

    /**
     * Find actual end date: last transition to a done status.
     */
    private LocalDate findActualEnd(List<StatusChangelogEntity> changelog, String issueType) {
        LocalDate lastDone = null;
        for (StatusChangelogEntity entry : changelog) {
            if (workflowConfigService.isDone(entry.getToStatus(), issueType)) {
                lastDone = entry.getTransitionedAt().toLocalDate();
            }
        }
        return lastDone;
    }

    private record SnapshotEntry(LocalDate date, UnifiedPlanningResult plan) {}
}

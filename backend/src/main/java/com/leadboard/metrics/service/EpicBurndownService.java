package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.forecast.service.ForecastSnapshotService;
import com.leadboard.metrics.dto.EpicBurndownResponse;
import com.leadboard.metrics.dto.EpicBurndownResponse.BurndownPoint;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.PlannedEpic;
import com.leadboard.planning.dto.UnifiedPlanningResult.PlannedStory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;


@Service
public class EpicBurndownService {

    private static final Logger log = LoggerFactory.getLogger(EpicBurndownService.class);
    private static final int MAX_EPICS_FOR_BURNDOWN = 50;
    private static final double SECONDS_PER_DAY = 8.0 * 3600;

    private final JiraIssueRepository issueRepository;
    private final WorkCalendarService calendarService;
    private final WorkflowConfigService workflowConfigService;
    private final ForecastSnapshotService forecastSnapshotService;
    private final IssueWorklogRepository issueWorklogRepository;

    public EpicBurndownService(JiraIssueRepository issueRepository,
                                WorkCalendarService calendarService,
                                WorkflowConfigService workflowConfigService,
                                ForecastSnapshotService forecastSnapshotService,
                                IssueWorklogRepository issueWorklogRepository) {
        this.issueRepository = issueRepository;
        this.calendarService = calendarService;
        this.workflowConfigService = workflowConfigService;
        this.forecastSnapshotService = forecastSnapshotService;
        this.issueWorklogRepository = issueWorklogRepository;
    }

    /**
     * Calculate burndown chart data for an epic.
     * Plan line: from forecast snapshot (stepped burndown by story forecast end dates).
     * Actual line: from worklogs (cumulative time spent subtracted from total estimate).
     */
    public EpicBurndownResponse calculateBurndown(String epicKey) {
        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicKey));

        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epicKey).stream()
                .filter(s -> workflowConfigService.isStoryOrBug(s.getIssueType()))
                .toList();

        if (stories.isEmpty()) {
            return new EpicBurndownResponse(
                    epicKey, epic.getSummary(), null, null, 0, 0, null, List.of(), List.of());
        }

        int totalStories = stories.size();
        double totalEstimateDays = roundTo1(stories.stream()
                .mapToDouble(this::getStoryEstimateDays)
                .sum());

        if (totalEstimateDays <= 0) {
            return new EpicBurndownResponse(
                    epicKey, epic.getSummary(), null, null, totalStories, 0, null, List.of(), List.of());
        }

        // Date range
        LocalDate startDate = stories.stream()
                .map(s -> {
                    if (s.getStartedAt() != null) return s.getStartedAt().toLocalDate();
                    if (s.getJiraCreatedAt() != null) return s.getJiraCreatedAt().toLocalDate();
                    return LocalDate.now();
                })
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate endDate = epic.getDoneAt() != null
                ? epic.getDoneAt().toLocalDate()
                : LocalDate.now();

        if (endDate.isBefore(startDate)) {
            endDate = startDate;
        }

        // Build plan line from snapshot (or fallback to linear)
        Double planEstimateDays = null;
        List<BurndownPoint> planLine;

        PlannedEpic snapshotEpic = findSnapshotEpic(epic, epicKey, startDate);
        if (snapshotEpic != null) {
            double snapshotEstimate = snapshotEpic.totalEstimateSeconds() != null
                    ? snapshotEpic.totalEstimateSeconds() / SECONDS_PER_DAY
                    : totalEstimateDays;
            planEstimateDays = roundTo1(snapshotEstimate);
            planLine = buildPlanLineFromSnapshot(snapshotEpic, startDate, endDate, snapshotEstimate);
        } else {
            planLine = buildLinearPlanLine(startDate, endDate, totalEstimateDays);
        }

        // Build actual line from worklogs
        List<BurndownPoint> actualLine = buildActualLineFromWorklogs(stories, startDate, endDate, totalEstimateDays);

        return new EpicBurndownResponse(
                epicKey, epic.getSummary(), startDate, endDate, totalStories, totalEstimateDays,
                planEstimateDays, planLine, actualLine);
    }

    /**
     * Get list of epics for a team (in-progress and closed).
     */
    public List<EpicInfo> getEpicsForTeam(Long teamId) {
        return issueRepository.findEpicsForBurndown(teamId).stream()
                .limit(MAX_EPICS_FOR_BURNDOWN)
                .map(e -> new EpicInfo(
                        e.getIssueKey(),
                        e.getSummary(),
                        e.getStatus(),
                        e.getDoneAt() != null))
                .toList();
    }

    /**
     * Find PlannedEpic from the closest forecast snapshot at or before epic start date.
     */
    private PlannedEpic findSnapshotEpic(JiraIssueEntity epic, String epicKey, LocalDate startDate) {
        Long teamId = epic.getTeamId();
        if (teamId == null) return null;

        LocalDate snapshotSearchDate = epic.getStartedAt() != null
                ? epic.getStartedAt().toLocalDate()
                : startDate;

        try {
            Optional<UnifiedPlanningResult> planOpt =
                    forecastSnapshotService.getUnifiedPlanningFromClosestSnapshot(teamId, snapshotSearchDate);

            if (planOpt.isEmpty()) return null;

            return planOpt.get().epics().stream()
                    .filter(pe -> epicKey.equals(pe.epicKey()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to load snapshot for epic {}: {}", epicKey, e.getMessage());
            return null;
        }
    }

    /**
     * Build plan line from snapshot data: stepped burndown.
     * Each story's estimate is subtracted at its forecast end date.
     */
    private List<BurndownPoint> buildPlanLineFromSnapshot(PlannedEpic snapshotEpic,
                                                           LocalDate start, LocalDate end,
                                                           double snapshotEstimate) {
        // Build map: date -> total estimate to subtract on that date
        Map<LocalDate, Double> burnByDate = new TreeMap<>();

        if (snapshotEpic.stories() != null) {
            for (PlannedStory story : snapshotEpic.stories()) {
                if (story.endDate() != null && story.totalEstimateSeconds() != null
                        && story.totalEstimateSeconds() > 0) {
                    double storyDays = story.totalEstimateSeconds() / SECONDS_PER_DAY;
                    burnByDate.merge(story.endDate(), storyDays, Double::sum);
                }
            }
        }

        List<BurndownPoint> points = new ArrayList<>();
        double remaining = snapshotEstimate;
        LocalDate current = start;

        while (!current.isAfter(end)) {
            Double burned = burnByDate.get(current);
            if (burned != null) {
                remaining -= burned;
                if (remaining < 0) remaining = 0;
            }
            points.add(new BurndownPoint(current, roundTo1(remaining)));
            current = current.plusDays(1);
        }

        return points;
    }

    /**
     * Fallback: linear plan line (current behavior).
     */
    private List<BurndownPoint> buildLinearPlanLine(LocalDate start, LocalDate end, double totalEstimate) {
        List<BurndownPoint> points = new ArrayList<>();

        int workingDays = calendarService.countWorkdays(start, end);
        if (workingDays <= 0) {
            points.add(new BurndownPoint(start, totalEstimate));
            points.add(new BurndownPoint(end, 0));
            return points;
        }

        double burnPerDay = totalEstimate / workingDays;
        LocalDate current = start;
        int workingDaysSoFar = 0;

        while (!current.isAfter(end)) {
            if (calendarService.isWorkday(current)) {
                workingDaysSoFar++;
            }
            double remaining = totalEstimate - burnPerDay * workingDaysSoFar;
            if (remaining < 0) remaining = 0;
            points.add(new BurndownPoint(current, roundTo1(remaining)));
            current = current.plusDays(1);
        }

        return points;
    }

    /**
     * Build actual burndown line from worklogs.
     * remaining = totalEstimate - cumulativeTimeSpent
     */
    private List<BurndownPoint> buildActualLineFromWorklogs(List<JiraIssueEntity> stories,
                                                             LocalDate start, LocalDate end,
                                                             double totalEstimate) {
        // Collect all subtask keys for worklog lookup
        List<String> storyKeys = stories.stream().map(JiraIssueEntity::getIssueKey).toList();
        List<JiraIssueEntity> allSubtasks = issueRepository.findByParentKeyIn(storyKeys);
        List<String> subtaskKeys = allSubtasks.stream().map(JiraIssueEntity::getIssueKey).toList();

        // Also include story keys themselves (worklogs can be logged on stories directly)
        List<String> allKeys = new ArrayList<>(storyKeys);
        allKeys.addAll(subtaskKeys);

        // Get daily aggregated worklogs
        Map<LocalDate, Double> dailySpent = new TreeMap<>();
        if (!allKeys.isEmpty()) {
            List<Object[]> rows = issueWorklogRepository.findDailyTimeSpentByIssueKeys(allKeys);
            for (Object[] row : rows) {
                LocalDate date = row[0] instanceof Date
                        ? ((Date) row[0]).toLocalDate()
                        : (LocalDate) row[0];
                long seconds = ((Number) row[1]).longValue();
                dailySpent.merge(date, seconds / SECONDS_PER_DAY, Double::sum);
            }
        }

        // Account for worklogs before the start date
        List<BurndownPoint> points = new ArrayList<>();
        double cumulative = dailySpent.entrySet().stream()
                .filter(e -> e.getKey().isBefore(start))
                .mapToDouble(Map.Entry::getValue)
                .sum();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            Double spent = dailySpent.get(current);
            if (spent != null) {
                cumulative += spent;
            }
            double remaining = totalEstimate - cumulative;
            if (remaining < 0) remaining = 0;
            points.add(new BurndownPoint(current, roundTo1(remaining)));
            current = current.plusDays(1);
        }

        return points;
    }

    /**
     * Get total estimate in person-days for a story.
     * Priority: rough estimates -> own originalEstimate -> sum of subtask originalEstimates.
     */
    private double getStoryEstimateDays(JiraIssueEntity story) {
        Map<String, BigDecimal> rough = story.getRoughEstimates();
        if (rough != null && !rough.isEmpty()) {
            return rough.values().stream()
                    .filter(v -> v != null)
                    .mapToDouble(BigDecimal::doubleValue)
                    .sum();
        }
        if (story.getOriginalEstimateSeconds() != null && story.getOriginalEstimateSeconds() > 0) {
            return story.getOriginalEstimateSeconds() / SECONDS_PER_DAY;
        }
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());
        long totalSubtaskSeconds = subtasks.stream()
                .filter(st -> st.getOriginalEstimateSeconds() != null)
                .mapToLong(JiraIssueEntity::getOriginalEstimateSeconds)
                .sum();
        if (totalSubtaskSeconds > 0) {
            return totalSubtaskSeconds / SECONDS_PER_DAY;
        }
        return 0;
    }

    private static double roundTo1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record EpicInfo(String key, String summary, String status, boolean completed) {}
}

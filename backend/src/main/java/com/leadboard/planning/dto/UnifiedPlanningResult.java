package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of unified planning algorithm.
 * Contains complete schedule for all epics, stories, and phases.
 */
public record UnifiedPlanningResult(
        Long teamId,
        OffsetDateTime planningDate,
        List<PlannedEpic> epics,
        List<PlanningWarning> warnings,
        Map<String, AssigneeUtilization> assigneeUtilization
) {

    /**
     * Planned epic with all its stories.
     */
    public record PlannedEpic(
            String epicKey,
            String summary,
            BigDecimal autoScore,
            LocalDate startDate,
            LocalDate endDate,
            List<PlannedStory> stories,
            Map<String, PhaseAggregationEntry> phaseAggregation,
            // Additional fields for epic card/tooltip
            String status,
            LocalDate dueDate,
            Long totalEstimateSeconds,
            Long totalLoggedSeconds,
            Integer progressPercent,
            Map<String, PhaseProgressInfo> roleProgress,
            int storiesTotal,
            int storiesActive,
            // Rough estimate fields (for epics without stories)
            boolean isRoughEstimate,
            Map<String, BigDecimal> roughEstimates
    ) {}

    /**
     * Planned story with phase schedules.
     */
    public record PlannedStory(
            String storyKey,
            String summary,
            BigDecimal autoScore,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, PhaseSchedule> phases,
            List<String> blockedBy,
            List<PlanningWarning> warnings,
            // Additional fields for tooltip
            String issueType,
            String priority,
            Boolean flagged,
            // Aggregated progress from subtasks
            Long totalEstimateSeconds,
            Long totalLoggedSeconds,
            Integer progressPercent,
            // Phase completion status
            Map<String, PhaseProgressInfo> roleProgress
    ) {}

    /**
     * Progress info for a single phase.
     */
    public record PhaseProgressInfo(
            Long estimateSeconds,
            Long loggedSeconds,
            boolean completed
    ) {}

    /**
     * Schedule for a single phase.
     */
    public record PhaseSchedule(
            String assigneeAccountId,
            String assigneeDisplayName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal hours,
            boolean noCapacity
    ) {
        public static PhaseSchedule noCapacity(BigDecimal hours) {
            return new PhaseSchedule(null, null, null, null, hours, true);
        }
    }

    /**
     * Aggregated phase data entry for a single role.
     */
    public record PhaseAggregationEntry(
            BigDecimal hours,
            LocalDate startDate,
            LocalDate endDate
    ) {}

    /**
     * Planning warning.
     */
    public record PlanningWarning(
            String issueKey,
            WarningType type,
            String message
    ) {}

    /**
     * Warning types.
     */
    public enum WarningType {
        NO_ESTIMATE,
        NO_CAPACITY,
        CIRCULAR_DEPENDENCY,
        FLAGGED
    }

    /**
     * Assignee utilization statistics.
     */
    public record AssigneeUtilization(
            String displayName,
            String role,
            BigDecimal totalHoursAssigned,
            BigDecimal effectiveHoursPerDay,
            Map<LocalDate, BigDecimal> dailyLoad
    ) {}
}

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
            PhaseAggregation phaseAggregation,
            // Additional fields for epic card/tooltip
            String status,
            LocalDate dueDate,
            Long totalEstimateSeconds,
            Long totalLoggedSeconds,
            Integer progressPercent,
            RoleProgressInfo roleProgress,
            int storiesTotal,
            int storiesActive
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
            PlannedPhases phases,
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
            RoleProgressInfo roleProgress
    ) {}

    /**
     * Progress info per role (SA/DEV/QA).
     */
    public record RoleProgressInfo(
            PhaseProgressInfo sa,
            PhaseProgressInfo dev,
            PhaseProgressInfo qa
    ) {
        public static RoleProgressInfo empty() {
            return new RoleProgressInfo(null, null, null);
        }
    }

    /**
     * Progress info for a single phase.
     */
    public record PhaseProgressInfo(
            Long estimateSeconds,
            Long loggedSeconds,
            boolean completed
    ) {}

    /**
     * Phase schedules for a story (SA -> DEV -> QA pipeline).
     */
    public record PlannedPhases(
            PhaseSchedule sa,
            PhaseSchedule dev,
            PhaseSchedule qa
    ) {
        public static PlannedPhases empty() {
            return new PlannedPhases(null, null, null);
        }
    }

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
     * Aggregated phase data for epic.
     */
    public record PhaseAggregation(
            BigDecimal saHours,
            BigDecimal devHours,
            BigDecimal qaHours,
            LocalDate saStartDate,
            LocalDate saEndDate,
            LocalDate devStartDate,
            LocalDate devEndDate,
            LocalDate qaStartDate,
            LocalDate qaEndDate
    ) {
        public static PhaseAggregation empty() {
            return new PhaseAggregation(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null, null, null, null, null
            );
        }
    }

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

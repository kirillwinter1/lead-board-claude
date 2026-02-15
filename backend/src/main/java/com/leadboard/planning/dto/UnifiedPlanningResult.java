package com.leadboard.planning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of unified planning algorithm.
 * Contains complete schedule for all epics, stories, and phases.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonIgnoreProperties(ignoreUnknown = true)
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
            Map<String, BigDecimal> roughEstimates,
            Boolean flagged
    ) {}

    /**
     * Planned story with phase schedules.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
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
     * Backward-compatible: old snapshots stored a plain number (hours), new format is {hours, startDate, endDate}.
     */
    @JsonDeserialize(using = PhaseAggregationEntryDeserializer.class)
    public record PhaseAggregationEntry(
            BigDecimal hours,
            LocalDate startDate,
            LocalDate endDate
    ) {}

    public static class PhaseAggregationEntryDeserializer extends JsonDeserializer<PhaseAggregationEntry> {
        @Override
        public PhaseAggregationEntry deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node.isNumber()) {
                return new PhaseAggregationEntry(node.decimalValue(), null, null);
            }
            BigDecimal hours = node.has("hours") ? node.get("hours").decimalValue() : null;
            LocalDate startDate = parseDate(node.get("startDate"));
            LocalDate endDate = parseDate(node.get("endDate"));
            return new PhaseAggregationEntry(hours, startDate, endDate);
        }

        private LocalDate parseDate(JsonNode node) {
            if (node == null || node.isNull()) return null;
            if (node.isArray() && node.size() == 3) {
                return LocalDate.of(node.get(0).intValue(), node.get(1).intValue(), node.get(2).intValue());
            }
            if (node.isTextual()) {
                return LocalDate.parse(node.textValue());
            }
            return null;
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

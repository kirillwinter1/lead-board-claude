package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for story-level forecast.
 */
public record StoryForecastResponse(
        String epicKey,
        LocalDate epicStartDate,
        List<StoryScheduleDto> stories,
        Map<String, AssigneeUtilizationDto> assigneeUtilization
) {

    public record StoryScheduleDto(
            String storyKey,
            String storySummary,
            String assigneeAccountId,
            String assigneeDisplayName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal workDays,
            boolean isUnassigned,
            boolean isBlocked,
            List<String> blockingStories,
            BigDecimal autoScore,
            String status,
            Long timeSpentSeconds,
            Long originalEstimateSeconds
    ) {}

    public record AssigneeUtilizationDto(
            String displayName,
            String roleCode,
            BigDecimal workDaysAssigned,
            BigDecimal effectiveHoursPerDay
    ) {}
}

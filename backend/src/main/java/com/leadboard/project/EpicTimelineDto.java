package com.leadboard.project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record EpicTimelineDto(
        String epicKey,
        String summary,
        String status,
        String issueType,
        String teamName,
        String teamColor,
        LocalDate startDate,
        LocalDate endDate,
        Integer progressPercent,
        boolean isRoughEstimate,
        Map<String, BigDecimal> roughEstimates,
        Map<String, PhaseAggregationInfo> phaseAggregation,
        Map<String, PhaseProgressInfo> roleProgress,
        Boolean flagged
) {

    public record PhaseAggregationInfo(
            BigDecimal hours,
            LocalDate startDate,
            LocalDate endDate
    ) {}

    public record PhaseProgressInfo(
            Long estimateSeconds,
            Long loggedSeconds,
            boolean completed
    ) {}
}

package com.leadboard.metrics.dto;

import java.util.List;

public record BugMetricsResponse(
        int openBugs,
        int resolvedBugs,
        int staleBugs,
        long avgResolutionHours,
        double slaCompliancePercent,
        List<PriorityMetrics> byPriority,
        List<OpenBugDto> openBugList
) {

    public record PriorityMetrics(
            String priority,
            int openCount,
            int resolvedCount,
            long avgResolutionHours,
            Integer slaLimitHours,
            double slaCompliancePercent
    ) {}

    public record OpenBugDto(
            String issueKey,
            String summary,
            String priority,
            String status,
            long ageDays,
            long ageHours,
            boolean slaBreach,
            String jiraUrl
    ) {}
}

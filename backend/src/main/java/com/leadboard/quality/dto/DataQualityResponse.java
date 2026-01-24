package com.leadboard.quality.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Aggregated data quality report response.
 *
 * @param generatedAt When this report was generated
 * @param teamId The team ID (null if all teams)
 * @param summary Summary statistics
 * @param violations List of issues with violations
 */
public record DataQualityResponse(
        OffsetDateTime generatedAt,
        Long teamId,
        Summary summary,
        List<IssueViolations> violations
) {
    /**
     * Summary statistics for the report.
     */
    public record Summary(
            int totalIssues,
            int issuesWithErrors,
            int issuesWithWarnings,
            int issuesWithInfo,
            Map<String, Integer> byRule,
            Map<String, Integer> bySeverity
    ) {}
}

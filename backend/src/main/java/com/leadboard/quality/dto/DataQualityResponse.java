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
 * @param rules Catalog of all known rules (for building filters without scanning violations)
 */
public record DataQualityResponse(
        OffsetDateTime generatedAt,
        Long teamId,
        Summary summary,
        List<IssueViolations> violations,
        List<RuleInfo> rules
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
            Map<String, Integer> byCategory,
            Map<String, Integer> bySeverity
    ) {}

    /**
     * Catalog entry describing a single rule.
     *
     * @param name The rule code (enum name)
     * @param label Human-readable rule name
     * @param category The category code (enum name)
     * @param categoryLabel Human-readable category name
     * @param severity The default severity
     */
    public record RuleInfo(
            String name,
            String label,
            String category,
            String categoryLabel,
            String severity
    ) {}
}

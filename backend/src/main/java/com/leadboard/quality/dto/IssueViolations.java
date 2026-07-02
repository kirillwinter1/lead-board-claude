package com.leadboard.quality.dto;

import com.leadboard.quality.DataQualityViolation;

import java.util.List;

/**
 * Data quality violations for a single issue.
 *
 * @param issueKey The issue key (e.g., "PROJ-123")
 * @param issueType The issue type (Epic, Story, Bug, Sub-task)
 * @param summary The issue summary/title
 * @param status The current status
 * @param jiraUrl URL to the issue in Jira
 * @param violations List of violations found
 */
public record IssueViolations(
        String issueKey,
        String issueType,
        String summary,
        String status,
        String jiraUrl,
        List<ViolationDto> violations
) {
    /**
     * Simplified violation DTO for API response.
     *
     * @param rule The rule code (enum name)
     * @param label Human-readable rule name
     * @param category The rule's category code (enum name)
     * @param categoryLabel Human-readable category name
     * @param severity The severity level
     * @param message Human-readable message describing the violation
     */
    public record ViolationDto(
            String rule,
            String label,
            String category,
            String categoryLabel,
            String severity,
            String message
    ) {
        public static ViolationDto from(DataQualityViolation violation) {
            var rule = violation.rule();
            return new ViolationDto(
                    rule.name(),
                    rule.getLabel(),
                    rule.getCategory().name(),
                    rule.getCategory().getLabel(),
                    violation.severity().name(),
                    violation.message()
            );
        }
    }

    /**
     * Checks if this issue has any errors.
     */
    public boolean hasErrors() {
        return violations.stream().anyMatch(v -> "ERROR".equals(v.severity()));
    }

    /**
     * Checks if this issue has any warnings.
     */
    public boolean hasWarnings() {
        return violations.stream().anyMatch(v -> "WARNING".equals(v.severity()));
    }
}

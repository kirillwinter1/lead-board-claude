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
     */
    public record ViolationDto(
            String rule,
            String severity,
            String message
    ) {
        public static ViolationDto from(DataQualityViolation violation) {
            return new ViolationDto(
                    violation.rule().name(),
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

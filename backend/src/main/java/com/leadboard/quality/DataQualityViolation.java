package com.leadboard.quality;

import java.util.Map;

/**
 * Represents a data quality violation for an issue.
 *
 * @param rule The violated rule
 * @param severity The severity level
 * @param message Human-readable message describing the violation
 * @param context Additional context (e.g., related issue keys, values)
 */
public record DataQualityViolation(
        DataQualityRule rule,
        DataQualitySeverity severity,
        String message,
        Map<String, Object> context
) {
    /**
     * Creates a violation with the rule's default severity and a formatted message.
     */
    public static DataQualityViolation of(DataQualityRule rule, Object... messageArgs) {
        return new DataQualityViolation(
                rule,
                rule.getSeverity(),
                rule.formatMessage(messageArgs),
                Map.of()
        );
    }

    /**
     * Creates a violation with context.
     */
    public static DataQualityViolation of(DataQualityRule rule, Map<String, Object> context, Object... messageArgs) {
        return new DataQualityViolation(
                rule,
                rule.getSeverity(),
                rule.formatMessage(messageArgs),
                context
        );
    }

    /**
     * Checks if this violation is a blocking error.
     */
    public boolean isBlocking() {
        return severity == DataQualitySeverity.ERROR;
    }
}

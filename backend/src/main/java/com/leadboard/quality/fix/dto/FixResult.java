package com.leadboard.quality.fix.dto;

import java.util.List;

/**
 * Result of POST /api/data-quality/fix.
 *
 * @param success       whether the fix fully succeeded
 * @param message       human-readable outcome (includes partial-failure detail for group-C fixes)
 * @param updatedIssues issue keys that were changed (used to refresh the UI)
 */
public record FixResult(
        boolean success,
        String message,
        List<String> updatedIssues
) {
    public static FixResult ok(String message, List<String> updatedIssues) {
        return new FixResult(true, message, updatedIssues);
    }

    public static FixResult partial(String message, List<String> updatedIssues) {
        return new FixResult(false, message, updatedIssues);
    }
}

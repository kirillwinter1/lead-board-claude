package com.leadboard.matrix;

/**
 * A single orphan task rendered as a card in the Eisenhower matrix.
 *
 * @param estimateHours original estimate in hours (seconds / 3600.0), or null
 *                       when the task has no original estimate.
 * @param quadrant       current Eisenhower quadrant (P1/P2/P3/P4) or null when
 *                       the task has not been triaged yet.
 * @param daysInStatus   whole days in current status, or null if unknown (F79).
 * @param statusAgeLevel coloring level NORMAL/WARNING/CRITICAL (F79).
 * @param statusAgeReason tooltip for WARNING/CRITICAL, or null (F79).
 */
public record MatrixCardDto(
        String issueKey,
        String summary,
        String issueType,
        String priority,
        Double estimateHours,
        String assigneeDisplayName,
        String status,
        String quadrant,
        Integer daysInStatus,
        String statusAgeLevel,
        String statusAgeReason
) {
}

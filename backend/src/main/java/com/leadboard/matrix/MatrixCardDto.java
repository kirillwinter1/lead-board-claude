package com.leadboard.matrix;

/**
 * A single orphan task rendered as a card in the Eisenhower matrix.
 *
 * @param estimateHours original estimate in hours (seconds / 3600.0), or null
 *                       when the task has no original estimate.
 * @param quadrant       current Eisenhower quadrant (P1/P2/P3/P4) or null when
 *                       the task has not been triaged yet.
 */
public record MatrixCardDto(
        String issueKey,
        String summary,
        String issueType,
        String priority,
        Double estimateHours,
        String assigneeDisplayName,
        String status,
        String quadrant
) {
}

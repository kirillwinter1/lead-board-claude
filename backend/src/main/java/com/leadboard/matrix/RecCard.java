package com.leadboard.matrix;

/**
 * A card shown in the recommendation panel (F78). Superset of the matrix card.
 *
 * @param quadrant        P1/P2/P3/P4 for triaged stories; null for bug cards.
 * @param workflowRole    the issue's own workflow role (used to show who fixes a bug).
 * @param roleSubtaskKey  for "ready" story cards: key of the matched role subtask; else null.
 * @param roleEstimateHours hours of that role subtask; null for needsEstimation / bugs.
 * @param cumulativeHours running sum of role-subtask hours over the "ready" list; null otherwise.
 * @param fitsInIdle      whether cumulativeHours is within the role's idle budget; null otherwise.
 */
public record RecCard(
        String issueKey,
        String summary,
        String issueType,
        String priority,
        Double estimateHours,
        String assigneeDisplayName,
        String status,
        String quadrant,
        String workflowRole,
        String roleSubtaskKey,
        Double roleEstimateHours,
        Double cumulativeHours,
        Boolean fitsInIdle
) {
}

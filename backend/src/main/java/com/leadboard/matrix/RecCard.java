package com.leadboard.matrix;

/**
 * A simple card in the recommendation panel (F78), used for Zero Bug Policy bugs
 * and the "needs estimation" warning list.
 *
 * @param quadrant     P1/P2/P3/P4 for triaged stories; null for bug cards.
 * @param workflowRole the issue's own workflow role (used to show who fixes a bug); may be null.
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
        String workflowRole
) {
}

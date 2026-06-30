package com.leadboard.matrix;

import java.util.List;

/**
 * F78 recommendation API payloads (they change together, so they share a file).
 *
 * <p>A recommended story is shown once as a whole work item: a story is executed by
 * the full SA→DEV→QA pipeline, so roles are its <em>composition</em>, not separate
 * tasks. There is no per-role grouping.</p>
 */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    /** Top-level response: Zero Bug Policy + prioritised stories + the "needs estimation" warning list. */
    public record RecommendationViewDto(
            ZeroBugPolicy zeroBugPolicy,
            List<StoryRec> recommended,
            List<RecCard> needsEstimation
    ) {
    }

    /** All open orphan bugs of the team — always shown, independent of stories. */
    public record ZeroBugPolicy(
            int openBugCount,
            List<RecCard> bugs
    ) {
    }

    /** One recommended (triaged, fully-estimated) orphan story with its role composition. */
    public record StoryRec(
            String issueKey,
            String summary,
            String issueType,
            String priority,
            String status,
            String quadrant,
            List<RoleSlice> roles,
            double totalHours,
            Integer daysInStatus,
            String statusAgeLevel,
            String statusAgeReason
    ) {
    }

    /** One role's share of a story (its subtask + estimate). */
    public record RoleSlice(
            String roleCode,
            String subtaskKey,
            double hours
    ) {
    }
}

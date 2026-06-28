package com.leadboard.matrix;

import java.util.List;

/**
 * F78 recommendation API payloads (they change together, so they share a file).
 */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    /** Top-level response: Zero Bug Policy section + per-idle-role recommendations. */
    public record RecommendationViewDto(
            ZeroBugPolicy zeroBugPolicy,
            List<RoleRecommendation> roles
    ) {
    }

    /** All open orphan bugs of the team — always shown, independent of idle roles. */
    public record ZeroBugPolicy(
            int openBugCount,
            List<RecCard> bugs
    ) {
    }

    /** Recommendations for one underloaded role. */
    public record RoleRecommendation(
            String roleCode,
            double idleHours,
            List<RecCard> ready,
            List<RecCard> needsEstimation
    ) {
    }
}

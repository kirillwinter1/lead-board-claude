package com.leadboard.poker.dto;

import java.util.List;
import java.util.Map;

/**
 * Session summary (F23 rework): final poker estimates per story plus a
 * rough-vs-poker comparison by role and the resulting planning error.
 *
 * Units: story {@code finalEstimates} are in hours (frontend converts to days,
 * 1 d = 8 h). The comparison block is in days for a like-for-like comparison
 * against the epic's rough estimate (stored in days). Planning error is the sum
 * of absolute per-role deltas — both under- and over-estimates count as error.
 */
public record SessionSummaryResponse(
        Long sessionId,
        String epicKey,
        List<StoryEstimate> stories,
        double totalPokerDays,
        List<RoleComparison> comparison,
        double roughTotalDays,
        double pokerTotalDays,
        // Σ|pokerDays_role − roughDays_role|
        double errorDays,
        // errorDays / roughTotalDays * 100 (0 when there is no rough estimate)
        double errorPercent
) {
    public record StoryEstimate(
            String storyKey,
            String title,
            Map<String, Integer> finalEstimates,
            int totalHours
    ) {}

    public record RoleComparison(
            String role,
            double roughDays,
            double pokerDays,
            // pokerDays − roughDays (signed)
            double deltaDays
    ) {}
}

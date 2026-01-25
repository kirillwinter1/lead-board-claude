package com.leadboard.planning.dto;

/**
 * Breakdown времени по одной роли (SA/DEV/QA).
 */
public record RoleBreakdown(
        Long estimateSeconds,
        Long loggedSeconds
) {
    public Long remainingSeconds() {
        if (estimateSeconds == null || estimateSeconds == 0) {
            return null;
        }
        long logged = loggedSeconds != null ? loggedSeconds : 0;
        return Math.max(0, estimateSeconds - logged);
    }

    public int progressPercent() {
        if (estimateSeconds == null || estimateSeconds == 0) {
            return 0;
        }
        long logged = loggedSeconds != null ? loggedSeconds : 0;
        return (int) Math.min(100, (logged * 100) / estimateSeconds);
    }
}

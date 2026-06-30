package com.leadboard.status;

/**
 * "Days in current status" signal for one issue (F79).
 *
 * @param daysInStatus whole days the issue has been in its current status, or null if unknown.
 * @param level        coloring level: NORMAL (grey), WARNING (amber), CRITICAL (red).
 *                     Backlog (NEW) and DONE are always NORMAL.
 * @param reason       human-readable tooltip for WARNING/CRITICAL; null for NORMAL.
 */
public record StatusAge(
        Integer daysInStatus,
        String level,
        String reason
) {
    public static final String NORMAL = "NORMAL";
    public static final String WARNING = "WARNING";
    public static final String CRITICAL = "CRITICAL";

    public static StatusAge normal(Integer days) {
        return new StatusAge(days, NORMAL, null);
    }
}

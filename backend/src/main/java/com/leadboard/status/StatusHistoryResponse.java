package com.leadboard.status;

import java.util.List;

/**
 * F81 — chronological journey of an issue through statuses, for the status-age tooltip.
 * Segments are ordered from the starting status ("New") to the current one.
 */
public record StatusHistoryResponse(
        String issueKey,
        String currentStatus,
        long totalSeconds,
        List<Segment> segments
) {
    public record Segment(
            String status,
            long durationSeconds,
            boolean current
    ) {}
}

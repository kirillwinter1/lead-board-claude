package com.leadboard.metrics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response containing forecast accuracy metrics for a team.
 */
public record ForecastAccuracyResponse(
        Long teamId,
        LocalDate from,
        LocalDate to,

        // Summary metrics
        BigDecimal avgAccuracyRatio,      // Average of (planned / actual), 1.0 = perfect
        BigDecimal onTimeDeliveryRate,    // % of epics delivered on or before expected
        BigDecimal avgScheduleVariance,   // Average days early (negative) or late (positive)
        int totalCompleted,               // Total epics completed in period
        int onTimeCount,                  // Epics delivered on time
        int lateCount,                    // Epics delivered late
        int earlyCount,                   // Epics delivered early

        // Per-epic breakdown
        List<EpicAccuracy> epics
) {

    /**
     * Accuracy data for a single epic.
     */
    public record EpicAccuracy(
            String epicKey,
            String summary,
            LocalDate plannedStart,        // When work was planned to start
            LocalDate plannedEnd,          // Expected completion date (from snapshot)
            LocalDate actualStart,         // When work actually started
            LocalDate actualEnd,           // When actually completed (done_at)
            int plannedDays,               // Planned duration in days
            int actualDays,                // Actual duration in days
            BigDecimal accuracyRatio,      // planned / actual (>1 = faster, <1 = slower)
            int scheduleVariance,          // Days early (negative) or late (positive)
            String status                  // ON_TIME, EARLY, LATE
    ) {}
}

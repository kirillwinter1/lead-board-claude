package com.leadboard.metrics.dto;

import java.math.BigDecimal;

public record AssigneeMetrics(
    String accountId,
    String displayName,
    int issuesClosed,
    BigDecimal avgLeadTimeDays,
    BigDecimal avgCycleTimeDays,
    BigDecimal personalDsr,
    BigDecimal velocityPercent,
    String trend,
    int issuesClosedPrev,
    BigDecimal avgCycleTimePrev,
    boolean isOutlier
) {
    /** Backwards-compatible constructor for existing callers. */
    public AssigneeMetrics(
            String accountId, String displayName, int issuesClosed,
            BigDecimal avgLeadTimeDays, BigDecimal avgCycleTimeDays,
            BigDecimal personalDsr, BigDecimal velocityPercent, String trend) {
        this(accountId, displayName, issuesClosed, avgLeadTimeDays, avgCycleTimeDays,
                personalDsr, velocityPercent, trend, 0, null, false);
    }
}

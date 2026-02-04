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
    String trend
) {}

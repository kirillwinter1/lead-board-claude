package com.leadboard.metrics.dto;

import java.math.BigDecimal;

public record TimeInStatusResponse(
    String status,
    BigDecimal avgHours,
    BigDecimal medianHours,
    int transitionsCount
) {}

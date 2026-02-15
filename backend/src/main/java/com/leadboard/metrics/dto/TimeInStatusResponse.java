package com.leadboard.metrics.dto;

import java.math.BigDecimal;

public record TimeInStatusResponse(
    String status,
    BigDecimal avgHours,
    BigDecimal medianHours,
    BigDecimal p85Hours,
    BigDecimal p99Hours,
    int transitionsCount,
    int sortOrder,
    String color
) {}

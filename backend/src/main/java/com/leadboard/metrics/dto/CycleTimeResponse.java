package com.leadboard.metrics.dto;

import java.math.BigDecimal;

public record CycleTimeResponse(
    BigDecimal avgDays,
    BigDecimal medianDays,
    BigDecimal p90Days,
    BigDecimal minDays,
    BigDecimal maxDays,
    int sampleSize
) {}

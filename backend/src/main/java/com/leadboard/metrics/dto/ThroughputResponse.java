package com.leadboard.metrics.dto;

import java.math.BigDecimal;
import java.util.List;

public record ThroughputResponse(
    int totalEpics,
    int totalStories,
    int totalSubtasks,
    int total,
    List<PeriodThroughput> byPeriod,
    List<BigDecimal> movingAverage
) {}

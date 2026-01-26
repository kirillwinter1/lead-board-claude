package com.leadboard.metrics.dto;

import java.util.List;

public record ThroughputResponse(
    int totalEpics,
    int totalStories,
    int totalSubtasks,
    int total,
    List<PeriodThroughput> byPeriod
) {}

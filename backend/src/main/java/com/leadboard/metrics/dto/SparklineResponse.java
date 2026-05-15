package com.leadboard.metrics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SparklineResponse(
    List<SparklinePoint> throughput,
    List<SparklinePoint> cycleTimeMedian,
    List<SparklinePoint> leadTimeMedian,
    List<SparklinePoint> predictability,
    List<SparklinePoint> utilization
) {
    public record SparklinePoint(
        LocalDate period,
        BigDecimal value
    ) {}
}

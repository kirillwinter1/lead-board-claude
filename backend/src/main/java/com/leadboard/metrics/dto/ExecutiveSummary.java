package com.leadboard.metrics.dto;

import java.math.BigDecimal;

public record ExecutiveSummary(
    KpiCard throughput,
    KpiCard cycleTimeMedian,
    KpiCard leadTimeMedian,
    KpiCard predictability,
    KpiCard capacityUtilization,
    KpiCard blockedRisk
) {
    public record KpiCard(
        String label,
        String value,
        BigDecimal rawValue,
        BigDecimal prevValue,
        BigDecimal deltaPercent,
        String trend,
        int sampleSize,
        BigDecimal target
    ) {}
}

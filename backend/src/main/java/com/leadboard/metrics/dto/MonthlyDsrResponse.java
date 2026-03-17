package com.leadboard.metrics.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyDsrResponse(
    Long teamId,
    List<MonthlyDsrPoint> months
) {
    public record MonthlyDsrPoint(
        String month,                // "2025-01"
        BigDecimal avgDsrActual,     // null if no epics
        BigDecimal avgDsrForecast,   // null if no epics
        int totalEpics,
        int onTimeCount,
        BigDecimal onTimeRate
    ) {}
}

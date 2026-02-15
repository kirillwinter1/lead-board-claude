package com.leadboard.metrics.dto;

import java.math.BigDecimal;

public record EpicDsr(
        String epicKey,
        String summary,
        boolean inProgress,
        int calendarWorkingDays,
        int flaggedDays,
        int effectiveWorkingDays,
        BigDecimal estimateDays,
        BigDecimal forecastDays,
        BigDecimal dsrActual,
        BigDecimal dsrForecast
) {}

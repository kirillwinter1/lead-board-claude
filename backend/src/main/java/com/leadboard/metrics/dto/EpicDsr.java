package com.leadboard.metrics.dto;

import java.math.BigDecimal;

public record EpicDsr(
        String epicKey,
        String summary,
        int workingDaysActual,
        BigDecimal estimateDays,
        BigDecimal forecastDays,
        BigDecimal dsrActual,
        BigDecimal dsrForecast
) {}

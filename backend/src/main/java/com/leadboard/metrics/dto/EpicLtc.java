package com.leadboard.metrics.dto;

import java.math.BigDecimal;

public record EpicLtc(
        String epicKey,
        String summary,
        int workingDaysActual,
        BigDecimal estimateDays,
        BigDecimal forecastDays,
        BigDecimal ltcActual,
        BigDecimal ltcForecast
) {}

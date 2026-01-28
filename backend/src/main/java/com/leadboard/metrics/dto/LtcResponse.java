package com.leadboard.metrics.dto;

import java.math.BigDecimal;
import java.util.List;

public record LtcResponse(
        BigDecimal avgLtcActual,
        BigDecimal avgLtcForecast,
        int totalEpics,
        int onTimeCount,
        BigDecimal onTimeRate,
        List<EpicLtc> epics
) {}

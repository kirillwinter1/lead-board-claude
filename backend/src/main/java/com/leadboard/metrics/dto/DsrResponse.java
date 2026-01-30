package com.leadboard.metrics.dto;

import java.math.BigDecimal;
import java.util.List;

public record DsrResponse(
        BigDecimal avgDsrActual,
        BigDecimal avgDsrForecast,
        int totalEpics,
        int onTimeCount,
        BigDecimal onTimeRate,
        List<EpicDsr> epics
) {}

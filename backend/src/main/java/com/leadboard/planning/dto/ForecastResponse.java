package com.leadboard.planning.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ответ API прогнозирования.
 */
public record ForecastResponse(
        OffsetDateTime calculatedAt,
        Long teamId,
        TeamCapacity teamCapacity,
        List<EpicForecast> epics
) {
    /**
     * Суммарный capacity команды по ролям (часы в день).
     */
    public record TeamCapacity(
            java.math.BigDecimal saHoursPerDay,
            java.math.BigDecimal devHoursPerDay,
            java.math.BigDecimal qaHoursPerDay
    ) {}
}

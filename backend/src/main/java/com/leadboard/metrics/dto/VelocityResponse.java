package com.leadboard.metrics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VelocityResponse(
    Long teamId,
    LocalDate from,
    LocalDate to,
    BigDecimal totalCapacityHours,
    BigDecimal totalLoggedHours,
    BigDecimal utilizationPercent,
    List<WeeklyVelocity> byWeek
) {
    public record WeeklyVelocity(
        LocalDate weekStart,
        BigDecimal capacityHours,
        BigDecimal loggedHours,
        BigDecimal utilizationPercent
    ) {}
}

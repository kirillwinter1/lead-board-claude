package com.leadboard.metrics.dto;

import java.time.LocalDate;
import java.util.List;

public record EpicBurndownResponse(
    String epicKey,
    String summary,
    LocalDate startDate,
    LocalDate endDate,
    int totalEstimateHours,
    List<BurndownPoint> idealLine,
    List<BurndownPoint> actualLine
) {
    public record BurndownPoint(LocalDate date, int remainingHours) {}
}

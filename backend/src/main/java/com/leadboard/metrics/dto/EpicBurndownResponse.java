package com.leadboard.metrics.dto;

import java.time.LocalDate;
import java.util.List;

public record EpicBurndownResponse(
    String epicKey,
    String summary,
    LocalDate startDate,
    LocalDate endDate,
    int totalStories,
    double totalEstimateDays,
    Double planEstimateDays,
    List<BurndownPoint> idealLine,
    List<BurndownPoint> actualLine
) {
    public record BurndownPoint(LocalDate date, double remaining) {}
}

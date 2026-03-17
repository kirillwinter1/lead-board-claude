package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.List;

public record QuarterlyProjectOverviewDto(
        String projectKey,
        String summary,
        boolean inQuarter,
        String quarterLabel,
        BigDecimal priorityScore,
        BigDecimal riceNormalizedScore,
        Integer manualBoost,
        int epicCount,
        int roughEstimateCoverage,
        int teamMappingCoverage,
        String planningStatus,
        BigDecimal demandDays,
        String forecastLabel,
        String risk,
        List<TeamRef> teams,
        List<String> blockers,
        List<EpicOverviewDto> epics
) {
    public record TeamRef(Long id, String name, String color) {}

    public record EpicOverviewDto(
            String key,
            String summary,
            List<TeamRef> teams,
            boolean roughEstimated,
            boolean teamMapped,
            List<String> blockers
    ) {}
}

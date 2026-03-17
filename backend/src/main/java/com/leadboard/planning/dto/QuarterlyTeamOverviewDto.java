package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record QuarterlyTeamOverviewDto(
        Long teamId,
        String teamName,
        String teamColor,
        BigDecimal capacityDays,
        BigDecimal demandDays,
        BigDecimal gapDays,
        int utilization,
        Map<String, BigDecimal> capacityByRole,
        Map<String, BigDecimal> demandByRole,
        int overloadedEpics,
        String risk,
        List<ProjectRef> impactingProjects
) {
    public record ProjectRef(String key, String name, String planningStatus) {}
}

package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Epic view for the Quarterly Planning kanban board (Backlog ↔ In Quarter).
 */
public record PlanningEpicDto(
        String epicKey,
        String epicSummary,
        String iconUrl,
        String typeName,
        String projectKey,
        String projectSummary,
        String quarterLabel,
        boolean inQuarter,
        BigDecimal riceScore,
        Integer manualBoost,
        BigDecimal priorityScore,
        List<TeamRef> teams,
        Map<String, BigDecimal> demandByRole,
        BigDecimal totalDemandDays,
        boolean hasEstimate,
        boolean hasTeamMapping,
        List<Long> overloadedTeams
) {
    public record TeamRef(Long id, String name, String color) {}
}

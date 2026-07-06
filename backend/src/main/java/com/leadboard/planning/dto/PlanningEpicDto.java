package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Epic view for the Quarterly Planning kanban board (Backlog ↔ In Quarter).
 *
 * <p>{@code projectDesiredQuarter} and {@code isStandalone} were added in F70 to
 * carry the customer-driven quarter context: the parent project's desired quarter
 * (so the frontend can render a "PM wants Q2" badge when committed != desired),
 * and a flag marking epics without any parent project ("standalone" — always
 * shown in the backlog regardless of the desired-quarter filter).</p>
 */
public record PlanningEpicDto(
        String epicKey,
        String epicSummary,
        String status,
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
        // Raw rough estimates (person-days) as entered in Jira. No risk buffer —
        // the safety margin is expressed on the capacity side (UI warns at >80%).
        Map<String, BigDecimal> demandByRole,
        BigDecimal totalDemandDays,
        boolean hasEstimate,
        boolean hasTeamMapping,
        List<Long> overloadedTeams,
        String projectDesiredQuarter,
        boolean isStandalone
) {
    public record TeamRef(Long id, String name, String color) {}
}

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
        // Capacity demand: rough estimate × (1 + team risk buffer). Feeds the
        // capacity bars and utilization math — NOT for card display.
        Map<String, BigDecimal> demandByRole,
        BigDecimal totalDemandDays,
        // Raw rough estimates as entered in Jira (no risk buffer) — what the
        // cards display, consistent with the Board page.
        Map<String, BigDecimal> estimateByRole,
        BigDecimal totalEstimateDays,
        boolean hasEstimate,
        boolean hasTeamMapping,
        List<Long> overloadedTeams,
        String projectDesiredQuarter,
        boolean isStandalone
) {
    public record TeamRef(Long id, String name, String color) {}
}

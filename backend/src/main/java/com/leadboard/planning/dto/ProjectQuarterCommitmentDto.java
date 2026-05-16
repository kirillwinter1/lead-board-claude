package com.leadboard.planning.dto;

import java.util.List;

/**
 * Aggregate of a project's desired quarter ("заказчик хочет в этот квартал")
 * together with how each involved team has committed its child epics.
 *
 * <p>Used by the Projects page (F70) for the PM-facing view: lets the PM see
 * which teams accepted the desired quarter for which epics and which slipped
 * to other quarters.</p>
 */
public record ProjectQuarterCommitmentDto(
        String projectKey,
        String projectSummary,
        String desiredQuarter,
        List<TeamCommitmentDto> commitmentByTeam
) {}

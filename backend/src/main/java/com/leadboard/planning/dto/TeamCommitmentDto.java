package com.leadboard.planning.dto;

/**
 * Per-team breakdown of how a project's child epics are committed against the
 * project's desired quarter. Used inside {@link ProjectQuarterCommitmentDto}.
 *
 * <p>Counts are mutually exclusive within {@code totalEpics}: every epic of the
 * project that maps to this team falls into exactly one of
 * {@code committedEpics} / {@code otherQuarterEpics} / {@code uncommittedEpics}.</p>
 *
 * <p>{@code teamId} may be {@code 0} for the synthetic "no team mapping" bucket
 * — surfaced so the PM sees epics that have not yet been assigned to a team.
 * In that case {@code teamName} is a localised placeholder and {@code teamColor}
 * is {@code null}.</p>
 */
public record TeamCommitmentDto(
        long teamId,
        String teamName,
        String teamColor,
        int totalEpics,
        int committedEpics,
        int otherQuarterEpics,
        int uncommittedEpics
) {}

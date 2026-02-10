package com.leadboard.simulation.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SimulationLogDto(
        Long id,
        Long teamId,
        LocalDate simDate,
        boolean dryRun,
        List<SimulationAction> actions,
        SimulationSummary summary,
        String status,
        String error,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {}

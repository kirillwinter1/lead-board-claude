package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record QuarterlySummaryDto(
        String quarter,
        List<TeamQuarterlySnapshotDto> teams,
        List<String> availableQuarters
) {
    public record TeamQuarterlySnapshotDto(
            Long teamId,
            String teamName,
            String teamColor,
            Map<String, BigDecimal> capacityByRole,
            Map<String, BigDecimal> demandByRole,
            Map<String, BigDecimal> utilizationPctByRole,
            boolean overloaded
    ) {}
}

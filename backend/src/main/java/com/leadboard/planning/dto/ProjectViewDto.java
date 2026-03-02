package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProjectViewDto(
        String projectKey,
        String summary,
        BigDecimal priorityScore,
        Integer manualBoost,
        String quarter,
        List<TeamAllocationDto> teams
) {
    public record TeamAllocationDto(
            Long teamId,
            String teamName,
            String teamColor,
            List<EpicDemandDto> epics,
            Map<String, BigDecimal> teamCapacity,
            Map<String, BigDecimal> projectDemand,
            boolean overloaded
    ) {}
}

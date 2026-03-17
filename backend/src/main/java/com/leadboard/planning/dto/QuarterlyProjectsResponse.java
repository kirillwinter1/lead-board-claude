package com.leadboard.planning.dto;

import java.util.List;

public record QuarterlyProjectsResponse(
        String quarter,
        int inQuarterCount,
        int readyCount,
        int blockedCount,
        int partialCount,
        int teamsInvolved,
        int totalEpics,
        int roughCoveragePct,
        List<QuarterlyProjectOverviewDto> projects
) {}

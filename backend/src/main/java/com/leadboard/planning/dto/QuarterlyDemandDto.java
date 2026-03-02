package com.leadboard.planning.dto;

import java.util.List;

public record QuarterlyDemandDto(
        Long teamId,
        String teamName,
        String quarter,
        QuarterlyCapacityDto capacity,
        List<ProjectDemandDto> projects,
        List<EpicDemandDto> unassignedEpics
) {}

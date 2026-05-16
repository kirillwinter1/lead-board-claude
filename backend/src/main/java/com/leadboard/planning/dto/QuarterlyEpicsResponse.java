package com.leadboard.planning.dto;

import java.util.List;

public record QuarterlyEpicsResponse(
        String quarter,
        List<PlanningEpicDto> epics
) {}

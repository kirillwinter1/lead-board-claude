package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProjectDemandDto(
        String projectKey,
        String summary,
        String status,
        BigDecimal priorityScore,
        BigDecimal riceNormalizedScore,
        Integer manualBoost,
        Map<String, BigDecimal> totalDemandByRole,
        List<EpicDemandDto> epics,
        boolean fitsInCapacity
) {}

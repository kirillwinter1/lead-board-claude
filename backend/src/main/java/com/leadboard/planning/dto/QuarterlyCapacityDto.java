package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.Map;

public record QuarterlyCapacityDto(
        Long teamId,
        String teamName,
        String teamColor,
        String quarter,
        Map<String, BigDecimal> capacityByRole,
        int totalWorkdays,
        int absenceDays
) {}

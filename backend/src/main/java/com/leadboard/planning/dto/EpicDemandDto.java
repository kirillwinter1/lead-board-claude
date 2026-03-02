package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.Map;

public record EpicDemandDto(
        String epicKey,
        String summary,
        String status,
        Integer manualOrder,
        Map<String, BigDecimal> demandByRole,
        boolean overCapacity,
        String quarterLabel
) {}

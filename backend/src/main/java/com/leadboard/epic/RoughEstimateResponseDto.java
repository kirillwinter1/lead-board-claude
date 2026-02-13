package com.leadboard.epic;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record RoughEstimateResponseDto(
        String epicKey,
        String role,
        BigDecimal updatedDays,
        Map<String, BigDecimal> roughEstimates,
        OffsetDateTime roughEstimateUpdatedAt,
        String roughEstimateUpdatedBy
) {}

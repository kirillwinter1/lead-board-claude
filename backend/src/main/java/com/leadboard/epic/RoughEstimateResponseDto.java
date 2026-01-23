package com.leadboard.epic;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RoughEstimateResponseDto(
        String epicKey,
        String role,
        BigDecimal updatedDays,
        BigDecimal saDays,
        BigDecimal devDays,
        BigDecimal qaDays,
        OffsetDateTime roughEstimateUpdatedAt,
        String roughEstimateUpdatedBy
) {}

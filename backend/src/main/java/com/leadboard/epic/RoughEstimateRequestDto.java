package com.leadboard.epic;

import java.math.BigDecimal;

public record RoughEstimateRequestDto(
        BigDecimal days,
        String updatedBy
) {}

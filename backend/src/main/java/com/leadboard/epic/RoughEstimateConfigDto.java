package com.leadboard.epic;

import java.math.BigDecimal;
import java.util.List;

public record RoughEstimateConfigDto(
        boolean enabled,
        List<String> allowedEpicStatuses,
        BigDecimal stepDays,
        BigDecimal minDays,
        BigDecimal maxDays
) {}

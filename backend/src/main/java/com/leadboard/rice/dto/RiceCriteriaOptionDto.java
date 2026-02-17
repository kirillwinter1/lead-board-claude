package com.leadboard.rice.dto;

import java.math.BigDecimal;

public record RiceCriteriaOptionDto(
        Long id,
        String label,
        String description,
        BigDecimal score,
        int sortOrder
) {}

package com.leadboard.rice.dto;

import java.math.BigDecimal;

public record RiceTemplateListDto(
        Long id,
        String name,
        String code,
        BigDecimal strategicWeight,
        boolean active,
        int criteriaCount
) {}

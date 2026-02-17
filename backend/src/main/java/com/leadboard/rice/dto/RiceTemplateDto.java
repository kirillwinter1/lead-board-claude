package com.leadboard.rice.dto;

import java.math.BigDecimal;
import java.util.List;

public record RiceTemplateDto(
        Long id,
        String name,
        String code,
        BigDecimal strategicWeight,
        boolean active,
        List<RiceCriteriaDto> criteria
) {}

package com.leadboard.rice.dto;

import java.math.BigDecimal;

public record RiceTemplateUpdateRequest(
        String name,
        String code,
        BigDecimal strategicWeight,
        Boolean active
) {}

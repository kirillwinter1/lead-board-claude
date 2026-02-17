package com.leadboard.rice.dto;

import java.math.BigDecimal;

public record RiceRankingEntryDto(
        String issueKey,
        String summary,
        String status,
        String templateName,
        BigDecimal riceScore,
        BigDecimal normalizedScore,
        BigDecimal reach,
        BigDecimal impact,
        BigDecimal confidence,
        BigDecimal effort
) {}

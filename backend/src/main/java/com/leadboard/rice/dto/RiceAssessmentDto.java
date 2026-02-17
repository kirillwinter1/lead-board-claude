package com.leadboard.rice.dto;

import java.math.BigDecimal;
import java.util.List;

public record RiceAssessmentDto(
        Long id,
        String issueKey,
        Long templateId,
        String templateName,
        String assessedByName,
        BigDecimal totalReach,
        BigDecimal totalImpact,
        BigDecimal confidence,
        String effortManual,
        BigDecimal effortAuto,
        BigDecimal effectiveEffort,
        BigDecimal riceScore,
        BigDecimal normalizedScore,
        List<AnswerDto> answers
) {
    public record AnswerDto(
            Long criteriaId,
            String criteriaName,
            String parameter,
            List<Long> selectedOptionIds
    ) {}
}

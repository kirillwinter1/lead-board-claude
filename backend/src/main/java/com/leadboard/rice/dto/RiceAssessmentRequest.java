package com.leadboard.rice.dto;

import java.util.List;

public record RiceAssessmentRequest(
        String issueKey,
        Long templateId,
        String effortManual,
        List<AnswerEntry> answers
) {
    public record AnswerEntry(
            Long criteriaId,
            List<Long> optionIds
    ) {}
}

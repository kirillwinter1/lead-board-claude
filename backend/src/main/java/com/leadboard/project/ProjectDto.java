package com.leadboard.project;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectDto(
        String issueKey,
        String summary,
        String status,
        String assigneeDisplayName,
        int childEpicCount,
        int completedEpicCount,
        int progressPercent,
        LocalDate expectedDone,
        BigDecimal riceScore,
        BigDecimal riceNormalizedScore
) {}

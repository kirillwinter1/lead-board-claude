package com.leadboard.project;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectDto(
        String issueKey,
        String issueType,
        String summary,
        String description,
        String status,
        String assigneeDisplayName,
        String assigneeAvatarUrl,
        int childEpicCount,
        int completedEpicCount,
        int progressPercent,
        Long totalEstimateSeconds,
        Long totalLoggedSeconds,
        LocalDate expectedDone,
        BigDecimal riceScore,
        BigDecimal riceNormalizedScore
) {}

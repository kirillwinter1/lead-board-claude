package com.leadboard.project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectDetailDto(
        String issueKey,
        String summary,
        String description,
        String status,
        String assigneeDisplayName,
        String assigneeAvatarUrl,
        int completedEpicCount,
        int progressPercent,
        Long totalEstimateSeconds,
        Long totalLoggedSeconds,
        LocalDate expectedDone,
        BigDecimal riceScore,
        BigDecimal riceNormalizedScore,
        List<ChildEpicDto> epics
) {}

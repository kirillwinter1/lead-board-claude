package com.leadboard.project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectDetailDto(
        String issueKey,
        String summary,
        String status,
        String assigneeDisplayName,
        int completedEpicCount,
        int progressPercent,
        LocalDate expectedDone,
        BigDecimal riceScore,
        BigDecimal riceNormalizedScore,
        List<ChildEpicDto> epics
) {}

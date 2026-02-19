package com.leadboard.project;

import java.math.BigDecimal;
import java.util.List;

public record ProjectTimelineDto(
        String issueKey,
        String summary,
        String status,
        String issueType,
        int progressPercent,
        BigDecimal riceNormalizedScore,
        String assigneeDisplayName,
        List<EpicTimelineDto> epics
) {}

package com.leadboard.project;

import java.math.BigDecimal;
import java.util.List;

public record ProjectTimelineDto(
        String issueKey,
        String summary,
        String status,
        int progressPercent,
        BigDecimal riceNormalizedScore,
        List<EpicTimelineDto> epics
) {}

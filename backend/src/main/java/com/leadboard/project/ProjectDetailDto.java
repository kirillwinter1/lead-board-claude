package com.leadboard.project;

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
        List<ChildEpicDto> epics
) {}

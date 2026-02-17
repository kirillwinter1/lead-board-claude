package com.leadboard.project;

import java.time.LocalDate;

public record ProjectDto(
        String issueKey,
        String summary,
        String status,
        String assigneeDisplayName,
        int childEpicCount,
        int completedEpicCount,
        int progressPercent,
        LocalDate expectedDone
) {}

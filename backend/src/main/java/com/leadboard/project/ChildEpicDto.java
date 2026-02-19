package com.leadboard.project;

import java.time.LocalDate;

public record ChildEpicDto(
        String issueKey,
        String issueType,
        String summary,
        String status,
        String teamName,
        String teamColor,
        Long estimateSeconds,
        Long loggedSeconds,
        Integer progressPercent,
        LocalDate expectedDone,
        LocalDate dueDate,
        Integer delayDays
) {}

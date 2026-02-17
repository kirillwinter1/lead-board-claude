package com.leadboard.project;

import java.time.LocalDate;

public record ChildEpicDto(
        String issueKey,
        String summary,
        String status,
        String teamName,
        Long estimateSeconds,
        Long loggedSeconds,
        Integer progressPercent,
        LocalDate expectedDone,
        LocalDate dueDate
) {}

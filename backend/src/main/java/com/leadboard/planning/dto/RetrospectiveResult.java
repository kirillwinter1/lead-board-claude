package com.leadboard.planning.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of retrospective timeline calculation.
 * Shows how stories actually passed through phases based on status changelog.
 */
public record RetrospectiveResult(
        Long teamId,
        OffsetDateTime calculatedAt,
        List<RetroEpic> epics
) {

    public record RetroEpic(
            String epicKey,
            String summary,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Integer progressPercent,
            List<RetroStory> stories
    ) {}

    public record RetroStory(
            String storyKey,
            String summary,
            String status,
            boolean completed,
            LocalDate startDate,
            LocalDate endDate,
            Integer progressPercent,
            Map<String, RetroPhase> phases
    ) {}

    public record RetroPhase(
            String roleCode,
            LocalDate startDate,
            LocalDate endDate,
            long durationDays,
            boolean active
    ) {}
}

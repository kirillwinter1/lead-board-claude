package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTO для информации о сторе (child issue эпика).
 */
public record StoryInfo(
        String storyKey,
        String summary,
        String status,
        String issueType,
        String assignee,
        LocalDate startDate,
        LocalDate endDate,
        Long estimateSeconds,
        Long timeSpentSeconds,
        String phase,
        Map<String, RoleBreakdown> roleBreakdowns,
        BigDecimal autoScore
) {
    /**
     * Процент выполнения сторя.
     */
    public int progressPercent() {
        if (estimateSeconds == null || estimateSeconds == 0) {
            return 0;
        }
        if (timeSpentSeconds == null) {
            return 0;
        }
        int percent = (int) ((timeSpentSeconds * 100) / estimateSeconds);
        return Math.min(100, percent);
    }
}

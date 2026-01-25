package com.leadboard.planning.dto;

import java.time.LocalDate;

/**
 * DTO для информации о сторе (child issue эпика).
 */
public record StoryInfo(
        String storyKey,
        String summary,
        String status,
        String issueType,
        String assignee,
        LocalDate startDate,          // Когда перешла в In Progress
        LocalDate endDate,            // Дата завершения или расчётная дата
        Long estimateSeconds,
        Long timeSpentSeconds,
        String phase,                  // SA, DEV, QA (по label/component или статусу)
        RoleBreakdown saBreakdown,    // Breakdown по SA
        RoleBreakdown devBreakdown,   // Breakdown по DEV
        RoleBreakdown qaBreakdown     // Breakdown по QA
) {
    /**
     * Определяет фазу сторя по статусу или типу задачи.
     */
    public static String determinePhase(String status, String issueType) {
        String statusLower = status != null ? status.toLowerCase() : "";
        String typeLower = issueType != null ? issueType.toLowerCase() : "";

        // SA фаза: анализ, аналитика
        if (statusLower.contains("analysis") || statusLower.contains("анализ") || statusLower.contains("аналитик") ||
            typeLower.contains("analysis") || typeLower.contains("анализ") || typeLower.contains("аналитик")) {
            return "SA";
        }

        // QA фаза: тест, тестирование, qa
        if (statusLower.contains("test") || statusLower.contains("qa") ||
            statusLower.contains("тест") || statusLower.contains("review") ||
            typeLower.contains("test") || typeLower.contains("qa") || typeLower.contains("тест")) {
            return "QA";
        }

        // По умолчанию DEV
        return "DEV";
    }

    /**
     * Определяет роль subtask по типу задачи.
     */
    public static String determineRole(String issueType) {
        if (issueType == null) {
            return "DEV";
        }
        String typeLower = issueType.toLowerCase();

        if (typeLower.contains("аналитик") || typeLower.contains("analysis") || typeLower.contains("analytics")) {
            return "SA";
        }
        if (typeLower.contains("тест") || typeLower.contains("test") || typeLower.contains("qa")) {
            return "QA";
        }
        return "DEV";
    }

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

    /**
     * Статус сторя в упрощённом виде.
     */
    public String statusCategory() {
        if (status == null) {
            return "TO_DO";
        }
        String statusLower = status.toLowerCase();

        if (statusLower.contains("done") || statusLower.contains("closed") ||
            statusLower.contains("resolved") || statusLower.contains("завершен") ||
            statusLower.contains("готов") || statusLower.contains("выполнен")) {
            return "DONE";
        }
        if (statusLower.contains("progress") || statusLower.contains("work") ||
            statusLower.contains("review") || statusLower.contains("test") ||
            statusLower.contains("в работе") || statusLower.contains("ревью")) {
            return "IN_PROGRESS";
        }
        return "TO_DO";
    }
}

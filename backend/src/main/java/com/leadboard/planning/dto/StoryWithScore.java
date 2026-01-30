package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record StoryWithScore(
        String storyKey,
        String summary,
        String issueType,
        String status,
        String priority,
        Boolean flagged,
        BigDecimal autoScore,
        List<String> blockedBy,
        List<String> blocks,
        Boolean canStart,
        Long estimateSeconds,
        Long timeSpentSeconds,
        Double progress,
        Map<String, BigDecimal> scoreBreakdown,
        List<SubtaskInfo> subtasks
) {
    public record SubtaskInfo(
            String subtaskKey,
            String summary,
            String issueType,
            String status,
            String assignee,
            Long estimateSeconds,
            Long timeSpentSeconds
    ) {}
}

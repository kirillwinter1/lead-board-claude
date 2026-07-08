package com.leadboard.poker.dto;

import java.util.List;
import java.util.Map;

/**
 * Result of publishing a poker session's final estimates to Jira (F23 rework).
 * One {@link StoryResult} per COMPLETED story that carried final estimates.
 */
public record PublishResultResponse(
        Long sessionId,
        List<StoryResult> stories
) {
    public record StoryResult(
            Long storyId,
            String storyKey,
            String title,
            // "ok" | "error"
            String status,
            // human-readable outcome or error text (never a stack trace)
            String message,
            // role code -> subtask key that received the Original Estimate
            Map<String, String> subtaskKeys
    ) {}
}

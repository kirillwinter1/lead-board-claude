package com.leadboard.poker.dto;

public record EpicStoryResponse(
    String storyKey,
    String summary,
    String status,
    boolean hasSaSubtask,
    boolean hasDevSubtask,
    boolean hasQaSubtask,
    Integer saEstimate,
    Integer devEstimate,
    Integer qaEstimate
) {
    // Constructor for backward compatibility
    public EpicStoryResponse(String storyKey, String summary, String status) {
        this(storyKey, summary, status, true, true, true, null, null, null);
    }
}

package com.leadboard.poker.dto;

public record SetFinalRequest(
    Long storyId,
    Integer saHours,
    Integer devHours,
    Integer qaHours
) {}

package com.leadboard.project;

public record ProjectRecommendation(
        RecommendationType type,
        String severity,
        String message,
        String epicKey,
        String teamName,
        Integer delayDays
) {}

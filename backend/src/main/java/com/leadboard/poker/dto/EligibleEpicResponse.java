package com.leadboard.poker.dto;

public record EligibleEpicResponse(
    String epicKey,
    String summary,
    String status,
    boolean hasPokerSession
) {}

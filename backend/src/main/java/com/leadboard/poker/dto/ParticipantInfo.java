package com.leadboard.poker.dto;

public record ParticipantInfo(
    String accountId,
    String displayName,
    String role, // SA, DEV, QA
    boolean isFacilitator,
    boolean isOnline
) {}

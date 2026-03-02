package com.leadboard.chat.dto;

public record ChatMessageRequest(
    String message,
    String sessionId,
    String currentPage
) {}

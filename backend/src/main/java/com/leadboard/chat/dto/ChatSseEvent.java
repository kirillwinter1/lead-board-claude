package com.leadboard.chat.dto;

public record ChatSseEvent(
    String type,
    String content,
    String sessionId
) {
    public static ChatSseEvent text(String content, String sessionId) {
        return new ChatSseEvent("text", content, sessionId);
    }

    public static ChatSseEvent toolCall(String toolName, String sessionId) {
        return new ChatSseEvent("tool_call", toolName, sessionId);
    }

    public static ChatSseEvent done(String sessionId) {
        return new ChatSseEvent("done", null, sessionId);
    }

    public static ChatSseEvent error(String message, String sessionId) {
        return new ChatSseEvent("error", message, sessionId);
    }
}

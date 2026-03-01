package com.leadboard.chat.llm;

import java.util.List;

public record LlmResponse(
    String content,
    List<LlmToolCall> toolCalls,
    String finishReason
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}

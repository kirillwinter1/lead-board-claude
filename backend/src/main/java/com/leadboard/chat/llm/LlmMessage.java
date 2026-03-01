package com.leadboard.chat.llm;

import java.util.List;

public record LlmMessage(
    String role,
    String content,
    List<LlmToolCall> toolCalls,
    String toolCallId,
    String name
) {
    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, null, null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, null, null, null);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content, null, null, null);
    }

    public static LlmMessage assistantWithToolCalls(List<LlmToolCall> toolCalls) {
        return new LlmMessage("assistant", null, toolCalls, null, null);
    }

    public static LlmMessage toolResult(String toolCallId, String name, String content) {
        return new LlmMessage("tool", content, null, toolCallId, name);
    }
}

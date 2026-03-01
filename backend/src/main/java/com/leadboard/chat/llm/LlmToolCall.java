package com.leadboard.chat.llm;

public record LlmToolCall(
    String id,
    String functionName,
    String argumentsJson
) {}

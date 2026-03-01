package com.leadboard.chat.llm;

import java.util.Map;

public record LlmToolDefinition(
    String name,
    String description,
    Map<String, Object> parameters
) {}

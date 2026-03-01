package com.leadboard.chat.llm;

import reactor.core.publisher.Flux;

import java.util.List;

public interface LlmClient {

    LlmResponse chat(List<LlmMessage> messages, List<LlmToolDefinition> tools);

    Flux<String> streamChat(List<LlmMessage> messages);
}

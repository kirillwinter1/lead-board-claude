package com.leadboard.chat.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leadboard.chat.ChatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal LLM client for any OpenAI-compatible API.
 * Works with: OpenRouter, Groq, OpenAI, Together, etc.
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final ChatProperties chatProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(ChatProperties chatProperties, ObjectMapper objectMapper) {
        this.chatProperties = chatProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(chatProperties.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public LlmResponse chat(List<LlmMessage> messages, List<LlmToolDefinition> tools) {
        ObjectNode requestBody = buildRequestBody(messages, false);

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = objectMapper.createArrayNode();
            for (LlmToolDefinition tool : tools) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("type", "function");
                ObjectNode functionNode = objectMapper.createObjectNode();
                functionNode.put("name", tool.name());
                functionNode.put("description", tool.description());
                functionNode.set("parameters", objectMapper.valueToTree(tool.parameters()));
                toolNode.set("function", functionNode);
                toolsArray.add(toolNode);
            }
            requestBody.set("tools", toolsArray);
        }

        String responseBody = webClient.post()
                .uri("/chat/completions")
                .headers(h -> addAuthHeaders(h))
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(chatProperties.getTimeoutSeconds()))
                .block();

        return parseResponse(responseBody);
    }

    @Override
    public Flux<String> streamChat(List<LlmMessage> messages) {
        ObjectNode requestBody = buildRequestBody(messages, true);

        return webClient.post()
                .uri("/chat/completions")
                .headers(h -> addAuthHeaders(h))
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(chatProperties.getTimeoutSeconds()))
                .filter(line -> !line.isBlank() && !line.equals("[DONE]"))
                .map(line -> {
                    String data = line;
                    if (data.startsWith("data: ")) {
                        data = data.substring(6);
                    }
                    if (data.equals("[DONE]")) {
                        return "";
                    }
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode choices = node.get("choices");
                        if (choices != null && choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                return delta.get("content").asText("");
                            }
                        }
                    } catch (JsonProcessingException e) {
                        log.debug("Failed to parse SSE chunk: {}", data);
                    }
                    return "";
                })
                .filter(s -> !s.isEmpty());
    }

    private void addAuthHeaders(org.springframework.http.HttpHeaders headers) {
        headers.set("Authorization", "Bearer " + chatProperties.getApiKey());

        // OpenRouter-specific headers for attribution
        if ("openrouter".equals(chatProperties.getProvider())) {
            headers.set("HTTP-Referer", "https://leadboard.app");
            headers.set("X-Title", "LeadBoard");
        }
    }

    private ObjectNode buildRequestBody(List<LlmMessage> messages, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", chatProperties.getModel());
        body.put("stream", stream);
        body.put("temperature", 0.3);
        body.put("max_tokens", 2048);

        ArrayNode messagesArray = objectMapper.createArrayNode();
        for (LlmMessage msg : messages) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.role());

            if (msg.content() != null) {
                msgNode.put("content", msg.content());
            }

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                ArrayNode tcArray = objectMapper.createArrayNode();
                for (LlmToolCall tc : msg.toolCalls()) {
                    ObjectNode tcNode = objectMapper.createObjectNode();
                    tcNode.put("id", tc.id());
                    tcNode.put("type", "function");
                    ObjectNode fnNode = objectMapper.createObjectNode();
                    fnNode.put("name", tc.functionName());
                    fnNode.put("arguments", tc.argumentsJson());
                    tcNode.set("function", fnNode);
                    tcArray.add(tcNode);
                }
                msgNode.set("tool_calls", tcArray);
            }

            if (msg.toolCallId() != null) {
                msgNode.put("tool_call_id", msg.toolCallId());
            }

            if (msg.name() != null) {
                msgNode.put("name", msg.name());
            }

            messagesArray.add(msgNode);
        }
        body.set("messages", messagesArray);

        return body;
    }

    private LlmResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return new LlmResponse("No response from LLM", null, "stop");
            }

            JsonNode choice = choices.get(0);
            String finishReason = choice.has("finish_reason") ? choice.get("finish_reason").asText("stop") : "stop";
            JsonNode message = choice.get("message");

            String content = null;
            if (message.has("content") && !message.get("content").isNull()) {
                content = message.get("content").asText();
            }

            List<LlmToolCall> toolCalls = null;
            if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
                toolCalls = new ArrayList<>();
                for (JsonNode tc : message.get("tool_calls")) {
                    String id = tc.get("id").asText();
                    JsonNode fn = tc.get("function");
                    String functionName = fn.get("name").asText();
                    String argumentsJson = fn.get("arguments").asText();
                    toolCalls.add(new LlmToolCall(id, functionName, argumentsJson));
                }
            }

            return new LlmResponse(content, toolCalls, finishReason);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM response: {}", responseBody, e);
            return new LlmResponse("Error parsing LLM response", null, "error");
        }
    }
}

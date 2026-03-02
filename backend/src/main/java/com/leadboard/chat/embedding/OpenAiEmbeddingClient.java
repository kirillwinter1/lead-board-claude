package com.leadboard.chat.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leadboard.chat.ChatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "chat.embedding-enabled", havingValue = "true")
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

    private final ChatProperties chatProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiEmbeddingClient(ChatProperties chatProperties, ObjectMapper objectMapper) {
        this.chatProperties = chatProperties;
        this.objectMapper = objectMapper;

        String baseUrl = chatProperties.getEmbeddingBaseUrl() != null
                && !chatProperties.getEmbeddingBaseUrl().isBlank()
                ? chatProperties.getEmbeddingBaseUrl()
                : chatProperties.getBaseUrl();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public float[] generateEmbedding(String text) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", chatProperties.getEmbeddingModel());
            requestBody.put("input", text);

            String responseBody = webClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + chatProperties.getApiKey())
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(chatProperties.getTimeoutSeconds()))
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                log.error("Empty embedding response");
                return null;
            }

            JsonNode embeddingNode = data.get(0).get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                log.error("No embedding array in response");
                return null;
            }

            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }
            return embedding;
        } catch (Exception e) {
            log.error("Failed to generate embedding: {}", e.getMessage());
            return null;
        }
    }
}

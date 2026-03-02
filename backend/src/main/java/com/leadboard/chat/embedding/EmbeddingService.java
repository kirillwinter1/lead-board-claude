package com.leadboard.chat.embedding;

import com.leadboard.chat.ChatProperties;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final ChatProperties chatProperties;
    private final JiraIssueRepository issueRepository;

    @Autowired(required = false)
    private EmbeddingClient embeddingClient;

    public EmbeddingService(ChatProperties chatProperties, JiraIssueRepository issueRepository) {
        this.chatProperties = chatProperties;
        this.issueRepository = issueRepository;
    }

    @Async
    public void generateAndStoreAsync(JiraIssueEntity entity) {
        generateAndStore(entity);
    }

    @Transactional
    public void generateAndStore(JiraIssueEntity entity) {
        if (!chatProperties.isEmbeddingEnabled() || embeddingClient == null) {
            return;
        }

        String text = buildText(entity);
        if (text.isBlank()) {
            return;
        }

        try {
            float[] embedding = embeddingClient.generateEmbedding(text);
            if (embedding != null) {
                String vectorString = toVectorString(embedding);
                issueRepository.updateEmbedding(entity.getId(), vectorString);
                log.debug("Stored embedding for {}", entity.getIssueKey());
            }
        } catch (Exception e) {
            log.warn("Failed to generate embedding for {}: {}", entity.getIssueKey(), e.getMessage());
        }
    }

    public List<JiraIssueEntity> search(String query, Long teamId, int limit) {
        if (!chatProperties.isEmbeddingEnabled() || embeddingClient == null) {
            return Collections.emptyList();
        }

        try {
            float[] queryEmbedding = embeddingClient.generateEmbedding(query);
            if (queryEmbedding == null) {
                return Collections.emptyList();
            }

            String vectorString = toVectorString(queryEmbedding);

            if (teamId != null) {
                return issueRepository.findByEmbeddingSimilarityAndTeamId(vectorString, teamId, limit);
            }
            return issueRepository.findByEmbeddingSimilarity(vectorString, limit);
        } catch (Exception e) {
            log.warn("Embedding search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public int reindexAll() {
        if (!chatProperties.isEmbeddingEnabled() || embeddingClient == null) {
            return 0;
        }

        List<JiraIssueEntity> issues = issueRepository.findWithoutEmbedding();
        log.info("Reindexing {} issues without embeddings", issues.size());

        for (JiraIssueEntity issue : issues) {
            generateAndStore(issue);
        }

        log.info("Reindexing complete");
        return issues.size();
    }

    private String buildText(JiraIssueEntity entity) {
        StringBuilder sb = new StringBuilder();
        if (entity.getSummary() != null) {
            sb.append(entity.getSummary());
        }
        if (entity.getDescription() != null) {
            sb.append(" ").append(entity.getDescription());
        }
        return sb.toString().trim();
    }

    String toVectorString(float[] embedding) {
        return "[" + Arrays.stream(toStringArray(embedding))
                .collect(Collectors.joining(",")) + "]";
    }

    private String[] toStringArray(float[] arr) {
        String[] result = new String[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = String.valueOf(arr[i]);
        }
        return result;
    }
}

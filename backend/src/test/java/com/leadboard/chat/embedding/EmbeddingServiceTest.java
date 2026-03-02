package com.leadboard.chat.embedding;

import com.leadboard.chat.ChatProperties;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock private JiraIssueRepository issueRepository;
    @Mock private EmbeddingClient embeddingClient;

    private ChatProperties chatProperties;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() throws Exception {
        chatProperties = new ChatProperties();
        embeddingService = new EmbeddingService(chatProperties, issueRepository);
    }

    private void injectEmbeddingClient(EmbeddingClient client) throws Exception {
        Field field = EmbeddingService.class.getDeclaredField("embeddingClient");
        field.setAccessible(true);
        field.set(embeddingService, client);
    }

    @Test
    @DisplayName("disabled: generateAndStore does nothing when embedding is disabled")
    void disabled_doesNothing() {
        chatProperties.setEmbeddingEnabled(false);

        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setId(1L);
        entity.setSummary("Test issue");

        embeddingService.generateAndStore(entity);

        verifyNoInteractions(issueRepository);
    }

    @Test
    @DisplayName("disabled: search returns empty list when embedding is disabled")
    void disabled_searchReturnsEmpty() {
        chatProperties.setEmbeddingEnabled(false);

        List<JiraIssueEntity> results = embeddingService.search("test query", null, 10);

        assertTrue(results.isEmpty());
        verifyNoInteractions(issueRepository);
    }

    @Test
    @DisplayName("generateAndStore generates embedding and stores it")
    void generateAndStore_works() throws Exception {
        chatProperties.setEmbeddingEnabled(true);
        injectEmbeddingClient(embeddingClient);

        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setId(42L);
        entity.setIssueKey("LB-42");
        entity.setSummary("Автоматизация отчётности");
        entity.setDescription("Создание системы автоматической генерации отчётов");

        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingClient.generateEmbedding(anyString())).thenReturn(embedding);

        embeddingService.generateAndStore(entity);

        verify(embeddingClient).generateEmbedding("Автоматизация отчётности Создание системы автоматической генерации отчётов");
        verify(issueRepository).updateEmbedding(eq(42L), contains("0.1"));
    }

    @Test
    @DisplayName("generateAndStore skips when embedding client returns null")
    void generateAndStore_skipsNullEmbedding() throws Exception {
        chatProperties.setEmbeddingEnabled(true);
        injectEmbeddingClient(embeddingClient);

        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setId(1L);
        entity.setIssueKey("LB-1");
        entity.setSummary("Test");

        when(embeddingClient.generateEmbedding(anyString())).thenReturn(null);

        embeddingService.generateAndStore(entity);

        verify(issueRepository, never()).updateEmbedding(anyLong(), anyString());
    }

    @Test
    @DisplayName("search returns results from repository")
    void search_returnsResults() throws Exception {
        chatProperties.setEmbeddingEnabled(true);
        injectEmbeddingClient(embeddingClient);

        float[] queryEmbedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingClient.generateEmbedding("отчётность")).thenReturn(queryEmbedding);

        JiraIssueEntity found = new JiraIssueEntity();
        found.setId(42L);
        found.setIssueKey("LB-42");
        found.setSummary("Автоматизация отчётности");

        when(issueRepository.findByEmbeddingSimilarity(anyString(), eq(10))).thenReturn(List.of(found));

        List<JiraIssueEntity> results = embeddingService.search("отчётность", null, 10);

        assertEquals(1, results.size());
        assertEquals("LB-42", results.get(0).getIssueKey());
    }

    @Test
    @DisplayName("search with teamId uses team-filtered query")
    void search_withTeamId() throws Exception {
        chatProperties.setEmbeddingEnabled(true);
        injectEmbeddingClient(embeddingClient);

        float[] queryEmbedding = new float[]{0.1f, 0.2f};
        when(embeddingClient.generateEmbedding("security")).thenReturn(queryEmbedding);

        when(issueRepository.findByEmbeddingSimilarityAndTeamId(anyString(), eq(5L), eq(10)))
                .thenReturn(List.of());

        List<JiraIssueEntity> results = embeddingService.search("security", 5L, 10);

        assertTrue(results.isEmpty());
        verify(issueRepository).findByEmbeddingSimilarityAndTeamId(anyString(), eq(5L), eq(10));
        verify(issueRepository, never()).findByEmbeddingSimilarity(anyString(), anyInt());
    }

    @Test
    @DisplayName("toVectorString formats correctly")
    void toVectorString_formatsCorrectly() {
        String result = embeddingService.toVectorString(new float[]{0.1f, 0.2f, 0.3f});
        assertEquals("[0.1,0.2,0.3]", result);
    }
}

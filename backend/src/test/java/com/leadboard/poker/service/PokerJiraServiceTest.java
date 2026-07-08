package com.leadboard.poker.service;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraClientException;
import com.leadboard.poker.dto.PublishResultResponse;
import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.entity.PokerStoryEntity.StoryStatus;
import com.leadboard.poker.repository.PokerSessionRepository;
import com.leadboard.poker.repository.PokerStoryRepository;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * F23 rework: publishing poker final estimates to Jira (Original Estimate on the
 * role's subtask) and error mapping for story creation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PokerJiraServiceTest {

    @Mock private JiraClient jiraClient;
    @Mock private PokerStoryRepository storyRepository;
    @Mock private PokerSessionRepository sessionRepository;
    @Mock private JiraIssueRepository issueRepository;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private JiraConfigResolver jiraConfigResolver;

    private PokerJiraService service;

    @BeforeEach
    void setUp() {
        service = new PokerJiraService(jiraClient, storyRepository, sessionRepository,
                issueRepository, workflowConfigService, jiraConfigResolver);
        when(jiraConfigResolver.getProjectKey()).thenReturn("LB");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session()));
    }

    private PokerSessionEntity session() {
        PokerSessionEntity s = new PokerSessionEntity();
        s.setId(1L);
        s.setEpicKey("LB-1");
        return s;
    }

    private PokerStoryEntity completedStory(Long id, String key, Map<String, Integer> finals) {
        PokerStoryEntity story = new PokerStoryEntity();
        story.setId(id);
        story.setStoryKey(key);
        story.setTitle(key);
        story.setStatus(StoryStatus.COMPLETED);
        story.setFinalEstimates(finals);
        return story;
    }

    private Map<String, Object> subtask(String key, String issueType) {
        Map<String, Object> st = new HashMap<>();
        st.put("key", key);
        st.put("fields", Map.of("issuetype", Map.of("name", issueType), "summary", issueType));
        return st;
    }

    @Test
    @DisplayName("publish writes Original Estimate on the existing role subtask and creates missing ones")
    void publishWritesEstimateAndCreatesMissingSubtask() {
        when(storyRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(
                completedStory(10L, "LB-2", Map.of("DEV", 16, "QA", 8))));
        // Only the DEV subtask exists; QA is missing.
        when(jiraClient.getSubtasks("LB-2")).thenReturn(List.of(subtask("LB-2-1", "Dev Sub")));
        when(workflowConfigService.getSubtaskRole("Dev Sub")).thenReturn("DEV");
        when(workflowConfigService.getSubtaskTypeName("QA")).thenReturn("QA Sub");
        when(jiraClient.createSubtask("LB-2", "QA Sub", "LB", "QA Sub")).thenReturn("LB-2-2");

        PublishResultResponse result = service.publishSession(1L);

        // DEV: reuse existing subtask, write 16h = 57600s as Original Estimate
        verify(jiraClient).updateEstimate("LB-2-1", 16 * 3600);
        // QA: missing -> create, then write 8h = 28800s
        verify(jiraClient).createSubtask("LB-2", "QA Sub", "LB", "QA Sub");
        verify(jiraClient).updateEstimate("LB-2-2", 8 * 3600);

        assertEquals(1, result.stories().size());
        PublishResultResponse.StoryResult sr = result.stories().get(0);
        assertEquals("ok", sr.status());
        assertEquals("LB-2-1", sr.subtaskKeys().get("DEV"));
        assertEquals("LB-2-2", sr.subtaskKeys().get("QA"));
    }

    @Test
    @DisplayName("publish is idempotent — re-run with all subtasks present creates none")
    void publishIsIdempotent() {
        when(storyRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(
                completedStory(10L, "LB-2", Map.of("DEV", 16, "QA", 8))));
        // Both subtasks already exist (state after a prior publish).
        when(jiraClient.getSubtasks("LB-2")).thenReturn(List.of(
                subtask("LB-2-1", "Dev Sub"),
                subtask("LB-2-2", "QA Sub")));
        when(workflowConfigService.getSubtaskRole("Dev Sub")).thenReturn("DEV");
        when(workflowConfigService.getSubtaskRole("QA Sub")).thenReturn("QA");

        PublishResultResponse result = service.publishSession(1L);

        // No new subtasks are created on a repeat publish.
        verify(jiraClient, never()).createSubtask(any(), any(), any(), any());
        // Estimates are simply re-written (idempotent overwrite).
        verify(jiraClient).updateEstimate("LB-2-1", 16 * 3600);
        verify(jiraClient).updateEstimate("LB-2-2", 8 * 3600);
        assertEquals("ok", result.stories().get(0).status());
    }

    @Test
    @DisplayName("story not yet in Jira is reported as an error, not published")
    void localOnlyStoryReportedAsError() {
        when(storyRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(
                completedStory(10L, null, Map.of("DEV", 8))));

        PublishResultResponse result = service.publishSession(1L);

        assertEquals(1, result.stories().size());
        assertEquals("error", result.stories().get(0).status());
        verify(jiraClient, never()).updateEstimate(any(), anyInt());
        verify(jiraClient, never()).getSubtasks(any());
    }

    @Test
    @DisplayName("non-completed / zero-estimate roles are skipped")
    void skipsNonCompletedAndZeroEstimates() {
        PokerStoryEntity pending = new PokerStoryEntity();
        pending.setId(11L);
        pending.setStoryKey("LB-9");
        pending.setStatus(StoryStatus.VOTING);
        pending.setFinalEstimates(Map.of("DEV", 8));

        when(storyRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(
                completedStory(10L, "LB-2", Map.of("DEV", 16, "QA", 0)),
                pending));
        when(jiraClient.getSubtasks("LB-2")).thenReturn(List.of(subtask("LB-2-1", "Dev Sub")));
        when(workflowConfigService.getSubtaskRole("Dev Sub")).thenReturn("DEV");

        PublishResultResponse result = service.publishSession(1L);

        // Only the completed story is published; the VOTING story is skipped entirely.
        assertEquals(1, result.stories().size());
        assertEquals("LB-2", result.stories().get(0).storyKey());
        // QA=0 is not written.
        verify(jiraClient).updateEstimate("LB-2-1", 16 * 3600);
        verify(jiraClient, never()).getSubtasks("LB-9");
    }

    @Test
    @DisplayName("createStoryInJira maps a Jira 4xx to a client error (400), not 500")
    void createStoryMaps4xxToClientError() {
        when(workflowConfigService.getStoryTypeName()).thenReturn("Story");
        WebClientResponseException badRequest = WebClientResponseException.create(
                400, "Bad Request", HttpHeaders.EMPTY,
                "{\"errors\":{\"components\":\"required\"}}".getBytes(), null);
        when(jiraClient.createIssue(eq("LB"), eq("Story"), eq("Title"), eq("LB-1"), any(), any()))
                .thenThrow(badRequest);

        assertThrows(IllegalArgumentException.class, () ->
                service.createStoryInJira("LB-1", "Title", "desc", "Comp", List.of("DEV")));
    }

    @Test
    @DisplayName("createStoryInJira maps a Jira 5xx to an upstream error (502)")
    void createStoryMaps5xxToUpstreamError() {
        when(workflowConfigService.getStoryTypeName()).thenReturn("Story");
        WebClientResponseException serverError = WebClientResponseException.create(
                500, "Server Error", HttpHeaders.EMPTY, new byte[0], null);
        when(jiraClient.createIssue(eq("LB"), eq("Story"), eq("Title"), eq("LB-1"), any(), any()))
                .thenThrow(serverError);

        assertThrows(JiraClientException.class, () ->
                service.createStoryInJira("LB-1", "Title", "desc", "Comp", List.of("DEV")));
    }
}

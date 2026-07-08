package com.leadboard.poker.controller;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.poker.dto.SessionResponse;
import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerSessionEntity.SessionStatus;
import com.leadboard.poker.repository.PokerSessionRepository;
import com.leadboard.poker.service.PokerJiraService;
import com.leadboard.poker.service.PokerSessionService;
import com.leadboard.poker.service.PokerSummaryService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F23 rework: rooms are addressed by epic key. {@code GET /sessions/epic/{epicKey}}
 * returns the active session enriched with the epic's summary/description, or 404.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PokerControllerEpicLookupTest {

    @Mock private PokerSessionService sessionService;
    @Mock private PokerJiraService jiraService;
    @Mock private PokerSummaryService summaryService;
    @Mock private JiraIssueRepository issueRepository;
    @Mock private PokerSessionRepository pokerSessionRepository;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private JiraClient jiraClient;
    @Mock private JiraConfigResolver jiraConfigResolver;

    private PokerController controller;

    @BeforeEach
    void setUp() {
        controller = new PokerController(sessionService, jiraService, summaryService,
                issueRepository, pokerSessionRepository, workflowConfigService,
                jiraClient, jiraConfigResolver);
    }

    private PokerSessionEntity session(SessionStatus status) {
        PokerSessionEntity s = new PokerSessionEntity();
        s.setId(1L);
        s.setEpicKey("LB-1");
        s.setStatus(status);
        return s;
    }

    @Test
    @DisplayName("returns the active session enriched with epic summary/description")
    void returnsActiveSessionWithEpicDetails() {
        when(pokerSessionRepository.findByEpicKeyAndStatusInWithStories(eq("LB-1"), anyList()))
                .thenReturn(List.of(session(SessionStatus.ACTIVE)));
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setSummary("Checkout revamp");
        epic.setDescription("Rework the checkout epic");
        when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));

        ResponseEntity<SessionResponse> response = controller.getActiveSessionByEpic("LB-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("LB-1", response.getBody().epicKey());
        assertEquals("Checkout revamp", response.getBody().epicSummary());
        assertEquals("Rework the checkout epic", response.getBody().epicDescription());
    }

    @Test
    @DisplayName("returns 404 when the epic has no active session")
    void returns404WhenNoActiveSession() {
        when(pokerSessionRepository.findByEpicKeyAndStatusInWithStories(eq("LB-9"), anyList()))
                .thenReturn(List.of());

        ResponseEntity<SessionResponse> response = controller.getActiveSessionByEpic("LB-9");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}

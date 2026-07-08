package com.leadboard.poker.service;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.poker.dto.SessionSummaryResponse;
import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.entity.PokerStoryEntity.StoryStatus;
import com.leadboard.poker.repository.PokerSessionRepository;
import com.leadboard.poker.repository.PokerStoryRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * F23 rework: session summary (rough vs poker comparison, planning error).
 * Planning error is Σ|Δ| by role — both under- and over-estimates are error.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PokerSummaryServiceTest {

    @Mock private PokerSessionRepository sessionRepository;
    @Mock private PokerStoryRepository storyRepository;
    @Mock private JiraIssueRepository issueRepository;
    @Mock private WorkflowConfigService workflowConfigService;

    private PokerSummaryService service;

    @BeforeEach
    void setUp() {
        service = new PokerSummaryService(sessionRepository, storyRepository,
                issueRepository, workflowConfigService);
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("SA", "DEV", "QA"));
    }

    private PokerSessionEntity session(String epicKey) {
        PokerSessionEntity s = new PokerSessionEntity();
        s.setId(1L);
        s.setEpicKey(epicKey);
        return s;
    }

    private PokerStoryEntity completedStory(String key, Map<String, Integer> finals) {
        PokerStoryEntity story = new PokerStoryEntity();
        story.setStoryKey(key);
        story.setTitle(key);
        story.setStatus(StoryStatus.COMPLETED);
        story.setFinalEstimates(finals);
        return story;
    }

    private JiraIssueEntity epicWithRough(Map<String, BigDecimal> rough) {
        JiraIssueEntity epic = new JiraIssueEntity();
        rough.forEach(epic::setRoughEstimate);
        return epic;
    }

    @Test
    @DisplayName("planning error = Σ|Δ| and error% = Σ|Δ| / rough_total")
    void computesErrorAsSumOfAbsoluteDeltas() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session("LB-1")));
        // poker: SA 8h(1d), DEV 24h(3d), QA 8h(1d)
        when(storyRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(
                completedStory("LB-2", Map.of("SA", 8, "DEV", 16, "QA", 8)),
                completedStory("LB-3", Map.of("DEV", 8))
        ));
        // rough (days): SA 1, DEV 2, QA 1 -> total 4
        when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epicWithRough(Map.of(
                "SA", new BigDecimal("1"), "DEV", new BigDecimal("2"), "QA", new BigDecimal("1")))));

        SessionSummaryResponse summary = service.buildSummary(1L);

        assertEquals(4.0, summary.roughTotalDays(), 1e-9);
        assertEquals(5.0, summary.pokerTotalDays(), 1e-9); // 1 + 3 + 1
        // deltas: SA 0, DEV +1, QA 0 -> Σ|Δ| = 1
        assertEquals(1.0, summary.errorDays(), 1e-9);
        assertEquals(25.0, summary.errorPercent(), 1e-9); // 1/4 * 100
        assertEquals(2, summary.stories().size());
    }

    @Test
    @DisplayName("under- and over-estimates both count toward the error")
    void underAndOverEstimatesBothCountAsError() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session("LB-1")));
        // poker: SA 16h(2d) over, DEV 24h(3d) under
        when(storyRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(
                completedStory("LB-2", Map.of("SA", 16, "DEV", 24))
        ));
        // rough (days): SA 1 (poker over by +1), DEV 5 (poker under by -2)
        when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.of(epicWithRough(Map.of(
                "SA", new BigDecimal("1"), "DEV", new BigDecimal("5")))));

        SessionSummaryResponse summary = service.buildSummary(1L);

        // Σ|Δ| = |+1| + |-2| = 3  (NOT +1 + -2 = -1)
        assertEquals(3.0, summary.errorDays(), 1e-9);
        assertEquals(6.0, summary.roughTotalDays(), 1e-9);
        assertEquals(50.0, summary.errorPercent(), 1e-9); // 3/6 * 100
    }

    @Test
    @DisplayName("no rough estimate -> error% is 0 (no division by zero)")
    void noRoughEstimateYieldsZeroPercent() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session("LB-1")));
        when(storyRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(
                completedStory("LB-2", Map.of("DEV", 16))
        ));
        when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.empty());

        SessionSummaryResponse summary = service.buildSummary(1L);

        assertEquals(0.0, summary.roughTotalDays(), 1e-9);
        assertEquals(2.0, summary.pokerTotalDays(), 1e-9);
        assertEquals(2.0, summary.errorDays(), 1e-9); // all poker is "error" vs zero rough
        assertEquals(0.0, summary.errorPercent(), 1e-9);
    }

    @Test
    @DisplayName("only COMPLETED stories with final estimates are included")
    void skipsIncompleteStories() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session("LB-1")));
        PokerStoryEntity pending = new PokerStoryEntity();
        pending.setStoryKey("LB-9");
        pending.setStatus(StoryStatus.VOTING);
        pending.setFinalEstimates(Map.of("DEV", 8));
        when(storyRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(
                completedStory("LB-2", Map.of("DEV", 8)),
                pending
        ));
        when(issueRepository.findByIssueKey("LB-1")).thenReturn(Optional.empty());

        SessionSummaryResponse summary = service.buildSummary(1L);

        assertEquals(1, summary.stories().size());
        assertEquals("LB-2", summary.stories().get(0).storyKey());
    }
}

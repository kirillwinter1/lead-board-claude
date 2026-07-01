package com.leadboard.insight;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InsightEngineTest {

    private JiraIssueEntity story(String key, String quarterLabel, boolean hasEstimate) {
        JiraIssueEntity e = new JiraIssueEntity();
        e.setIssueKey(key);
        e.setIssueType("История");
        e.setBoardCategory("STORY");
        e.setStatus("Новое");
        if (quarterLabel != null) {
            e.setLabels(new String[]{quarterLabel});
        }
        if (hasEstimate) {
            e.setRoughEstimates(Map.of("DEV", new BigDecimal("2")));
        }
        return e;
    }

    @Test
    void planningLensRedWhenMostStoriesHaveNoQuarter() {
        JiraIssueRepository repo = mock(JiraIssueRepository.class);
        when(repo.findActiveStoriesForReadiness(1L)).thenReturn(List.of(
                story("LB-1", null, false),
                story("LB-2", null, false),
                story("LB-3", "2026Q3", true)
        ));

        InsightEngine engine = new InsightEngine(repo, mock(WorkflowConfigService.class));
        TeamReadiness r = engine.briefing(1L);

        assertEquals("RED", r.planning().level());
        assertTrue(r.planning().issueKeys().contains("LB-1"));
        assertTrue(r.planning().headline().toLowerCase().contains("квартал"));
    }

    @Test
    void planningLensGreenWhenAllHaveQuarter() {
        JiraIssueRepository repo = mock(JiraIssueRepository.class);
        when(repo.findActiveStoriesForReadiness(1L)).thenReturn(List.of(
                story("LB-1", "2026Q3", true),
                story("LB-2", "2026Q3", true)
        ));

        InsightEngine engine = new InsightEngine(repo, mock(WorkflowConfigService.class));
        TeamReadiness r = engine.briefing(1L);

        assertEquals("GREEN", r.planning().level());
        assertTrue(r.planning().issueKeys().isEmpty());
    }

    @Test
    void dataQualityLensListsStoriesWithoutEstimate() {
        JiraIssueRepository repo = mock(JiraIssueRepository.class);
        when(repo.findActiveStoriesForReadiness(1L)).thenReturn(List.of(
                story("LB-1", "2026Q3", false),
                story("LB-2", "2026Q3", true)
        ));

        InsightEngine engine = new InsightEngine(repo, mock(WorkflowConfigService.class));
        TeamReadiness r = engine.briefing(1L);

        assertTrue(r.dataQuality().issueKeys().contains("LB-1"));
        assertFalse(r.dataQuality().issueKeys().contains("LB-2"));
    }

    @Test
    void excludesDoneStoriesFromCounts() {
        JiraIssueRepository repo = mock(JiraIssueRepository.class);
        JiraIssueEntity active = story("LB-1", null, false);
        JiraIssueEntity done = story("LB-2", null, false);
        done.setStatus("Готово");
        when(repo.findActiveStoriesForReadiness(1L)).thenReturn(List.of(active, done));

        WorkflowConfigService wcs = mock(WorkflowConfigService.class);
        when(wcs.isDone("Готово", "История")).thenReturn(true);
        when(wcs.isDone("Новое", "История")).thenReturn(false);

        InsightEngine engine = new InsightEngine(repo, wcs);
        TeamReadiness r = engine.briefing(1L);

        // only the active story counts; done one excluded
        assertTrue(r.planning().issueKeys().contains("LB-1"));
        assertFalse(r.planning().issueKeys().contains("LB-2"));
        assertTrue(r.planning().headline().contains("из 1"));
    }

    @Test
    void allFourLensesPresent() {
        JiraIssueRepository repo = mock(JiraIssueRepository.class);
        when(repo.findActiveStoriesForReadiness(null)).thenReturn(List.of());

        InsightEngine engine = new InsightEngine(repo, mock(WorkflowConfigService.class));
        TeamReadiness r = engine.briefing(null);

        assertNotNull(r.planning());
        assertNotNull(r.load());
        assertNotNull(r.dataQuality());
        assertNotNull(r.flow());
    }
}

package com.leadboard.insight;

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

        InsightEngine engine = new InsightEngine(repo);
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

        InsightEngine engine = new InsightEngine(repo);
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

        InsightEngine engine = new InsightEngine(repo);
        TeamReadiness r = engine.briefing(1L);

        assertTrue(r.dataQuality().issueKeys().contains("LB-1"));
        assertFalse(r.dataQuality().issueKeys().contains("LB-2"));
    }

    @Test
    void allFourLensesPresent() {
        JiraIssueRepository repo = mock(JiraIssueRepository.class);
        when(repo.findActiveStoriesForReadiness(null)).thenReturn(List.of());

        InsightEngine engine = new InsightEngine(repo);
        TeamReadiness r = engine.briefing(null);

        assertNotNull(r.planning());
        assertNotNull(r.load());
        assertNotNull(r.dataQuality());
        assertNotNull(r.flow());
    }
}

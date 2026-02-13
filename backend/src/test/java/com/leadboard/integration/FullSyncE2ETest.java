package com.leadboard.integration;

import com.leadboard.board.BoardResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E test: Full sync pipeline.
 * Tests the complete flow: Data → Board → Forecast
 */
@DisplayName("E2E: Full Sync Pipeline")
class FullSyncE2ETest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("E2E: Create data → Get board → Get forecast")
    void shouldCompleteFullSyncPipeline() {
        // ===== Step 1: Create team and hierarchy =====
        var team = createTeam("E2E Sync Team");

        // Create epic with rough estimates
        var epic = createEpic("E2E-EPIC-1", "Payment Integration", "В работе", team.getId());
        epic.setRoughEstimate("SA", new BigDecimal("2.0"));
        epic.setRoughEstimate("DEV", new BigDecimal("5.0"));
        epic.setRoughEstimate("QA", new BigDecimal("2.0"));
        epic.setAutoScore(new BigDecimal("75.0"));
        issueRepository.save(epic);

        // Create story with subtasks
        var story = createStory("E2E-STORY-1", "Implement payment gateway", "В работе", "E2E-EPIC-1", team.getId());

        // Create subtasks for different roles
        createSubtask("E2E-SUB-1", "Analyze requirements", "Done", "E2E-STORY-1",
                "Аналитика", team.getId(), 8 * 3600L, 8 * 3600L);
        createSubtask("E2E-SUB-2", "Develop API", "В работе", "E2E-STORY-1",
                "Разработка", team.getId(), 24 * 3600L, 16 * 3600L);
        createSubtask("E2E-SUB-3", "Write tests", "Новое", "E2E-STORY-1",
                "Тестирование", team.getId(), 8 * 3600L, 0L);

        // Create status changelog
        var now = OffsetDateTime.now();
        createStatusChange("E2E-EPIC-1", "id-E2E-EPIC-1", "Новое", "В работе", now.minusDays(5));
        createStatusChange("E2E-SUB-1", "id-E2E-SUB-1", "Новое", "Done", now.minusDays(3));

        // ===== Step 2: Verify Board API =====
        var boardResponse = restTemplate.getForEntity(
                "/api/board?teamId=" + team.getId(),
                BoardResponse.class);

        assertEquals(HttpStatus.OK, boardResponse.getStatusCode());
        assertNotNull(boardResponse.getBody());

        var items = boardResponse.getBody().getItems();
        assertEquals(1, items.size(), "Should have 1 epic");

        var epicNode = items.get(0);
        assertEquals("E2E-EPIC-1", epicNode.getIssueKey());
        assertEquals("Payment Integration", epicNode.getTitle());

        // Verify story is aggregated under epic
        assertNotNull(epicNode.getChildren());
        assertEquals(1, epicNode.getChildren().size(), "Epic should have 1 story");
        assertEquals("E2E-STORY-1", epicNode.getChildren().get(0).getIssueKey());

        // ===== Step 3: Verify Forecast API =====
        var forecastResponse = restTemplate.getForEntity(
                "/api/planning/forecast?teamId=" + team.getId(),
                Map.class);

        assertEquals(HttpStatus.OK, forecastResponse.getStatusCode());
        assertNotNull(forecastResponse.getBody());

        // ===== Step 4: Verify Unified Planning API =====
        var unifiedResponse = restTemplate.getForEntity(
                "/api/planning/unified?teamId=" + team.getId(),
                Map.class);

        assertEquals(HttpStatus.OK, unifiedResponse.getStatusCode());
        assertNotNull(unifiedResponse.getBody());
    }

    @Test
    @DisplayName("E2E: Multiple epics with different priorities")
    void shouldHandleMultipleEpicsWithPriorities() {
        // ===== Setup: Create team with multiple prioritized epics =====
        var team = createTeam("E2E Priority Team");

        // High priority epic
        var epic1 = createEpic("E2E-HIGH-1", "Critical Feature", "В работе", team.getId());
        epic1.setAutoScore(new BigDecimal("95.0"));
        issueRepository.save(epic1);

        // Medium priority epic
        var epic2 = createEpic("E2E-MED-1", "Medium Feature", "В работе", team.getId());
        epic2.setAutoScore(new BigDecimal("60.0"));
        issueRepository.save(epic2);

        // Low priority epic
        var epic3 = createEpic("E2E-LOW-1", "Low Feature", "Новое", team.getId());
        epic3.setAutoScore(new BigDecimal("25.0"));
        issueRepository.save(epic3);

        // ===== Verify Board returns sorted by priority =====
        var boardResponse = restTemplate.getForEntity(
                "/api/board?teamId=" + team.getId(),
                BoardResponse.class);

        assertEquals(HttpStatus.OK, boardResponse.getStatusCode());
        var items = boardResponse.getBody().getItems();
        assertEquals(3, items.size());

        // Should be sorted by autoScore descending
        assertEquals("E2E-HIGH-1", items.get(0).getIssueKey());
        assertEquals("E2E-MED-1", items.get(1).getIssueKey());
        assertEquals("E2E-LOW-1", items.get(2).getIssueKey());
    }
}

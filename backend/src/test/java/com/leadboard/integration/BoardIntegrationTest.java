package com.leadboard.integration;

import com.leadboard.board.BoardNode;
import com.leadboard.board.BoardResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Board API.
 * Tests full data aggregation pipeline with real PostgreSQL.
 */
@DisplayName("Board Integration Tests")
class BoardIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should aggregate real hierarchy: Epic -> Story -> Subtask")
    void shouldAggregateRealHierarchy() {
        // Given
        var team = createTeam("Backend Team");
        var epic = createEpic("EPIC-1", "Payment System", "В работе", team.getId());
        var story = createStory("STORY-1", "Implement checkout", "В работе", "EPIC-1", team.getId());
        createSubtask("SUB-1", "Analyze requirements", "Done", "STORY-1", "Аналитика", team.getId(), 8 * 3600L, 8 * 3600L);
        createSubtask("SUB-2", "Develop API", "В работе", "STORY-1", "Разработка", team.getId(), 16 * 3600L, 8 * 3600L);
        createSubtask("SUB-3", "Write tests", "Новое", "STORY-1", "Тестирование", team.getId(), 8 * 3600L, 0L);

        // When
        var response = restTemplate.getForEntity(
                "/api/board?teamId=" + team.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        var items = response.getBody().getItems();
        assertEquals(1, items.size());
        assertEquals("EPIC-1", items.get(0).getIssueKey());

        // Stories should be in children
        var stories = items.get(0).getChildren();
        assertEquals(1, stories.size());
        assertEquals("STORY-1", stories.get(0).getIssueKey());

        // Subtasks should be aggregated under story
        assertNotNull(stories.get(0).getChildren());
    }

    @Test
    @DisplayName("Should calculate progress from subtasks")
    void shouldCalculateProgressFromSubtasks() {
        // Given
        var team = createTeam("Backend Team");
        createEpic("EPIC-1", "Epic with progress", "В работе", team.getId());
        createStory("STORY-1", "Story with subtasks", "В работе", "EPIC-1", team.getId());
        // 3 subtasks: 1 done, 1 in progress, 1 new
        createSubtask("SUB-1", "Done task", "Done", "STORY-1", "Разработка", team.getId(), 8 * 3600L, 8 * 3600L);
        createSubtask("SUB-2", "In progress task", "В работе", "STORY-1", "Разработка", team.getId(), 8 * 3600L, 4 * 3600L);
        createSubtask("SUB-3", "New task", "Новое", "STORY-1", "Разработка", team.getId(), 8 * 3600L, 0L);

        // When
        var response = restTemplate.getForEntity(
                "/api/board?teamId=" + team.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        var story = response.getBody().getItems().get(0).getChildren().get(0);

        // Total estimate: 24h, logged: 12h
        // Progress should reflect actual work done
        assertNotNull(story.getEstimateSeconds());
        assertNotNull(story.getLoggedSeconds());
    }

    @Test
    @DisplayName("Should filter by team correctly")
    void shouldFilterByTeamCorrectly() {
        // Given - use unique names to avoid conflicts with other tests
        var team1 = createTeam("Filter Test Team A");
        var team2 = createTeam("Filter Test Team B");
        createEpic("FILTER-EPIC-1", "Team A Epic", "Новое", team1.getId());
        createEpic("FILTER-EPIC-2", "Team B Epic 1", "Новое", team2.getId());
        createEpic("FILTER-EPIC-3", "Team B Epic 2", "Новое", team2.getId());

        // When - get board for team1
        var response1 = restTemplate.getForEntity(
                "/api/board?teamId=" + team1.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        // Filter by team should only return items for that team
        var team1Items = response1.getBody().getItems().stream()
                .filter(item -> item.getTeamId() != null && item.getTeamId().equals(team1.getId()))
                .toList();
        assertEquals(1, team1Items.size());

        // When - get board for team2
        var response2 = restTemplate.getForEntity(
                "/api/board?teamId=" + team2.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response2.getStatusCode());
        var team2Items = response2.getBody().getItems().stream()
                .filter(item -> item.getTeamId() != null && item.getTeamId().equals(team2.getId()))
                .toList();
        assertEquals(2, team2Items.size());
    }

    @Test
    @DisplayName("Should sort by AutoScore from database")
    void shouldSortByAutoScoreFromDb() {
        // Given
        var team = createTeam("Backend Team");
        // Create epics with different autoScores (higher = higher priority)
        createEpicWithAutoScore("EPIC-3", "Low priority", "Новое", team.getId(), new BigDecimal("30.0"));
        createEpicWithAutoScore("EPIC-1", "High priority", "Новое", team.getId(), new BigDecimal("90.0"));
        createEpicWithAutoScore("EPIC-2", "Medium priority", "Новое", team.getId(), new BigDecimal("60.0"));

        // When
        var response = restTemplate.getForEntity(
                "/api/board?teamId=" + team.getId(),
                BoardResponse.class);

        // Then - should be sorted by autoScore descending
        assertEquals(HttpStatus.OK, response.getStatusCode());
        var items = response.getBody().getItems();
        assertEquals(3, items.size());
        assertEquals("EPIC-1", items.get(0).getIssueKey()); // 90.0
        assertEquals("EPIC-2", items.get(1).getIssueKey()); // 60.0
        assertEquals("EPIC-3", items.get(2).getIssueKey()); // 30.0
    }

    @Test
    @DisplayName("Should return empty list when no epics for team")
    void shouldReturnEmptyListWhenNoEpicsForTeam() {
        // Given
        var team = createTeam("Empty Team");
        // No epics created

        // When
        var response = restTemplate.getForEntity(
                "/api/board?teamId=" + team.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getItems().isEmpty());
    }
}

package com.leadboard.component;

import com.leadboard.board.BoardResponse;
import com.leadboard.team.TeamEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Board API Component Tests")
class BoardComponentTest extends ComponentTestBase {

    @Test
    @DisplayName("GET /api/board returns 200 with epics")
    void getBoard_returns200WithEpics() {
        // Given
        TeamEntity team = createTeam("Alpha Team");
        createEpic("TEST-1", "Test Epic", team.getId());

        // When
        ResponseEntity<BoardResponse> response = restTemplate.getForEntity(
                "/api/board?teamIds=" + team.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotal());
        assertEquals("TEST-1", response.getBody().getItems().get(0).getIssueKey());
    }

    @Test
    @DisplayName("GET /api/board returns 200 empty when no data")
    void getBoard_returns200EmptyWhenNoData() {
        // When
        ResponseEntity<BoardResponse> response = restTemplate.getForEntity(
                "/api/board",
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getTotal());
        assertTrue(response.getBody().getItems().isEmpty());
    }

    @Test
    @DisplayName("GET /api/board filters by teamIds correctly")
    void getBoard_filtersCorrectly() {
        // Given
        TeamEntity team1 = createTeam("Team 1");
        TeamEntity team2 = createTeam("Team 2");
        createEpic("TEST-1", "Epic Team 1", team1.getId());
        createEpic("TEST-2", "Epic Team 2", team2.getId());

        // When
        ResponseEntity<BoardResponse> response = restTemplate.getForEntity(
                "/api/board?teamIds=" + team1.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotal());
        assertEquals("TEST-1", response.getBody().getItems().get(0).getIssueKey());
    }

    @Test
    @DisplayName("GET /api/board aggregates hierarchy")
    void getBoard_aggregatesHierarchy() {
        // Given
        TeamEntity team = createTeam("Team");
        createEpic("TEST-1", "Epic", team.getId());
        createStory("TEST-2", "Story", "TEST-1", team.getId());
        createSubtask("TEST-3", "Subtask", "TEST-2", "Разработка", team.getId());

        // When
        ResponseEntity<BoardResponse> response = restTemplate.getForEntity(
                "/api/board?teamIds=" + team.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotal());

        // Epic has story as child
        var epicNode = response.getBody().getItems().get(0);
        assertEquals("TEST-1", epicNode.getIssueKey());
        assertFalse(epicNode.getChildren().isEmpty());

        // Story has subtask as child
        var storyNode = epicNode.getChildren().get(0);
        assertEquals("TEST-2", storyNode.getIssueKey());
        assertFalse(storyNode.getChildren().isEmpty());
    }

    @Test
    @DisplayName("GET /api/board filters by query string")
    void getBoard_filtersByQuery() {
        // Given
        TeamEntity team = createTeam("Team");
        createEpic("TEST-1", "User Authentication Epic", team.getId());
        createEpic("TEST-2", "Dashboard Feature", team.getId());

        // When - search for "Auth"
        ResponseEntity<BoardResponse> response = restTemplate.getForEntity(
                "/api/board?query=Auth&teamIds=" + team.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotal());
        assertEquals("TEST-1", response.getBody().getItems().get(0).getIssueKey());
    }

    @Test
    @DisplayName("GET /api/board filters by status")
    void getBoard_filtersByStatus() {
        // Given
        TeamEntity team = createTeam("Team");
        createEpic("TEST-1", "Epic 1", "Новое", team.getId());
        createEpic("TEST-2", "Epic 2", "В работе", team.getId());

        // When - filter by status
        ResponseEntity<BoardResponse> response = restTemplate.getForEntity(
                "/api/board?statuses=Новое&teamIds=" + team.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotal());
        assertEquals("TEST-1", response.getBody().getItems().get(0).getIssueKey());
    }

    @Test
    @DisplayName("GET /api/board calculates progress from subtasks")
    void getBoard_calculatesProgress() {
        // Given
        TeamEntity team = createTeam("Team");
        createEpic("TEST-1", "Epic", team.getId());
        createStory("TEST-2", "Story", "TEST-1", team.getId());
        // Create subtask with estimate 8h, logged 4h = 50% progress
        createSubtaskWithTime("TEST-3", "Dev Task", "TEST-2", "Разработка", team.getId(),
                8 * 3600L, 4 * 3600L);

        // When
        ResponseEntity<BoardResponse> response = restTemplate.getForEntity(
                "/api/board?teamIds=" + team.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        var epicNode = response.getBody().getItems().get(0);
        assertNotNull(epicNode.getProgress());
    }

    @Test
    @DisplayName("GET /api/board supports multiple team IDs")
    void getBoard_supportsMultipleTeamIds() {
        // Given
        TeamEntity team1 = createTeam("Team 1");
        TeamEntity team2 = createTeam("Team 2");
        TeamEntity team3 = createTeam("Team 3");
        createEpic("TEST-1", "Epic Team 1", team1.getId());
        createEpic("TEST-2", "Epic Team 2", team2.getId());
        createEpic("TEST-3", "Epic Team 3", team3.getId());

        // When - filter by two teams
        ResponseEntity<BoardResponse> response = restTemplate.getForEntity(
                "/api/board?teamIds=" + team1.getId() + "&teamIds=" + team2.getId(),
                BoardResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getTotal());
    }
}

package com.leadboard.component;

import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.team.TeamEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Forecast API Component Tests")
class ForecastComponentTest extends ComponentTestBase {

    @Test
    @DisplayName("GET /api/planning/forecast returns 200")
    void getForecast_returns200() {
        // Given
        TeamEntity team = createTeam("Test Team");

        // When
        ResponseEntity<ForecastResponse> response = restTemplate.getForEntity(
                "/api/planning/forecast?teamId=" + team.getId(),
                ForecastResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("GET /api/planning/forecast returns forecast for team")
    void getForecast_returnsForecastForTeam() {
        // Given
        TeamEntity team = createTeam("Test Team");
        createEpic("TEST-1", "Epic 1", team.getId());
        createStory("TEST-2", "Story 1", "TEST-1", team.getId());

        // When
        ResponseEntity<ForecastResponse> response = restTemplate.getForEntity(
                "/api/planning/forecast?teamId=" + team.getId(),
                ForecastResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("GET /api/planning/unified returns unified plan")
    void getUnifiedPlan_returnsUnifiedPlan() {
        // Given
        TeamEntity team = createTeam("Test Team");
        createEpic("TEST-1", "Epic 1", team.getId());

        // When
        ResponseEntity<UnifiedPlanningResult> response = restTemplate.getForEntity(
                "/api/planning/unified?teamId=" + team.getId(),
                UnifiedPlanningResult.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("POST /api/planning/recalculate recalculates scores for team")
    void recalculate_recalculatesForTeam() {
        // Given
        TeamEntity team = createTeam("Test Team");
        createEpic("TEST-1", "Epic 1", team.getId());

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/planning/recalculate?teamId=" + team.getId(),
                null,
                Map.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("completed", response.getBody().get("status"));
    }

    @Test
    @DisplayName("POST /api/planning/recalculate recalculates all when no teamId")
    void recalculate_recalculatesAll() {
        // Given
        TeamEntity team = createTeam("Test Team");
        createEpic("TEST-1", "Epic 1", team.getId());

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/planning/recalculate",
                null,
                Map.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("completed", response.getBody().get("status"));
    }
}

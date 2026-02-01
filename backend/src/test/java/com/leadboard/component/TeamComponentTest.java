package com.leadboard.component;

import com.leadboard.team.CreateTeamRequest;
import com.leadboard.team.TeamDto;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.UpdateTeamRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Team API Component Tests")
class TeamComponentTest extends ComponentTestBase {

    @Test
    @DisplayName("GET /api/teams returns empty list when no teams")
    void getAllTeams_returnsEmptyList() {
        // When
        ResponseEntity<List<TeamDto>> response = restTemplate.exchange(
                "/api/teams",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<TeamDto>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    @DisplayName("GET /api/teams returns all teams")
    void getAllTeams_returnsAllTeams() {
        // Given
        createTeam("Team Alpha");
        createTeam("Team Beta");

        // When
        ResponseEntity<List<TeamDto>> response = restTemplate.exchange(
                "/api/teams",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<TeamDto>>() {});

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    @DisplayName("GET /api/teams/{id} returns team")
    void getTeam_returnsTeam() {
        // Given
        TeamEntity team = createTeam("Test Team");

        // When
        ResponseEntity<TeamDto> response = restTemplate.getForEntity(
                "/api/teams/" + team.getId(),
                TeamDto.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Test Team", response.getBody().name());
    }

    @Test
    @DisplayName("GET /api/teams/{id} returns 404 for non-existent team")
    void getTeam_returns404ForNonExistent() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/teams/99999",
                Map.class);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("POST /api/teams creates new team")
    void createTeam_createsTeam() {
        // Given
        CreateTeamRequest request = new CreateTeamRequest("New Team", "new-team-jira");

        // When
        ResponseEntity<TeamDto> response = restTemplate.postForEntity(
                "/api/teams",
                request,
                TeamDto.class);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().id());
        assertEquals("New Team", response.getBody().name());
    }

    @Test
    @DisplayName("POST /api/teams returns 409 for duplicate name")
    void createTeam_returns409ForDuplicate() {
        // Given
        createTeam("Existing Team");
        CreateTeamRequest request = new CreateTeamRequest("Existing Team", "jira-value");

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/teams",
                request,
                Map.class);

        // Then
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @DisplayName("PUT /api/teams/{id} updates team")
    void updateTeam_updatesTeam() {
        // Given
        TeamEntity team = createTeam("Old Name");
        UpdateTeamRequest request = new UpdateTeamRequest("New Name", null);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UpdateTeamRequest> entity = new HttpEntity<>(request, headers);

        // When
        ResponseEntity<TeamDto> response = restTemplate.exchange(
                "/api/teams/" + team.getId(),
                HttpMethod.PUT,
                entity,
                TeamDto.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("New Name", response.getBody().name());
    }

    @Test
    @DisplayName("DELETE /api/teams/{id} deactivates team")
    void deactivateTeam_deactivatesTeam() {
        // Given
        TeamEntity team = createTeam("Team to Deactivate");

        // When
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/teams/" + team.getId(),
                HttpMethod.DELETE,
                null,
                Void.class);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verify team is deactivated
        TeamEntity deactivatedTeam = teamRepository.findById(team.getId()).orElseThrow();
        assertFalse(deactivatedTeam.getActive());
    }

    @Test
    @DisplayName("GET /api/teams/config returns configuration")
    void getConfig_returnsConfig() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/teams/config",
                Map.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().get("manualTeamManagement"));
    }
}

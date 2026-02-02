package com.leadboard.integration;

import com.leadboard.poker.dto.*;
import com.leadboard.poker.repository.PokerSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Poker API.
 * Tests session lifecycle and story management with real PostgreSQL.
 * Note: WebSocket tests are in a separate class (PokerWebSocketTest).
 */
@DisplayName("Poker Integration Tests")
class PokerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PokerSessionRepository pokerSessionRepository;

    @Test
    @DisplayName("Should create poker session for epic")
    void shouldCreatePokerSessionForEpic() {
        // Given
        var team = createTeam("Poker Team");
        var epic = createEpic("POKER-EPIC-1", "Epic for Poker", "Новое", team.getId());

        // When
        var request = new CreateSessionRequest(team.getId(), "POKER-EPIC-1");
        var response = restTemplate.postForEntity(
                "/api/poker/sessions",
                request,
                SessionResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().id());
        assertNotNull(response.getBody().roomCode());
        assertEquals("POKER-EPIC-1", response.getBody().epicKey());
        assertEquals("PREPARING", response.getBody().status());
    }

    @Test
    @DisplayName("Should get session by ID and room code")
    void shouldGetSessionByIdAndRoomCode() {
        // Given
        var team = createTeam("Session Team");
        createEpic("SESSION-EPIC-1", "Session Epic", "Новое", team.getId());
        var createRequest = new CreateSessionRequest(team.getId(), "SESSION-EPIC-1");
        var createResponse = restTemplate.postForEntity(
                "/api/poker/sessions",
                createRequest,
                SessionResponse.class);

        var sessionId = createResponse.getBody().id();
        var roomCode = createResponse.getBody().roomCode();

        // When - get by ID
        var byIdResponse = restTemplate.getForEntity(
                "/api/poker/sessions/" + sessionId,
                SessionResponse.class);

        // Then
        assertEquals(HttpStatus.OK, byIdResponse.getStatusCode());
        assertEquals(sessionId, byIdResponse.getBody().id());

        // When - get by room code
        var byCodeResponse = restTemplate.getForEntity(
                "/api/poker/sessions/room/" + roomCode,
                SessionResponse.class);

        // Then
        assertEquals(HttpStatus.OK, byCodeResponse.getStatusCode());
        assertEquals(roomCode, byCodeResponse.getBody().roomCode());
    }

    @Test
    @DisplayName("Should start and complete session")
    void shouldStartAndCompleteSession() {
        // Given
        var team = createTeam("Lifecycle Team");
        createEpic("LIFECYCLE-EPIC-1", "Lifecycle Epic", "Новое", team.getId());
        var createRequest = new CreateSessionRequest(team.getId(), "LIFECYCLE-EPIC-1");
        var session = restTemplate.postForEntity(
                "/api/poker/sessions",
                createRequest,
                SessionResponse.class).getBody();

        // When - start session
        var startResponse = restTemplate.postForEntity(
                "/api/poker/sessions/" + session.id() + "/start",
                null,
                SessionResponse.class);

        // Then
        assertEquals(HttpStatus.OK, startResponse.getStatusCode());
        assertEquals("ACTIVE", startResponse.getBody().status());

        // When - complete session
        var completeResponse = restTemplate.postForEntity(
                "/api/poker/sessions/" + session.id() + "/complete",
                null,
                SessionResponse.class);

        // Then
        assertEquals(HttpStatus.OK, completeResponse.getStatusCode());
        assertEquals("COMPLETED", completeResponse.getBody().status());
    }

    @Test
    @DisplayName("Should add and list stories in session")
    void shouldAddAndListStoriesInSession() {
        // Given
        var team = createTeam("Stories Team");
        createEpic("STORIES-EPIC-1", "Stories Epic", "Новое", team.getId());
        var createRequest = new CreateSessionRequest(team.getId(), "STORIES-EPIC-1");
        var session = restTemplate.postForEntity(
                "/api/poker/sessions",
                createRequest,
                SessionResponse.class).getBody();

        // When - add stories
        var story1 = new AddStoryRequest("User login feature", true, true, true, null);
        var story2 = new AddStoryRequest("Dashboard widget", false, true, true, null);

        var addResponse1 = restTemplate.postForEntity(
                "/api/poker/sessions/" + session.id() + "/stories",
                story1,
                StoryResponse.class);

        var addResponse2 = restTemplate.postForEntity(
                "/api/poker/sessions/" + session.id() + "/stories",
                story2,
                StoryResponse.class);

        // Then
        assertEquals(HttpStatus.OK, addResponse1.getStatusCode());
        assertEquals("User login feature", addResponse1.getBody().title());
        assertTrue(addResponse1.getBody().needsSa());

        assertEquals(HttpStatus.OK, addResponse2.getStatusCode());
        assertFalse(addResponse2.getBody().needsSa());

        // When - list stories
        var listResponse = restTemplate.getForEntity(
                "/api/poker/sessions/" + session.id() + "/stories",
                StoryResponse[].class);

        // Then
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertEquals(2, listResponse.getBody().length);
    }

    @Test
    @DisplayName("Should get eligible epics for team")
    void shouldGetEligibleEpicsForTeam() {
        // Given - create epics with eligible statuses
        var team = createTeam("Eligible Team");

        // Create epic with eligible status
        var eligibleEpic = createEpic("ELIGIBLE-1", "Eligible Epic", "Новое", team.getId());
        eligibleEpic.setStatus("планирование");
        issueRepository.save(eligibleEpic);

        // Create epic with non-eligible status (Done)
        var doneEpic = createEpic("DONE-1", "Done Epic", "Done", team.getId());

        // When
        var response = restTemplate.getForEntity(
                "/api/poker/eligible-epics/" + team.getId(),
                EligibleEpicResponse[].class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // Eligible epic should be in list
        boolean foundEligible = false;
        for (EligibleEpicResponse epic : response.getBody()) {
            if ("ELIGIBLE-1".equals(epic.epicKey())) {
                foundEligible = true;
                break;
            }
        }
        assertTrue(foundEligible, "Eligible epic should be in the list");
    }
}

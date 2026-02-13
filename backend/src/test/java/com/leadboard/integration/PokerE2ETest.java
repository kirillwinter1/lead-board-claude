package com.leadboard.integration;

import com.leadboard.poker.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E test: Planning Poker session.
 * Tests the complete flow: Create → Add Stories → Start → Vote → Reveal → Complete
 */
@DisplayName("E2E: Planning Poker Session")
class PokerE2ETest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("E2E: Complete poker session lifecycle")
    void shouldCompletePokerSessionLifecycle() {
        // ===== Step 1: Setup team and epic =====
        var team = createTeam("E2E Poker Team");
        var epic = createEpic("POKER-E2E-1", "Feature to Estimate", "планирование", team.getId());

        // ===== Step 2: Create poker session =====
        var createRequest = new CreateSessionRequest(team.getId(), "POKER-E2E-1");
        var sessionResponse = restTemplate.postForEntity(
                "/api/poker/sessions",
                createRequest,
                SessionResponse.class);

        assertEquals(HttpStatus.OK, sessionResponse.getStatusCode());
        var session = sessionResponse.getBody();
        assertNotNull(session.id());
        assertNotNull(session.roomCode());
        assertEquals("PREPARING", session.status());
        assertEquals("POKER-E2E-1", session.epicKey());

        Long sessionId = session.id();
        String roomCode = session.roomCode();

        // ===== Step 3: Add stories to estimate =====
        var story1 = new AddStoryRequest("User authentication", List.of("SA", "DEV", "QA"), null);
        var story1Response = restTemplate.postForEntity(
                "/api/poker/sessions/" + sessionId + "/stories",
                story1,
                StoryResponse.class);

        assertEquals(HttpStatus.OK, story1Response.getStatusCode());
        assertTrue(story1Response.getBody().needsRoles().contains("SA"));
        assertTrue(story1Response.getBody().needsRoles().contains("DEV"));
        assertTrue(story1Response.getBody().needsRoles().contains("QA"));

        var story2 = new AddStoryRequest("Dashboard widget", List.of("DEV", "QA"), null);
        var story2Response = restTemplate.postForEntity(
                "/api/poker/sessions/" + sessionId + "/stories",
                story2,
                StoryResponse.class);

        assertEquals(HttpStatus.OK, story2Response.getStatusCode());
        assertFalse(story2Response.getBody().needsRoles().contains("SA"));

        var story3 = new AddStoryRequest("API endpoint", List.of("DEV"), null);
        restTemplate.postForEntity(
                "/api/poker/sessions/" + sessionId + "/stories",
                story3,
                StoryResponse.class);

        // ===== Step 4: Verify stories list =====
        var storiesResponse = restTemplate.getForEntity(
                "/api/poker/sessions/" + sessionId + "/stories",
                StoryResponse[].class);

        assertEquals(HttpStatus.OK, storiesResponse.getStatusCode());
        assertEquals(3, storiesResponse.getBody().length);

        // ===== Step 5: Start session =====
        var startResponse = restTemplate.postForEntity(
                "/api/poker/sessions/" + sessionId + "/start",
                null,
                SessionResponse.class);

        assertEquals(HttpStatus.OK, startResponse.getStatusCode());
        assertEquals("ACTIVE", startResponse.getBody().status());

        // ===== Step 6: Get session by room code (simulates participant joining) =====
        var joinResponse = restTemplate.getForEntity(
                "/api/poker/sessions/room/" + roomCode,
                SessionResponse.class);

        assertEquals(HttpStatus.OK, joinResponse.getStatusCode());
        assertEquals(sessionId, joinResponse.getBody().id());
        assertEquals("ACTIVE", joinResponse.getBody().status());

        // ===== Step 7: Reveal votes for first story =====
        Long firstStoryId = story1Response.getBody().id();
        var revealResponse = restTemplate.postForEntity(
                "/api/poker/stories/" + firstStoryId + "/reveal",
                null,
                StoryResponse.class);

        assertEquals(HttpStatus.OK, revealResponse.getStatusCode());

        // ===== Step 8: Set final estimate =====
        var finalRequest = new SetFinalRequest(firstStoryId, Map.of("SA", 4, "DEV", 16, "QA", 8));
        var finalResponse = restTemplate.postForEntity(
                "/api/poker/stories/" + firstStoryId + "/final?updateJira=false",
                finalRequest,
                StoryResponse.class);

        assertEquals(HttpStatus.OK, finalResponse.getStatusCode());
        assertEquals(4, finalResponse.getBody().finalEstimates().get("SA"));
        assertEquals(16, finalResponse.getBody().finalEstimates().get("DEV"));
        assertEquals(8, finalResponse.getBody().finalEstimates().get("QA"));

        // ===== Step 9: Move to next story =====
        var nextResponse = restTemplate.postForEntity(
                "/api/poker/sessions/" + sessionId + "/next",
                null,
                StoryResponse.class);

        assertEquals(HttpStatus.OK, nextResponse.getStatusCode());
        assertEquals("Dashboard widget", nextResponse.getBody().title());

        // ===== Step 10: Complete session =====
        var completeResponse = restTemplate.postForEntity(
                "/api/poker/sessions/" + sessionId + "/complete",
                null,
                SessionResponse.class);

        assertEquals(HttpStatus.OK, completeResponse.getStatusCode());
        assertEquals("COMPLETED", completeResponse.getBody().status());
        assertNotNull(completeResponse.getBody().completedAt());

        // ===== Step 11: Verify session is in team's session list =====
        var teamSessionsResponse = restTemplate.getForEntity(
                "/api/poker/sessions/team/" + team.getId(),
                SessionResponse[].class);

        assertEquals(HttpStatus.OK, teamSessionsResponse.getStatusCode());
        assertTrue(teamSessionsResponse.getBody().length >= 1);

        // Find our session
        boolean found = false;
        for (SessionResponse s : teamSessionsResponse.getBody()) {
            if (s.id().equals(sessionId)) {
                found = true;
                assertEquals("COMPLETED", s.status());
                break;
            }
        }
        assertTrue(found, "Session should be in team's session list");
    }

    @Test
    @DisplayName("E2E: Link existing Jira stories to poker session")
    void shouldLinkExistingJiraStories() {
        // ===== Setup =====
        var team = createTeam("E2E Poker Link Team");
        var epic = createEpic("LINK-EPIC-1", "Epic with stories", "планирование", team.getId());

        // Create existing stories in Jira (database)
        var existingStory = createStory("LINK-STORY-1", "Existing Story", "Новое", "LINK-EPIC-1", team.getId());

        // Create poker session
        var sessionResponse = restTemplate.postForEntity(
                "/api/poker/sessions",
                new CreateSessionRequest(team.getId(), "LINK-EPIC-1"),
                SessionResponse.class);

        Long sessionId = sessionResponse.getBody().id();

        // ===== Get epic stories from Jira =====
        var epicStoriesResponse = restTemplate.getForEntity(
                "/api/poker/epic-stories/LINK-EPIC-1",
                EpicStoryResponse[].class);

        assertEquals(HttpStatus.OK, epicStoriesResponse.getStatusCode());
        assertTrue(epicStoriesResponse.getBody().length >= 1);

        // ===== Add story with link to existing =====
        var linkedStory = new AddStoryRequest(
                "Existing Story",
                List.of("SA", "DEV", "QA"),
                "LINK-STORY-1" // Link to existing Jira story
        );

        var addResponse = restTemplate.postForEntity(
                "/api/poker/sessions/" + sessionId + "/stories",
                linkedStory,
                StoryResponse.class);

        assertEquals(HttpStatus.OK, addResponse.getStatusCode());
        assertEquals("LINK-STORY-1", addResponse.getBody().storyKey());
    }
}

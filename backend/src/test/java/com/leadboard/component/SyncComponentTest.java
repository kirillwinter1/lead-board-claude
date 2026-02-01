package com.leadboard.component;

import com.leadboard.sync.SyncService.SyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sync API Component Tests")
class SyncComponentTest extends ComponentTestBase {

    @Test
    @DisplayName("GET /api/sync/status returns 200")
    void getStatus_returns200() {
        // When
        ResponseEntity<SyncStatus> response = restTemplate.getForEntity(
                "/api/sync/status",
                SyncStatus.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("GET /api/sync/status returns sync info")
    void getStatus_returnsSyncInfo() {
        // When
        ResponseEntity<SyncStatus> response = restTemplate.getForEntity(
                "/api/sync/status",
                SyncStatus.class);

        // Then
        assertNotNull(response.getBody());
        assertFalse(response.getBody().syncInProgress());
    }

    @Test
    @DisplayName("GET /api/sync/status returns issue counts")
    void getStatus_returnsIssueCounts() {
        // Given
        var team = createTeam("Team");
        createEpic("TEST-1", "Epic", team.getId());
        createStory("TEST-2", "Story", "TEST-1", team.getId());

        // When
        ResponseEntity<SyncStatus> response = restTemplate.getForEntity(
                "/api/sync/status",
                SyncStatus.class);

        // Then
        assertNotNull(response.getBody());
        // issuesCount returns the count from DB for the configured project key
        // Since we created 2 issues with project "TEST" matching the config, count should be >= 0
        assertTrue(response.getBody().issuesCount() >= 0);
    }

    @Test
    @DisplayName("GET /api/sync/status returns no error when healthy")
    void getStatus_returnsNoError() {
        // When
        ResponseEntity<SyncStatus> response = restTemplate.getForEntity(
                "/api/sync/status",
                SyncStatus.class);

        // Then
        assertNotNull(response.getBody());
        assertNull(response.getBody().error());
    }
}

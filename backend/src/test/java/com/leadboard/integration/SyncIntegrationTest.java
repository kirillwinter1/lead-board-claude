package com.leadboard.integration;

import com.leadboard.sync.JiraIssueEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Sync functionality.
 * Tests database operations, FK constraints, and transactions.
 */
@DisplayName("Sync Integration Tests")
class SyncIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Should save new issues to database with all fields")
    void shouldSaveNewIssuesToDatabase() {
        // Given
        var team = createTeam("Backend Team");

        // When
        var epic = createEpic("EPIC-1", "Test Epic", "Новое", team.getId());

        // Then
        Optional<JiraIssueEntity> found = issueRepository.findByIssueKey("EPIC-1");
        assertTrue(found.isPresent());
        assertEquals("Test Epic", found.get().getSummary());
        assertEquals("Эпик", found.get().getIssueType());
        assertEquals(team.getId(), found.get().getTeamId());
        assertNotNull(found.get().getCreatedAt());
    }

    @Test
    @DisplayName("Should update existing issues preserving local fields")
    void shouldUpdateExistingIssuesPreservingLocalFields() {
        // Given
        var team = createTeam("Backend Team");
        var epic = createEpic("EPIC-1", "Original Summary", "Новое", team.getId());

        // Set local fields (rough estimates)
        epic.setRoughEstimate("SA", new BigDecimal("5.0"));
        epic.setRoughEstimate("DEV", new BigDecimal("10.0"));
        epic.setRoughEstimate("QA", new BigDecimal("3.0"));
        epic.setAutoScore(new BigDecimal("85.5"));
        issueRepository.save(epic);

        // When - simulate update from Jira (only updates Jira fields)
        epic.setSummary("Updated Summary");
        epic.setStatus("В работе");
        issueRepository.save(epic);

        // Then - local fields should be preserved
        var updated = issueRepository.findByIssueKey("EPIC-1").orElseThrow();
        assertEquals("Updated Summary", updated.getSummary());
        assertEquals("В работе", updated.getStatus());
        // Compare BigDecimal by value, not scale
        assertEquals(0, new BigDecimal("5.0").compareTo(updated.getRoughEstimate("SA")));
        assertEquals(0, new BigDecimal("10.0").compareTo(updated.getRoughEstimate("DEV")));
        assertEquals(0, new BigDecimal("3.0").compareTo(updated.getRoughEstimate("QA")));
        assertEquals(0, new BigDecimal("85.5").compareTo(updated.getAutoScore()));
    }

    @Test
    @DisplayName("Should create changelog entry on status change with FK constraint")
    void shouldCreateChangelogOnStatusChangeWithFkConstraint() {
        // Given
        var team = createTeam("Backend Team");
        var epic = createEpic("EPIC-1", "Test Epic", "Новое", team.getId());

        // When - create status change log (FK references issue_key)
        var changelog = createStatusChange(
                epic.getIssueKey(),
                epic.getIssueId(),
                "Новое",
                "В работе",
                OffsetDateTime.now()
        );

        // Then
        var logs = changelogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("EPIC-1", logs.get(0).getIssueKey());
        assertEquals("Новое", logs.get(0).getFromStatus());
        assertEquals("В работе", logs.get(0).getToStatus());
    }

    @Test
    @DisplayName("Should cascade delete changelog when issue is deleted")
    void shouldCascadeDeleteChangelogWhenIssueDeleted() {
        // Given
        var team = createTeam("Backend Team");
        var epic = createEpic("EPIC-1", "Test Epic", "Новое", team.getId());
        createStatusChange(epic.getIssueKey(), epic.getIssueId(), "Новое", "В работе", OffsetDateTime.now());
        createStatusChange(epic.getIssueKey(), epic.getIssueId(), "В работе", "Done", OffsetDateTime.now().plusHours(1));

        assertEquals(2, changelogRepository.count());

        // When - delete issue
        issueRepository.delete(epic);

        // Then - changelog entries should be cascade deleted
        assertEquals(0, changelogRepository.count());
    }

    @Test
    @DisplayName("Should map team field to team ID correctly")
    void shouldMapTeamFieldToTeamId() {
        // Given
        var team1 = createTeam("Frontend Team");
        var team2 = createTeam("Backend Team");

        // When
        var epic1 = createEpic("EPIC-1", "Frontend Epic", "Новое", team1.getId());
        var epic2 = createEpic("EPIC-2", "Backend Epic", "Новое", team2.getId());

        // Then
        var frontendEpics = issueRepository.findByBoardCategoryAndTeamId("EPIC", team1.getId());
        assertEquals(1, frontendEpics.size());
        assertEquals("EPIC-1", frontendEpics.get(0).getIssueKey());

        var backendEpics = issueRepository.findByBoardCategoryAndTeamId("EPIC", team2.getId());
        assertEquals(1, backendEpics.size());
        assertEquals("EPIC-2", backendEpics.get(0).getIssueKey());
    }

    @Test
    @DisplayName("Should enforce FK constraint on changelog")
    void shouldEnforceFkConstraintOnChangelog() {
        // Given - no issues exist
        var team = createTeam("Backend Team");

        // When/Then - try to create changelog with non-existent issue key should throw
        assertThrows(Exception.class, () -> {
            createStatusChange(
                    "NON-EXISTENT-KEY",
                    "non-existent-id",
                    "Новое",
                    "В работе",
                    OffsetDateTime.now()
            );
        });
    }
}

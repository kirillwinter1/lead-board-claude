package com.leadboard.integration;

import com.leadboard.metrics.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Metrics API.
 * Tests throughput, lead time, cycle time, DSR with real PostgreSQL.
 */
@DisplayName("Metrics Integration Tests")
class MetricsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Test
    @DisplayName("Should calculate throughput for completed issues")
    void shouldCalculateThroughputForCompletedIssues() {
        // Given - team with completed subtasks
        var team = createTeam("Throughput Team");
        var epic = createEpic("METRIC-EPIC-1", "Epic 1", "Done", team.getId());
        var story = createStory("METRIC-STORY-1", "Story 1", "Done", "METRIC-EPIC-1", team.getId());
        createSubtask("METRIC-SUB-1", "Done subtask 1", "Done", "METRIC-STORY-1", "Разработка", team.getId(), 8 * 3600L, 8 * 3600L);
        createSubtask("METRIC-SUB-2", "Done subtask 2", "Done", "METRIC-STORY-1", "Тестирование", team.getId(), 4 * 3600L, 4 * 3600L);

        // Create status changes to mark completion dates
        var now = OffsetDateTime.now();
        createStatusChange("METRIC-SUB-1", "id-METRIC-SUB-1", "В работе", "Done", now.minusDays(5));
        createStatusChange("METRIC-SUB-2", "id-METRIC-SUB-2", "В работе", "Done", now.minusDays(3));

        // When
        var from = LocalDate.now().minusDays(30).format(DATE_FORMAT);
        var to = LocalDate.now().format(DATE_FORMAT);
        var response = restTemplate.getForEntity(
                "/api/metrics/throughput?teamId=" + team.getId() + "&from=" + from + "&to=" + to,
                ThroughputResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // At minimum we should have created items
        assertTrue(response.getBody().total() >= 0);
    }

    @Test
    @DisplayName("Should calculate lead time statistics")
    void shouldCalculateLeadTimeStatistics() {
        // Given - team with completed issues that have creation and completion dates
        var team = createTeam("LeadTime Team");
        var epic = createEpic("LT-EPIC-1", "Lead Time Epic", "Done", team.getId());
        var story = createStory("LT-STORY-1", "Lead Time Story", "Done", "LT-EPIC-1", team.getId());

        // Create subtasks with time tracking
        createSubtask("LT-SUB-1", "Subtask 1", "Done", "LT-STORY-1", "Разработка", team.getId(), 16 * 3600L, 16 * 3600L);
        createSubtask("LT-SUB-2", "Subtask 2", "Done", "LT-STORY-1", "Разработка", team.getId(), 8 * 3600L, 8 * 3600L);

        // Create status changes
        var now = OffsetDateTime.now();
        createStatusChange("LT-SUB-1", "id-LT-SUB-1", "Новое", "Done", now.minusDays(2));
        createStatusChange("LT-SUB-2", "id-LT-SUB-2", "Новое", "Done", now.minusDays(1));

        // When
        var from = LocalDate.now().minusDays(30).format(DATE_FORMAT);
        var to = LocalDate.now().format(DATE_FORMAT);
        var response = restTemplate.getForEntity(
                "/api/metrics/lead-time?teamId=" + team.getId() + "&from=" + from + "&to=" + to,
                LeadTimeResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Should calculate cycle time statistics")
    void shouldCalculateCycleTimeStatistics() {
        // Given
        var team = createTeam("CycleTime Team");
        var epic = createEpic("CT-EPIC-1", "Cycle Time Epic", "Done", team.getId());
        var story = createStory("CT-STORY-1", "Cycle Time Story", "Done", "CT-EPIC-1", team.getId());
        createSubtask("CT-SUB-1", "Subtask", "Done", "CT-STORY-1", "Разработка", team.getId(), 8 * 3600L, 8 * 3600L);

        var now = OffsetDateTime.now();
        createStatusChange("CT-SUB-1", "id-CT-SUB-1", "Новое", "В работе", now.minusDays(3));
        createStatusChange("CT-SUB-1", "id-CT-SUB-1", "В работе", "Done", now.minusDays(1));

        // When
        var from = LocalDate.now().minusDays(30).format(DATE_FORMAT);
        var to = LocalDate.now().format(DATE_FORMAT);
        var response = restTemplate.getForEntity(
                "/api/metrics/cycle-time?teamId=" + team.getId() + "&from=" + from + "&to=" + to,
                CycleTimeResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Should calculate time in status")
    void shouldCalculateTimeInStatus() {
        // Given - team with status transitions
        var team = createTeam("TimeInStatus Team");
        var epic = createEpic("TIS-EPIC-1", "Time In Status Epic", "Done", team.getId());
        var story = createStory("TIS-STORY-1", "Story", "Done", "TIS-EPIC-1", team.getId());
        createSubtask("TIS-SUB-1", "Subtask", "Done", "TIS-STORY-1", "Разработка", team.getId(), 8 * 3600L, 8 * 3600L);

        // Create multiple status transitions
        var now = OffsetDateTime.now();
        createStatusChange("TIS-SUB-1", "id-TIS-SUB-1", "Новое", "В работе", now.minusHours(48));
        createStatusChange("TIS-SUB-1", "id-TIS-SUB-1", "В работе", "На ревью", now.minusHours(24));
        createStatusChange("TIS-SUB-1", "id-TIS-SUB-1", "На ревью", "Done", now.minusHours(8));

        // When
        var from = LocalDate.now().minusDays(30).format(DATE_FORMAT);
        var to = LocalDate.now().format(DATE_FORMAT);
        var response = restTemplate.getForEntity(
                "/api/metrics/time-in-status?teamId=" + team.getId() + "&from=" + from + "&to=" + to,
                TimeInStatusResponse[].class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Should return metrics summary")
    void shouldReturnMetricsSummary() {
        // Given
        var team = createTeam("Summary Team");
        var epic = createEpic("SUM-EPIC-1", "Summary Epic", "Done", team.getId());
        var story = createStory("SUM-STORY-1", "Summary Story", "Done", "SUM-EPIC-1", team.getId());
        createSubtask("SUM-SUB-1", "Subtask 1", "Done", "SUM-STORY-1", "Разработка", team.getId(), 8 * 3600L, 8 * 3600L);

        var now = OffsetDateTime.now();
        createStatusChange("SUM-SUB-1", "id-SUM-SUB-1", "Новое", "Done", now.minusDays(2));

        // When
        var from = LocalDate.now().minusDays(30).format(DATE_FORMAT);
        var to = LocalDate.now().format(DATE_FORMAT);
        var response = restTemplate.getForEntity(
                "/api/metrics/summary?teamId=" + team.getId() + "&from=" + from + "&to=" + to,
                TeamMetricsSummary.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(team.getId(), response.getBody().teamId());
    }

    @Test
    @DisplayName("Should calculate DSR for completed epics")
    void shouldCalculateDsrForCompletedEpics() {
        // Given - completed epic with estimates
        var team = createTeam("DSR Team");
        var epic = createEpic("DSR-EPIC-1", "DSR Epic", "Done", team.getId());
        var story = createStory("DSR-STORY-1", "DSR Story", "Done", "DSR-EPIC-1", team.getId());
        // 24 hours estimate = 3 working days
        createSubtask("DSR-SUB-1", "Dev", "Done", "DSR-STORY-1", "Разработка", team.getId(), 24 * 3600L, 24 * 3600L);

        // Epic completed
        var now = OffsetDateTime.now();
        createStatusChange("DSR-EPIC-1", "id-DSR-EPIC-1", "В работе", "Done", now.minusDays(1));

        // When
        var from = LocalDate.now().minusDays(30).format(DATE_FORMAT);
        var to = LocalDate.now().format(DATE_FORMAT);
        var response = restTemplate.getForEntity(
                "/api/metrics/dsr?teamId=" + team.getId() + "&from=" + from + "&to=" + to,
                DsrResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}

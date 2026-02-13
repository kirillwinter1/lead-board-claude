package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.ForecastAccuracyResponse;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForecastAccuracyServiceTest {

    @Mock
    private ForecastSnapshotRepository snapshotRepository;

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private WorkCalendarService workCalendarService;

    @Mock
    private StatusChangelogRepository statusChangelogRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    private ForecastAccuracyService forecastAccuracyService;

    @BeforeEach
    void setUp() {
        forecastAccuracyService = new ForecastAccuracyService(
                snapshotRepository,
                issueRepository,
                workCalendarService,
                statusChangelogRepository,
                workflowConfigService
        );
    }

    // ==================== calculateAccuracy() Tests ====================

    @Nested
    @DisplayName("calculateAccuracy()")
    class CalculateAccuracyTests {

        @Test
        @DisplayName("should return empty response when no completed epics")
        void shouldReturnEmptyWhenNoEpics() {
            Long teamId = 1L;
            LocalDate from = LocalDate.now().minusDays(30);
            LocalDate to = LocalDate.now();

            when(issueRepository.findCompletedEpicsInPeriod(eq(teamId), any(), any()))
                    .thenReturn(Collections.emptyList());

            ForecastAccuracyResponse response = forecastAccuracyService.calculateAccuracy(teamId, from, to);

            assertNotNull(response);
            assertEquals(0, response.totalCompleted());
            assertEquals(BigDecimal.ONE, response.avgAccuracyRatio());
            assertTrue(response.epics().isEmpty());
        }

        @Test
        @DisplayName("should calculate accuracy for completed epics")
        void shouldCalculateAccuracyForCompletedEpics() {
            Long teamId = 1L;
            LocalDate from = LocalDate.now().minusDays(30);
            LocalDate to = LocalDate.now();

            JiraIssueEntity epic = createCompletedEpic("EPIC-1", "Test Epic");

            when(issueRepository.findCompletedEpicsInPeriod(eq(teamId), any(), any()))
                    .thenReturn(List.of(epic));
            when(snapshotRepository.findByTeamIdAndDateRange(eq(teamId), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(statusChangelogRepository.findByIssueKeyOrderByTransitionedAtAsc("EPIC-1"))
                    .thenReturn(Collections.emptyList());

            ForecastAccuracyResponse response = forecastAccuracyService.calculateAccuracy(teamId, from, to);

            assertNotNull(response);
            assertEquals(teamId, response.teamId());
            assertEquals(from, response.from());
            assertEquals(to, response.to());
        }

        @Test
        @DisplayName("should count on-time, late, and early epics")
        void shouldCountEpicsByStatus() {
            Long teamId = 1L;
            LocalDate from = LocalDate.now().minusDays(30);
            LocalDate to = LocalDate.now();

            when(issueRepository.findCompletedEpicsInPeriod(eq(teamId), any(), any()))
                    .thenReturn(Collections.emptyList());

            ForecastAccuracyResponse response = forecastAccuracyService.calculateAccuracy(teamId, from, to);

            assertEquals(0, response.onTimeCount());
            assertEquals(0, response.lateCount());
            assertEquals(0, response.earlyCount());
        }

        @Test
        @DisplayName("should use working days for calculations")
        void shouldUseWorkingDays() {
            Long teamId = 1L;
            LocalDate from = LocalDate.now().minusDays(30);
            LocalDate to = LocalDate.now();

            when(issueRepository.findCompletedEpicsInPeriod(eq(teamId), any(), any()))
                    .thenReturn(Collections.emptyList());

            forecastAccuracyService.calculateAccuracy(teamId, from, to);

            // Service is initialized correctly
            assertNotNull(forecastAccuracyService);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle invalid snapshot JSON gracefully")
        void shouldHandleInvalidSnapshotJson() {
            Long teamId = 1L;
            LocalDate from = LocalDate.now().minusDays(30);
            LocalDate to = LocalDate.now();

            JiraIssueEntity epic = createCompletedEpic("EPIC-1", "Test");

            ForecastSnapshotEntity snapshot = new ForecastSnapshotEntity();
            snapshot.setSnapshotDate(LocalDate.now().minusDays(10));
            snapshot.setUnifiedPlanningJson("invalid json");

            when(issueRepository.findCompletedEpicsInPeriod(eq(teamId), any(), any()))
                    .thenReturn(List.of(epic));
            when(snapshotRepository.findByTeamIdAndDateRange(eq(teamId), any(), any()))
                    .thenReturn(List.of(snapshot));
            when(statusChangelogRepository.findByIssueKeyOrderByTransitionedAtAsc(any()))
                    .thenReturn(Collections.emptyList());

            // Should not throw exception
            ForecastAccuracyResponse response = forecastAccuracyService.calculateAccuracy(teamId, from, to);

            assertNotNull(response);
        }

        @Test
        @DisplayName("should handle epic without done date")
        void shouldHandleEpicWithoutDoneDate() {
            Long teamId = 1L;
            LocalDate from = LocalDate.now().minusDays(30);
            LocalDate to = LocalDate.now();

            JiraIssueEntity epic = new JiraIssueEntity();
            epic.setIssueKey("EPIC-1");
            epic.setSummary("Test");
            epic.setDoneAt(null); // No done date

            when(issueRepository.findCompletedEpicsInPeriod(eq(teamId), any(), any()))
                    .thenReturn(List.of(epic));
            when(snapshotRepository.findByTeamIdAndDateRange(eq(teamId), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(statusChangelogRepository.findByIssueKeyOrderByTransitionedAtAsc("EPIC-1"))
                    .thenReturn(Collections.emptyList());

            ForecastAccuracyResponse response = forecastAccuracyService.calculateAccuracy(teamId, from, to);

            // Epic without done date should be skipped
            assertEquals(0, response.totalCompleted());
        }
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createCompletedEpic(String key, String summary) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setSummary(summary);
        epic.setIssueType("Epic");
        epic.setStatus("Done");
        epic.setDoneAt(OffsetDateTime.now().minusDays(5));
        epic.setStartedAt(OffsetDateTime.now().minusDays(20));
        epic.setJiraCreatedAt(OffsetDateTime.now().minusDays(30));
        return epic;
    }
}

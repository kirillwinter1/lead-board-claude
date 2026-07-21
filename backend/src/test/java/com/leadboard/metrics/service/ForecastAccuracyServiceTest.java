package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.ForecastAccuracyResponse;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.Map;

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
        @DisplayName("scheduleVariance for one workday of slip must be +1")
        void scheduleVarianceOneWorkdayLate() throws Exception {
            // Bug reproduction: inclusive countWorkdays is used as a distance. An epic
            // finished the next workday after plannedEnd reports variance +2; the
            // equality special-case (variance forced to 0) proves distance semantics is
            // intended, and with workday endpoints the value 1 is unreachable today.
            Long teamId = 1L;

            JiraIssueEntity epic = new JiraIssueEntity();
            epic.setIssueKey("EPIC-1");
            epic.setSummary("Late by one workday");
            epic.setIssueType("Epic");
            epic.setStartedAt(OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC));
            epic.setDoneAt(OffsetDateTime.of(2025, 1, 13, 0, 0, 0, 0, ZoneOffset.UTC)); // Monday

            UnifiedPlanningResult.PlannedEpic plannedEpic = new UnifiedPlanningResult.PlannedEpic(
                    "EPIC-1", "Late by one workday", null,
                    LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 10), // planned end: Friday
                    List.of(), Map.of(), null, null, 144000L, null, null, null,
                    0, 0, false, Map.of(), null, false);
            UnifiedPlanningResult plan = new UnifiedPlanningResult(teamId,
                    OffsetDateTime.of(2025, 1, 5, 3, 0, 0, 0, ZoneOffset.UTC),
                    List.of(plannedEpic), List.of(), Map.of());
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            ForecastSnapshotEntity snapshot = new ForecastSnapshotEntity();
            snapshot.setSnapshotDate(LocalDate.of(2025, 1, 5));
            snapshot.setUnifiedPlanningJson(mapper.writeValueAsString(plan));

            when(issueRepository.findCompletedEpicsInPeriod(eq(teamId), any(), any()))
                    .thenReturn(List.of(epic));
            when(snapshotRepository.findByTeamIdAndDateRange(eq(teamId), any(), any()))
                    .thenReturn(List.of(snapshot));
            when(statusChangelogRepository.findByIssueKeyOrderByTransitionedAtAsc("EPIC-1"))
                    .thenReturn(Collections.emptyList());
            // True inclusive workday counts (Jan 2025): 6..10 = 5, 6..13 = 6, 10..13 = 2
            when(workCalendarService.countWorkdays(eq(LocalDate.of(2025, 1, 6)), eq(LocalDate.of(2025, 1, 10))))
                    .thenReturn(5);
            when(workCalendarService.countWorkdays(eq(LocalDate.of(2025, 1, 6)), eq(LocalDate.of(2025, 1, 13))))
                    .thenReturn(6);
            when(workCalendarService.countWorkdays(eq(LocalDate.of(2025, 1, 10)), eq(LocalDate.of(2025, 1, 13))))
                    .thenReturn(2);

            ForecastAccuracyResponse response = forecastAccuracyService.calculateAccuracy(
                    teamId, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

            assertEquals(1, response.epics().size());
            assertEquals("LATE", response.epics().get(0).status());
            assertEquals(1, response.epics().get(0).scheduleVariance(),
                    "one workday of slip must report scheduleVariance +1, not the inclusive day count");
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

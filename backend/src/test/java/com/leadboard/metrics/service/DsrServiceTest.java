package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.DsrResponse;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DsrServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private WorkCalendarService workCalendarService;

    @Mock
    private ForecastSnapshotRepository snapshotRepository;

    private DsrService service;

    @BeforeEach
    void setUp() {
        service = new DsrService(issueRepository, workCalendarService, snapshotRepository);
    }

    @Test
    void calculateDsr_noEpics_returnsEmpty() {
        when(issueRepository.findCompletedEpicsInPeriod(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(0, result.totalEpics());
        assertEquals(0, result.onTimeCount());
        assertTrue(result.epics().isEmpty());
    }

    @Test
    void calculateDsr_withEpics_calculatesCorrectly() {
        JiraIssueEntity epic = createEpic("PROJ-1", "Test Epic",
                OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        // Stories and subtasks with estimates (10 days = 80 hours = 288000 seconds)
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-1-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(10L * 8 * 3600); // 10 days estimate

        when(issueRepository.findCompletedEpicsInPeriod(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-1"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-1-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(10); // 10 working days

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        assertEquals(1, result.epics().size());
        // DSR = 10 workdays / 10 estimate days = 1.00
        assertNotNull(result.epics().get(0).dsrActual());
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.00")));
        // On time (1.00 <= 1.1)
        assertEquals(1, result.onTimeCount());
    }

    @Test
    void calculateDsr_epicWithoutStartDate_usesJiraCreatedAt() {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("PROJ-2");
        epic.setSummary("No start date");
        epic.setDoneAt(OffsetDateTime.of(2025, 1, 20, 0, 0, 0, 0, ZoneOffset.UTC));
        epic.setStartedAt(null);
        epic.setJiraCreatedAt(OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC));

        // Stories and subtasks with estimates (5 days = 40 hours)
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-2-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(5L * 8 * 3600);

        when(issueRepository.findCompletedEpicsInPeriod(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-2"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-2-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 10)), eq(LocalDate.of(2025, 1, 20))))
                .thenReturn(7);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        // DSR = 7 workdays / 5 estimate days = 1.40
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.40")));
        // Not on time (1.40 > 1.1)
        assertEquals(0, result.onTimeCount());
    }

    @Test
    void calculateDsr_epicWithSubtaskEstimates_sumsSubtasks() {
        JiraIssueEntity epic = createEpic("PROJ-3", "With subtasks",
                OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC));
        // Epic has no estimate
        epic.setOriginalEstimateSeconds(null);

        // Story under epic
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-3-S1");

        // Subtasks under story
        JiraIssueEntity sub1 = new JiraIssueEntity();
        sub1.setOriginalEstimateSeconds(2L * 8 * 3600);
        JiraIssueEntity sub2 = new JiraIssueEntity();
        sub2.setOriginalEstimateSeconds(3L * 8 * 3600);

        when(issueRepository.findCompletedEpicsInPeriod(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-3"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-3-S1")))
                .thenReturn(List.of(sub1, sub2));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(5);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        // estimateDays = (2+3)*8*3600 / 3600 / 8 = 5.00
        assertEquals(0, result.epics().get(0).estimateDays().compareTo(new java.math.BigDecimal("5.00")));
        // DSR = 5 / 5 = 1.00
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.00")));
    }

    @Test
    void calculateDsr_epicWithNoEstimate_dsrActualNull() {
        JiraIssueEntity epic = createEpic("PROJ-4", "No estimate",
                OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC));
        epic.setOriginalEstimateSeconds(null);

        when(issueRepository.findCompletedEpicsInPeriod(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-4"))
                .thenReturn(Collections.emptyList());
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(5);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        assertNull(result.epics().get(0).dsrActual());
        assertNull(result.epics().get(0).dsrForecast());
    }

    private JiraIssueEntity createEpic(String key, String summary, OffsetDateTime startedAt, OffsetDateTime doneAt) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setSummary(summary);
        epic.setIssueType("Epic");
        epic.setStartedAt(startedAt);
        epic.setDoneAt(doneAt);
        return epic;
    }
}

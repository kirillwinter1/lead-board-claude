package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.DsrResponse;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
@MockitoSettings(strictness = Strictness.LENIENT)
class DsrServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private WorkCalendarService workCalendarService;

    @Mock
    private ForecastSnapshotRepository snapshotRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    @Mock
    private FlagChangelogService flagChangelogService;

    private DsrService service;

    @BeforeEach
    void setUp() {
        when(workflowConfigService.getRolesInPipelineOrder()).thenReturn(Collections.emptyList());
        service = new DsrService(issueRepository, workCalendarService, snapshotRepository,
                workflowConfigService, flagChangelogService);
    }

    @Test
    void calculateDsr_noEpics_returnsEmpty() {
        when(issueRepository.findEpicsForDsr(any(), any(), any()))
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
        subtask.setDoneAt(OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-1"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-1-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(10); // 10 working days
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        assertEquals(1, result.epics().size());
        assertFalse(result.epics().get(0).inProgress());
        assertEquals(10, result.epics().get(0).calendarWorkingDays());
        assertEquals(0, result.epics().get(0).flaggedDays());
        assertEquals(10, result.epics().get(0).effectiveWorkingDays());
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

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
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
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

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
        sub1.setDoneAt(OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC));
        JiraIssueEntity sub2 = new JiraIssueEntity();
        sub2.setOriginalEstimateSeconds(3L * 8 * 3600);
        sub2.setDoneAt(OffsetDateTime.of(2025, 1, 9, 0, 0, 0, 0, ZoneOffset.UTC));

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-3"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-3-S1")))
                .thenReturn(List.of(sub1, sub2));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(5);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

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

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-4"))
                .thenReturn(Collections.emptyList());
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(5);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        assertNull(result.epics().get(0).dsrActual());
        assertNull(result.epics().get(0).dsrForecast());
    }

    @Test
    void calculateDsr_withFlaggedDays_subtractsFromWorkingDays() {
        JiraIssueEntity epic = createEpic("PROJ-5", "Flagged Epic",
                OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-5-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(8L * 8 * 3600); // 8 days estimate
        subtask.setDoneAt(OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-5"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-5-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(10); // 10 calendar working days
        when(flagChangelogService.calculateFlaggedWorkdays(eq("PROJ-5"), any(), any()))
                .thenReturn(2); // 2 days flagged

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        assertEquals(10, result.epics().get(0).calendarWorkingDays());
        assertEquals(2, result.epics().get(0).flaggedDays());
        assertEquals(8, result.epics().get(0).effectiveWorkingDays());
        // DSR = 8 effective / 8 estimate = 1.00
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.00")));
    }

    @Test
    void calculateDsr_usesMaxSubtaskDoneAt_notEpicDoneAt() {
        // Epic doneAt = Jan 20, but max subtask doneAt = Jan 15
        JiraIssueEntity epic = createEpic("PROJ-6", "Early subtasks",
                OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2025, 1, 20, 0, 0, 0, 0, ZoneOffset.UTC));

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-6-S1");
        JiraIssueEntity sub1 = new JiraIssueEntity();
        sub1.setOriginalEstimateSeconds(5L * 8 * 3600);
        sub1.setDoneAt(OffsetDateTime.of(2025, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC)); // max
        JiraIssueEntity sub2 = new JiraIssueEntity();
        sub2.setOriginalEstimateSeconds(3L * 8 * 3600);
        sub2.setDoneAt(OffsetDateTime.of(2025, 1, 13, 0, 0, 0, 0, ZoneOffset.UTC));

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-6"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-6-S1")))
                .thenReturn(List.of(sub1, sub2));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        // Expect countWorkdays called with startDate=Jan 6 and endDate=Jan 15 (max subtask)
        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 6)), eq(LocalDate.of(2025, 1, 15))))
                .thenReturn(8);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        assertEquals(8, result.epics().get(0).calendarWorkingDays());
        // DSR = 8 / 8 (5+3 estimate) = 1.00
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.00")));
    }

    @Test
    void calculateDsr_noSubtasksDoneAt_fallsBackToEpicDoneAt() {
        JiraIssueEntity epic = createEpic("PROJ-7", "No subtask doneAt",
                OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-7-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(5L * 8 * 3600);
        subtask.setDoneAt(null); // no doneAt

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-7"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-7-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        // Should use epic.doneAt = Jan 17
        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 6)), eq(LocalDate.of(2025, 1, 17))))
                .thenReturn(10);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        assertEquals(10, result.epics().get(0).calendarWorkingDays());
    }

    @Test
    void calculateDsr_inProgressEpic_usesTodayAsEndDate() {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("PROJ-8");
        epic.setSummary("In progress epic");
        epic.setIssueType("Epic");
        epic.setStartedAt(OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC));
        epic.setDoneAt(null); // in-progress

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-8-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(10L * 8 * 3600);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-8"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-8-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(15);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertEquals(1, result.totalEpics());
        assertTrue(result.epics().get(0).inProgress());
        // DSR = 15 / 10 = 1.50
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.50")));
    }

    @Test
    void calculateDsr_mixedCompletedAndInProgress() {
        JiraIssueEntity completedEpic = createEpic("PROJ-9", "Completed",
                OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        JiraIssueEntity inProgressEpic = new JiraIssueEntity();
        inProgressEpic.setIssueKey("PROJ-10");
        inProgressEpic.setSummary("In Progress");
        inProgressEpic.setIssueType("Epic");
        inProgressEpic.setStartedAt(OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC));
        inProgressEpic.setDoneAt(null);

        // Story + subtask for completed epic
        JiraIssueEntity story1 = new JiraIssueEntity();
        story1.setIssueKey("PROJ-9-S1");
        JiraIssueEntity sub1 = new JiraIssueEntity();
        sub1.setOriginalEstimateSeconds(10L * 8 * 3600);
        sub1.setDoneAt(OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        // Story + subtask for in-progress epic
        JiraIssueEntity story2 = new JiraIssueEntity();
        story2.setIssueKey("PROJ-10-S1");
        JiraIssueEntity sub2 = new JiraIssueEntity();
        sub2.setOriginalEstimateSeconds(10L * 8 * 3600);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(completedEpic, inProgressEpic));
        when(issueRepository.findByParentKey("PROJ-9")).thenReturn(List.of(story1));
        when(issueRepository.findByParentKey("PROJ-10")).thenReturn(List.of(story2));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-9-S1"))).thenReturn(List.of(sub1));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-10-S1"))).thenReturn(List.of(sub2));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(10);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertEquals(2, result.totalEpics());
        // One completed, one in-progress
        long completedCount = result.epics().stream().filter(e -> !e.inProgress()).count();
        long inProgressCount = result.epics().stream().filter(e -> e.inProgress()).count();
        assertEquals(1, completedCount);
        assertEquals(1, inProgressCount);
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

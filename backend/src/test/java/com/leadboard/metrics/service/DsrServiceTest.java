package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.DsrResponse;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
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

    @Mock
    private StatusChangelogRepository statusChangelogRepository;

    private DsrService service;

    @BeforeEach
    void setUp() {
        when(workflowConfigService.getRolesInPipelineOrder()).thenReturn(Collections.emptyList());
        service = new DsrService(issueRepository, workCalendarService, snapshotRepository,
                workflowConfigService, flagChangelogService, statusChangelogRepository);
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
    void calculateDsr_withChangelogBasedWorkdays_calculatesCorrectly() {
        JiraIssueEntity epic = createEpic("PROJ-1", "Test Epic",
                OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        // Changelog: entered In Progress on Jan 6, left on Jan 17
        setupChangelog("PROJ-1", List.of(
                changelogEntry(null, "In Progress",
                        OffsetDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("In Progress", "Done",
                        OffsetDateTime.of(2025, 1, 17, 10, 0, 0, 0, ZoneOffset.UTC))
        ));
        when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);
        when(workflowConfigService.isEpicInProgress("Done")).thenReturn(false);

        // Subtask estimate: 10 days
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-1-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(10L * 8 * 3600);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-1"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-1-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 6)), eq(LocalDate.of(2025, 1, 17))))
                .thenReturn(10);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        assertEquals(1, result.epics().size());
        assertEquals(10, result.epics().get(0).inProgressWorkdays());
        assertEquals(0, result.epics().get(0).flaggedDays());
        assertEquals(10, result.epics().get(0).effectiveWorkingDays());
        // DSR = 10 / 10 = 1.00
        assertNotNull(result.epics().get(0).dsrActual());
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.00")));
        assertEquals(1, result.onTimeCount());
    }

    @Test
    void calculateDsr_statusRollback_onlyCountsInProgressPeriods() {
        // LB-1 case: was in In Progress for 5 days, then rolled back to Planned
        JiraIssueEntity epic = createEpicNoDate("LB-1", "Rollback Epic", "Запланировано");

        setupChangelog("LB-1", List.of(
                changelogEntry(null, "In Progress",
                        OffsetDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("In Progress", "Запланировано",
                        OffsetDateTime.of(2025, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC))
        ));
        when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);
        when(workflowConfigService.isEpicInProgress("Запланировано")).thenReturn(false);

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("LB-1-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(10L * 8 * 3600);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("LB-1"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("LB-1-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 6)), eq(LocalDate.of(2025, 1, 10))))
                .thenReturn(5);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        assertEquals(5, result.epics().get(0).inProgressWorkdays());
        // DSR = 5 / 10 = 0.50
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("0.50")));
    }

    @Test
    void calculateDsr_multipleInProgressPeriods_sumsWorkdays() {
        JiraIssueEntity epic = createEpic("PROJ-2", "Multi-period Epic",
                OffsetDateTime.of(2025, 2, 7, 0, 0, 0, 0, ZoneOffset.UTC));

        // Period 1: Jan 6–10 (5 days), Period 2: Jan 20–24 (5 days)
        setupChangelog("PROJ-2", List.of(
                changelogEntry(null, "In Progress",
                        OffsetDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("In Progress", "Blocked",
                        OffsetDateTime.of(2025, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("Blocked", "In Progress",
                        OffsetDateTime.of(2025, 1, 20, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("In Progress", "Done",
                        OffsetDateTime.of(2025, 2, 7, 10, 0, 0, 0, ZoneOffset.UTC))
        ));
        when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);
        when(workflowConfigService.isEpicInProgress("Blocked")).thenReturn(false);
        when(workflowConfigService.isEpicInProgress("Done")).thenReturn(false);

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-2-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(10L * 8 * 3600);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-2"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-2-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 6)), eq(LocalDate.of(2025, 1, 10))))
                .thenReturn(5);
        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 20)), eq(LocalDate.of(2025, 2, 7))))
                .thenReturn(15);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 28));

        assertEquals(1, result.totalEpics());
        assertEquals(20, result.epics().get(0).inProgressWorkdays()); // 5 + 15
        // DSR = 20 / 10 = 2.00
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("2.00")));
    }

    @Test
    void calculateDsr_zeroInProgressDays_excludesEpic() {
        // Epic has no changelog entries → 0 IN_PROGRESS days → excluded
        JiraIssueEntity epic = createEpicNoDate("PROJ-3", "Never started", "Запланировано");

        setupChangelog("PROJ-3", Collections.emptyList());

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(0, result.totalEpics());
        assertTrue(result.epics().isEmpty());
    }

    @Test
    void calculateDsr_flaggedDaysOnlyInInProgressPeriods() {
        JiraIssueEntity epic = createEpic("PROJ-4", "Flagged Epic",
                OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        setupChangelog("PROJ-4", List.of(
                changelogEntry(null, "In Progress",
                        OffsetDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("In Progress", "Done",
                        OffsetDateTime.of(2025, 1, 17, 10, 0, 0, 0, ZoneOffset.UTC))
        ));
        when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);
        when(workflowConfigService.isEpicInProgress("Done")).thenReturn(false);

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-4-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(8L * 8 * 3600);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-4"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-4-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 6)), eq(LocalDate.of(2025, 1, 17))))
                .thenReturn(10);
        when(flagChangelogService.calculateFlaggedWorkdays(
                eq("PROJ-4"),
                eq(LocalDate.of(2025, 1, 6)),
                eq(LocalDate.of(2025, 1, 17))))
                .thenReturn(2);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalEpics());
        assertEquals(10, result.epics().get(0).inProgressWorkdays());
        assertEquals(2, result.epics().get(0).flaggedDays());
        assertEquals(8, result.epics().get(0).effectiveWorkingDays());
        // DSR = 8 / 8 = 1.00
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.00")));
    }

    @Test
    void calculateDsr_epicWithSubtaskEstimates_sumsSubtasks() {
        JiraIssueEntity epic = createEpic("PROJ-5", "With subtasks",
                OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC));

        setupChangelog("PROJ-5", List.of(
                changelogEntry(null, "In Progress",
                        OffsetDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("In Progress", "Done",
                        OffsetDateTime.of(2025, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC))
        ));
        when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);
        when(workflowConfigService.isEpicInProgress("Done")).thenReturn(false);

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-5-S1");
        JiraIssueEntity sub1 = new JiraIssueEntity();
        sub1.setOriginalEstimateSeconds(2L * 8 * 3600);
        JiraIssueEntity sub2 = new JiraIssueEntity();
        sub2.setOriginalEstimateSeconds(3L * 8 * 3600);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-5"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-5-S1")))
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
        // estimateDays = (2+3) = 5.00
        assertEquals(0, result.epics().get(0).estimateDays().compareTo(new java.math.BigDecimal("5.00")));
        // DSR = 5 / 5 = 1.00
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.00")));
    }

    @Test
    void calculateDsr_epicWithNoEstimate_dsrActualNull() {
        JiraIssueEntity epic = createEpicNoDate("PROJ-6", "No estimate", "In Progress");
        epic.setDoneAt(OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC));

        setupChangelog("PROJ-6", List.of(
                changelogEntry(null, "In Progress",
                        OffsetDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("In Progress", "Done",
                        OffsetDateTime.of(2025, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC))
        ));
        when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);
        when(workflowConfigService.isEpicInProgress("Done")).thenReturn(false);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-6"))
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

    @Test
    void calculateDsr_openInProgressPeriod_usesToday() {
        JiraIssueEntity epic = createEpicNoDate("PROJ-7", "Still in progress", "In Progress");

        // Entered In Progress, no exit → still going
        setupChangelog("PROJ-7", List.of(
                changelogEntry(null, "In Progress",
                        OffsetDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneOffset.UTC))
        ));
        when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-7-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(10L * 8 * 3600);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("PROJ-7"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-7-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        // Will be called with (Jan 6, today) — just return some value
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(15);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertEquals(1, result.totalEpics());
        assertEquals(15, result.epics().get(0).inProgressWorkdays());
        // DSR = 15 / 10 = 1.50
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.50")));
    }

    @Test
    void calculateDsr_completedAndInProgress_mixed() {
        JiraIssueEntity completedEpic = createEpic("PROJ-8", "Completed",
                OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));
        JiraIssueEntity inProgressEpic = createEpicNoDate("PROJ-9", "In Progress", "In Progress");

        setupChangelog("PROJ-8", List.of(
                changelogEntry(null, "In Progress",
                        OffsetDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("In Progress", "Done",
                        OffsetDateTime.of(2025, 1, 17, 10, 0, 0, 0, ZoneOffset.UTC))
        ));
        setupChangelog("PROJ-9", List.of(
                changelogEntry(null, "In Progress",
                        OffsetDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneOffset.UTC))
        ));
        when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);
        when(workflowConfigService.isEpicInProgress("Done")).thenReturn(false);

        JiraIssueEntity story1 = new JiraIssueEntity();
        story1.setIssueKey("PROJ-8-S1");
        JiraIssueEntity sub1 = new JiraIssueEntity();
        sub1.setOriginalEstimateSeconds(10L * 8 * 3600);

        JiraIssueEntity story2 = new JiraIssueEntity();
        story2.setIssueKey("PROJ-9-S1");
        JiraIssueEntity sub2 = new JiraIssueEntity();
        sub2.setOriginalEstimateSeconds(10L * 8 * 3600);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(completedEpic, inProgressEpic));
        when(issueRepository.findByParentKey("PROJ-8")).thenReturn(List.of(story1));
        when(issueRepository.findByParentKey("PROJ-9")).thenReturn(List.of(story2));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-8-S1"))).thenReturn(List.of(sub1));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-9-S1"))).thenReturn(List.of(sub2));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(10);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertEquals(2, result.totalEpics());
    }

    @Test
    void calculateInProgressWorkdays_multiplePeriodsWithGaps() {
        JiraIssueEntity epic = createEpicNoDate("TEST-1", "Test", "Done");

        setupChangelog("TEST-1", List.of(
                changelogEntry(null, "In Progress",
                        OffsetDateTime.of(2025, 1, 6, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("In Progress", "Blocked",
                        OffsetDateTime.of(2025, 1, 10, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("Blocked", "In Progress",
                        OffsetDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC)),
                changelogEntry("In Progress", "Done",
                        OffsetDateTime.of(2025, 1, 20, 10, 0, 0, 0, ZoneOffset.UTC))
        ));
        when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);
        when(workflowConfigService.isEpicInProgress("Blocked")).thenReturn(false);
        when(workflowConfigService.isEpicInProgress("Done")).thenReturn(false);

        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 6)), eq(LocalDate.of(2025, 1, 10))))
                .thenReturn(5);
        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 15)), eq(LocalDate.of(2025, 1, 20))))
                .thenReturn(4);

        DsrService.InProgressResult result = service.calculateInProgressWorkdays("TEST-1", epic);

        assertEquals(9, result.totalWorkdays()); // 5 + 4
        assertEquals(2, result.periods().size());
    }

    @Test
    void calculateDsr_completedEpicWithoutChangelog_fallsBackToStartedAt() {
        // Historical epic completed before changelog tracking
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("HIST-1");
        epic.setSummary("Historical Epic");
        epic.setIssueType("Epic");
        epic.setStartedAt(OffsetDateTime.of(2025, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC));
        epic.setDoneAt(OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        // No changelog entries
        setupChangelog("HIST-1", Collections.emptyList());

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("HIST-1-S1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setOriginalEstimateSeconds(10L * 8 * 3600);

        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKey("HIST-1"))
                .thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("HIST-1-S1")))
                .thenReturn(List.of(subtask));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(workCalendarService.countWorkdays(
                eq(LocalDate.of(2025, 1, 6)), eq(LocalDate.of(2025, 1, 17))))
                .thenReturn(10);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any()))
                .thenReturn(0);

        DsrResponse result = service.calculateDsr(1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        // Should NOT be excluded — fallback to startedAt/doneAt
        assertEquals(1, result.totalEpics());
        assertEquals(10, result.epics().get(0).inProgressWorkdays());
        assertEquals(0, result.epics().get(0).dsrActual().compareTo(new java.math.BigDecimal("1.00")));
    }

    // Helper methods

    private JiraIssueEntity createEpic(String key, String summary, OffsetDateTime doneAt) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setSummary(summary);
        epic.setIssueType("Epic");
        epic.setDoneAt(doneAt);
        return epic;
    }

    private JiraIssueEntity createEpicNoDate(String key, String summary, String status) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setSummary(summary);
        epic.setIssueType("Epic");
        epic.setStatus(status);
        epic.setDoneAt(null);
        return epic;
    }

    private void setupChangelog(String issueKey, List<StatusChangelogEntity> entries) {
        when(statusChangelogRepository.findByIssueKeyOrderByTransitionedAtAsc(issueKey))
                .thenReturn(entries);
    }

    private StatusChangelogEntity changelogEntry(String fromStatus, String toStatus, OffsetDateTime transitionedAt) {
        StatusChangelogEntity entry = new StatusChangelogEntity();
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus);
        entry.setTransitionedAt(transitionedAt);
        return entry;
    }
}

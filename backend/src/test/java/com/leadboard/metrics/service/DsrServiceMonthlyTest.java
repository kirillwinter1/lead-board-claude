package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.DsrResponse;
import com.leadboard.metrics.dto.MonthlyDsrResponse;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DsrServiceMonthlyTest {

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
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        service = new DsrService(issueRepository, workCalendarService, snapshotRepository,
                workflowConfigService, flagChangelogService, statusChangelogRepository, objectMapper);
    }

    @Test
    void calculateMonthlyDsr_returnsCorrectMonthCount() {
        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        MonthlyDsrResponse result = service.calculateMonthlyDsr(1L, 6);

        assertNotNull(result);
        assertEquals(1L, result.teamId());
        assertEquals(6, result.months().size());
    }

    @Test
    void calculateMonthlyDsr_defaultTwelveMonths() {
        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        MonthlyDsrResponse result = service.calculateMonthlyDsr(1L, 12);

        assertEquals(12, result.months().size());
    }

    @Test
    void calculateMonthlyDsr_emptyMonthsHaveNullDsr() {
        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        MonthlyDsrResponse result = service.calculateMonthlyDsr(1L, 3);

        for (MonthlyDsrResponse.MonthlyDsrPoint point : result.months()) {
            assertNull(point.avgDsrActual(), "Empty month should have null DSR actual");
            assertNull(point.avgDsrForecast(), "Empty month should have null DSR forecast");
            assertEquals(0, point.totalEpics());
            assertEquals(0, point.onTimeCount());
        }
    }

    @Test
    void calculateMonthlyDsr_clampsMonthsToMax24() {
        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        MonthlyDsrResponse result = service.calculateMonthlyDsr(1L, 100);

        assertEquals(24, result.months().size());
    }

    @Test
    void calculateMonthlyDsr_clampsMonthsToMin1() {
        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        MonthlyDsrResponse result = service.calculateMonthlyDsr(1L, 0);

        assertEquals(1, result.months().size());
    }

    @Test
    void calculateMonthlyDsr_openEpicNotCountedInMonthsBeforeItStarted() {
        // BUG 2 reproduction: findEpicsForDsr returns still-open epics (doneAt IS NULL) for
        // EVERY month window. Before the fix, an open epic's lifetime in-progress days were
        // counted in every historical month — including months before the epic even started,
        // flattening the trend. Contract: a MonthlyDsrPoint means "this month".
        YearMonth currentMonth = YearMonth.now();
        LocalDate inProgressStart = currentMonth.atDay(1);

        JiraIssueEntity openEpic = new JiraIssueEntity();
        openEpic.setIssueKey("OPEN-1");
        openEpic.setSummary("Still in progress");
        openEpic.setIssueType("Epic");
        openEpic.setStatus("In Progress");
        openEpic.setDoneAt(null);

        // The query matches the open epic in every month window.
        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(List.of(openEpic));
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Entered In Progress on the 1st of the current month, never left → open period.
        StatusChangelogEntity entered = new StatusChangelogEntity();
        entered.setFromStatus(null);
        entered.setToStatus("In Progress");
        entered.setTransitionedAt(inProgressStart.atStartOfDay().atOffset(ZoneOffset.UTC));
        when(statusChangelogRepository.findByIssueKeyOrderByTransitionedAtAsc("OPEN-1"))
                .thenReturn(List.of(entered));
        when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);

        // Estimate + workdays so the epic is a valid DSR row in months where it IS counted.
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("OPEN-1-S1");
        story.setParentKey("OPEN-1");
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setIssueKey("OPEN-1-ST1");
        subtask.setParentKey("OPEN-1-S1");
        subtask.setOriginalEstimateSeconds(10L * 8 * 3600);
        when(issueRepository.findByParentKeyIn(List.of("OPEN-1"))).thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("OPEN-1-S1"))).thenReturn(List.of(subtask));
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class))).thenReturn(10);
        when(flagChangelogService.calculateFlaggedWorkdays(any(), any(), any())).thenReturn(0);

        MonthlyDsrResponse result = service.calculateMonthlyDsr(1L, 12);

        assertEquals(12, result.months().size());
        List<MonthlyDsrResponse.MonthlyDsrPoint> months = result.months();

        // The 11 months BEFORE the current month must not include the still-open epic.
        for (int i = 0; i < months.size() - 1; i++) {
            MonthlyDsrResponse.MonthlyDsrPoint point = months.get(i);
            assertEquals(0, point.totalEpics(),
                    "month " + point.month() + " ended before the epic started — must have 0 epics");
            assertNull(point.avgDsrActual(),
                    "month " + point.month() + " must have null DSR (no epics)");
        }

        // The current month DOES include the open epic.
        MonthlyDsrResponse.MonthlyDsrPoint currentPoint = months.get(months.size() - 1);
        assertEquals(1, currentPoint.totalEpics(),
                "the current month overlaps the epic's in-progress period and must count it");
    }

    @Test
    void calculateMonthlyDsr_monthFormatIsCorrect() {
        when(issueRepository.findEpicsForDsr(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(snapshotRepository.findByTeamIdAndDateRange(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        MonthlyDsrResponse result = service.calculateMonthlyDsr(1L, 3);

        for (MonthlyDsrResponse.MonthlyDsrPoint point : result.months()) {
            assertNotNull(point.month());
            assertTrue(point.month().matches("\\d{4}-\\d{2}"),
                    "Month should be in YYYY-MM format, got: " + point.month());
        }
    }
}

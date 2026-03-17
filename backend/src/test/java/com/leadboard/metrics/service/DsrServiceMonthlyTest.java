package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.metrics.dto.DsrResponse;
import com.leadboard.metrics.dto.MonthlyDsrResponse;
import com.leadboard.metrics.repository.StatusChangelogRepository;
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
import java.util.Collections;

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

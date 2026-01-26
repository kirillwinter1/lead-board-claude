package com.leadboard.metrics.service;

import com.leadboard.metrics.dto.*;
import com.leadboard.metrics.repository.MetricsQueryRepository;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamMetricsServiceTest {

    @Mock
    private MetricsQueryRepository metricsRepository;

    @Mock
    private StatusChangelogRepository changelogRepository;

    private TeamMetricsService service;

    @BeforeEach
    void setUp() {
        service = new TeamMetricsService(metricsRepository, changelogRepository);
    }

    @Test
    void calculateThroughput_returnsCorrectCount() {
        // Given
        Long teamId = 1L;
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        Timestamp periodStart = Timestamp.valueOf("2024-01-01 00:00:00");
        List<Object[]> mockData = Arrays.asList(
                new Object[]{periodStart, "Story", 5L},
                new Object[]{periodStart, "Epic", 2L},
                new Object[]{Timestamp.valueOf("2024-01-08 00:00:00"), "Story", 3L}
        );

        when(metricsRepository.getThroughputByWeek(eq(teamId), any(), any(), any(), any(), any()))
                .thenReturn(mockData);

        // When
        ThroughputResponse result = service.calculateThroughput(teamId, from, to, null, null, null);

        // Then
        assertEquals(2, result.totalEpics());
        assertEquals(8, result.totalStories());
        assertEquals(10, result.total());
        assertEquals(2, result.byPeriod().size());
    }

    @Test
    void calculateThroughput_handlesEmptyData() {
        // Given
        Long teamId = 1L;
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        when(metricsRepository.getThroughputByWeek(eq(teamId), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        ThroughputResponse result = service.calculateThroughput(teamId, from, to, null, null, null);

        // Then
        assertEquals(0, result.total());
        assertTrue(result.byPeriod().isEmpty());
    }

    @Test
    void calculateLeadTime_calculatesCorrectStats() {
        // Given
        Long teamId = 1L;
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        List<Object[]> mockData = Arrays.asList(
                new Object[]{new BigDecimal("2.0"), "TEST-1"},
                new Object[]{new BigDecimal("4.0"), "TEST-2"},
                new Object[]{new BigDecimal("6.0"), "TEST-3"},
                new Object[]{new BigDecimal("8.0"), "TEST-4"},
                new Object[]{new BigDecimal("10.0"), "TEST-5"}
        );

        when(metricsRepository.getLeadTimeDays(eq(teamId), any(), any(), any(), any(), any()))
                .thenReturn(mockData);

        // When
        LeadTimeResponse result = service.calculateLeadTime(teamId, from, to, null, null, null);

        // Then
        assertEquals(5, result.sampleSize());
        assertEquals(new BigDecimal("6.00"), result.avgDays());
        assertEquals(new BigDecimal("6.00"), result.medianDays());
        assertEquals(new BigDecimal("2.00"), result.minDays());
        assertEquals(new BigDecimal("10.00"), result.maxDays());
    }

    @Test
    void calculateLeadTime_handlesEmptyData() {
        // Given
        Long teamId = 1L;
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        when(metricsRepository.getLeadTimeDays(eq(teamId), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        LeadTimeResponse result = service.calculateLeadTime(teamId, from, to, null, null, null);

        // Then
        assertEquals(0, result.sampleSize());
        assertEquals(BigDecimal.ZERO, result.avgDays());
    }

    @Test
    void calculateCycleTime_calculatesCorrectly() {
        // Given
        Long teamId = 1L;
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        List<Object[]> mockData = Arrays.asList(
                new Object[]{new BigDecimal("1.5"), "TEST-1"},
                new Object[]{new BigDecimal("3.5"), "TEST-2"}
        );

        when(metricsRepository.getCycleTimeDays(eq(teamId), any(), any(), any(), any(), any()))
                .thenReturn(mockData);

        // When
        CycleTimeResponse result = service.calculateCycleTime(teamId, from, to, null, null, null);

        // Then
        assertEquals(2, result.sampleSize());
        assertEquals(new BigDecimal("2.50"), result.avgDays());
    }

    @Test
    void calculateByAssignee_aggregatesCorrectly() {
        // Given
        Long teamId = 1L;
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        List<Object[]> mockData = Arrays.asList(
                new Object[]{"user1", "John Doe", 10L, new BigDecimal("5.5"), new BigDecimal("3.2")},
                new Object[]{"user2", "Jane Smith", 8L, new BigDecimal("4.0"), new BigDecimal("2.5")}
        );

        when(metricsRepository.getMetricsByAssignee(eq(teamId), any(), any()))
                .thenReturn(mockData);

        // When
        List<AssigneeMetrics> result = service.calculateByAssignee(teamId, from, to);

        // Then
        assertEquals(2, result.size());

        AssigneeMetrics first = result.get(0);
        assertEquals("user1", first.accountId());
        assertEquals("John Doe", first.displayName());
        assertEquals(10, first.issuesClosed());
        assertEquals(new BigDecimal("5.5"), first.avgLeadTimeDays());
        assertEquals(new BigDecimal("3.2"), first.avgCycleTimeDays());
    }

    @Test
    void calculateTimeInStatuses_convertsToHours() {
        // Given
        Long teamId = 1L;
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        // 7200 seconds = 2 hours
        Object[] row = new Object[]{"In Progress", new BigDecimal("7200"), new BigDecimal("3600"), 15L};
        List<Object[]> mockData = Collections.singletonList(row);

        when(changelogRepository.getTimeInStatusStats(eq(teamId), any(), any()))
                .thenReturn(mockData);

        // When
        List<TimeInStatusResponse> result = service.calculateTimeInStatuses(teamId, from, to);

        // Then
        assertEquals(1, result.size());
        TimeInStatusResponse item = result.get(0);
        assertEquals("In Progress", item.status());
        assertEquals(new BigDecimal("2.00"), item.avgHours());
        assertEquals(new BigDecimal("1.00"), item.medianHours());
        assertEquals(15, item.transitionsCount());
    }

    @Test
    void getSummary_returnsAllMetrics() {
        // Given
        Long teamId = 1L;
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        when(metricsRepository.getThroughputByWeek(eq(teamId), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(metricsRepository.getLeadTimeDays(eq(teamId), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(metricsRepository.getCycleTimeDays(eq(teamId), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(changelogRepository.getTimeInStatusStats(eq(teamId), any(), any()))
                .thenReturn(Collections.emptyList());
        when(metricsRepository.getMetricsByAssignee(eq(teamId), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        TeamMetricsSummary result = service.getSummary(teamId, from, to, null, null);

        // Then
        assertNotNull(result);
        assertEquals(from, result.from());
        assertEquals(to, result.to());
        assertEquals(teamId, result.teamId());
        assertNotNull(result.throughput());
        assertNotNull(result.leadTime());
        assertNotNull(result.cycleTime());
        assertNotNull(result.timeInStatuses());
        assertNotNull(result.byAssignee());
    }
}

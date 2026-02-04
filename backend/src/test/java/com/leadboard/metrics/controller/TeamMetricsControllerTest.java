package com.leadboard.metrics.controller;

import com.leadboard.auth.OAuthTokenRepository;
import com.leadboard.metrics.dto.*;
import com.leadboard.metrics.service.DsrService;
import com.leadboard.metrics.service.ForecastAccuracyService;
import com.leadboard.metrics.service.TeamMetricsService;
import com.leadboard.metrics.service.VelocityService;
import com.leadboard.metrics.service.EpicBurndownService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TeamMetricsController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeamMetricsService metricsService;

    @MockBean
    private ForecastAccuracyService forecastAccuracyService;

    @MockBean
    private DsrService dsrService;

    @MockBean
    private VelocityService velocityService;

    @MockBean
    private EpicBurndownService burndownService;

    @MockBean
    private OAuthTokenRepository oAuthTokenRepository;

    @Test
    void getSummary_returnsAllMetrics() throws Exception {
        // Given
        TeamMetricsSummary summary = new TeamMetricsSummary(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
                1L,
                new ThroughputResponse(2, 10, 5, 17, Collections.emptyList(), Collections.emptyList()),
                new LeadTimeResponse(
                        new BigDecimal("5.5"),
                        new BigDecimal("4.0"),
                        new BigDecimal("10.0"),
                        new BigDecimal("1.0"),
                        new BigDecimal("15.0"),
                        17
                ),
                new CycleTimeResponse(
                        new BigDecimal("3.5"),
                        new BigDecimal("2.5"),
                        new BigDecimal("8.0"),
                        new BigDecimal("0.5"),
                        new BigDecimal("12.0"),
                        17
                ),
                Collections.emptyList(),
                Collections.emptyList()
        );

        when(metricsService.getSummary(eq(1L), any(), any(), any(), any()))
                .thenReturn(summary);

        // When & Then
        mockMvc.perform(get("/api/metrics/summary")
                        .param("teamId", "1")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.throughput.total").value(17))
                .andExpect(jsonPath("$.leadTime.avgDays").value(5.5))
                .andExpect(jsonPath("$.cycleTime.avgDays").value(3.5));
    }

    @Test
    void getThroughput_filtersByTeamAndPeriod() throws Exception {
        // Given
        ThroughputResponse response = new ThroughputResponse(
                1, 5, 3, 9,
                Arrays.asList(
                        new PeriodThroughput(
                                LocalDate.of(2024, 1, 1),
                                LocalDate.of(2024, 1, 7),
                                1, 3, 2, 6
                        )
                ),
                Arrays.asList(new BigDecimal("6.0"))
        );

        when(metricsService.calculateThroughput(eq(1L), any(), any(), any(), any(), any()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/metrics/throughput")
                        .param("teamId", "1")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(9))
                .andExpect(jsonPath("$.totalEpics").value(1))
                .andExpect(jsonPath("$.totalStories").value(5))
                .andExpect(jsonPath("$.byPeriod").isArray())
                .andExpect(jsonPath("$.byPeriod[0].total").value(6));
    }

    @Test
    void getByAssignee_returnsCorrectData() throws Exception {
        // Given
        when(metricsService.calculateByAssignee(eq(1L), any(), any()))
                .thenReturn(Arrays.asList(
                        new AssigneeMetrics(
                                "user1",
                                "John Doe",
                                10,
                                new BigDecimal("5.5"),
                                new BigDecimal("3.2"),
                                new BigDecimal("1.05"),
                                new BigDecimal("95"),
                                "UP"
                        ),
                        new AssigneeMetrics(
                                "user2",
                                "Jane Smith",
                                8,
                                new BigDecimal("4.0"),
                                new BigDecimal("2.5"),
                                new BigDecimal("0.92"),
                                new BigDecimal("110"),
                                "STABLE"
                        )
                ));

        // When & Then
        mockMvc.perform(get("/api/metrics/by-assignee")
                        .param("teamId", "1")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].displayName").value("John Doe"))
                .andExpect(jsonPath("$[0].issuesClosed").value(10))
                .andExpect(jsonPath("$[1].displayName").value("Jane Smith"));
    }

    @Test
    void getLeadTime_withFilters() throws Exception {
        // Given
        LeadTimeResponse response = new LeadTimeResponse(
                new BigDecimal("6.0"),
                new BigDecimal("5.0"),
                new BigDecimal("12.0"),
                new BigDecimal("1.0"),
                new BigDecimal("20.0"),
                25
        );

        when(metricsService.calculateLeadTime(eq(1L), any(), any(), eq("Story"), isNull(), isNull()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/metrics/lead-time")
                        .param("teamId", "1")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31")
                        .param("issueType", "Story"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgDays").value(6.0))
                .andExpect(jsonPath("$.medianDays").value(5.0))
                .andExpect(jsonPath("$.sampleSize").value(25));
    }

    @Test
    void getTimeInStatus_returnsData() throws Exception {
        // Given
        when(metricsService.calculateTimeInStatuses(eq(1L), any(), any()))
                .thenReturn(Arrays.asList(
                        new TimeInStatusResponse("In Progress", new BigDecimal("24.5"), new BigDecimal("20.0"), 50),
                        new TimeInStatusResponse("In Review", new BigDecimal("8.2"), new BigDecimal("6.0"), 45)
                ));

        // When & Then
        mockMvc.perform(get("/api/metrics/time-in-status")
                        .param("teamId", "1")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].status").value("In Progress"))
                .andExpect(jsonPath("$[0].avgHours").value(24.5))
                .andExpect(jsonPath("$[1].status").value("In Review"));
    }
}

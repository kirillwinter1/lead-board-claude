package com.leadboard.component;

import com.leadboard.metrics.dto.*;
import com.leadboard.team.TeamEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Metrics API Component Tests")
class MetricsComponentTest extends ComponentTestBase {

    private static final String DATE_FROM = LocalDate.now().minusDays(30).toString();
    private static final String DATE_TO = LocalDate.now().toString();

    @Test
    @DisplayName("GET /api/metrics/throughput returns 200")
    void getThroughput_returns200() {
        // Given
        TeamEntity team = createTeam("Test Team");

        // When
        ResponseEntity<ThroughputResponse> response = restTemplate.getForEntity(
                "/api/metrics/throughput?teamId=" + team.getId() +
                        "&from=" + DATE_FROM + "&to=" + DATE_TO,
                ThroughputResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("GET /api/metrics/lead-time returns 200")
    void getLeadTime_returns200() {
        // Given
        TeamEntity team = createTeam("Test Team");

        // When
        ResponseEntity<LeadTimeResponse> response = restTemplate.getForEntity(
                "/api/metrics/lead-time?teamId=" + team.getId() +
                        "&from=" + DATE_FROM + "&to=" + DATE_TO,
                LeadTimeResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("GET /api/metrics/cycle-time returns 200")
    void getCycleTime_returns200() {
        // Given
        TeamEntity team = createTeam("Test Team");

        // When
        ResponseEntity<CycleTimeResponse> response = restTemplate.getForEntity(
                "/api/metrics/cycle-time?teamId=" + team.getId() +
                        "&from=" + DATE_FROM + "&to=" + DATE_TO,
                CycleTimeResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("GET /api/metrics/summary returns 200")
    void getSummary_returns200() {
        // Given
        TeamEntity team = createTeam("Test Team");

        // When
        ResponseEntity<TeamMetricsSummary> response = restTemplate.getForEntity(
                "/api/metrics/summary?teamId=" + team.getId() +
                        "&from=" + DATE_FROM + "&to=" + DATE_TO,
                TeamMetricsSummary.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("GET /api/metrics/forecast-accuracy returns 200")
    void getForecastAccuracy_returns200() {
        // Given
        TeamEntity team = createTeam("Test Team");

        // When
        ResponseEntity<ForecastAccuracyResponse> response = restTemplate.getForEntity(
                "/api/metrics/forecast-accuracy?teamId=" + team.getId() +
                        "&from=" + DATE_FROM + "&to=" + DATE_TO,
                ForecastAccuracyResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("GET /api/metrics/dsr returns 200")
    void getDsr_returns200() {
        // Given
        TeamEntity team = createTeam("Test Team");

        // When
        ResponseEntity<DsrResponse> response = restTemplate.getForEntity(
                "/api/metrics/dsr?teamId=" + team.getId() +
                        "&from=" + DATE_FROM + "&to=" + DATE_TO,
                DsrResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}

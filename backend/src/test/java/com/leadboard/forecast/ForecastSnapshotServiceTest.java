package com.leadboard.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.forecast.service.ForecastSnapshotService;
import com.leadboard.planning.ForecastService;
import com.leadboard.planning.UnifiedPlanningService;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForecastSnapshotServiceTest {

    @Mock
    private ForecastSnapshotRepository snapshotRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ForecastService forecastService;

    @Mock
    private UnifiedPlanningService unifiedPlanningService;

    private ForecastSnapshotService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = new ForecastSnapshotService(
                snapshotRepository,
                teamRepository,
                forecastService,
                unifiedPlanningService
        );
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void createSnapshot_createsNewSnapshot() {
        // Arrange
        Long teamId = 1L;
        LocalDate today = LocalDate.now();

        when(snapshotRepository.existsByTeamIdAndSnapshotDate(teamId, today)).thenReturn(false);

        ForecastResponse mockForecast = new ForecastResponse(
                OffsetDateTime.now(),
                teamId,
                new ForecastResponse.TeamCapacity(new BigDecimal("8.0"), new BigDecimal("8.0"), new BigDecimal("4.0")),
                new ForecastResponse.WipStatus(5, 3, false, null, null, null),
                Collections.emptyList()
        );
        when(forecastService.calculateForecast(teamId)).thenReturn(mockForecast);

        UnifiedPlanningResult mockPlan = new UnifiedPlanningResult(
                teamId,
                OffsetDateTime.now(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()
        );
        when(unifiedPlanningService.calculatePlan(teamId)).thenReturn(mockPlan);

        when(snapshotRepository.save(any(ForecastSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ForecastSnapshotEntity result = service.createSnapshot(teamId);

        // Assert
        assertNotNull(result);
        assertEquals(teamId, result.getTeamId());
        assertEquals(today, result.getSnapshotDate());
        assertNotNull(result.getForecastJson());
        assertNotNull(result.getUnifiedPlanningJson());

        verify(snapshotRepository).save(any(ForecastSnapshotEntity.class));
    }

    @Test
    void createSnapshot_returnsExistingWhenAlreadyExists() {
        // Arrange
        Long teamId = 1L;
        LocalDate today = LocalDate.now();

        ForecastSnapshotEntity existing = new ForecastSnapshotEntity(
                teamId, today, "{}", "{}"
        );

        when(snapshotRepository.existsByTeamIdAndSnapshotDate(teamId, today)).thenReturn(true);
        when(snapshotRepository.findByTeamIdAndSnapshotDate(teamId, today))
                .thenReturn(Optional.of(existing));

        // Act
        ForecastSnapshotEntity result = service.createSnapshot(teamId);

        // Assert
        assertNotNull(result);
        assertEquals(existing, result);

        verify(forecastService, never()).calculateForecast(any());
        verify(unifiedPlanningService, never()).calculatePlan(any());
    }

    @Test
    void getAvailableDates_returnsDates() {
        // Arrange
        Long teamId = 1L;
        List<LocalDate> dates = List.of(
                LocalDate.now(),
                LocalDate.now().minusDays(1),
                LocalDate.now().minusDays(2)
        );
        when(snapshotRepository.findAvailableDatesByTeamId(teamId)).thenReturn(dates);

        // Act
        List<LocalDate> result = service.getAvailableDates(teamId);

        // Assert
        assertEquals(3, result.size());
        assertEquals(dates, result);
    }

    @Test
    void getUnifiedPlanningFromSnapshot_deserializesCorrectly() throws Exception {
        // Arrange
        Long teamId = 1L;
        LocalDate date = LocalDate.now();

        UnifiedPlanningResult mockPlan = new UnifiedPlanningResult(
                teamId,
                OffsetDateTime.now(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()
        );
        String json = objectMapper.writeValueAsString(mockPlan);

        ForecastSnapshotEntity snapshot = new ForecastSnapshotEntity(
                teamId, date, json, "{}"
        );

        when(snapshotRepository.findByTeamIdAndSnapshotDate(teamId, date))
                .thenReturn(Optional.of(snapshot));

        // Act
        Optional<UnifiedPlanningResult> result = service.getUnifiedPlanningFromSnapshot(teamId, date);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(teamId, result.get().teamId());
    }

    @Test
    void createDailySnapshots_createsForAllActiveTeams() {
        // Arrange
        TeamEntity team1 = new TeamEntity();
        team1.setId(1L);
        team1.setName("Team 1");
        team1.setActive(true);

        TeamEntity team2 = new TeamEntity();
        team2.setId(2L);
        team2.setName("Team 2");
        team2.setActive(true);

        when(teamRepository.findByActiveTrue()).thenReturn(List.of(team1, team2));
        when(snapshotRepository.existsByTeamIdAndSnapshotDate(any(), any())).thenReturn(false);

        ForecastResponse mockForecast = new ForecastResponse(
                OffsetDateTime.now(),
                1L,
                new ForecastResponse.TeamCapacity(new BigDecimal("8.0"), new BigDecimal("8.0"), new BigDecimal("4.0")),
                new ForecastResponse.WipStatus(5, 3, false, null, null, null),
                Collections.emptyList()
        );
        when(forecastService.calculateForecast(any())).thenReturn(mockForecast);

        UnifiedPlanningResult mockPlan = new UnifiedPlanningResult(
                1L,
                OffsetDateTime.now(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()
        );
        when(unifiedPlanningService.calculatePlan(any())).thenReturn(mockPlan);

        when(snapshotRepository.save(any(ForecastSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.createDailySnapshots();

        // Assert
        verify(snapshotRepository, times(2)).save(any(ForecastSnapshotEntity.class));
    }
}

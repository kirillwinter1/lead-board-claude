package com.leadboard.planning;

import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.ForecastResponse.RoleWipStatus;
import com.leadboard.planning.dto.ForecastResponse.WipStatus;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WipSnapshotServiceTest {

    @Mock
    private WipSnapshotRepository snapshotRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ForecastService forecastService;

    private WipSnapshotService wipSnapshotService;

    @BeforeEach
    void setUp() {
        wipSnapshotService = new WipSnapshotService(snapshotRepository, teamRepository, forecastService);
    }

    // ==================== createSnapshot() Tests ====================

    @Nested
    @DisplayName("createSnapshot()")
    class CreateSnapshotTests {

        @Test
        @DisplayName("should create snapshot with WIP data")
        void shouldCreateSnapshotWithWipData() {
            Long teamId = 1L;
            LocalDate today = LocalDate.now();

            WipStatus wipStatus = WipStatus.of(5, 3, Map.of(
                    "SA", RoleWipStatus.of(2, 1),
                    "DEV", RoleWipStatus.of(2, 1),
                    "QA", RoleWipStatus.of(1, 1)));

            ForecastResponse forecast = new ForecastResponse(
                    OffsetDateTime.now(), teamId, Map.of(), wipStatus, List.of());

            when(snapshotRepository.existsByTeamIdAndSnapshotDate(teamId, today)).thenReturn(false);
            when(forecastService.calculateForecast(teamId)).thenReturn(forecast);
            when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            WipSnapshotEntity result = wipSnapshotService.createSnapshot(teamId);

            assertNotNull(result);
            assertEquals(5, result.getTeamWipLimit());
            assertEquals(3, result.getTeamWipCurrent());
            verify(snapshotRepository).save(any(WipSnapshotEntity.class));
        }

        @Test
        @DisplayName("should not create duplicate snapshot for same day")
        void shouldNotCreateDuplicateSnapshot() {
            Long teamId = 1L;
            LocalDate today = LocalDate.now();

            WipSnapshotEntity existing = new WipSnapshotEntity(teamId, today);

            when(snapshotRepository.existsByTeamIdAndSnapshotDate(teamId, today)).thenReturn(true);
            when(snapshotRepository.findTopByTeamIdOrderBySnapshotDateDesc(teamId))
                    .thenReturn(Optional.of(existing));

            WipSnapshotEntity result = wipSnapshotService.createSnapshot(teamId);

            assertEquals(existing, result);
            verify(forecastService, never()).calculateForecast(any());
        }

        @Test
        @DisplayName("should save role WIP data")
        void shouldSaveRoleWipData() {
            Long teamId = 1L;

            WipStatus wipStatus = WipStatus.of(10, 6, Map.of(
                    "SA", RoleWipStatus.of(3, 2),
                    "DEV", RoleWipStatus.of(4, 3),
                    "QA", RoleWipStatus.of(3, 1)));

            ForecastResponse forecast = new ForecastResponse(
                    OffsetDateTime.now(), teamId, Map.of(), wipStatus, List.of());

            when(snapshotRepository.existsByTeamIdAndSnapshotDate(any(), any())).thenReturn(false);
            when(forecastService.calculateForecast(teamId)).thenReturn(forecast);
            when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            wipSnapshotService.createSnapshot(teamId);

            ArgumentCaptor<WipSnapshotEntity> captor = ArgumentCaptor.forClass(WipSnapshotEntity.class);
            verify(snapshotRepository).save(captor.capture());

            WipSnapshotEntity saved = captor.getValue();
            assertNotNull(saved.getRoleWipData());
            assertEquals(3, saved.getRoleWipData().size());

            WipSnapshotEntity.RoleWipEntry saEntry = saved.getRoleWipData().get("SA");
            assertNotNull(saEntry);
            assertEquals(3, saEntry.limit());
            assertEquals(2, saEntry.current());

            WipSnapshotEntity.RoleWipEntry devEntry = saved.getRoleWipData().get("DEV");
            assertNotNull(devEntry);
            assertEquals(4, devEntry.limit());
            assertEquals(3, devEntry.current());

            WipSnapshotEntity.RoleWipEntry qaEntry = saved.getRoleWipData().get("QA");
            assertNotNull(qaEntry);
            assertEquals(3, qaEntry.limit());
            assertEquals(1, qaEntry.current());
        }
    }

    // ==================== getHistory() Tests ====================

    @Nested
    @DisplayName("getHistory()")
    class GetHistoryTests {

        @Test
        @DisplayName("should return snapshots in date range")
        void shouldReturnSnapshotsInRange() {
            Long teamId = 1L;
            LocalDate from = LocalDate.now().minusDays(7);
            LocalDate to = LocalDate.now();

            List<WipSnapshotEntity> snapshots = List.of(
                    new WipSnapshotEntity(teamId, from),
                    new WipSnapshotEntity(teamId, to)
            );

            when(snapshotRepository.findByTeamIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(teamId, from, to))
                    .thenReturn(snapshots);

            List<WipSnapshotEntity> result = wipSnapshotService.getHistory(teamId, from, to);

            assertEquals(2, result.size());
        }
    }

    // ==================== getRecentHistory() Tests ====================

    @Nested
    @DisplayName("getRecentHistory()")
    class GetRecentHistoryTests {

        @Test
        @DisplayName("should return recent N days of history")
        void shouldReturnRecentHistory() {
            Long teamId = 1L;
            int days = 30;

            when(snapshotRepository.findByTeamIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                    eq(teamId), any(), any()))
                    .thenReturn(Collections.emptyList());

            wipSnapshotService.getRecentHistory(teamId, days);

            verify(snapshotRepository).findByTeamIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                    eq(teamId),
                    eq(LocalDate.now().minusDays(days)),
                    eq(LocalDate.now())
            );
        }
    }

    // ==================== createDailySnapshots() Tests ====================

    @Nested
    @DisplayName("createDailySnapshots()")
    class CreateDailySnapshotsTests {

        @Test
        @DisplayName("should create snapshots for all active teams")
        void shouldCreateSnapshotsForAllTeams() {
            TeamEntity team1 = new TeamEntity();
            team1.setId(1L);
            team1.setActive(true);

            TeamEntity team2 = new TeamEntity();
            team2.setId(2L);
            team2.setActive(true);

            WipStatus wipStatus = WipStatus.of(5, 3, Map.of());
            ForecastResponse forecast = new ForecastResponse(
                    OffsetDateTime.now(), 1L, Map.of(), wipStatus, List.of());

            when(teamRepository.findByActiveTrue()).thenReturn(List.of(team1, team2));
            when(snapshotRepository.existsByTeamIdAndSnapshotDate(any(), any())).thenReturn(false);
            when(forecastService.calculateForecast(any())).thenReturn(forecast);
            when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            wipSnapshotService.createDailySnapshots();

            verify(snapshotRepository, times(2)).save(any(WipSnapshotEntity.class));
        }

        @Test
        @DisplayName("should continue on error for one team")
        void shouldContinueOnError() {
            TeamEntity team1 = new TeamEntity();
            team1.setId(1L);

            TeamEntity team2 = new TeamEntity();
            team2.setId(2L);

            WipStatus wipStatus = WipStatus.of(5, 3, Map.of());
            ForecastResponse forecast = new ForecastResponse(
                    OffsetDateTime.now(), 2L, Map.of(), wipStatus, List.of());

            when(teamRepository.findByActiveTrue()).thenReturn(List.of(team1, team2));
            when(snapshotRepository.existsByTeamIdAndSnapshotDate(any(), any())).thenReturn(false);
            when(forecastService.calculateForecast(1L)).thenThrow(new RuntimeException("Error"));
            when(forecastService.calculateForecast(2L)).thenReturn(forecast);
            when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            wipSnapshotService.createDailySnapshots();

            // Should still create snapshot for team2
            verify(snapshotRepository, times(1)).save(any(WipSnapshotEntity.class));
        }
    }

    // ==================== cleanupOldSnapshots() Tests ====================

    @Nested
    @DisplayName("cleanupOldSnapshots()")
    class CleanupOldSnapshotsTests {

        @Test
        @DisplayName("should delete snapshots older than 90 days")
        void shouldDeleteOldSnapshots() {
            wipSnapshotService.cleanupOldSnapshots();

            ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
            verify(snapshotRepository).deleteBySnapshotDateBefore(captor.capture());

            LocalDate cutoff = captor.getValue();
            assertEquals(LocalDate.now().minusDays(90), cutoff);
        }
    }
}

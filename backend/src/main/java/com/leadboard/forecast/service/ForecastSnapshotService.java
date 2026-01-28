package com.leadboard.forecast.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import com.leadboard.forecast.repository.ForecastSnapshotRepository;
import com.leadboard.planning.ForecastService;
import com.leadboard.planning.UnifiedPlanningService;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing forecast snapshots.
 * Creates daily snapshots at 3 AM and provides historical data retrieval.
 */
@Service
public class ForecastSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ForecastSnapshotService.class);

    private final ForecastSnapshotRepository snapshotRepository;
    private final TeamRepository teamRepository;
    private final ForecastService forecastService;
    private final UnifiedPlanningService unifiedPlanningService;
    private final ObjectMapper objectMapper;

    public ForecastSnapshotService(
            ForecastSnapshotRepository snapshotRepository,
            TeamRepository teamRepository,
            ForecastService forecastService,
            UnifiedPlanningService unifiedPlanningService
    ) {
        this.snapshotRepository = snapshotRepository;
        this.teamRepository = teamRepository;
        this.forecastService = forecastService;
        this.unifiedPlanningService = unifiedPlanningService;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Creates a forecast snapshot for a team on the current date.
     */
    @Transactional
    public ForecastSnapshotEntity createSnapshot(Long teamId) {
        LocalDate today = LocalDate.now();

        // Check if snapshot already exists for today
        if (snapshotRepository.existsByTeamIdAndSnapshotDate(teamId, today)) {
            log.debug("Snapshot already exists for team {} on {}", teamId, today);
            return snapshotRepository.findByTeamIdAndSnapshotDate(teamId, today).orElse(null);
        }

        try {
            // Get current forecast and unified planning data
            ForecastResponse forecast = forecastService.calculateForecast(teamId);
            UnifiedPlanningResult unifiedPlan = unifiedPlanningService.calculatePlan(teamId);

            // Serialize to JSON
            String forecastJson = objectMapper.writeValueAsString(forecast);
            String unifiedPlanJson = objectMapper.writeValueAsString(unifiedPlan);

            // Create and save snapshot
            ForecastSnapshotEntity snapshot = new ForecastSnapshotEntity(
                    teamId,
                    today,
                    unifiedPlanJson,
                    forecastJson
            );

            ForecastSnapshotEntity saved = snapshotRepository.save(snapshot);
            log.info("Created forecast snapshot for team {} on {} (epics: {})",
                    teamId, today, unifiedPlan.epics().size());

            return saved;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize forecast data for team {}: {}", teamId, e.getMessage());
            throw new RuntimeException("Failed to create forecast snapshot", e);
        }
    }

    /**
     * Gets a snapshot for a specific team and date.
     */
    public Optional<ForecastSnapshotEntity> getSnapshot(Long teamId, LocalDate date) {
        return snapshotRepository.findByTeamIdAndSnapshotDate(teamId, date);
    }

    /**
     * Gets the unified planning result from a snapshot.
     */
    public Optional<UnifiedPlanningResult> getUnifiedPlanningFromSnapshot(Long teamId, LocalDate date) {
        return getSnapshot(teamId, date)
                .map(snapshot -> {
                    try {
                        return objectMapper.readValue(snapshot.getUnifiedPlanningJson(), UnifiedPlanningResult.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize unified planning from snapshot: {}", e.getMessage());
                        return null;
                    }
                });
    }

    /**
     * Gets the forecast response from a snapshot.
     */
    public Optional<ForecastResponse> getForecastFromSnapshot(Long teamId, LocalDate date) {
        return getSnapshot(teamId, date)
                .map(snapshot -> {
                    try {
                        return objectMapper.readValue(snapshot.getForecastJson(), ForecastResponse.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize forecast from snapshot: {}", e.getMessage());
                        return null;
                    }
                });
    }

    /**
     * Gets all available snapshot dates for a team.
     */
    public List<LocalDate> getAvailableDates(Long teamId) {
        return snapshotRepository.findAvailableDatesByTeamId(teamId);
    }

    /**
     * Gets snapshots within a date range.
     */
    public List<ForecastSnapshotEntity> getSnapshotsInRange(Long teamId, LocalDate from, LocalDate to) {
        return snapshotRepository.findByTeamIdAndDateRange(teamId, from, to);
    }

    /**
     * Scheduled job: creates daily snapshots for all active teams.
     * Runs every day at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void createDailySnapshots() {
        log.info("Starting daily forecast snapshot creation");

        List<TeamEntity> activeTeams = teamRepository.findByActiveTrue();
        int created = 0;
        int failed = 0;

        for (TeamEntity team : activeTeams) {
            try {
                createSnapshot(team.getId());
                created++;
            } catch (Exception e) {
                log.error("Failed to create forecast snapshot for team {}: {}", team.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Completed daily forecast snapshots: {} created, {} failed for {} teams",
                created, failed, activeTeams.size());
    }

    /**
     * Scheduled job: cleans up old snapshots (older than 180 days).
     * Runs every Sunday at 4:00 AM.
     */
    @Scheduled(cron = "0 0 4 * * SUN")
    @Transactional
    public void cleanupOldSnapshots() {
        LocalDate cutoff = LocalDate.now().minusDays(180);
        log.info("Cleaning up forecast snapshots older than {}", cutoff);

        List<TeamEntity> teams = teamRepository.findAll();
        for (TeamEntity team : teams) {
            try {
                snapshotRepository.deleteOldSnapshots(team.getId(), cutoff);
            } catch (Exception e) {
                log.error("Failed to cleanup snapshots for team {}: {}", team.getId(), e.getMessage());
            }
        }
    }

    /**
     * Manually triggers snapshot creation for a team (for testing).
     */
    @Transactional
    public ForecastSnapshotEntity createSnapshotForDate(Long teamId, LocalDate date) {
        // Check if snapshot already exists
        Optional<ForecastSnapshotEntity> existing = snapshotRepository.findByTeamIdAndSnapshotDate(teamId, date);
        if (existing.isPresent()) {
            log.debug("Snapshot already exists for team {} on {}", teamId, date);
            return existing.get();
        }

        try {
            ForecastResponse forecast = forecastService.calculateForecast(teamId);
            UnifiedPlanningResult unifiedPlan = unifiedPlanningService.calculatePlan(teamId);

            String forecastJson = objectMapper.writeValueAsString(forecast);
            String unifiedPlanJson = objectMapper.writeValueAsString(unifiedPlan);

            ForecastSnapshotEntity snapshot = new ForecastSnapshotEntity(
                    teamId,
                    date,
                    unifiedPlanJson,
                    forecastJson
            );

            return snapshotRepository.save(snapshot);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create forecast snapshot", e);
        }
    }
}

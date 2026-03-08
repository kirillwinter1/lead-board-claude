package com.leadboard.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.simulation.dto.SimulationAction;
import com.leadboard.simulation.dto.SimulationLogDto;
import com.leadboard.simulation.dto.SimulationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final SimulationPlanner planner;
    private final SimulationExecutor executor;
    private final SimulationLogRepository logRepository;
    private final SimulationRecoveryService recoveryService;
    private final ObjectMapper objectMapper;

    public SimulationService(SimulationPlanner planner,
                             SimulationExecutor executor,
                             SimulationLogRepository logRepository,
                             SimulationRecoveryService recoveryService,
                             ObjectMapper objectMapper) {
        this.planner = planner;
        this.executor = executor;
        this.logRepository = logRepository;
        this.recoveryService = recoveryService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SimulationLogDto runSimulation(Long teamId, LocalDate date, boolean dryRun) {
        if (teamId == null) {
            throw new IllegalArgumentException("teamId is required");
        }

        // BUG-88/94: self-heal stuck RUNNING simulations in a separate transaction
        recoveryService.recoverStuckSimulations();

        LocalDate simDate = date != null ? date : LocalDate.now();

        // Create log entry with RUNNING status.
        // BUG-76 fix: partial unique index (T10) prevents two RUNNING rows for the same team.
        SimulationLogEntity logEntity = new SimulationLogEntity();
        logEntity.setTeamId(teamId);
        logEntity.setSimDate(simDate);
        logEntity.setDryRun(dryRun);
        logEntity.setStatus("RUNNING");
        logEntity.setStartedAt(OffsetDateTime.now());

        try {
            logRepository.saveAndFlush(logEntity);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Another simulation is already running for team " + teamId);
        }

        try {
            // Plan
            List<SimulationAction> actions = planner.planDay(teamId, simDate);

            // Execute (unless dry run)
            if (!dryRun && !actions.isEmpty()) {
                actions = executor.execute(actions, simDate, teamId);
            }

            if (dryRun) {
                log.info("Simulation dry-run completed: {} actions planned", actions.size());
            }

            // Build summary
            SimulationSummary summary = dryRun
                    ? new SimulationSummary(actions.size(), 0, 0, 0, 0, 0, 0)
                    : SimulationSummary.fromActions(actions);

            // Update log
            logEntity.setActions(toJson(actions));
            logEntity.setSummary(toJson(summary));
            logEntity.setStatus("COMPLETED");
            logEntity.setCompletedAt(OffsetDateTime.now());
            logRepository.save(logEntity);

            return toDto(logEntity, actions, summary);

        } catch (Exception e) {
            log.error("Simulation failed for team {} on {}: {}", teamId, simDate, e.getMessage(), e);

            try {
                logEntity.setStatus("FAILED");
                logEntity.setError(e.getMessage());
                logEntity.setCompletedAt(OffsetDateTime.now());
                logRepository.save(logEntity);
            } catch (Exception saveError) {
                // BUG-88: If even the error save fails, log it but don't mask the original error
                log.error("Failed to save FAILED status for simulation id={}: {}",
                        logEntity.getId(), saveError.getMessage());
            }

            throw new RuntimeException("Simulation failed: " + e.getMessage(), e);
        }
    }

    public SimulationLogDto getLog(Long id) {
        SimulationLogEntity entity = logRepository.findById(id)
                .orElseThrow(() -> new SimulationNotFoundException("Simulation log not found: " + id));
        return toDto(entity);
    }

    public List<SimulationLogDto> getLogs(Long teamId, LocalDate from, LocalDate to) {
        List<SimulationLogEntity> entities;
        if (from != null && to != null) {
            entities = logRepository.findByTeamIdAndDateRange(teamId, from, to);
        } else if (from != null || to != null) {
            // BUG-85: partial date range — require both
            throw new IllegalArgumentException("Both 'from' and 'to' dates are required for date range filtering");
        } else {
            entities = logRepository.findByTeamIdOrderBySimDateDesc(teamId);
        }
        return entities.stream().map(this::toDto).toList();
    }

    public boolean isRunning() {
        return logRepository.existsByStatus("RUNNING");
    }

    private SimulationLogDto toDto(SimulationLogEntity entity) {
        // BUG-80 fix: handle null/corrupt JSON gracefully
        List<SimulationAction> actions = fromJsonList(entity.getActions());
        SimulationSummary summary = fromJson(entity.getSummary(), SimulationSummary.class);
        if (summary == null) {
            summary = new SimulationSummary(0, 0, 0, 0, 0, 0, 0);
        }
        return toDto(entity, actions, summary);
    }

    private SimulationLogDto toDto(SimulationLogEntity entity, List<SimulationAction> actions,
                                   SimulationSummary summary) {
        return new SimulationLogDto(
                entity.getId(),
                entity.getTeamId(),
                entity.getSimDate(),
                entity.isDryRun(),
                actions != null ? actions : Collections.emptyList(),
                summary != null ? summary : new SimulationSummary(0, 0, 0, 0, 0, 0, 0),
                entity.getStatus(),
                entity.getError(),
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }

    // BUG-81 fix: throw on serialization failure instead of silently returning "[]"
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize simulation data to JSON", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to deserialize from JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<SimulationAction> fromJsonList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SimulationAction.class));
        } catch (Exception e) {
            log.error("Failed to deserialize actions from JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}

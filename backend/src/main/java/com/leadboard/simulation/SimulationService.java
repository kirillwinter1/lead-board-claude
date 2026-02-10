package com.leadboard.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leadboard.simulation.dto.SimulationAction;
import com.leadboard.simulation.dto.SimulationLogDto;
import com.leadboard.simulation.dto.SimulationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final SimulationPlanner planner;
    private final SimulationExecutor executor;
    private final SimulationLogRepository logRepository;
    private final ObjectMapper objectMapper;

    public SimulationService(SimulationPlanner planner,
                             SimulationExecutor executor,
                             SimulationLogRepository logRepository) {
        this.planner = planner;
        this.executor = executor;
        this.logRepository = logRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Transactional
    public SimulationLogDto runSimulation(Long teamId, LocalDate date, boolean dryRun) {
        // Guard: no concurrent runs
        if (logRepository.existsByStatus("RUNNING")) {
            throw new IllegalStateException("Another simulation is already running");
        }

        LocalDate simDate = date != null ? date : LocalDate.now();

        // Create log entry
        SimulationLogEntity logEntity = new SimulationLogEntity();
        logEntity.setTeamId(teamId);
        logEntity.setSimDate(simDate);
        logEntity.setDryRun(dryRun);
        logEntity.setStatus("RUNNING");
        logEntity.setStartedAt(OffsetDateTime.now());
        logRepository.save(logEntity);

        try {
            // Plan
            List<SimulationAction> actions = planner.planDay(teamId, simDate);

            // Execute (unless dry run)
            if (!dryRun && !actions.isEmpty()) {
                actions = executor.execute(actions, simDate, teamId);
            }

            // For dry run, mark all as "not executed"
            if (dryRun) {
                // Actions are already not executed (executed=false)
                log.info("Simulation dry-run completed: {} actions planned", actions.size());
            }

            // Build summary
            SimulationSummary summary = dryRun
                    ? new SimulationSummary(actions.size(), 0, 0, 0, 0, 0)
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

            logEntity.setStatus("FAILED");
            logEntity.setError(e.getMessage());
            logEntity.setCompletedAt(OffsetDateTime.now());
            logRepository.save(logEntity);

            throw new RuntimeException("Simulation failed: " + e.getMessage(), e);
        }
    }

    public SimulationLogDto getLog(Long id) {
        SimulationLogEntity entity = logRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Simulation log not found: " + id));
        return toDto(entity);
    }

    public List<SimulationLogDto> getLogs(Long teamId, LocalDate from, LocalDate to) {
        List<SimulationLogEntity> entities;
        if (from != null && to != null) {
            entities = logRepository.findByTeamIdAndDateRange(teamId, from, to);
        } else {
            entities = logRepository.findByTeamIdOrderBySimDateDesc(teamId);
        }
        return entities.stream().map(this::toDto).toList();
    }

    public boolean isRunning() {
        return logRepository.existsByStatus("RUNNING");
    }

    private SimulationLogDto toDto(SimulationLogEntity entity) {
        List<SimulationAction> actions = fromJson(entity.getActions(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SimulationAction.class));
        SimulationSummary summary = fromJson(entity.getSummary(), SimulationSummary.class);
        return toDto(entity, actions, summary);
    }

    private SimulationLogDto toDto(SimulationLogEntity entity, List<SimulationAction> actions,
                                   SimulationSummary summary) {
        return new SimulationLogDto(
                entity.getId(),
                entity.getTeamId(),
                entity.getSimDate(),
                entity.isDryRun(),
                actions,
                summary,
                entity.getStatus(),
                entity.getError(),
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON", e);
            return "[]";
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to deserialize from JSON", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T fromJson(String json, com.fasterxml.jackson.databind.JavaType type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to deserialize from JSON", e);
            return null;
        }
    }
}

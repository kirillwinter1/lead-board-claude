package com.leadboard.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "simulation.enabled", havingValue = "true")
public class SimulationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SimulationScheduler.class);

    private final SimulationService simulationService;
    private final SimulationProperties properties;

    public SimulationScheduler(SimulationService simulationService, SimulationProperties properties) {
        this.simulationService = simulationService;
        this.properties = properties;
    }

    @Scheduled(cron = "${simulation.cron:0 0 19 * * MON-FRI}")
    public void runScheduled() {
        log.info("Simulation scheduler triggered");

        for (Long teamId : properties.getTeamIds()) {
            try {
                log.info("Running scheduled simulation for team {}", teamId);
                simulationService.runSimulation(teamId, LocalDate.now(), false);
            } catch (Exception e) {
                log.error("Scheduled simulation failed for team {}: {}", teamId, e.getMessage(), e);
            }
        }
    }
}

package com.leadboard.simulation;

import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@ConditionalOnProperty(name = "simulation.enabled", havingValue = "true")
public class SimulationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SimulationScheduler.class);

    private final SimulationService simulationService;
    private final SimulationRecoveryService recoveryService;
    private final SimulationProperties properties;
    private final TenantRepository tenantRepository;

    public SimulationScheduler(SimulationService simulationService,
                                SimulationRecoveryService recoveryService,
                                SimulationProperties properties,
                                TenantRepository tenantRepository) {
        this.simulationService = simulationService;
        this.recoveryService = recoveryService;
        this.properties = properties;
        this.tenantRepository = tenantRepository;
    }

    @Scheduled(cron = "${simulation.cron:0 0 19 * * MON-FRI}")
    public void runScheduled() {
        log.info("Simulation scheduler triggered");

        if (properties.getTeamIds().isEmpty()) {
            log.warn("Simulation scheduler enabled but no teamIds configured — skipping");
            return;
        }

        // BUG-77 fix: iterate tenants and set TenantContext like TenantSyncScheduler
        List<TenantEntity> tenants = tenantRepository.findAllActive();

        if (tenants.isEmpty()) {
            // Single-tenant mode: run without TenantContext
            runForTeams();
        } else {
            for (TenantEntity tenant : tenants) {
                try {
                    TenantContext.setTenant(tenant.getId(), tenant.getSchemaName());
                    runForTeams();
                } catch (Exception e) {
                    log.error("Scheduled simulation failed for tenant '{}': {}",
                            tenant.getSlug(), e.getMessage(), e);
                } finally {
                    TenantContext.clear();
                }
            }
        }
    }

    private void runForTeams() {
        // BUG-88/94: Recover stuck RUNNING simulations in a separate transaction
        recoveryService.recoverStuckSimulations();

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

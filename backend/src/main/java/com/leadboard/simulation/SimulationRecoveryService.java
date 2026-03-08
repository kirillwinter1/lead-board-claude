package com.leadboard.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * BUG-94 fix: Recovery must run in a SEPARATE transaction from runSimulation().
 * Hibernate flushes INSERTs before UPDATEs, so if recovery (UPDATE) and
 * new simulation (INSERT) are in the same transaction, the INSERT fails
 * because the old RUNNING row hasn't been updated yet.
 */
@Service
public class SimulationRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(SimulationRecoveryService.class);

    private final SimulationLogRepository logRepository;

    public SimulationRecoveryService(SimulationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverStuckSimulations() {
        try {
            List<SimulationLogEntity> stuck = logRepository.findByStatus("RUNNING");
            for (SimulationLogEntity entity : stuck) {
                Duration stuckDuration = Duration.between(entity.getStartedAt(), OffsetDateTime.now());
                if (stuckDuration.toMinutes() > 10) {
                    log.warn("Recovering stuck simulation id={} for team {} (stuck for {})",
                            entity.getId(), entity.getTeamId(), stuckDuration);
                    entity.setStatus("FAILED");
                    entity.setError("Auto-recovered: simulation was stuck for " + stuckDuration.toMinutes() + " minutes");
                    entity.setCompletedAt(OffsetDateTime.now());
                    logRepository.save(entity);
                }
            }
        } catch (Exception e) {
            log.warn("Could not recover stuck simulations: {}", e.getMessage());
        }
    }
}

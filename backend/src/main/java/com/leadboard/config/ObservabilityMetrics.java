package com.leadboard.config;

import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ObservabilityMetrics {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityMetrics.class);

    private final MeterRegistry registry;
    private final JiraIssueRepository issueRepository;
    private final TenantRepository tenantRepository;

    private final Timer syncDuration;
    private final Counter issuesSynced;
    private final Counter syncErrors;
    private final AtomicLong issuesTotal = new AtomicLong(0);
    private final AtomicLong tenantsActive = new AtomicLong(0);

    public ObservabilityMetrics(MeterRegistry registry,
                                JiraIssueRepository issueRepository,
                                TenantRepository tenantRepository) {
        this.registry = registry;
        this.issueRepository = issueRepository;
        this.tenantRepository = tenantRepository;

        this.syncDuration = Timer.builder("leadboard.sync.duration")
                .description("Duration of sync operations")
                .register(registry);

        this.issuesSynced = Counter.builder("leadboard.sync.issues_synced")
                .description("Number of issues synced from Jira")
                .register(registry);

        this.syncErrors = Counter.builder("leadboard.sync.errors")
                .description("Number of sync errors")
                .register(registry);

        registry.gauge("leadboard.issues.total", issuesTotal);
        registry.gauge("leadboard.tenants.active", tenantsActive);
    }

    public Timer.Sample startSyncTimer() {
        return Timer.start(registry);
    }

    public void stopSyncTimer(Timer.Sample sample) {
        sample.stop(syncDuration);
    }

    public void recordIssuesSynced(int count) {
        issuesSynced.increment(count);
    }

    public void recordSyncError() {
        syncErrors.increment();
    }

    @Scheduled(fixedDelay = 60_000)
    public void refreshGauges() {
        try {
            List<TenantEntity> tenants = tenantRepository.findAllActive();
            tenantsActive.set(tenants.size());

            long totalIssues = 0;
            for (TenantEntity tenant : tenants) {
                try {
                    TenantContext.setTenant(tenant.getId(), tenant.getSchemaName());
                    totalIssues += issueRepository.count();
                } catch (Exception e) {
                    log.debug("Could not count issues for tenant '{}': {}", tenant.getSlug(), e.getMessage());
                } finally {
                    TenantContext.clear();
                }
            }
            issuesTotal.set(totalIssues);
        } catch (Exception e) {
            log.debug("Could not refresh gauges: {}", e.getMessage());
        }
    }
}

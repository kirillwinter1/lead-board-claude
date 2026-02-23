package com.leadboard.tenant;

import com.leadboard.sync.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler that iterates over active tenants and triggers sync for each.
 * Each tenant may have multiple project keys in their Jira config.
 */
@Component
public class TenantSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(TenantSyncScheduler.class);

    private final TenantRepository tenantRepository;
    private final TenantJiraConfigRepository jiraConfigRepository;
    private final SyncService syncService;

    public TenantSyncScheduler(TenantRepository tenantRepository,
                                TenantJiraConfigRepository jiraConfigRepository,
                                SyncService syncService) {
        this.tenantRepository = tenantRepository;
        this.jiraConfigRepository = jiraConfigRepository;
        this.syncService = syncService;
    }

    /**
     * Runs every 60 seconds, checks each active tenant if sync is due.
     */
    @Scheduled(fixedDelay = 60_000)
    public void scheduledTenantSync() {
        List<TenantEntity> tenants = tenantRepository.findAllActive();
        if (tenants.isEmpty()) {
            return;
        }

        for (TenantEntity tenant : tenants) {
            try {
                TenantContext.setTenant(tenant.getId(), tenant.getSchemaName());
                syncTenant(tenant);
            } catch (Exception e) {
                log.error("Sync failed for tenant '{}': {}", tenant.getSlug(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void syncTenant(TenantEntity tenant) {
        TenantJiraConfigEntity config;
        try {
            config = jiraConfigRepository.findActive().orElse(null);
        } catch (Exception e) {
            log.debug("No jira config for tenant '{}'", tenant.getSlug());
            return;
        }

        if (config == null) {
            return;
        }

        List<String> projectKeys = config.getProjectKeysList();
        for (String projectKey : projectKeys) {
            try {
                syncService.syncProjectForTenant(projectKey);
            } catch (Exception e) {
                log.error("Sync failed for tenant '{}' project '{}': {}",
                        tenant.getSlug(), projectKey, e.getMessage());
            }
        }
    }
}

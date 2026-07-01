package com.leadboard.sync;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantEntity;
import com.leadboard.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-off системный re-import changelog по всем активным tenant'ам (F80).
 *
 * <p>Включается флагом {@code app.reimport-changelogs=true} (env APP_REIMPORT_CHANGELOGS=true).
 * Нужен для заполнения нового поля {@code author_account_id} (closedBy) в исторических записях
 * status_changelog. Работает от системного BasicAuth (Jira), без пользовательской сессии,
 * поэтому проходит по tenant-схемам напрямую (домен не резолвит tenant).</p>
 *
 * <p>Выполняется в фоновом потоке, чтобы не блокировать старт приложения. После прогона
 * флаг нужно снять и перезапустить backend.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app", name = "reimport-changelogs", havingValue = "true")
public class ChangelogReimportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChangelogReimportRunner.class);

    private final TenantRepository tenantRepository;
    private final JiraConfigResolver jiraConfigResolver;
    private final ChangelogImportService changelogImportService;

    public ChangelogReimportRunner(TenantRepository tenantRepository,
                                   JiraConfigResolver jiraConfigResolver,
                                   ChangelogImportService changelogImportService) {
        this.tenantRepository = tenantRepository;
        this.jiraConfigResolver = jiraConfigResolver;
        this.changelogImportService = changelogImportService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(() -> {
            log.warn("=== One-off changelog re-import STARTED (all tenants) ===");
            List<TenantEntity> tenants = tenantRepository.findAllActive();
            for (TenantEntity tenant : tenants) {
                try {
                    TenantContext.setTenant(tenant.getId(), tenant.getSchemaName());
                    List<String> keys = jiraConfigResolver.getActiveProjectKeys();
                    for (String pk : keys) {
                        log.warn("Re-import changelog: tenant={} project={}", tenant.getSlug(), pk);
                        var result = changelogImportService.importAllChangelogs(pk, 6);
                        log.warn("Re-import done: tenant={} project={} result={}", tenant.getSlug(), pk, result);
                    }
                } catch (Exception e) {
                    log.error("Re-import failed for tenant {}: {}", tenant.getSlug(), e.getMessage(), e);
                } finally {
                    TenantContext.clear();
                }
            }
            log.warn("=== One-off changelog re-import FINISHED — remove APP_REIMPORT_CHANGELOGS and restart ===");
        }, "changelog-reimport");
        t.setDaemon(true);
        t.start();
        log.info("Changelog re-import launched in background");
    }
}

package com.leadboard.integration;

import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantJiraConfigEntity;
import com.leadboard.tenant.TenantJiraConfigReader;
import com.leadboard.tenant.TenantJiraConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the OAuth-callback login failure: {@code tenant_jira_config} lives in
 * the tenant schema, but the callback's {@code @Transactional} session is created on the
 * bare backend host (no tenant → search_path=public) BEFORE {@link TenantContext} is
 * switched to the tenant. Hibernate resolves the schema once per session, so a mid-
 * transaction context switch does not move queries to the tenant schema — the lookup died
 * with "relation does not exist" and PostgreSQL aborted the whole login transaction.
 *
 * <p>{@link TenantJiraConfigReader} fixes this with {@code REQUIRES_NEW}: a fresh
 * transaction opens a fresh session that picks up the already-switched context.</p>
 */
class TenantJiraConfigReaderIntegrationTest extends IntegrationTestBase {

    private static final String CLOUD_ID = "cloud-integtest-1";

    @Autowired
    private TenantJiraConfigReader reader;

    @Autowired
    private TenantJiraConfigRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private com.leadboard.tenant.TenantRepository tenantRepo;

    private Long tenantId;
    private String tenantSchema;

    @BeforeEach
    void seedConfig() {
        // Base class setUp() has set TenantContext to the test tenant schema.
        tenantId = TenantContext.getCurrentTenantId();
        tenantSchema = TenantContext.getCurrentSchema();
        if (repository.findActive().isEmpty()) {
            TenantJiraConfigEntity config = new TenantJiraConfigEntity();
            config.setJiraCloudId(CLOUD_ID);
            config.setProjectKeys("TEST");
            config.setActive(true);
            repository.save(config);
        }
    }

    @Test
    @DisplayName("reader resolves the tenant's cloudId even from inside a transaction pinned to public")
    void readerWorksInsidePublicPinnedTransaction() {
        TenantContext.clear();
        try {
            Optional<String> cloudId = transactionTemplate.execute(status -> {
                // Pin this transaction's Hibernate session to the public schema (this is what
                // the OAuth callback's earlier user/token queries do before the gate runs).
                assertTrue(tenantRepo.existsBySlug("integtest"));

                // Mid-transaction tenant switch — exactly what resolveTenantJiraCloudId does.
                TenantContext.setTenant(tenantId, tenantSchema);
                try {
                    return reader.findActiveCloudId();
                } finally {
                    TenantContext.clear();
                }
            });

            assertNotNull(cloudId);
            assertEquals(CLOUD_ID, cloudId.orElse(null));
        } finally {
            TenantContext.setTenant(tenantId, tenantSchema);
        }
    }

    @Test
    @DisplayName("documents the pitfall: a direct repository read after a mid-transaction switch still hits public and fails")
    void directRepositoryReadInsidePublicPinnedTransactionFails() {
        TenantContext.clear();
        try {
            assertThrows(DataAccessException.class, () -> transactionTemplate.execute(status -> {
                assertTrue(tenantRepo.existsBySlug("integtest"));

                TenantContext.setTenant(tenantId, tenantSchema);
                try {
                    // Session was created with search_path=public; tenant_jira_config only
                    // exists in the tenant schema → "relation does not exist".
                    return repository.findActive();
                } finally {
                    TenantContext.clear();
                }
            }));
        } finally {
            TenantContext.setTenant(tenantId, tenantSchema);
        }
    }
}

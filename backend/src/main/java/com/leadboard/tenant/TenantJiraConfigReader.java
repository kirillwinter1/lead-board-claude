package com.leadboard.tenant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Reads {@code tenant_jira_config} in a fresh transaction so the lookup works even when the
 * caller is already inside an open transaction.
 *
 * <p>Hibernate resolves the tenant schema ({@link TenantSchemaResolver} → search_path) once,
 * when the session is created at transaction begin. Switching {@link TenantContext} mid-
 * transaction therefore has no effect on queries in that transaction: they keep hitting the
 * schema the session started with. The OAuth callback runs as {@code @Transactional} on the
 * bare backend host (no tenant → public), so reading the per-tenant-schema
 * {@code tenant_jira_config} from inside it failed with "relation does not exist" and PG
 * aborted the whole login transaction. {@code REQUIRES_NEW} suspends the caller's
 * transaction and opens a new session that picks up the {@link TenantContext} the caller
 * set just before invoking this bean.</p>
 */
@Component
public class TenantJiraConfigReader {

    private final TenantJiraConfigRepository repository;

    public TenantJiraConfigReader(TenantJiraConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the active config's Jira cloudId for the tenant currently set in
     * {@link TenantContext}. The caller must set the tenant context BEFORE calling — the new
     * session's schema is resolved at transaction begin, i.e. on entry to this method.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<String> findActiveCloudId() {
        return repository.findActive().map(TenantJiraConfigEntity::getJiraCloudId);
    }
}

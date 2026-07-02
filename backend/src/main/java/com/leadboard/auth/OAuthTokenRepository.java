package com.leadboard.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthTokenEntity, Long> {

    Optional<OAuthTokenEntity> findByUserId(Long userId);

    /**
     * Global "latest updated token" lookup — deliberately NOT tenant-scoped.
     * Only safe to use in single-tenant deployments (no TenantContext, .env-driven config),
     * where there is at most one Jira integration in the whole deployment.
     * In multi-tenant mode, use {@link #findLatestTokenForTenant(Long)} instead —
     * this method would otherwise leak/return another tenant's OAuth token.
     */
    @Query("SELECT t FROM OAuthTokenEntity t JOIN FETCH t.user ORDER BY t.updatedAt DESC LIMIT 1")
    Optional<OAuthTokenEntity> findLatestToken();

    /**
     * Latest updated OAuth token belonging to a user who is a member of the given tenant
     * (via {@code tenant_users}). Used to resolve the Jira OAuth token strictly within the
     * current tenant, preventing cross-tenant token/cloudId leakage in multi-tenant mode.
     */
    @Query("SELECT t FROM OAuthTokenEntity t JOIN FETCH t.user u " +
            "WHERE EXISTS (SELECT 1 FROM TenantUserEntity tu WHERE tu.tenant.id = :tenantId AND tu.user = u) " +
            "ORDER BY t.updatedAt DESC LIMIT 1")
    Optional<OAuthTokenEntity> findLatestTokenForTenant(Long tenantId);

    @Query("SELECT t FROM OAuthTokenEntity t JOIN FETCH t.user WHERE t.user.atlassianAccountId = :accountId")
    Optional<OAuthTokenEntity> findByAtlassianAccountId(String accountId);
}

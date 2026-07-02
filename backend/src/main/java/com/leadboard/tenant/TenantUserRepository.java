package com.leadboard.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUserEntity, Long> {

    @Query("SELECT tu FROM TenantUserEntity tu JOIN FETCH tu.tenant JOIN FETCH tu.user WHERE tu.tenant.id = :tenantId AND tu.user.id = :userId")
    Optional<TenantUserEntity> findByTenantIdAndUserId(Long tenantId, Long userId);

    /**
     * F82: same lookup as {@link #findByTenantIdAndUserId}, but only returns the membership
     * if it is active. Used by authentication paths (LeadBoardAuthenticationFilter,
     * McpJwtContextFilter) so a deactivated membership (Jira access lost) is treated exactly
     * like "not a member" — no fallback, no partial access.
     */
    @Query("SELECT tu FROM TenantUserEntity tu JOIN FETCH tu.tenant JOIN FETCH tu.user " +
            "WHERE tu.tenant.id = :tenantId AND tu.user.id = :userId AND tu.active = true")
    Optional<TenantUserEntity> findByTenantIdAndUserIdAndActiveTrue(Long tenantId, Long userId);

    @Query("SELECT tu FROM TenantUserEntity tu JOIN FETCH tu.tenant WHERE tu.user.id = :userId")
    List<TenantUserEntity> findByUserId(Long userId);

    @Query("SELECT tu FROM TenantUserEntity tu JOIN FETCH tu.user WHERE tu.tenant.id = :tenantId")
    List<TenantUserEntity> findByTenantId(Long tenantId);

    /**
     * F82: all active memberships across all tenants — used by {@code TenantAccessReconciler}
     * to periodically re-verify Jira access.
     */
    @Query("SELECT tu FROM TenantUserEntity tu JOIN FETCH tu.tenant JOIN FETCH tu.user WHERE tu.active = true")
    List<TenantUserEntity> findAllByActiveTrue();

    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);
}

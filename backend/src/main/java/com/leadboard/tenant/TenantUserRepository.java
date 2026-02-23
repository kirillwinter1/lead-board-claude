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

    @Query("SELECT tu FROM TenantUserEntity tu JOIN FETCH tu.tenant WHERE tu.user.id = :userId")
    List<TenantUserEntity> findByUserId(Long userId);

    @Query("SELECT tu FROM TenantUserEntity tu JOIN FETCH tu.user WHERE tu.tenant.id = :tenantId")
    List<TenantUserEntity> findByTenantId(Long tenantId);

    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);
}

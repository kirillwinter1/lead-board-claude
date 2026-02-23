package com.leadboard.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantJiraConfigRepository extends JpaRepository<TenantJiraConfigEntity, Long> {

    @Query("SELECT c FROM TenantJiraConfigEntity c WHERE c.active = true ORDER BY c.id LIMIT 1")
    Optional<TenantJiraConfigEntity> findActive();
}

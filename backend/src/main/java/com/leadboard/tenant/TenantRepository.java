package com.leadboard.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    Optional<TenantEntity> findBySlug(String slug);

    Optional<TenantEntity> findBySchemaName(String schemaName);

    boolean existsBySlug(String slug);

    boolean existsBySchemaName(String schemaName);

    @Query("SELECT t FROM TenantEntity t WHERE t.active = true")
    List<TenantEntity> findAllActive();
}

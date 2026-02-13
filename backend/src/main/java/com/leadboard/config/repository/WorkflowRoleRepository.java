package com.leadboard.config.repository;

import com.leadboard.config.entity.WorkflowRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRoleRepository extends JpaRepository<WorkflowRoleEntity, Long> {
    List<WorkflowRoleEntity> findByConfigIdOrderBySortOrderAsc(Long configId);
    Optional<WorkflowRoleEntity> findByConfigIdAndCode(Long configId, String code);
    Optional<WorkflowRoleEntity> findByConfigIdAndIsDefaultTrue(Long configId);
    @Transactional
    void deleteByConfigId(Long configId);
}

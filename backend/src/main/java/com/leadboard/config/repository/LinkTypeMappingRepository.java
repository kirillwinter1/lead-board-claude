package com.leadboard.config.repository;

import com.leadboard.config.entity.LinkTypeMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface LinkTypeMappingRepository extends JpaRepository<LinkTypeMappingEntity, Long> {
    List<LinkTypeMappingEntity> findByConfigId(Long configId);
    Optional<LinkTypeMappingEntity> findByConfigIdAndJiraLinkTypeName(Long configId, String jiraLinkTypeName);
    @Transactional
    void deleteByConfigId(Long configId);
}

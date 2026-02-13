package com.leadboard.config.repository;

import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.entity.StatusMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface StatusMappingRepository extends JpaRepository<StatusMappingEntity, Long> {
    List<StatusMappingEntity> findByConfigId(Long configId);
    List<StatusMappingEntity> findByConfigIdAndIssueCategory(Long configId, BoardCategory issueCategory);
    Optional<StatusMappingEntity> findByConfigIdAndJiraStatusNameAndIssueCategory(
            Long configId, String jiraStatusName, BoardCategory issueCategory);
    @Transactional
    void deleteByConfigId(Long configId);
}

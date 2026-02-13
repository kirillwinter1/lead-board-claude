package com.leadboard.config.repository;

import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.entity.IssueTypeMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssueTypeMappingRepository extends JpaRepository<IssueTypeMappingEntity, Long> {
    List<IssueTypeMappingEntity> findByConfigId(Long configId);
    List<IssueTypeMappingEntity> findByConfigIdAndBoardCategory(Long configId, BoardCategory boardCategory);
    Optional<IssueTypeMappingEntity> findByConfigIdAndJiraTypeName(Long configId, String jiraTypeName);
    @Transactional
    void deleteByConfigId(Long configId);
}

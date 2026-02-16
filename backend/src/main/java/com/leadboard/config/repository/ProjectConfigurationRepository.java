package com.leadboard.config.repository;

import com.leadboard.config.entity.ProjectConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectConfigurationRepository extends JpaRepository<ProjectConfigurationEntity, Long> {
    Optional<ProjectConfigurationEntity> findByIsDefaultTrue();
    Optional<ProjectConfigurationEntity> findByProjectKey(String projectKey);
}

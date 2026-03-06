package com.leadboard.config.repository;

import com.leadboard.config.entity.JiraProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JiraProjectRepository extends JpaRepository<JiraProjectEntity, Long> {

    Optional<JiraProjectEntity> findByProjectKey(String projectKey);

    List<JiraProjectEntity> findByActiveTrue();

    List<JiraProjectEntity> findByActiveTrueAndSyncEnabledTrue();

    boolean existsByProjectKey(String projectKey);
}

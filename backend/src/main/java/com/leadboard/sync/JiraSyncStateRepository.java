package com.leadboard.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JiraSyncStateRepository extends JpaRepository<JiraSyncStateEntity, Long> {

    Optional<JiraSyncStateEntity> findByProjectKey(String projectKey);
}

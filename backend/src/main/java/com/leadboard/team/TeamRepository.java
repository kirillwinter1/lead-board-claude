package com.leadboard.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, Long> {

    List<TeamEntity> findByActiveTrue();

    Optional<TeamEntity> findByIdAndActiveTrue(Long id);

    Optional<TeamEntity> findByJiraTeamValue(String jiraTeamValue);

    boolean existsByNameAndActiveTrue(String name);
}

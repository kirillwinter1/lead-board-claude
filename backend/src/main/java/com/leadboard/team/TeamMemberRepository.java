package com.leadboard.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, Long> {

    List<TeamMemberEntity> findByTeamIdAndActiveTrue(Long teamId);

    Optional<TeamMemberEntity> findByIdAndTeamIdAndActiveTrue(Long id, Long teamId);

    boolean existsByTeamIdAndJiraAccountIdAndActiveTrue(Long teamId, String jiraAccountId);

    Optional<TeamMemberEntity> findFirstByJiraAccountIdAndActiveTrue(String jiraAccountId);

    Optional<TeamMemberEntity> findByTeamIdAndJiraAccountId(Long teamId, String jiraAccountId);

    Optional<TeamMemberEntity> findByTeamIdAndJiraAccountIdAndActiveTrue(Long teamId, String jiraAccountId);

    List<TeamMemberEntity> findAllByJiraAccountIdAndActiveTrue(String jiraAccountId);
}

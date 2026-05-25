package com.leadboard.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, Long> {

    List<TeamMemberEntity> findByTeamIdAndActiveTrue(Long teamId);

    Optional<TeamMemberEntity> findByIdAndTeamIdAndActiveTrue(Long id, Long teamId);

    boolean existsByTeamIdAndJiraAccountIdAndActiveTrue(Long teamId, String jiraAccountId);

    Optional<TeamMemberEntity> findFirstByJiraAccountIdAndActiveTrue(String jiraAccountId);

    Optional<TeamMemberEntity> findByTeamIdAndJiraAccountId(Long teamId, String jiraAccountId);

    Optional<TeamMemberEntity> findByTeamIdAndJiraAccountIdAndActiveTrue(Long teamId, String jiraAccountId);

    List<TeamMemberEntity> findAllByJiraAccountIdAndActiveTrue(String jiraAccountId);

    /**
     * Direct membership existence check — avoids the LAZY {@code team} proxy
     * SELECT that would fire if we fetched the entity and called
     * {@code m.getTeam().getId()}.
     */
    boolean existsByJiraAccountIdAndTeamIdAndActiveTrue(String jiraAccountId, Long teamId);

    /**
     * Returns just the team ids the user actively belongs to. JPQL avoids
     * initialising the {@code team} proxy that the entity-fetching alternative
     * would trigger via {@code m.getTeam().getId()}.
     */
    @Query("SELECT m.team.id FROM TeamMemberEntity m WHERE m.jiraAccountId = :accountId AND m.active = true")
    Set<Long> findTeamIdsByJiraAccountIdAndActiveTrue(@Param("accountId") String accountId);
}

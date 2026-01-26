package com.leadboard.metrics.repository;

import com.leadboard.metrics.entity.StatusChangelogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatusChangelogRepository extends JpaRepository<StatusChangelogEntity, Long> {

    List<StatusChangelogEntity> findByIssueKeyOrderByTransitionedAtDesc(String issueKey);

    Optional<StatusChangelogEntity> findFirstByIssueKeyOrderByTransitionedAtDesc(String issueKey);

    @Query("SELECT s FROM StatusChangelogEntity s WHERE s.issueKey = :issueKey AND s.toStatus = :toStatus AND s.transitionedAt = :transitionedAt")
    Optional<StatusChangelogEntity> findByIssueKeyAndToStatusAndTransitionedAt(
            @Param("issueKey") String issueKey,
            @Param("toStatus") String toStatus,
            @Param("transitionedAt") OffsetDateTime transitionedAt);

    @Query(value = """
        SELECT to_status, AVG(time_in_previous_status_seconds) as avg_seconds,
               PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY time_in_previous_status_seconds) as median_seconds,
               COUNT(*) as transitions_count
        FROM status_changelog sc
        JOIN jira_issues ji ON sc.issue_key = ji.issue_key
        WHERE ji.team_id = :teamId
          AND sc.transitioned_at BETWEEN :from AND :to
          AND sc.time_in_previous_status_seconds IS NOT NULL
        GROUP BY to_status
        ORDER BY avg_seconds DESC
        """, nativeQuery = true)
    List<Object[]> getTimeInStatusStats(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}

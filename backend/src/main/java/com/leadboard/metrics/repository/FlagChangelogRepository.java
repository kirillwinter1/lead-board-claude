package com.leadboard.metrics.repository;

import com.leadboard.metrics.entity.FlagChangelogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlagChangelogRepository extends JpaRepository<FlagChangelogEntity, Long> {

    List<FlagChangelogEntity> findByIssueKey(String issueKey);

    List<FlagChangelogEntity> findByIssueKeyIn(List<String> issueKeys);

    @Query("SELECT f FROM FlagChangelogEntity f WHERE f.issueKey = :issueKey AND f.unflaggedAt IS NULL")
    Optional<FlagChangelogEntity> findOpenByIssueKey(@Param("issueKey") String issueKey);

    /** The most recent still-open flag entry (unflagged_at IS NULL) for the issue. */
    Optional<FlagChangelogEntity> findFirstByIssueKeyAndUnflaggedAtIsNullOrderByFlaggedAtDesc(String issueKey);

    /** All still-open flag entries (unflagged_at IS NULL) for the given issues — batch load to avoid N+1. */
    List<FlagChangelogEntity> findByIssueKeyInAndUnflaggedAtIsNull(List<String> issueKeys);
}

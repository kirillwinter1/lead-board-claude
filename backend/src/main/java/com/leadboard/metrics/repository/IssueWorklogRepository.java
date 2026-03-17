package com.leadboard.metrics.repository;

import com.leadboard.metrics.entity.IssueWorklogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface IssueWorklogRepository extends JpaRepository<IssueWorklogEntity, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM IssueWorklogEntity w WHERE w.issueKey = :issueKey")
    void deleteByIssueKey(@Param("issueKey") String issueKey);

    List<IssueWorklogEntity> findByIssueKeyIn(List<String> issueKeys);

    @Query(value = """
            SELECT w.issue_key, w.started_date, w.role_code, SUM(w.time_spent_seconds) as total_seconds
            FROM issue_worklogs w
            WHERE w.issue_key IN :keys AND w.role_code IS NOT NULL
            GROUP BY w.issue_key, w.started_date, w.role_code
            ORDER BY w.started_date
            """, nativeQuery = true)
    List<Object[]> findAggregatedWorklogsByIssueKeys(@Param("keys") List<String> keys);

    /**
     * Aggregate daily total time spent across a set of issue keys.
     * Used by Epic Burndown to build the actual (worklog-based) burndown line.
     */
    @Query(value = """
            SELECT w.started_date, SUM(w.time_spent_seconds) as total_seconds
            FROM issue_worklogs w
            WHERE w.issue_key IN :keys
            GROUP BY w.started_date
            ORDER BY w.started_date
            """, nativeQuery = true)
    List<Object[]> findDailyTimeSpentByIssueKeys(@Param("keys") List<String> keys);

    /**
     * Aggregate daily worklogs per author for the worklog timeline.
     * Groups by author_account_id + started_date, returns total seconds per day.
     */
    @Query(value = """
            SELECT w.author_account_id, w.started_date, SUM(w.time_spent_seconds) as total_seconds
            FROM issue_worklogs w
            WHERE w.author_account_id IN :accountIds
              AND w.started_date BETWEEN :fromDate AND :toDate
            GROUP BY w.author_account_id, w.started_date
            ORDER BY w.author_account_id, w.started_date
            """, nativeQuery = true)
    List<Object[]> findDailyWorklogsByAuthors(
            @Param("accountIds") List<String> accountIds,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}

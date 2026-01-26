package com.leadboard.metrics.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface MetricsQueryRepository extends org.springframework.data.repository.Repository<com.leadboard.sync.JiraIssueEntity, Long> {

    @Query(value = """
        SELECT
            DATE_TRUNC('week', done_at) as period_start,
            issue_type,
            COUNT(*) as count
        FROM jira_issues
        WHERE team_id = :teamId
          AND done_at BETWEEN :from AND :to
          AND (:issueType IS NULL OR issue_type = :issueType)
          AND (:epicKey IS NULL OR parent_key = :epicKey OR issue_key = :epicKey)
          AND (:assigneeAccountId IS NULL OR assignee_account_id = :assigneeAccountId)
        GROUP BY DATE_TRUNC('week', done_at), issue_type
        ORDER BY period_start
        """, nativeQuery = true)
    List<Object[]> getThroughputByWeek(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("issueType") String issueType,
            @Param("epicKey") String epicKey,
            @Param("assigneeAccountId") String assigneeAccountId);

    @Query(value = """
        SELECT
            EXTRACT(EPOCH FROM (done_at - jira_created_at)) / 86400.0 as lead_time_days,
            issue_key
        FROM jira_issues
        WHERE team_id = :teamId
          AND done_at BETWEEN :from AND :to
          AND done_at IS NOT NULL
          AND jira_created_at IS NOT NULL
          AND (:issueType IS NULL OR issue_type = :issueType)
          AND (:epicKey IS NULL OR parent_key = :epicKey OR issue_key = :epicKey)
          AND (:assigneeAccountId IS NULL OR assignee_account_id = :assigneeAccountId)
        ORDER BY lead_time_days
        """, nativeQuery = true)
    List<Object[]> getLeadTimeDays(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("issueType") String issueType,
            @Param("epicKey") String epicKey,
            @Param("assigneeAccountId") String assigneeAccountId);

    @Query(value = """
        SELECT
            EXTRACT(EPOCH FROM (done_at - COALESCE(started_at, jira_created_at))) / 86400.0 as cycle_time_days,
            issue_key
        FROM jira_issues
        WHERE team_id = :teamId
          AND done_at BETWEEN :from AND :to
          AND done_at IS NOT NULL
          AND (:issueType IS NULL OR issue_type = :issueType)
          AND (:epicKey IS NULL OR parent_key = :epicKey OR issue_key = :epicKey)
          AND (:assigneeAccountId IS NULL OR assignee_account_id = :assigneeAccountId)
        ORDER BY cycle_time_days
        """, nativeQuery = true)
    List<Object[]> getCycleTimeDays(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("issueType") String issueType,
            @Param("epicKey") String epicKey,
            @Param("assigneeAccountId") String assigneeAccountId);

    @Query(value = """
        SELECT
            assignee_account_id,
            assignee_display_name,
            COUNT(*) as issues_closed,
            AVG(EXTRACT(EPOCH FROM (done_at - jira_created_at)) / 86400.0) as avg_lead_time,
            AVG(EXTRACT(EPOCH FROM (done_at - COALESCE(started_at, jira_created_at))) / 86400.0) as avg_cycle_time
        FROM jira_issues
        WHERE team_id = :teamId
          AND done_at BETWEEN :from AND :to
          AND done_at IS NOT NULL
          AND assignee_account_id IS NOT NULL
        GROUP BY assignee_account_id, assignee_display_name
        ORDER BY issues_closed DESC
        """, nativeQuery = true)
    List<Object[]> getMetricsByAssignee(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query(value = """
        SELECT
            issue_type,
            COUNT(*) as count
        FROM jira_issues
        WHERE team_id = :teamId
          AND done_at BETWEEN :from AND :to
          AND (:issueType IS NULL OR issue_type = :issueType)
          AND (:epicKey IS NULL OR parent_key = :epicKey OR issue_key = :epicKey)
        GROUP BY issue_type
        """, nativeQuery = true)
    List<Object[]> getThroughputByType(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("issueType") String issueType,
            @Param("epicKey") String epicKey);
}

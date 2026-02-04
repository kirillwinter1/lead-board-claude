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

    /**
     * Extended assignee metrics with personal DSR, velocity, and trend calculation.
     * Returns: account_id, display_name, issues_closed, avg_lead_time, avg_cycle_time,
     *          personal_dsr, total_time_spent_hours, total_estimate_hours, issues_prev_period
     */
    @Query(value = """
        WITH current_period AS (
            SELECT
                assignee_account_id,
                assignee_display_name,
                COUNT(*) as issues_closed,
                AVG(EXTRACT(EPOCH FROM (done_at - jira_created_at)) / 86400.0) as avg_lead_time,
                AVG(EXTRACT(EPOCH FROM (done_at - COALESCE(started_at, jira_created_at))) / 86400.0) as avg_cycle_time,
                SUM(time_spent_seconds) / 3600.0 as total_time_spent_hours,
                SUM(original_estimate_seconds) / 3600.0 as total_estimate_hours,
                CASE
                    WHEN SUM(original_estimate_seconds) > 0
                    THEN CAST(SUM(time_spent_seconds) AS numeric) / CAST(SUM(original_estimate_seconds) AS numeric)
                    ELSE NULL
                END as personal_dsr
            FROM jira_issues
            WHERE team_id = ?1
              AND done_at BETWEEN ?2 AND ?3
              AND done_at IS NOT NULL
              AND assignee_account_id IS NOT NULL
            GROUP BY assignee_account_id, assignee_display_name
        ),
        prev_period AS (
            SELECT
                assignee_account_id,
                COUNT(*) as issues_closed_prev
            FROM jira_issues
            WHERE team_id = ?1
              AND done_at BETWEEN ?4 AND ?2
              AND done_at IS NOT NULL
              AND assignee_account_id IS NOT NULL
            GROUP BY assignee_account_id
        )
        SELECT
            c.assignee_account_id,
            c.assignee_display_name,
            c.issues_closed,
            c.avg_lead_time,
            c.avg_cycle_time,
            c.personal_dsr,
            c.total_time_spent_hours,
            c.total_estimate_hours,
            COALESCE(p.issues_closed_prev, 0) as issues_prev_period
        FROM current_period c
        LEFT JOIN prev_period p ON c.assignee_account_id = p.assignee_account_id
        ORDER BY c.issues_closed DESC
        """, nativeQuery = true)
    List<Object[]> getExtendedMetricsByAssignee(
            Long teamId,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime prevFrom);
}

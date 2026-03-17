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
            COALESCE(board_category, 'STORY') as category,
            COUNT(*) as count
        FROM jira_issues
        WHERE team_id = :teamId
          AND done_at BETWEEN :from AND :to
          AND (:issueType IS NULL OR issue_type = :issueType)
          AND (:epicKey IS NULL OR parent_key = :epicKey OR issue_key = :epicKey)
          AND (:assigneeAccountId IS NULL OR assignee_account_id = :assigneeAccountId)
        GROUP BY DATE_TRUNC('week', done_at), COALESCE(board_category, 'STORY')
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
            COALESCE(board_category, 'STORY') as category,
            COUNT(*) as count
        FROM jira_issues
        WHERE team_id = :teamId
          AND done_at BETWEEN :from AND :to
          AND (:issueType IS NULL OR issue_type = :issueType)
          AND (:epicKey IS NULL OR parent_key = :epicKey OR issue_key = :epicKey)
        GROUP BY COALESCE(board_category, 'STORY')
        """, nativeQuery = true)
    List<Object[]> getThroughputByType(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("issueType") String issueType,
            @Param("epicKey") String epicKey);

    /**
     * Get completed issues with time_spent for velocity calculation.
     * Returns: time_spent_seconds, started_at::date, done_at::date
     */
    @Query(value = """
        SELECT
            COALESCE(time_spent_seconds, 0) as time_spent,
            CAST(started_at AS date) as started,
            CAST(done_at AS date) as done
        FROM jira_issues
        WHERE team_id = :teamId
          AND done_at IS NOT NULL
          AND done_at BETWEEN :from AND :to
          AND COALESCE(time_spent_seconds, 0) > 0
        """, nativeQuery = true)
    List<Object[]> getVelocityData(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /**
     * Count issues in scope (completed in date range for team).
     */
    @Query(value = """
        SELECT COUNT(*)
        FROM jira_issues
        WHERE team_id = :teamId
          AND done_at BETWEEN :from AND :to
        """, nativeQuery = true)
    int countIssuesInScope(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /**
     * Count issues with changelog data.
     */
    @Query(value = """
        SELECT COUNT(DISTINCT sc.issue_key)
        FROM status_changelog sc
        JOIN jira_issues ji ON sc.issue_key = ji.issue_key
        WHERE ji.team_id = :teamId
          AND ji.done_at BETWEEN :from AND :to
        """, nativeQuery = true)
    int countIssuesWithChangelog(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /**
     * Count blocked (flagged) and aging (in-progress > threshold) issues.
     * Returns: [flaggedCount, agingCount]
     */
    @Query(value = """
        SELECT
            COUNT(CASE WHEN flagged = true THEN 1 END) as flagged_count,
            COUNT(CASE WHEN started_at IS NOT NULL AND done_at IS NULL
                       AND started_at < :threshold THEN 1 END) as aging_count
        FROM jira_issues
        WHERE team_id = :teamId
          AND done_at IS NULL
        """, nativeQuery = true)
    Object[] countBlockedAndAgingIssues(
            @Param("teamId") Long teamId,
            @Param("threshold") OffsetDateTime threshold);

    /**
     * Extended assignee metrics with previous period cycle time.
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
              AND (started_at IS NULL OR done_at >= started_at)
            GROUP BY assignee_account_id, assignee_display_name
        ),
        prev_period AS (
            SELECT
                assignee_account_id,
                COUNT(*) as issues_closed_prev,
                AVG(EXTRACT(EPOCH FROM (done_at - COALESCE(started_at, jira_created_at))) / 86400.0) as avg_cycle_time_prev
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
            COALESCE(p.issues_closed_prev, 0) as issues_prev_period,
            p.avg_cycle_time_prev
        FROM current_period c
        LEFT JOIN prev_period p ON c.assignee_account_id = p.assignee_account_id
        ORDER BY c.issues_closed DESC
        """, nativeQuery = true)
    List<Object[]> getExtendedMetricsByAssigneeV2(
            Long teamId,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime prevFrom);
}

package com.leadboard.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JiraIssueRepository extends JpaRepository<JiraIssueEntity, Long> {

    Optional<JiraIssueEntity> findByIssueKey(String issueKey);

    List<JiraIssueEntity> findByProjectKey(String projectKey);

    List<JiraIssueEntity> findByProjectKeyAndIssueType(String projectKey, String issueType);

    List<JiraIssueEntity> findByProjectKeyAndSubtask(String projectKey, boolean subtask);

    List<JiraIssueEntity> findByParentKey(String parentKey);

    List<JiraIssueEntity> findByParentKeyIn(List<String> parentKeys);

    void deleteByProjectKey(String projectKey);

    @Query("SELECT e.issueKey FROM JiraIssueEntity e WHERE e.projectKey = :projectKey")
    List<String> findAllIssueKeysByProjectKey(@Param("projectKey") String projectKey);

    @Modifying
    @Transactional
    @Query("DELETE FROM JiraIssueEntity e WHERE e.issueKey IN :issueKeys")
    void deleteByIssueKeyIn(@Param("issueKeys") List<String> issueKeys);

    @Query("SELECT COUNT(e) FROM JiraIssueEntity e WHERE e.projectKey = :projectKey " +
           "AND e.updatedAt >= :since")
    long countByProjectKeyAndUpdatedAtAfter(
            @Param("projectKey") String projectKey,
            @Param("since") OffsetDateTime since);

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.projectKey = :projectKey " +
           "AND e.updatedAt >= :since")
    List<JiraIssueEntity> findByProjectKeyAndUpdatedAtAfter(
            @Param("projectKey") String projectKey,
            @Param("since") OffsetDateTime since);

    // BUG-42: Use jiraUpdatedAt (Jira's own updated timestamp) instead of local updatedAt
    @Query("SELECT COUNT(e) FROM JiraIssueEntity e WHERE e.projectKey = :projectKey " +
           "AND e.jiraUpdatedAt >= :since")
    long countByProjectKeyAndJiraUpdatedAtAfter(
            @Param("projectKey") String projectKey,
            @Param("since") OffsetDateTime since);

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.projectKey = :projectKey " +
           "AND e.jiraUpdatedAt >= :since")
    List<JiraIssueEntity> findByProjectKeyAndJiraUpdatedAtAfter(
            @Param("projectKey") String projectKey,
            @Param("since") OffsetDateTime since);

    @Query("SELECT COUNT(e) FROM JiraIssueEntity e WHERE e.projectKey = :projectKey")
    long countByProjectKey(@Param("projectKey") String projectKey);

    // ==================== Board Category based queries ====================

    List<JiraIssueEntity> findByBoardCategoryAndTeamId(String boardCategory, Long teamId);

    List<JiraIssueEntity> findByBoardCategoryInAndTeamId(List<String> boardCategories, Long teamId);

    List<JiraIssueEntity> findByBoardCategoryAndTeamIdIn(String boardCategory, Collection<Long> teamIds);

    List<JiraIssueEntity> findByProjectKeyAndBoardCategory(String projectKey, String boardCategory);

    List<JiraIssueEntity> findByBoardCategoryAndTeamIdOrderByAutoScoreDesc(String boardCategory, Long teamId);

    List<JiraIssueEntity> findByBoardCategoryAndTeamIdAndStatusInOrderByAutoScoreDesc(
            String boardCategory, Long teamId, List<String> statuses);

    List<JiraIssueEntity> findByBoardCategory(String boardCategory);

    List<JiraIssueEntity> findByParentKeyAndBoardCategory(String parentKey, String boardCategory);

    List<JiraIssueEntity> findByIssueKeyIn(List<String> issueKeys);

    // ==================== Forecast Accuracy (using board_category) ====================

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.teamId = :teamId " +
           "AND e.boardCategory = 'EPIC' " +
           "AND e.doneAt IS NOT NULL " +
           "AND e.doneAt BETWEEN :from AND :to " +
           "ORDER BY e.doneAt DESC")
    List<JiraIssueEntity> findCompletedEpicsInPeriod(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.teamId = :teamId " +
           "AND e.boardCategory = 'EPIC' " +
           "AND (e.doneAt BETWEEN :from AND :to OR e.doneAt IS NULL) " +
           "ORDER BY e.issueKey")
    List<JiraIssueEntity> findEpicsForDsr(
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    // ==================== Planning Poker (using board_category) ====================

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.teamId = :teamId " +
           "AND e.boardCategory = 'EPIC' " +
           "AND LOWER(e.status) IN :statuses " +
           "ORDER BY e.issueKey")
    List<JiraIssueEntity> findEpicsByTeamAndStatuses(
            @Param("teamId") Long teamId,
            @Param("statuses") List<String> statuses
    );

    // ==================== Manual order queries (using board_category) ====================

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.teamId = :teamId " +
           "AND e.boardCategory = 'EPIC' " +
           "ORDER BY e.manualOrder ASC")
    List<JiraIssueEntity> findEpicsByTeamOrderByManualOrder(@Param("teamId") Long teamId);

    List<JiraIssueEntity> findByParentKeyOrderByManualOrderAsc(String parentKey);

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.teamId = :teamId " +
           "AND e.boardCategory = 'EPIC' " +
           "AND e.manualOrder > :fromOrder " +
           "ORDER BY e.manualOrder ASC")
    List<JiraIssueEntity> findEpicsWithOrderGreaterThan(
            @Param("teamId") Long teamId,
            @Param("fromOrder") Integer fromOrder
    );

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.parentKey = :parentKey " +
           "AND e.boardCategory = 'STORY' " +
           "AND e.manualOrder > :fromOrder " +
           "ORDER BY e.manualOrder ASC")
    List<JiraIssueEntity> findStoriesWithOrderGreaterThan(
            @Param("parentKey") String parentKey,
            @Param("fromOrder") Integer fromOrder
    );

    @Query("SELECT COALESCE(MAX(e.manualOrder), 0) FROM JiraIssueEntity e " +
           "WHERE e.teamId = :teamId AND e.boardCategory = 'EPIC'")
    Integer findMaxEpicOrderForTeam(@Param("teamId") Long teamId);

    @Query("SELECT COALESCE(MAX(e.manualOrder), 0) FROM JiraIssueEntity e " +
           "WHERE e.parentKey = :parentKey AND e.boardCategory = 'STORY'")
    Integer findMaxStoryOrderForParent(
            @Param("parentKey") String parentKey
    );

    // ==================== Bulk reorder shifts (single UPDATE instead of N saves) ====================

    @Modifying
    @Query("UPDATE JiraIssueEntity e SET e.manualOrder = e.manualOrder + 1 " +
           "WHERE e.teamId = :teamId AND e.boardCategory = 'EPIC' " +
           "AND e.manualOrder >= :fromOrder AND e.manualOrder < :toOrder")
    int shiftEpicOrdersDown(@Param("teamId") Long teamId,
                            @Param("fromOrder") int fromOrder, @Param("toOrder") int toOrder);

    @Modifying
    @Query("UPDATE JiraIssueEntity e SET e.manualOrder = e.manualOrder - 1 " +
           "WHERE e.teamId = :teamId AND e.boardCategory = 'EPIC' " +
           "AND e.manualOrder > :fromOrder AND e.manualOrder <= :toOrder")
    int shiftEpicOrdersUp(@Param("teamId") Long teamId,
                          @Param("fromOrder") int fromOrder, @Param("toOrder") int toOrder);

    @Modifying
    @Query("UPDATE JiraIssueEntity e SET e.manualOrder = e.manualOrder + 1 " +
           "WHERE e.parentKey = :parentKey AND e.boardCategory IN ('STORY', 'BUG') " +
           "AND e.manualOrder >= :fromOrder AND e.manualOrder < :toOrder")
    int shiftStoryOrdersDown(@Param("parentKey") String parentKey,
                             @Param("fromOrder") int fromOrder, @Param("toOrder") int toOrder);

    @Modifying
    @Query("UPDATE JiraIssueEntity e SET e.manualOrder = e.manualOrder - 1 " +
           "WHERE e.parentKey = :parentKey AND e.boardCategory IN ('STORY', 'BUG') " +
           "AND e.manualOrder > :fromOrder AND e.manualOrder <= :toOrder")
    int shiftStoryOrdersUp(@Param("parentKey") String parentKey,
                           @Param("fromOrder") int fromOrder, @Param("toOrder") int toOrder);

    @Modifying
    @Transactional
    @Query("UPDATE JiraIssueEntity e SET e.teamId = :teamId WHERE e.teamFieldValue = :teamFieldValue")
    int linkIssuesToTeam(@Param("teamId") Long teamId, @Param("teamFieldValue") String teamFieldValue);

    @Modifying
    @Transactional
    @Query(value = "UPDATE jira_issues child SET team_id = parent.team_id " +
           "FROM jira_issues parent " +
           "WHERE child.parent_key = parent.issue_key AND child.team_id IS NULL AND parent.team_id IS NOT NULL",
           nativeQuery = true)
    int inheritTeamFromParent();

    // ==================== Type & Status discovery ====================

    @Query("SELECT DISTINCT e.issueType FROM JiraIssueEntity e WHERE e.issueType IS NOT NULL ORDER BY e.issueType")
    List<String> findDistinctIssueTypes();

    @Query("SELECT DISTINCT e.status FROM JiraIssueEntity e WHERE e.boardCategory = :boardCategory AND e.status IS NOT NULL ORDER BY e.status")
    List<String> findDistinctStatusesByBoardCategory(@Param("boardCategory") String boardCategory);

    // ==================== Workflow Graph queries ====================

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.status = :status AND e.boardCategory = :boardCategory ORDER BY e.id ASC")
    List<JiraIssueEntity> findByStatusAndBoardCategory(
            @Param("status") String status,
            @Param("boardCategory") String boardCategory);

    // ==================== Priority discovery ====================

    @Query("SELECT DISTINCT e.priority FROM JiraIssueEntity e WHERE e.priority IS NOT NULL ORDER BY e.priority")
    List<String> findDistinctPriorities();

    // ==================== Status Issue Counts ====================

    @Query("SELECT e.status, e.boardCategory, COUNT(e) FROM JiraIssueEntity e " +
           "WHERE e.boardCategory IN ('EPIC','STORY','BUG','SUBTASK') " +
           "GROUP BY e.status, e.boardCategory")
    List<Object[]> countByStatusAndBoardCategory();

    // ==================== Member Profile queries ====================

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.teamId = :teamId " +
           "AND e.boardCategory = 'SUBTASK' AND e.assigneeAccountId IS NULL")
    List<JiraIssueEntity> findUnassignedSubtasksByTeam(@Param("teamId") Long teamId);

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.assigneeAccountId = :accountId " +
           "AND e.teamId = :teamId AND e.boardCategory = 'SUBTASK'")
    List<JiraIssueEntity> findSubtasksByAssigneeAndTeam(
            @Param("accountId") String accountId,
            @Param("teamId") Long teamId
    );

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.assigneeAccountId = :accountId " +
           "AND e.teamId = :teamId AND e.boardCategory = 'SUBTASK' " +
           "AND e.doneAt BETWEEN :from AND :to ORDER BY e.doneAt DESC")
    List<JiraIssueEntity> findCompletedSubtasksByAssigneeInPeriod(
            @Param("accountId") String accountId,
            @Param("teamId") Long teamId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    // ==================== Competency Matrix queries ====================

    @Query(value = "SELECT DISTINCT unnest(components) FROM jira_issues WHERE components IS NOT NULL ORDER BY 1",
           nativeQuery = true)
    List<String> findDistinctComponents();

    @Query(value = "SELECT DISTINCT unnest(components) FROM jira_issues WHERE team_id = :teamId AND components IS NOT NULL ORDER BY 1",
           nativeQuery = true)
    List<String> findDistinctComponentsByTeamId(@Param("teamId") Long teamId);

    // ==================== Embedding (pgvector) ====================

    @Modifying
    @Transactional
    @Query(value = "UPDATE jira_issues SET embedding = cast(:vec as vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") Long id, @Param("vec") String vectorString);

    @Query(value = "SELECT * FROM jira_issues WHERE embedding IS NOT NULL " +
           "ORDER BY embedding <=> cast(:vec as vector) LIMIT :lim", nativeQuery = true)
    List<JiraIssueEntity> findByEmbeddingSimilarity(@Param("vec") String vec, @Param("lim") int lim);

    @Query(value = "SELECT * FROM jira_issues WHERE embedding IS NOT NULL AND team_id = :teamId " +
           "ORDER BY embedding <=> cast(:vec as vector) LIMIT :lim", nativeQuery = true)
    List<JiraIssueEntity> findByEmbeddingSimilarityAndTeamId(
            @Param("vec") String vec, @Param("teamId") Long teamId, @Param("lim") int lim);

    @Query(value = "SELECT * FROM jira_issues WHERE embedding IS NULL AND summary IS NOT NULL", nativeQuery = true)
    List<JiraIssueEntity> findWithoutEmbedding();

    // ==================== Quarterly Planning ====================

    List<JiraIssueEntity> findByLabelsIsNotNull();

    List<JiraIssueEntity> findByBoardCategoryAndTeamIdOrderByManualOrderAsc(String boardCategory, Long teamId);

    // ==================== Simulation: stuck subtasks ====================

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.teamId = :teamId " +
           "AND e.subtask = true " +
           "AND e.status NOT IN :doneStatuses " +
           "AND e.originalEstimateSeconds > 0 " +
           "AND e.timeSpentSeconds >= e.originalEstimateSeconds " +
           "AND (e.remainingEstimateSeconds IS NULL OR e.remainingEstimateSeconds <= 0)")
    List<JiraIssueEntity> findStuckSubtasks(
            @Param("teamId") Long teamId,
            @Param("doneStatuses") List<String> doneStatuses
    );
}

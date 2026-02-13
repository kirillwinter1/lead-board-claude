package com.leadboard.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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

    // ==================== Board Category based queries ====================

    List<JiraIssueEntity> findByBoardCategoryAndTeamId(String boardCategory, Long teamId);

    List<JiraIssueEntity> findByBoardCategoryAndTeamIdOrderByAutoScoreDesc(String boardCategory, Long teamId);

    List<JiraIssueEntity> findByBoardCategoryAndTeamIdAndStatusInOrderByAutoScoreDesc(
            String boardCategory, Long teamId, List<String> statuses);

    List<JiraIssueEntity> findByBoardCategory(String boardCategory);

    // ==================== Legacy type-based queries (kept for transition) ====================

    List<JiraIssueEntity> findByIssueType(String issueType);

    List<JiraIssueEntity> findByIssueTypeAndTeamId(String issueType, Long teamId);

    List<JiraIssueEntity> findByIssueTypeAndTeamIdOrderByAutoScoreDesc(String issueType, Long teamId);

    List<JiraIssueEntity> findByIssueTypeAndTeamIdAndStatusInOrderByAutoScoreDesc(
            String issueType, Long teamId, List<String> statuses);

    List<JiraIssueEntity> findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(List<String> issueTypes, Long teamId);

    List<JiraIssueEntity> findByIssueTypeInAndTeamIdAndStatusInOrderByAutoScoreDesc(
            List<String> issueTypes, Long teamId, List<String> statuses);

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

    List<JiraIssueEntity> findByIssueTypeInAndTeamIdOrderByManualOrderAsc(List<String> issueTypes, Long teamId);

    List<JiraIssueEntity> findByParentKeyOrderByManualOrderAsc(String parentKey);

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.teamId = :teamId " +
           "AND e.boardCategory = 'EPIC' " +
           "AND e.manualOrder > :fromOrder " +
           "ORDER BY e.manualOrder ASC")
    List<JiraIssueEntity> findEpicsWithOrderGreaterThan(
            @Param("teamId") Long teamId,
            @Param("fromOrder") Integer fromOrder
    );

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.teamId = :teamId " +
           "AND e.issueType IN :issueTypes " +
           "AND e.manualOrder > :fromOrder " +
           "ORDER BY e.manualOrder ASC")
    List<JiraIssueEntity> findEpicsWithOrderGreaterThanByType(
            @Param("teamId") Long teamId,
            @Param("issueTypes") List<String> issueTypes,
            @Param("fromOrder") Integer fromOrder
    );

    @Query("SELECT e FROM JiraIssueEntity e WHERE e.parentKey = :parentKey " +
           "AND e.issueType IN :issueTypes " +
           "AND e.manualOrder > :fromOrder " +
           "ORDER BY e.manualOrder ASC")
    List<JiraIssueEntity> findStoriesWithOrderGreaterThan(
            @Param("parentKey") String parentKey,
            @Param("issueTypes") List<String> issueTypes,
            @Param("fromOrder") Integer fromOrder
    );

    @Query("SELECT COALESCE(MAX(e.manualOrder), 0) FROM JiraIssueEntity e " +
           "WHERE e.teamId = :teamId AND e.boardCategory = 'EPIC'")
    Integer findMaxEpicOrderForTeam(@Param("teamId") Long teamId);

    @Query("SELECT COALESCE(MAX(e.manualOrder), 0) FROM JiraIssueEntity e " +
           "WHERE e.teamId = :teamId AND e.issueType IN :issueTypes")
    Integer findMaxEpicOrderForTeamByType(
            @Param("teamId") Long teamId,
            @Param("issueTypes") List<String> issueTypes
    );

    @Query("SELECT COALESCE(MAX(e.manualOrder), 0) FROM JiraIssueEntity e " +
           "WHERE e.parentKey = :parentKey AND e.issueType IN :issueTypes")
    Integer findMaxStoryOrderForParent(
            @Param("parentKey") String parentKey,
            @Param("issueTypes") List<String> issueTypes
    );
}

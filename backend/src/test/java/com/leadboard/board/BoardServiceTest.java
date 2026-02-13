package com.leadboard.board;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.RoughEstimateProperties;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.UnifiedPlanningService;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.DataQualityService;
import com.leadboard.quality.DataQualityViolation;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private JiraProperties jiraProperties;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private RoughEstimateProperties roughEstimateProperties;

    @Mock
    private DataQualityService dataQualityService;


    @Mock
    private UnifiedPlanningService unifiedPlanningService;

    @Mock
    private WorkflowConfigService workflowConfigService;

    private BoardService boardService;

    @BeforeEach
    void setUp() {
        boardService = new BoardService(
                issueRepository,
                jiraProperties,
                teamRepository,
                roughEstimateProperties,
                dataQualityService,
                unifiedPlanningService,
                workflowConfigService
        );

        // Common setup
        when(jiraProperties.getProjectKey()).thenReturn("LB");
        when(jiraProperties.getBaseUrl()).thenReturn("https://jira.example.com");
        when(teamRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        when(dataQualityService.checkEpic(any(), anyList())).thenReturn(Collections.emptyList());
        when(dataQualityService.checkStory(any(), any(), anyList())).thenReturn(Collections.emptyList());
        when(dataQualityService.checkSubtask(any(), any(), any())).thenReturn(Collections.emptyList());

        // WorkflowConfigService stubs
        when(workflowConfigService.isEpic("Эпик")).thenReturn(true);
        when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        when(workflowConfigService.isEpic("История")).thenReturn(false);
        when(workflowConfigService.isEpic("Баг")).thenReturn(false);
        when(workflowConfigService.isEpic("Аналитика")).thenReturn(false);
        when(workflowConfigService.isEpic("Разработка")).thenReturn(false);
        when(workflowConfigService.isEpic("Тестирование")).thenReturn(false);
        when(workflowConfigService.isStory("История")).thenReturn(true);
        when(workflowConfigService.isStory("Баг")).thenReturn(true);
        when(workflowConfigService.isStory("Эпик")).thenReturn(false);
        when(workflowConfigService.getSubtaskRole("Аналитика")).thenReturn("SA");
        when(workflowConfigService.getSubtaskRole("Разработка")).thenReturn("DEV");
        when(workflowConfigService.getSubtaskRole("Тестирование")).thenReturn("QA");
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("SA", "DEV", "QA"));
        when(workflowConfigService.isAllowedForRoughEstimate("Новое")).thenReturn(true);
        when(workflowConfigService.isAllowedForRoughEstimate("Developing")).thenReturn(false);
        when(workflowConfigService.isAllowedForRoughEstimate("Готово")).thenReturn(false);
    }

    // ==================== Basic Board Tests ====================

    @Nested
    @DisplayName("getBoard() - basic scenarios")
    class GetBoardBasicTests {

        @Test
        @DisplayName("should return empty board when no project key configured")
        void shouldReturnEmptyWhenNoProjectKey() {
            when(jiraProperties.getProjectKey()).thenReturn(null);

            BoardResponse response = boardService.getBoard();

            assertEquals(0, response.getTotal());
            assertTrue(response.getItems().isEmpty());
        }

        @Test
        @DisplayName("should return empty board when no base URL configured")
        void shouldReturnEmptyWhenNoBaseUrl() {
            when(jiraProperties.getBaseUrl()).thenReturn(null);

            BoardResponse response = boardService.getBoard();

            assertEquals(0, response.getTotal());
            assertTrue(response.getItems().isEmpty());
        }

        @Test
        @DisplayName("should return empty board when no issues in repository")
        void shouldReturnEmptyWhenNoIssues() {
            when(issueRepository.findByProjectKey("LB")).thenReturn(Collections.emptyList());

            BoardResponse response = boardService.getBoard();

            assertEquals(0, response.getTotal());
            assertTrue(response.getItems().isEmpty());
        }

        @Test
        @DisplayName("should return epics from repository")
        void shouldReturnEpicsFromRepository() {
            JiraIssueEntity epic = createEpic("LB-1", "Test Epic", "Новое", 1L);
            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic));

            BoardResponse response = boardService.getBoard();

            assertEquals(1, response.getTotal());
            assertEquals(1, response.getItems().size());
            assertEquals("LB-1", response.getItems().get(0).getIssueKey());
            assertEquals("Test Epic", response.getItems().get(0).getTitle());
        }
    }

    // ==================== Hierarchy Tests ====================

    @Nested
    @DisplayName("getBoard() - hierarchy aggregation")
    class HierarchyTests {

        @Test
        @DisplayName("should aggregate stories under epics")
        void shouldAggregateStoriesUnderEpics() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic", "Developing", 1L);
            JiraIssueEntity story = createStory("LB-2", "Story", "Development", "LB-1");

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic, story));

            BoardResponse response = boardService.getBoard();

            assertEquals(1, response.getTotal());
            BoardNode epicNode = response.getItems().get(0);
            assertEquals(1, epicNode.getChildren().size());
            assertEquals("LB-2", epicNode.getChildren().get(0).getIssueKey());
        }

        @Test
        @DisplayName("should aggregate subtasks under stories")
        void shouldAggregateSubtasksUnderStories() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic", "Developing", 1L);
            JiraIssueEntity story = createStory("LB-2", "Story", "Development", "LB-1");
            JiraIssueEntity subtask = createSubtask("LB-3", "Subtask", "В работе", "LB-2", "Разработка");

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic, story, subtask));

            BoardResponse response = boardService.getBoard();

            BoardNode epicNode = response.getItems().get(0);
            BoardNode storyNode = epicNode.getChildren().get(0);
            assertEquals(1, storyNode.getChildren().size());
            assertEquals("LB-3", storyNode.getChildren().get(0).getIssueKey());
        }

        @Test
        @DisplayName("should aggregate bugs under epics like stories")
        void shouldAggregateBugsUnderEpics() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic", "Developing", 1L);
            JiraIssueEntity bug = createBug("LB-2", "Bug", "Open", "LB-1");

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic, bug));

            BoardResponse response = boardService.getBoard();

            assertEquals(1, response.getTotal());
            BoardNode epicNode = response.getItems().get(0);
            assertEquals(1, epicNode.getChildren().size());
            assertEquals("Баг", epicNode.getChildren().get(0).getIssueType());
        }
    }

    // ==================== Progress Calculation Tests ====================

    @Nested
    @DisplayName("getBoard() - progress calculation")
    class ProgressTests {

        @Test
        @DisplayName("should calculate epic progress from stories")
        void shouldCalculateEpicProgressFromStories() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic", "Developing", 1L);
            JiraIssueEntity story = createStory("LB-2", "Story", "Development", "LB-1");
            JiraIssueEntity subtask = createSubtaskWithTime("LB-3", "Dev", "LB-2", "Разработка",
                    3600L * 8, 3600L * 4); // 8h estimate, 4h logged = 50%

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic, story, subtask));

            BoardResponse response = boardService.getBoard();

            BoardNode epicNode = response.getItems().get(0);
            assertEquals(50, epicNode.getProgress());
        }

        @Test
        @DisplayName("should calculate role progress (SA/DEV/QA)")
        void shouldCalculateRoleProgress() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic", "Developing", 1L);
            JiraIssueEntity story = createStory("LB-2", "Story", "Development", "LB-1");

            JiraIssueEntity saTask = createSubtaskWithTime("LB-3", "SA", "LB-2", "Аналитика", 3600L * 8, 3600L * 8);
            JiraIssueEntity devTask = createSubtaskWithTime("LB-4", "DEV", "LB-2", "Разработка", 3600L * 16, 3600L * 8);
            JiraIssueEntity qaTask = createSubtaskWithTime("LB-5", "QA", "LB-2", "Тестирование", 3600L * 8, 0L);

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic, story, saTask, devTask, qaTask));

            BoardResponse response = boardService.getBoard();

            BoardNode epicNode = response.getItems().get(0);
            assertNotNull(epicNode.getRoleProgress());

            // SA: 8h/8h = 100%
            assertEquals(3600L * 8, epicNode.getRoleProgress().get("SA").getEstimateSeconds());
            assertEquals(3600L * 8, epicNode.getRoleProgress().get("SA").getLoggedSeconds());

            // DEV: 8h/16h = 50%
            assertEquals(3600L * 16, epicNode.getRoleProgress().get("DEV").getEstimateSeconds());
            assertEquals(3600L * 8, epicNode.getRoleProgress().get("DEV").getLoggedSeconds());

            // QA: 0h/8h = 0%
            assertEquals(3600L * 8, epicNode.getRoleProgress().get("QA").getEstimateSeconds());
            assertEquals(0L, epicNode.getRoleProgress().get("QA").getLoggedSeconds());
        }

        @Test
        @DisplayName("should handle zero estimates gracefully")
        void shouldHandleZeroEstimates() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic", "Developing", 1L);
            JiraIssueEntity story = createStory("LB-2", "Story", "Development", "LB-1");
            JiraIssueEntity subtask = createSubtaskWithTime("LB-3", "Dev", "LB-2", "Разработка", 0L, 0L);

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic, story, subtask));

            BoardResponse response = boardService.getBoard();

            BoardNode epicNode = response.getItems().get(0);
            assertEquals(0, epicNode.getProgress());
        }
    }

    // ==================== Filtering Tests ====================

    @Nested
    @DisplayName("getBoard() - filtering")
    class FilteringTests {

        @Test
        @DisplayName("should filter epics by team ID")
        void shouldFilterByTeamId() {
            JiraIssueEntity epic1 = createEpic("LB-1", "Epic Team 1", "Новое", 1L);
            JiraIssueEntity epic2 = createEpic("LB-2", "Epic Team 2", "Новое", 2L);

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic1, epic2));

            BoardResponse response = boardService.getBoard(null, null, List.of(1L), 0, 50);

            assertEquals(1, response.getTotal());
            assertEquals("LB-1", response.getItems().get(0).getIssueKey());
        }

        @Test
        @DisplayName("should filter epics by status")
        void shouldFilterByStatus() {
            JiraIssueEntity epic1 = createEpic("LB-1", "Epic New", "Новое", 1L);
            JiraIssueEntity epic2 = createEpic("LB-2", "Epic Done", "Готово", 1L);

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic1, epic2));

            BoardResponse response = boardService.getBoard(null, List.of("Новое"), null, 0, 50);

            assertEquals(1, response.getTotal());
            assertEquals("LB-1", response.getItems().get(0).getIssueKey());
        }

        @Test
        @DisplayName("should filter epics by query (key)")
        void shouldFilterByQueryKey() {
            JiraIssueEntity epic1 = createEpic("LB-100", "First Epic", "Новое", 1L);
            JiraIssueEntity epic2 = createEpic("LB-200", "Second Epic", "Новое", 1L);

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic1, epic2));

            BoardResponse response = boardService.getBoard("LB-100", null, null, 0, 50);

            assertEquals(1, response.getTotal());
            assertEquals("LB-100", response.getItems().get(0).getIssueKey());
        }

        @Test
        @DisplayName("should filter epics by query (summary)")
        void shouldFilterByQuerySummary() {
            JiraIssueEntity epic1 = createEpic("LB-1", "Authentication Feature", "Новое", 1L);
            JiraIssueEntity epic2 = createEpic("LB-2", "Payment Integration", "Новое", 1L);

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic1, epic2));

            BoardResponse response = boardService.getBoard("payment", null, null, 0, 50);

            assertEquals(1, response.getTotal());
            assertEquals("LB-2", response.getItems().get(0).getIssueKey());
        }

        @Test
        @DisplayName("should exclude epics without team when filtering by team")
        void shouldExcludeEpicsWithoutTeam() {
            JiraIssueEntity epicWithTeam = createEpic("LB-1", "Epic With Team", "Новое", 1L);
            JiraIssueEntity epicWithoutTeam = createEpic("LB-2", "Epic Without Team", "Новое", null);

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epicWithTeam, epicWithoutTeam));

            BoardResponse response = boardService.getBoard(null, null, List.of(1L), 0, 50);

            assertEquals(1, response.getTotal());
            assertEquals("LB-1", response.getItems().get(0).getIssueKey());
        }
    }

    // ==================== Sorting Tests ====================

    @Nested
    @DisplayName("getBoard() - sorting")
    class SortingTests {

        @Test
        @DisplayName("should sort epics by manual order (ascending)")
        void shouldSortByManualOrder() {
            JiraIssueEntity epic1 = createEpicWithOrder("LB-1", "Epic 1", "Новое", 1L, 2);
            JiraIssueEntity epic2 = createEpicWithOrder("LB-2", "Epic 2", "Новое", 1L, 1);
            JiraIssueEntity epic3 = createEpicWithOrder("LB-3", "Epic 3", "Новое", 1L, 3);

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic1, epic2, epic3));

            BoardResponse response = boardService.getBoard();

            assertEquals(3, response.getTotal());
            assertEquals("LB-2", response.getItems().get(0).getIssueKey()); // order 1
            assertEquals("LB-1", response.getItems().get(1).getIssueKey()); // order 2
            assertEquals("LB-3", response.getItems().get(2).getIssueKey()); // order 3
        }

        @Test
        @DisplayName("should sort epics by autoScore when no manual order")
        void shouldSortByAutoScoreWhenNoManualOrder() {
            JiraIssueEntity epic1 = createEpicWithScore("LB-1", "Epic 1", "Новое", 1L, BigDecimal.valueOf(50));
            JiraIssueEntity epic2 = createEpicWithScore("LB-2", "Epic 2", "Новое", 1L, BigDecimal.valueOf(80));
            JiraIssueEntity epic3 = createEpicWithScore("LB-3", "Epic 3", "Новое", 1L, BigDecimal.valueOf(30));

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic1, epic2, epic3));

            BoardResponse response = boardService.getBoard();

            assertEquals(3, response.getTotal());
            assertEquals("LB-2", response.getItems().get(0).getIssueKey()); // score 80
            assertEquals("LB-1", response.getItems().get(1).getIssueKey()); // score 50
            assertEquals("LB-3", response.getItems().get(2).getIssueKey()); // score 30
        }

        @Test
        @DisplayName("should prioritize manual order over autoScore")
        void shouldPrioritizeManualOrderOverAutoScore() {
            JiraIssueEntity epic1 = createEpic("LB-1", "Epic 1", "Новое", 1L);
            epic1.setManualOrder(1);
            epic1.setAutoScore(BigDecimal.valueOf(10)); // low score but has order

            JiraIssueEntity epic2 = createEpic("LB-2", "Epic 2", "Новое", 1L);
            epic2.setAutoScore(BigDecimal.valueOf(90)); // high score but no order

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic1, epic2));

            BoardResponse response = boardService.getBoard();

            assertEquals("LB-1", response.getItems().get(0).getIssueKey()); // manual order wins
        }
    }

    // ==================== Pagination Tests ====================

    @Nested
    @DisplayName("getBoard() - pagination")
    class PaginationTests {

        @Test
        @DisplayName("should apply pagination correctly")
        void shouldApplyPagination() {
            List<JiraIssueEntity> epics = List.of(
                    createEpicWithOrder("LB-1", "Epic 1", "Новое", 1L, 1),
                    createEpicWithOrder("LB-2", "Epic 2", "Новое", 1L, 2),
                    createEpicWithOrder("LB-3", "Epic 3", "Новое", 1L, 3),
                    createEpicWithOrder("LB-4", "Epic 4", "Новое", 1L, 4),
                    createEpicWithOrder("LB-5", "Epic 5", "Новое", 1L, 5)
            );

            when(issueRepository.findByProjectKey("LB")).thenReturn(epics);

            // Page 0, size 2
            BoardResponse page0 = boardService.getBoard(null, null, null, 0, 2);
            assertEquals(5, page0.getTotal());
            assertEquals(2, page0.getItems().size());
            assertEquals("LB-1", page0.getItems().get(0).getIssueKey());
            assertEquals("LB-2", page0.getItems().get(1).getIssueKey());

            // Page 1, size 2
            BoardResponse page1 = boardService.getBoard(null, null, null, 1, 2);
            assertEquals(5, page1.getTotal());
            assertEquals(2, page1.getItems().size());
            assertEquals("LB-3", page1.getItems().get(0).getIssueKey());
            assertEquals("LB-4", page1.getItems().get(1).getIssueKey());

            // Page 2, size 2 (last page, only 1 item)
            BoardResponse page2 = boardService.getBoard(null, null, null, 2, 2);
            assertEquals(5, page2.getTotal());
            assertEquals(1, page2.getItems().size());
            assertEquals("LB-5", page2.getItems().get(0).getIssueKey());
        }
    }

    // ==================== Data Quality Alerts Tests ====================

    @Nested
    @DisplayName("getBoard() - data quality alerts")
    class DataQualityTests {

        @Test
        @DisplayName("should include data quality alerts for epics")
        void shouldIncludeAlertsForEpics() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic without due date", "Новое", 1L);

            DataQualityViolation violation = DataQualityViolation.of(DataQualityRule.EPIC_NO_DUE_DATE);

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic));
            when(dataQualityService.checkEpic(any(), anyList())).thenReturn(List.of(violation));

            BoardResponse response = boardService.getBoard();

            BoardNode epicNode = response.getItems().get(0);
            assertFalse(epicNode.getAlerts().isEmpty());
            assertEquals(DataQualityRule.EPIC_NO_DUE_DATE, epicNode.getAlerts().get(0).rule());
        }
    }

    // ==================== Team Mapping Tests ====================

    @Nested
    @DisplayName("getBoard() - team mapping")
    class TeamMappingTests {

        @Test
        @DisplayName("should map team ID to team name")
        void shouldMapTeamIdToName() {
            JiraIssueEntity epic = createEpic("LB-1", "Epic", "Новое", 1L);

            TeamEntity team = new TeamEntity();
            team.setId(1L);
            team.setName("Alpha Team");
            team.setActive(true);

            when(issueRepository.findByProjectKey("LB")).thenReturn(List.of(epic));
            when(teamRepository.findByActiveTrue()).thenReturn(List.of(team));

            BoardResponse response = boardService.getBoard();

            assertEquals("Alpha Team", response.getItems().get(0).getTeamName());
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    @DisplayName("getBoard() - error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should return empty board on repository exception")
        void shouldReturnEmptyOnException() {
            when(issueRepository.findByProjectKey("LB")).thenThrow(new RuntimeException("DB error"));

            BoardResponse response = boardService.getBoard();

            assertEquals(0, response.getTotal());
            assertTrue(response.getItems().isEmpty());
        }
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createEpic(String key, String summary, String status, Long teamId) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setIssueId("id-" + key);
        entity.setSummary(summary);
        entity.setStatus(status);
        entity.setIssueType("Эпик");
        entity.setProjectKey("LB");
        entity.setTeamId(teamId);
        entity.setSubtask(false);
        return entity;
    }

    private JiraIssueEntity createEpicWithOrder(String key, String summary, String status, Long teamId, Integer order) {
        JiraIssueEntity entity = createEpic(key, summary, status, teamId);
        entity.setManualOrder(order);
        return entity;
    }

    private JiraIssueEntity createEpicWithScore(String key, String summary, String status, Long teamId, BigDecimal score) {
        JiraIssueEntity entity = createEpic(key, summary, status, teamId);
        entity.setAutoScore(score);
        return entity;
    }

    private JiraIssueEntity createStory(String key, String summary, String status, String parentKey) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setIssueId("id-" + key);
        entity.setSummary(summary);
        entity.setStatus(status);
        entity.setIssueType("История");
        entity.setProjectKey("LB");
        entity.setParentKey(parentKey);
        entity.setSubtask(false);
        return entity;
    }

    private JiraIssueEntity createBug(String key, String summary, String status, String parentKey) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setIssueId("id-" + key);
        entity.setSummary(summary);
        entity.setStatus(status);
        entity.setIssueType("Баг");
        entity.setProjectKey("LB");
        entity.setParentKey(parentKey);
        entity.setSubtask(false);
        return entity;
    }

    private JiraIssueEntity createSubtask(String key, String summary, String status, String parentKey, String subtaskType) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setIssueId("id-" + key);
        entity.setSummary(summary);
        entity.setStatus(status);
        entity.setIssueType(subtaskType);
        entity.setProjectKey("LB");
        entity.setParentKey(parentKey);
        entity.setSubtask(true);
        return entity;
    }

    private JiraIssueEntity createSubtaskWithTime(String key, String summary, String parentKey, String subtaskType,
                                                   Long estimateSeconds, Long loggedSeconds) {
        JiraIssueEntity entity = createSubtask(key, summary, "В работе", parentKey, subtaskType);
        entity.setOriginalEstimateSeconds(estimateSeconds);
        entity.setTimeSpentSeconds(loggedSeconds);
        return entity;
    }
}

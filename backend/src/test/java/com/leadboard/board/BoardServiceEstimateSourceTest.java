package com.leadboard.board;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.RoughEstimateProperties;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.UnifiedPlanningService;
import com.leadboard.quality.DataQualityService;
import com.leadboard.status.StatusAgeService;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

/**
 * F23 rework: the epic estimate badge switches from rough (pre-poker) to clean once
 * any child story has an estimate. {@code BoardNode.estimateSource} carries the flag.
 * Exercises the private {@code aggregateProgress} directly (the public path needs a
 * full board fixture; the estimate-source decision is fully covered here).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardServiceEstimateSourceTest {

    @Mock private JiraIssueRepository issueRepository;
    @Mock private JiraConfigResolver jiraConfigResolver;
    @Mock private TeamRepository teamRepository;
    @Mock private RoughEstimateProperties roughEstimateProperties;
    @Mock private DataQualityService dataQualityService;
    @Mock private UnifiedPlanningService unifiedPlanningService;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private StatusAgeService statusAgeService;

    private BoardService boardService;
    private Method aggregateProgress;

    @BeforeEach
    void setUp() throws Exception {
        boardService = new BoardService(issueRepository, jiraConfigResolver, teamRepository,
                roughEstimateProperties, dataQualityService, unifiedPlanningService,
                workflowConfigService, statusAgeService);

        when(workflowConfigService.isEpic(eq("Epic"), nullable(String.class))).thenReturn(true);
        when(workflowConfigService.isEpic(eq("Story"), nullable(String.class))).thenReturn(false);
        when(workflowConfigService.isEpic(eq("Sub-task"), nullable(String.class))).thenReturn(false);
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("SA", "DEV", "QA"));

        aggregateProgress = BoardService.class.getDeclaredMethod("aggregateProgress", BoardNode.class);
        aggregateProgress.setAccessible(true);
    }

    private void aggregate(BoardNode node) throws Exception {
        aggregateProgress.invoke(boardService, node);
    }

    private BoardNode epic(boolean inTodo) {
        BoardNode e = new BoardNode("LB-1", "Epic", "To Do", "Epic", null);
        e.setEpicInTodo(inTodo);
        return e;
    }

    private BoardNode storyWithSubtask(String subtaskKey, long estimateSeconds) {
        BoardNode subtask = new BoardNode(subtaskKey, "Sub", "In Progress", "Sub-task", null);
        subtask.setRole("DEV");
        subtask.setEstimateSeconds(estimateSeconds);
        subtask.setLoggedSeconds(0L);
        BoardNode story = new BoardNode("LB-2", "Story", "In Progress", "Story", null);
        story.addChild(subtask);
        return story;
    }

    @Test
    @DisplayName("epic with no stories -> rough")
    void emptyEpicIsRough() throws Exception {
        BoardNode e = epic(true);
        aggregate(e);
        assertEquals("rough", e.getEstimateSource());
    }

    @Test
    @DisplayName("planning epic with an estimated story -> clean")
    void planningEpicWithEstimatesIsClean() throws Exception {
        BoardNode e = epic(true);
        e.addChild(storyWithSubtask("LB-2-1", 8 * 3600));
        aggregate(e);
        assertEquals("clean", e.getEstimateSource());
        assertEquals(8 * 3600, e.getEstimateSeconds());
    }

    @Test
    @DisplayName("planning epic with a story but no subtask estimates -> rough")
    void planningEpicWithoutEstimatesIsRough() throws Exception {
        BoardNode e = epic(true);
        e.addChild(storyWithSubtask("LB-2-1", 0));
        aggregate(e);
        assertEquals("rough", e.getEstimateSource());
    }

    @Test
    @DisplayName("epic past the planning phase -> clean (actuals)")
    void inProgressEpicIsClean() throws Exception {
        BoardNode e = epic(false);
        e.addChild(storyWithSubtask("LB-2-1", 8 * 3600));
        aggregate(e);
        assertEquals("clean", e.getEstimateSource());
    }

    @Test
    @DisplayName("epic past planning but with no subtask estimates -> still rough")
    void inProgressEpicWithoutEstimatesIsRough() throws Exception {
        BoardNode e = epic(false);
        e.addChild(storyWithSubtask("LB-2-1", 0));
        aggregate(e);
        assertEquals("rough", e.getEstimateSource());
    }
}

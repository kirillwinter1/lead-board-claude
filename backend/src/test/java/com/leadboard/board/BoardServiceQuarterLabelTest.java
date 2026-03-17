package com.leadboard.board;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.RoughEstimateProperties;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.UnifiedPlanningService;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.quality.DataQualityService;
import com.leadboard.sync.JiraIssueEntity;
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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardServiceQuarterLabelTest {

    @Mock private JiraIssueRepository issueRepository;
    @Mock private JiraConfigResolver jiraConfigResolver;
    @Mock private TeamRepository teamRepository;
    @Mock private RoughEstimateProperties roughEstimateProperties;
    @Mock private DataQualityService dataQualityService;
    @Mock private UnifiedPlanningService unifiedPlanningService;
    @Mock private WorkflowConfigService workflowConfigService;

    private BoardService boardService;

    @BeforeEach
    void setUp() {
        boardService = new BoardService(
                issueRepository, jiraConfigResolver, teamRepository,
                roughEstimateProperties, dataQualityService,
                unifiedPlanningService, workflowConfigService
        );

        when(jiraConfigResolver.getActiveProjectKeys()).thenReturn(List.of("LB"));
        when(jiraConfigResolver.getBaseUrl()).thenReturn("https://jira.example.com");
        when(teamRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        when(dataQualityService.checkEpic(any(), anyList())).thenReturn(Collections.emptyList());
        when(dataQualityService.checkStory(any(), any(), anyList())).thenReturn(Collections.emptyList());
        when(dataQualityService.checkSubtask(any(), any(), any())).thenReturn(Collections.emptyList());
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("SA", "DEV", "QA"));
    }

    private JiraIssueEntity createEpic(String key, String[] labels) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setSummary("Epic " + key);
        epic.setStatus("In Progress");
        epic.setIssueType("Epic");
        epic.setProjectKey("LB");
        epic.setBoardCategory("EPIC");
        epic.setLabels(labels);
        return epic;
    }

    private JiraIssueEntity createProject(String key, String[] labels) {
        JiraIssueEntity project = new JiraIssueEntity();
        project.setIssueKey(key);
        project.setSummary("Project " + key);
        project.setStatus("In Progress");
        project.setIssueType("Project");
        project.setProjectKey("LB");
        project.setBoardCategory("PROJECT");
        project.setLabels(labels);
        return project;
    }

    @Test
    @DisplayName("Epic with direct quarter label gets it resolved")
    void epicWithDirectQuarterLabel() {
        JiraIssueEntity epic = createEpic("LB-10", new String[]{"2026Q2", "backend"});

        when(workflowConfigService.isEpic("Epic", "LB")).thenReturn(true);
        when(issueRepository.findByProjectKeyIn(List.of("LB"))).thenReturn(List.of(epic));

        BoardResponse response = boardService.getBoard(null, null, null, 0, 50, false);

        assertEquals(1, response.getItems().size());
        assertEquals("2026Q2", response.getItems().get(0).getQuarterLabel());
    }

    @Test
    @DisplayName("Epic inherits quarter label from parent project")
    void epicInheritsFromParentProject() {
        JiraIssueEntity project = createProject("LB-1", new String[]{"2026Q1"});
        JiraIssueEntity epic = createEpic("LB-10", null);
        epic.setParentKey("LB-1");

        when(workflowConfigService.isEpic("Epic", "LB")).thenReturn(true);
        when(issueRepository.findByProjectKeyIn(List.of("LB"))).thenReturn(List.of(project, epic));

        BoardResponse response = boardService.getBoard(null, null, null, 0, 50, false);

        assertEquals(1, response.getItems().size());
        assertEquals("2026Q1", response.getItems().get(0).getQuarterLabel());
    }

    @Test
    @DisplayName("Epic with no quarter label returns null")
    void epicWithNoQuarterLabel() {
        JiraIssueEntity epic = createEpic("LB-10", new String[]{"backend", "urgent"});

        when(workflowConfigService.isEpic("Epic", "LB")).thenReturn(true);
        when(issueRepository.findByProjectKeyIn(List.of("LB"))).thenReturn(List.of(epic));

        BoardResponse response = boardService.getBoard(null, null, null, 0, 50, false);

        assertEquals(1, response.getItems().size());
        assertNull(response.getItems().get(0).getQuarterLabel());
    }

    @Test
    @DisplayName("Epic's own quarter label takes priority over parent's")
    void epicOwnLabelTakesPriority() {
        JiraIssueEntity project = createProject("LB-1", new String[]{"2026Q1"});
        JiraIssueEntity epic = createEpic("LB-10", new String[]{"2026Q3"});
        epic.setParentKey("LB-1");

        when(workflowConfigService.isEpic("Epic", "LB")).thenReturn(true);
        when(issueRepository.findByProjectKeyIn(List.of("LB"))).thenReturn(List.of(project, epic));

        BoardResponse response = boardService.getBoard(null, null, null, 0, 50, false);

        assertEquals(1, response.getItems().size());
        assertEquals("2026Q3", response.getItems().get(0).getQuarterLabel());
    }
}

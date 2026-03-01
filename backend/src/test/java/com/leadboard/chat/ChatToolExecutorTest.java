package com.leadboard.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.auth.AuthorizationService;
import com.leadboard.chat.tools.ChatToolExecutor;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.service.TeamMetricsService;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatToolExecutorTest {

    @Mock private JiraIssueRepository issueRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private TeamMetricsService teamMetricsService;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private AuthorizationService authorizationService;

    private ChatToolExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        executor = new ChatToolExecutor(
                issueRepository, teamRepository, teamMemberRepository,
                teamMetricsService, workflowConfigService,
                authorizationService, objectMapper
        );
        when(authorizationService.isAdmin()).thenReturn(true);
    }

    @Test
    @DisplayName("board_summary returns epic and story counts")
    void boardSummaryReturnsData() {
        JiraIssueEntity epic1 = makeIssue("PROJ-1", "Epic", "EPIC", "In Progress");
        JiraIssueEntity epic2 = makeIssue("PROJ-2", "Epic", "EPIC", "Done");
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic1, epic2));

        JiraIssueEntity story1 = makeIssue("PROJ-3", "Story", "STORY", "To Do");
        story1.setParentKey("PROJ-1");
        when(issueRepository.findByParentKeyIn(anyList())).thenReturn(List.of(story1));

        when(workflowConfigService.categorize("In Progress", "Epic")).thenReturn(StatusCategory.IN_PROGRESS);
        when(workflowConfigService.categorize("Done", "Epic")).thenReturn(StatusCategory.DONE);
        when(workflowConfigService.categorize("To Do", "Story")).thenReturn(StatusCategory.NEW);

        String result = executor.executeTool("board_summary", "{}");

        assertTrue(result.contains("\"totalEpics\":2"));
        assertTrue(result.contains("\"totalStories\":1"));
    }

    @Test
    @DisplayName("team_list returns all active teams")
    void teamListReturnsTeams() {
        TeamEntity team = new TeamEntity();
        team.setId(1L);
        team.setName("Backend Team");
        team.setColor("#0052cc");
        when(teamRepository.findByActiveTrue()).thenReturn(List.of(team));
        when(teamMemberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

        String result = executor.executeTool("team_list", null);

        assertTrue(result.contains("Backend Team"));
        assertTrue(result.contains("\"totalTeams\":1"));
        assertTrue(result.contains("\"memberCount\":1"));
    }

    @Test
    @DisplayName("task_count counts issues by filters")
    void taskCountFilters() {
        JiraIssueEntity epic = makeIssue("PROJ-1", "Epic", "EPIC", "In Progress");
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic));
        when(workflowConfigService.categorize("In Progress", "Epic")).thenReturn(StatusCategory.IN_PROGRESS);

        String result = executor.executeTool("task_count", "{\"type\":\"EPIC\"}");

        assertTrue(result.contains("\"totalCount\":1"));
        assertTrue(result.contains("EPIC"));
    }

    @Test
    @DisplayName("data_quality_summary returns quality metrics")
    void dataQualitySummary() {
        JiraIssueEntity epic = makeIssue("PROJ-1", "Epic", "EPIC", "In Progress");
        epic.setTeamId(null); // No team assigned
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic));
        when(issueRepository.findByParentKeyIn(anyList())).thenReturn(List.of());

        String result = executor.executeTool("data_quality_summary", "{}");

        assertTrue(result.contains("\"totalEpicsChecked\":1"));
        assertTrue(result.contains("\"epicsWithoutTeam\":1"));
    }

    @Test
    @DisplayName("RBAC: TEAM_LEAD can only view their own team")
    void rbacTeamLeadRestriction() {
        when(authorizationService.isAdmin()).thenReturn(false);
        when(authorizationService.isProjectManager()).thenReturn(false);
        when(authorizationService.getUserTeamIds()).thenReturn(Set.of(1L));

        String result = executor.executeTool("board_summary", "{\"teamId\":999}");

        assertTrue(result.contains("Access denied"));
    }

    @Test
    @DisplayName("RBAC: TEAM_LEAD can view their own team")
    void rbacTeamLeadOwnTeam() {
        when(authorizationService.isAdmin()).thenReturn(false);
        when(authorizationService.isProjectManager()).thenReturn(false);
        when(authorizationService.getUserTeamIds()).thenReturn(Set.of(1L));
        when(issueRepository.findByBoardCategoryAndTeamId("EPIC", 1L)).thenReturn(List.of());
        when(issueRepository.findByParentKeyIn(anyList())).thenReturn(List.of());

        String result = executor.executeTool("board_summary", "{\"teamId\":1}");

        assertFalse(result.contains("Access denied"));
        assertTrue(result.contains("\"totalEpics\":0"));
    }

    @Test
    @DisplayName("Unknown tool returns error")
    void unknownToolReturnsError() {
        String result = executor.executeTool("nonexistent_tool", "{}");
        assertTrue(result.contains("Unknown tool"));
    }

    @Test
    @DisplayName("Invalid JSON arguments are handled gracefully")
    void invalidArgsHandledGracefully() {
        String result = executor.executeTool("board_summary", "{invalid json}");
        assertTrue(result.contains("error"));
    }

    private JiraIssueEntity makeIssue(String key, String type, String boardCategory, String status) {
        JiraIssueEntity issue = new JiraIssueEntity();
        issue.setIssueKey(key);
        issue.setIssueType(type);
        issue.setBoardCategory(boardCategory);
        issue.setStatus(status);
        issue.setTeamId(1L);
        return issue;
    }
}

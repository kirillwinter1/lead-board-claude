package com.leadboard.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.auth.AuthorizationService;
import com.leadboard.chat.tools.ChatToolExecutor;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.dto.BugMetricsResponse;
import com.leadboard.metrics.service.BugMetricsService;
import com.leadboard.metrics.service.TeamMetricsService;
import com.leadboard.project.ProjectDto;
import com.leadboard.project.ProjectService;
import com.leadboard.quality.BugSlaConfigEntity;
import com.leadboard.quality.BugSlaService;
import com.leadboard.rice.RiceAssessmentService;
import com.leadboard.rice.dto.RiceRankingEntryDto;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.*;
import com.leadboard.team.dto.AbsenceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
    @Mock private BugMetricsService bugMetricsService;
    @Mock private ProjectService projectService;
    @Mock private RiceAssessmentService riceAssessmentService;
    @Mock private AbsenceService absenceService;
    @Mock private BugSlaService bugSlaService;

    private ChatToolExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        executor = new ChatToolExecutor(
                issueRepository, teamRepository, teamMemberRepository,
                teamMetricsService, workflowConfigService,
                authorizationService, bugMetricsService, projectService,
                riceAssessmentService, absenceService, bugSlaService,
                objectMapper
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

    @Test
    @DisplayName("bug_metrics returns bug data")
    void bugMetricsReturnsData() {
        var metrics = new BugMetricsResponse(
                5, 10, 2, 48L, 85.0,
                List.of(new BugMetricsResponse.PriorityMetrics("High", 3, 5, 36L, 72, 90.0)),
                List.of(new BugMetricsResponse.OpenBugDto("BUG-1", "Test bug", "High", "Open", 5, 120, true, null))
        );
        when(bugMetricsService.getBugMetrics(null)).thenReturn(metrics);

        String result = executor.executeTool("bug_metrics", "{}");

        assertTrue(result.contains("\"openBugs\":5"));
        assertTrue(result.contains("\"resolvedBugs\":10"));
        assertTrue(result.contains("\"slaCompliancePercent\":85.0"));
    }

    @Test
    @DisplayName("project_list returns projects")
    void projectListReturnsProjects() {
        var project = new ProjectDto(
                "PROJ-1", "Initiative", "Project Alpha", null, "In Progress",
                "John", null, 3, 1, 33,
                null, null, LocalDate.of(2026, 6, 1),
                new BigDecimal("75.5"), new BigDecimal("80.0")
        );
        when(projectService.listProjects()).thenReturn(List.of(project));

        String result = executor.executeTool("project_list", "{}");

        assertTrue(result.contains("Project Alpha"));
        assertTrue(result.contains("\"totalProjects\":1"));
        assertTrue(result.contains("\"progressPercent\":33"));
    }

    @Test
    @DisplayName("rice_ranking returns ranking")
    void riceRankingReturnsRanking() {
        var entry = new RiceRankingEntryDto(
                "PROJ-1", "Epic One", "In Progress", "Business",
                new BigDecimal("90"), new BigDecimal("85"),
                new BigDecimal("100"), new BigDecimal("3"), new BigDecimal("80"), new BigDecimal("2.5")
        );
        when(riceAssessmentService.getRanking(null)).thenReturn(List.of(entry));

        String result = executor.executeTool("rice_ranking", "{}");

        assertTrue(result.contains("Epic One"));
        assertTrue(result.contains("\"totalRanked\":1"));
    }

    @Test
    @DisplayName("member_absences returns absences")
    void memberAbsencesReturnsAbsences() {
        var absence = new AbsenceDto(1L, 10L, AbsenceType.VACATION,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 20), "Holiday", OffsetDateTime.now());
        when(absenceService.getAbsencesForTeam(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(absence));

        String result = executor.executeTool("member_absences", "{\"teamId\":1}");

        assertTrue(result.contains("VACATION"));
        assertTrue(result.contains("\"totalAbsences\":1"));
    }

    @Test
    @DisplayName("bug_sla_settings returns SLA configs")
    void bugSlaSettingsReturnsSla() {
        var config = new BugSlaConfigEntity();
        config.setPriority("Critical");
        config.setMaxResolutionHours(24);
        when(bugSlaService.getAllSlaConfigs()).thenReturn(List.of(config));

        String result = executor.executeTool("bug_sla_settings", "{}");

        assertTrue(result.contains("Critical"));
        assertTrue(result.contains("\"maxResolutionHours\":24"));
    }

    @Test
    @DisplayName("task_details returns single task")
    void taskDetailsReturnsSingleTask() {
        JiraIssueEntity issue = makeIssue("PROJ-42", "Story", "STORY", "In Progress");
        issue.setAssigneeDisplayName("Alice");
        issue.setPriority("High");
        when(issueRepository.findByIssueKey("PROJ-42")).thenReturn(Optional.of(issue));
        when(workflowConfigService.categorize("In Progress", "Story")).thenReturn(StatusCategory.IN_PROGRESS);

        String result = executor.executeTool("task_details", "{\"issueKey\":\"PROJ-42\"}");

        assertTrue(result.contains("PROJ-42"));
        assertTrue(result.contains("Alice"));
        assertTrue(result.contains("IN_PROGRESS"));
    }

    @Test
    @DisplayName("team_members returns members list")
    void teamMembersReturnsMembers() {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(1L);
        member.setDisplayName("Bob");
        member.setRole("DEV");
        member.setGrade(Grade.SENIOR);
        when(teamMemberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(member));

        String result = executor.executeTool("team_members", "{\"teamId\":1}");

        assertTrue(result.contains("Bob"));
        assertTrue(result.contains("SENIOR"));
        assertTrue(result.contains("\"totalMembers\":1"));
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

package com.leadboard.simulation;

import com.leadboard.auth.OAuthService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraTransition;
import com.leadboard.simulation.dto.SimulationAction;
import com.leadboard.team.Role;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimulationExecutorTest {

    @Mock private JiraClient jiraClient;
    @Mock private OAuthService oauthService;
    @Mock private TeamMemberRepository memberRepository;

    private SimulationExecutor executor;

    private static final Long TEAM_ID = 1L;
    private static final LocalDate SIM_DATE = LocalDate.of(2025, 6, 2);
    private static final String ACCESS_TOKEN = "test-token";
    private static final String CLOUD_ID = "test-cloud-id";

    @BeforeEach
    void setUp() {
        executor = new SimulationExecutor(jiraClient, oauthService, memberRepository);

        // Setup team member mapping
        TeamMemberEntity member = new TeamMemberEntity();
        member.setJiraAccountId("acc-1");
        member.setDisplayName("Dev One");
        member.setRole(Role.DEV);
        member.setHoursPerDay(new BigDecimal("6.0"));
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(member));

        // Default: OAuth token available
        when(oauthService.getValidAccessTokenForUser("acc-1"))
                .thenReturn(new OAuthService.TokenInfo(ACCESS_TOKEN, CLOUD_ID));
    }

    @Test
    void execute_transition_success() {
        JiraTransition transition = new JiraTransition("21", "In Progress",
                new JiraTransition.TransitionTarget("3", "In Progress"));
        when(jiraClient.getTransitions("PROJ-11", ACCESS_TOKEN, CLOUD_ID))
                .thenReturn(List.of(transition));

        SimulationAction action = SimulationAction.transition(
                "PROJ-11", "Sub-task", "Dev One", "New", "In Progress", "Starting work");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertTrue(results.get(0).executed());
        verify(jiraClient).transitionIssue("PROJ-11", "21", ACCESS_TOKEN, CLOUD_ID);
    }

    @Test
    void execute_transition_notFound() {
        // Only "Done" transition available, but we want "In Progress"
        JiraTransition transition = new JiraTransition("31", "Done",
                new JiraTransition.TransitionTarget("5", "Done"));
        when(jiraClient.getTransitions("PROJ-11", ACCESS_TOKEN, CLOUD_ID))
                .thenReturn(List.of(transition));

        SimulationAction action = SimulationAction.transition(
                "PROJ-11", "Sub-task", "Dev One", "New", "In Progress", "Starting work");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertFalse(results.get(0).executed());
        assertNotNull(results.get(0).error());
        assertTrue(results.get(0).error().contains("not found"));
    }

    @Test
    void execute_worklog_success() {
        SimulationAction action = SimulationAction.worklog(
                "PROJ-11", "Sub-task", "Dev One", 6.0, "Daily work");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertTrue(results.get(0).executed());
        verify(jiraClient).addWorklog(eq("PROJ-11"), eq(21600), eq(SIM_DATE),
                eq(ACCESS_TOKEN), eq(CLOUD_ID));
    }

    @Test
    void execute_noOAuthToken_skipsWithError() {
        when(oauthService.getValidAccessTokenForUser("acc-1")).thenReturn(null);

        SimulationAction action = SimulationAction.worklog(
                "PROJ-11", "Sub-task", "Dev One", 6.0, "Daily work");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertFalse(results.get(0).executed());
        assertNotNull(results.get(0).error());
        assertTrue(results.get(0).error().contains("No OAuth token"));
    }

    @Test
    void execute_unknownAssignee_skipsWithError() {
        SimulationAction action = SimulationAction.worklog(
                "PROJ-11", "Sub-task", "Unknown User", 6.0, "Daily work");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertFalse(results.get(0).executed());
        assertNotNull(results.get(0).error());
    }

    @Test
    void execute_skipAction_passedThrough() {
        SimulationAction action = SimulationAction.skip(
                "PROJ-11", "Sub-task", "Dev One", "Not needed");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertEquals(SimulationAction.ActionType.SKIP, results.get(0).type());
        verifyNoInteractions(jiraClient);
    }

    @Test
    void execute_jiraError_capturedInResult() {
        when(jiraClient.getTransitions("PROJ-11", ACCESS_TOKEN, CLOUD_ID))
                .thenThrow(new RuntimeException("Jira API error"));

        SimulationAction action = SimulationAction.transition(
                "PROJ-11", "Sub-task", "Dev One", "New", "In Progress", "Starting work");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertFalse(results.get(0).executed());
        assertNotNull(results.get(0).error());
        assertTrue(results.get(0).error().contains("Jira API error"));
    }

    @Test
    void execute_multipleActions_processedInOrder() {
        JiraTransition transition = new JiraTransition("21", "In Progress",
                new JiraTransition.TransitionTarget("3", "In Progress"));
        when(jiraClient.getTransitions(anyString(), eq(ACCESS_TOKEN), eq(CLOUD_ID)))
                .thenReturn(List.of(transition));

        List<SimulationAction> actions = List.of(
                SimulationAction.transition("PROJ-11", "Sub-task", "Dev One",
                        "New", "In Progress", "Start"),
                SimulationAction.worklog("PROJ-12", "Sub-task", "Dev One",
                        4.0, "Work")
        );

        List<SimulationAction> results = executor.execute(actions, SIM_DATE, TEAM_ID);

        assertEquals(2, results.size());
    }
}

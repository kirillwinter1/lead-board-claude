package com.leadboard.simulation;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraTransition;
import com.leadboard.simulation.dto.SimulationAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimulationExecutorTest {

    @Mock private JiraClient jiraClient;
    @Mock private WorkflowConfigService workflowConfigService;

    private SimulationExecutor executor;

    private static final Long TEAM_ID = 1L;
    private static final LocalDate SIM_DATE = LocalDate.of(2025, 6, 2);

    @BeforeEach
    void setUp() {
        executor = new SimulationExecutor(jiraClient, workflowConfigService);
    }

    @Test
    void execute_transition_success() {
        JiraTransition transition = new JiraTransition("21", "In Progress",
                new JiraTransition.TransitionTarget("3", "In Progress", null));
        when(jiraClient.getTransitionsBasicAuth("PROJ-11"))
                .thenReturn(List.of(transition));

        SimulationAction action = SimulationAction.transition(
                "PROJ-11", "Sub-task", "Dev One", "New", "In Progress", "Starting work");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertTrue(results.get(0).executed());
        verify(jiraClient).transitionIssueBasicAuth("PROJ-11", "21");
    }

    @Test
    void execute_transition_notFound() {
        // Only "Done" transition available, but we want "In Progress"
        JiraTransition transition = new JiraTransition("31", "Done",
                new JiraTransition.TransitionTarget("5", "Done", null));
        when(jiraClient.getTransitionsBasicAuth("PROJ-11"))
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
        verify(jiraClient).addWorklogBasicAuth(eq("PROJ-11"), eq(21600), eq(SIM_DATE));
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
        when(jiraClient.getTransitionsBasicAuth("PROJ-11"))
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
                new JiraTransition.TransitionTarget("3", "In Progress", null));
        when(jiraClient.getTransitionsBasicAuth(anyString()))
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

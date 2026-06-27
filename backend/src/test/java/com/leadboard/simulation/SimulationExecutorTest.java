package com.leadboard.simulation;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraTransition;
import com.leadboard.simulation.dto.SimulationAction;
import com.leadboard.status.StatusCategory;
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
    private SimulationProperties properties;

    private static final Long TEAM_ID = 1L;
    private static final LocalDate SIM_DATE = LocalDate.of(2025, 6, 2);

    @BeforeEach
    void setUp() {
        properties = new SimulationProperties();
        properties.setMaxConsecutiveFailures(3);
        executor = new SimulationExecutor(jiraClient, workflowConfigService, properties);
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
        // categorize returns IN_PROGRESS for the target, but only DONE transitions available
        when(workflowConfigService.categorize("In Progress", "Sub-task")).thenReturn(StatusCategory.IN_PROGRESS);
        when(workflowConfigService.categorize("Done", "Sub-task")).thenReturn(StatusCategory.DONE);

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
    void execute_assign_success() {
        SimulationAction action = SimulationAction.assign(
                "PROJ-11", "Sub-task", "Dev One", "acc-1", "Assigning to Dev One");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertTrue(results.get(0).executed());
        verify(jiraClient).assignIssueBasicAuth("PROJ-11", "acc-1");
    }

    @Test
    void execute_assign_noAccountId_returnsError() {
        SimulationAction action = SimulationAction.assign(
                "PROJ-11", "Sub-task", "Dev One", null, "Missing account");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertFalse(results.get(0).executed());
        assertNotNull(results.get(0).error());
        verifyNoInteractions(jiraClient);
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

    @Test
    void execute_transition_multiStep_doneViaIntermediate() {
        // First call: from "В работе" — only "В Ревью" (→ Проверка) available, no direct DONE
        JiraTransition reviewTransition = new JiraTransition("41", "В Ревью",
                new JiraTransition.TransitionTarget("10", "Проверка", null));
        // Second call: from "Проверка" — now "Готово" is available
        JiraTransition doneTransition = new JiraTransition("51", "Готово",
                new JiraTransition.TransitionTarget("11", "Готово", null));

        when(jiraClient.getTransitionsBasicAuth("PROJ-11"))
                .thenReturn(List.of(reviewTransition))   // first call
                .thenReturn(List.of(doneTransition));     // second call after intermediate

        // "Готово" (requested target) is DONE category
        when(workflowConfigService.categorize("Готово", "Sub-task")).thenReturn(StatusCategory.DONE);
        // "Проверка" (intermediate) is IN_PROGRESS category
        when(workflowConfigService.categorize("Проверка", "Sub-task")).thenReturn(StatusCategory.IN_PROGRESS);

        SimulationAction action = SimulationAction.transition(
                "PROJ-11", "Sub-task", "Dev One", "В работе", "Готово", "Completing subtask");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertTrue(results.get(0).executed());
        // Should have transitioned twice: first to "Проверка", then to "Готово"
        verify(jiraClient).transitionIssueBasicAuth("PROJ-11", "41");
        verify(jiraClient).transitionIssueBasicAuth("PROJ-11", "51");
    }

    @Test
    void execute_circuitBreaker_abortsAfterConsecutiveFailures() {
        properties.setMaxConsecutiveFailures(2);

        when(jiraClient.getTransitionsBasicAuth(anyString()))
                .thenThrow(new RuntimeException("connection timed out"));

        List<SimulationAction> actions = List.of(
                SimulationAction.transition("PROJ-1", "Sub-task", "Dev", "New", "In Progress", ""),
                SimulationAction.transition("PROJ-2", "Sub-task", "Dev", "New", "In Progress", ""),
                SimulationAction.transition("PROJ-3", "Sub-task", "Dev", "New", "In Progress", ""),
                SimulationAction.transition("PROJ-4", "Sub-task", "Dev", "New", "In Progress", "")
        );

        List<SimulationAction> results = executor.execute(actions, SIM_DATE, TEAM_ID);

        assertEquals(4, results.size());
        // First 2 failed normally
        assertTrue(results.get(0).error().contains("connection timed out"));
        assertTrue(results.get(1).error().contains("connection timed out"));
        // Last 2 skipped by circuit breaker
        assertTrue(results.get(2).error().contains("circuit breaker"));
        assertTrue(results.get(3).error().contains("circuit breaker"));
        // Only 2 actual Jira calls made (not 4)
        verify(jiraClient, times(2)).getTransitionsBasicAuth(anyString());
    }

    @Test
    void execute_circuitBreaker_resetsOnSuccess() {
        properties.setMaxConsecutiveFailures(2);

        JiraTransition transition = new JiraTransition("21", "In Progress",
                new JiraTransition.TransitionTarget("3", "In Progress", null));

        // Fail, succeed, fail — should NOT trigger circuit breaker
        when(jiraClient.getTransitionsBasicAuth("PROJ-1"))
                .thenThrow(new RuntimeException("timeout"));
        when(jiraClient.getTransitionsBasicAuth("PROJ-2"))
                .thenReturn(List.of(transition));
        when(jiraClient.getTransitionsBasicAuth("PROJ-3"))
                .thenThrow(new RuntimeException("timeout"));

        List<SimulationAction> actions = List.of(
                SimulationAction.transition("PROJ-1", "Sub-task", "Dev", "New", "In Progress", ""),
                SimulationAction.transition("PROJ-2", "Sub-task", "Dev", "New", "In Progress", ""),
                SimulationAction.transition("PROJ-3", "Sub-task", "Dev", "New", "In Progress", "")
        );

        List<SimulationAction> results = executor.execute(actions, SIM_DATE, TEAM_ID);

        assertEquals(3, results.size());
        assertNotNull(results.get(0).error());
        assertTrue(results.get(1).executed());
        assertNotNull(results.get(2).error());
        // All 3 actually executed (no circuit breaker)
        assertFalse(results.get(2).error().contains("circuit breaker"));
    }

    @Test
    void execute_transition_multiStep_noSecondStep() {
        // Forward fallback fires: wanted DONE, got IN_PROGRESS
        JiraTransition reviewTransition = new JiraTransition("41", "В Ревью",
                new JiraTransition.TransitionTarget("10", "Проверка", null));

        // Second call: still no DONE available (stuck in intermediate)
        JiraTransition backTransition = new JiraTransition("42", "Вернуть",
                new JiraTransition.TransitionTarget("12", "В работе", null));

        when(jiraClient.getTransitionsBasicAuth("PROJ-11"))
                .thenReturn(List.of(reviewTransition))
                .thenReturn(List.of(backTransition));

        when(workflowConfigService.categorize("Готово", "Sub-task")).thenReturn(StatusCategory.DONE);
        when(workflowConfigService.categorize("Проверка", "Sub-task")).thenReturn(StatusCategory.IN_PROGRESS);
        when(workflowConfigService.categorize("В работе", "Sub-task")).thenReturn(StatusCategory.IN_PROGRESS);

        SimulationAction action = SimulationAction.transition(
                "PROJ-11", "Sub-task", "Dev One", "В работе", "Готово", "Completing subtask");

        List<SimulationAction> results = executor.execute(List.of(action), SIM_DATE, TEAM_ID);

        assertEquals(1, results.size());
        assertTrue(results.get(0).executed()); // first step still executed
        // Only one transition actually performed (no DONE found on retry)
        verify(jiraClient, times(1)).transitionIssueBasicAuth(eq("PROJ-11"), eq("41"));
        verify(jiraClient, never()).transitionIssueBasicAuth(eq("PROJ-11"), eq("42"));
    }
}

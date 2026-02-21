package com.leadboard.metrics.service;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.dto.BugMetricsResponse;
import com.leadboard.quality.BugSlaService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BugMetricsServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    @Mock
    private BugSlaService bugSlaService;

    @Mock
    private JiraProperties jiraProperties;

    private BugMetricsService bugMetricsService;

    @BeforeEach
    void setUp() {
        bugMetricsService = new BugMetricsService(
                issueRepository, workflowConfigService, bugSlaService, jiraProperties);
    }

    @Test
    void emptyBugList_returnsAllZeros() {
        when(issueRepository.findByBoardCategory("BUG")).thenReturn(List.of());

        BugMetricsResponse result = bugMetricsService.getBugMetrics(null);

        assertEquals(0, result.openBugs());
        assertEquals(0, result.resolvedBugs());
        assertEquals(0, result.staleBugs());
        assertEquals(0, result.avgResolutionHours());
        assertEquals(100.0, result.slaCompliancePercent());
        assertTrue(result.byPriority().isEmpty());
        assertTrue(result.openBugList().isEmpty());
    }

    @Test
    void mixedOpenAndResolved_correctCounts() {
        JiraIssueEntity openBug = createBug("BUG-1", "Open bug", "High", "Open");
        JiraIssueEntity resolvedBug = createBug("BUG-2", "Resolved bug", "Medium", "Done");

        when(issueRepository.findByBoardCategory("BUG")).thenReturn(List.of(openBug, resolvedBug));
        when(workflowConfigService.isDone("Open", "Bug")).thenReturn(false);
        when(workflowConfigService.isDone("Done", "Bug")).thenReturn(true);
        when(bugSlaService.checkStale(openBug)).thenReturn(false);
        when(bugSlaService.getResolutionTimeHours(resolvedBug)).thenReturn(48L);
        when(bugSlaService.checkSlaBreach(resolvedBug)).thenReturn(false);
        when(bugSlaService.checkSlaBreach(openBug)).thenReturn(false);
        when(bugSlaService.getSlaForPriority("High")).thenReturn(Optional.of(72));
        when(bugSlaService.getSlaForPriority("Medium")).thenReturn(Optional.of(120));
        when(jiraProperties.getBaseUrl()).thenReturn("https://jira.example.com");

        BugMetricsResponse result = bugMetricsService.getBugMetrics(null);

        assertEquals(1, result.openBugs());
        assertEquals(1, result.resolvedBugs());
        assertEquals(0, result.staleBugs());
        assertEquals(48, result.avgResolutionHours());
        assertEquals(100.0, result.slaCompliancePercent());
    }

    @Test
    void slaBreachCompliance_correctPercentage() {
        JiraIssueEntity resolved1 = createBug("BUG-1", "Resolved 1", "High", "Done");
        JiraIssueEntity resolved2 = createBug("BUG-2", "Resolved 2", "High", "Done");
        JiraIssueEntity resolved3 = createBug("BUG-3", "Resolved 3", "High", "Done");

        when(issueRepository.findByBoardCategory("BUG")).thenReturn(List.of(resolved1, resolved2, resolved3));
        when(workflowConfigService.isDone(eq("Done"), eq("Bug"))).thenReturn(true);
        when(bugSlaService.getResolutionTimeHours(any())).thenReturn(24L);
        when(bugSlaService.checkSlaBreach(resolved1)).thenReturn(false);
        when(bugSlaService.checkSlaBreach(resolved2)).thenReturn(true);
        when(bugSlaService.checkSlaBreach(resolved3)).thenReturn(false);
        when(bugSlaService.getSlaForPriority("High")).thenReturn(Optional.of(72));

        BugMetricsResponse result = bugMetricsService.getBugMetrics(null);

        assertEquals(3, result.resolvedBugs());
        assertEquals(66.7, result.slaCompliancePercent());
    }

    @Test
    void groupByPriority_correctBreakdown() {
        JiraIssueEntity highOpen = createBug("BUG-1", "High open", "High", "Open");
        JiraIssueEntity highResolved = createBug("BUG-2", "High resolved", "High", "Done");
        JiraIssueEntity lowOpen = createBug("BUG-3", "Low open", "Low", "Open");

        when(issueRepository.findByBoardCategory("BUG")).thenReturn(List.of(highOpen, highResolved, lowOpen));
        when(workflowConfigService.isDone("Open", "Bug")).thenReturn(false);
        when(workflowConfigService.isDone("Done", "Bug")).thenReturn(true);
        when(bugSlaService.checkStale(any())).thenReturn(false);
        when(bugSlaService.getResolutionTimeHours(highResolved)).thenReturn(36L);
        when(bugSlaService.checkSlaBreach(any())).thenReturn(false);
        when(bugSlaService.getSlaForPriority("High")).thenReturn(Optional.of(72));
        when(bugSlaService.getSlaForPriority("Low")).thenReturn(Optional.of(240));
        when(jiraProperties.getBaseUrl()).thenReturn("https://jira.example.com");

        BugMetricsResponse result = bugMetricsService.getBugMetrics(null);

        assertEquals(2, result.byPriority().size());
        // High should come before Low
        assertEquals("High", result.byPriority().get(0).priority());
        assertEquals(1, result.byPriority().get(0).openCount());
        assertEquals(1, result.byPriority().get(0).resolvedCount());
        assertEquals("Low", result.byPriority().get(1).priority());
        assertEquals(1, result.byPriority().get(1).openCount());
        assertEquals(0, result.byPriority().get(1).resolvedCount());
    }

    @Test
    void filterByTeamId_usesCorrectRepository() {
        Long teamId = 5L;
        when(issueRepository.findByBoardCategoryAndTeamId("BUG", teamId)).thenReturn(List.of());

        BugMetricsResponse result = bugMetricsService.getBugMetrics(teamId);

        verify(issueRepository).findByBoardCategoryAndTeamId("BUG", teamId);
        verify(issueRepository, never()).findByBoardCategory(anyString());
        assertEquals(0, result.openBugs());
    }

    @Test
    void staleBugs_countedCorrectly() {
        JiraIssueEntity staleBug = createBug("BUG-1", "Stale", "Medium", "Open");
        JiraIssueEntity freshBug = createBug("BUG-2", "Fresh", "Medium", "Open");

        when(issueRepository.findByBoardCategory("BUG")).thenReturn(List.of(staleBug, freshBug));
        when(workflowConfigService.isDone("Open", "Bug")).thenReturn(false);
        when(bugSlaService.checkStale(staleBug)).thenReturn(true);
        when(bugSlaService.checkStale(freshBug)).thenReturn(false);
        when(bugSlaService.checkSlaBreach(any())).thenReturn(false);
        when(bugSlaService.getSlaForPriority("Medium")).thenReturn(Optional.empty());
        when(jiraProperties.getBaseUrl()).thenReturn("https://jira.example.com");

        BugMetricsResponse result = bugMetricsService.getBugMetrics(null);

        assertEquals(2, result.openBugs());
        assertEquals(1, result.staleBugs());
    }

    @Test
    void openBugList_sortedByPriorityThenAge() {
        JiraIssueEntity highOld = createBug("BUG-1", "High old", "High", "Open");
        highOld.setJiraCreatedAt(OffsetDateTime.now().minusDays(10));

        JiraIssueEntity highNew = createBug("BUG-2", "High new", "High", "Open");
        highNew.setJiraCreatedAt(OffsetDateTime.now().minusDays(2));

        JiraIssueEntity lowOld = createBug("BUG-3", "Low old", "Low", "Open");
        lowOld.setJiraCreatedAt(OffsetDateTime.now().minusDays(20));

        when(issueRepository.findByBoardCategory("BUG")).thenReturn(List.of(lowOld, highNew, highOld));
        when(workflowConfigService.isDone("Open", "Bug")).thenReturn(false);
        when(bugSlaService.checkStale(any())).thenReturn(false);
        when(bugSlaService.checkSlaBreach(any())).thenReturn(false);
        when(bugSlaService.getSlaForPriority(any())).thenReturn(Optional.empty());
        when(jiraProperties.getBaseUrl()).thenReturn("https://jira.example.com");

        BugMetricsResponse result = bugMetricsService.getBugMetrics(null);

        assertEquals(3, result.openBugList().size());
        // High bugs first, older one first among same priority
        assertEquals("BUG-1", result.openBugList().get(0).issueKey());
        assertEquals("BUG-2", result.openBugList().get(1).issueKey());
        assertEquals("BUG-3", result.openBugList().get(2).issueKey());
    }

    private JiraIssueEntity createBug(String key, String summary, String priority, String status) {
        JiraIssueEntity bug = new JiraIssueEntity();
        bug.setIssueKey(key);
        bug.setSummary(summary);
        bug.setPriority(priority);
        bug.setStatus(status);
        bug.setIssueType("Bug");
        bug.setBoardCategory("BUG");
        bug.setJiraCreatedAt(OffsetDateTime.now().minusDays(5));
        return bug;
    }
}

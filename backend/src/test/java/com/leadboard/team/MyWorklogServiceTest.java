package com.leadboard.team;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraWriteService;
import com.leadboard.metrics.entity.IssueWorklogEntity;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyWorklogServiceTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final String ISSUE_KEY = "LB-101";

    @Mock private JiraIssueRepository issueRepository;
    @Mock private IssueWorklogRepository worklogRepository;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private JiraWriteService jiraWriteService;

    private MyWorklogService service;

    @BeforeEach
    void setUp() {
        service = new MyWorklogService(issueRepository, worklogRepository, workflowConfigService, jiraWriteService);
    }

    private JiraIssueEntity createSubtask(String assigneeAccountId, String workflowRole, String issueType,
            Long timeSpentSeconds) {
        JiraIssueEntity issue = new JiraIssueEntity();
        issue.setIssueKey(ISSUE_KEY);
        issue.setBoardCategory("SUBTASK");
        issue.setAssigneeAccountId(assigneeAccountId);
        issue.setWorkflowRole(workflowRole);
        issue.setIssueType(issueType);
        issue.setTimeSpentSeconds(timeSpentSeconds);
        return issue;
    }

    @Test
    void logsTimeJiraFirstThenUpsertsLocally() {
        JiraIssueEntity issue = createSubtask(ACCOUNT_ID, "ROLE_X", "TypeX", 1800L);
        when(issueRepository.findByIssueKey(ISSUE_KEY)).thenReturn(Optional.of(issue));
        when(jiraWriteService.logWorkAs(eq(ACCOUNT_ID), eq(ISSUE_KEY), eq(3600), eq(LocalDate.of(2026, 7, 1)), eq("comment")))
                .thenReturn("wl-777");

        String worklogId = service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.of(2026, 7, 1), BigDecimal.ONE, "comment");

        assertEquals("wl-777", worklogId);

        InOrder inOrder = inOrder(jiraWriteService, worklogRepository, issueRepository);
        inOrder.verify(jiraWriteService).logWorkAs(ACCOUNT_ID, ISSUE_KEY, 3600, LocalDate.of(2026, 7, 1), "comment");
        inOrder.verify(worklogRepository).save(any(IssueWorklogEntity.class));
        inOrder.verify(issueRepository).save(issue);

        ArgumentCaptor<IssueWorklogEntity> captor = ArgumentCaptor.forClass(IssueWorklogEntity.class);
        verify(worklogRepository).save(captor.capture());
        IssueWorklogEntity saved = captor.getValue();
        assertEquals(ISSUE_KEY, saved.getIssueKey());
        assertEquals("wl-777", saved.getWorklogId());
        assertEquals(ACCOUNT_ID, saved.getAuthorAccountId());
        assertEquals(3600, saved.getTimeSpentSeconds());
        assertEquals(LocalDate.of(2026, 7, 1), saved.getStartedDate());
        assertEquals("ROLE_X", saved.getRoleCode());

        assertEquals(3600L + 1800L, issue.getTimeSpentSeconds());
    }

    @Test
    void jiraFailureLeavesNoLocalWrites() {
        JiraIssueEntity issue = createSubtask(ACCOUNT_ID, "ROLE_X", "TypeX", 0L);
        when(issueRepository.findByIssueKey(ISSUE_KEY)).thenReturn(Optional.of(issue));
        WebClientResponseException jiraError = WebClientResponseException.create(500, "Internal Server Error", null, null, null);
        when(jiraWriteService.logWorkAs(anyString(), anyString(), anyInt(), any(), any())).thenThrow(jiraError);

        assertThrows(WebClientResponseException.class, () ->
                service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.of(2026, 7, 1), BigDecimal.ONE, "comment"));

        verifyNoInteractions(worklogRepository);
        verify(issueRepository, never()).save(any());
    }

    @Test
    void rejectsForeignTask() {
        JiraIssueEntity issue = createSubtask("other", "ROLE_X", "TypeX", 0L);
        when(issueRepository.findByIssueKey(ISSUE_KEY)).thenReturn(Optional.of(issue));

        assertThrows(MyWorklogService.LogTimeForbiddenException.class, () ->
                service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.of(2026, 7, 1), BigDecimal.ONE, "comment"));

        verifyNoInteractions(jiraWriteService);
    }

    @Test
    void rejectsNonSubtask() {
        JiraIssueEntity issue = createSubtask(ACCOUNT_ID, "ROLE_X", "TypeX", 0L);
        issue.setBoardCategory("STORY");
        when(issueRepository.findByIssueKey(ISSUE_KEY)).thenReturn(Optional.of(issue));

        assertThrows(MyWorklogService.LogTimeValidationException.class, () ->
                service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.of(2026, 7, 1), BigDecimal.ONE, "comment"));

        verifyNoInteractions(jiraWriteService);
    }

    @Test
    void rejectsFutureDateAndBadHours() {
        // The +1 day tz-tolerance (see acceptsDateOneDayAheadForTimezoneTolerance below) means
        // tomorrow alone no longer trips this — the day after tomorrow still must.
        assertThrows(MyWorklogService.LogTimeValidationException.class, () ->
                service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.now().plusDays(2), BigDecimal.ONE, "comment"));

        assertThrows(MyWorklogService.LogTimeValidationException.class, () ->
                service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.of(2026, 7, 1), BigDecimal.ZERO, "comment"));

        assertThrows(MyWorklogService.LogTimeValidationException.class, () ->
                service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.of(2026, 7, 1), new BigDecimal("24.5"), "comment"));

        verifyNoInteractions(issueRepository);
        verifyNoInteractions(jiraWriteService);
    }

    @Test
    void acceptsDateOneDayAheadForTimezoneTolerance() {
        // A server clock running behind the user's local timezone (e.g. UTC server, MSK user
        // just after midnight) can see "today" from the user as "tomorrow" server-side.
        JiraIssueEntity issue = createSubtask(ACCOUNT_ID, "ROLE_X", "TypeX", 0L);
        when(issueRepository.findByIssueKey(ISSUE_KEY)).thenReturn(Optional.of(issue));
        when(jiraWriteService.logWorkAs(anyString(), anyString(), anyInt(), any(), any())).thenReturn("wl-tz");

        String worklogId = service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.now().plusDays(1), BigDecimal.ONE, "comment");

        assertEquals("wl-tz", worklogId);
    }

    @Test
    void rejectsHoursThatRoundToZeroSeconds() {
        assertThrows(MyWorklogService.LogTimeValidationException.class, () ->
                service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.of(2026, 7, 1), new BigDecimal("0.0001"), "comment"));

        verifyNoInteractions(jiraWriteService);
    }

    @Test
    void throwsJiraNoIdExceptionWhenJiraReturnsNoWorklogId() {
        JiraIssueEntity issue = createSubtask(ACCOUNT_ID, "ROLE_X", "TypeX", 0L);
        when(issueRepository.findByIssueKey(ISSUE_KEY)).thenReturn(Optional.of(issue));
        when(jiraWriteService.logWorkAs(anyString(), anyString(), anyInt(), any(), any())).thenReturn(null);

        assertThrows(MyWorklogService.JiraNoIdException.class, () ->
                service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.of(2026, 7, 1), BigDecimal.ONE, "comment"));

        verifyNoInteractions(worklogRepository);
    }

    @Test
    void roleCodeFallsBackToSubtaskRole() {
        JiraIssueEntity issue = createSubtask(ACCOUNT_ID, null, "TypeX", 0L);
        when(issueRepository.findByIssueKey(ISSUE_KEY)).thenReturn(Optional.of(issue));
        when(jiraWriteService.logWorkAs(anyString(), anyString(), anyInt(), any(), any())).thenReturn("wl-1");
        when(workflowConfigService.getSubtaskRole("TypeX")).thenReturn("ROLE_FALLBACK");

        service.logTime(ACCOUNT_ID, ISSUE_KEY, LocalDate.of(2026, 7, 1), BigDecimal.ONE, "comment");

        ArgumentCaptor<IssueWorklogEntity> captor = ArgumentCaptor.forClass(IssueWorklogEntity.class);
        verify(worklogRepository).save(captor.capture());
        assertEquals("ROLE_FALLBACK", captor.getValue().getRoleCode());
    }
}

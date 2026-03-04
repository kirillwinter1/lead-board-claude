package com.leadboard.sync;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraWorklogResponse;
import com.leadboard.metrics.entity.IssueWorklogEntity;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorklogImportService")
class WorklogImportServiceTest {

    @Mock
    private JiraClient jiraClient;

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private IssueWorklogRepository worklogRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    private WorklogImportService service;

    @BeforeEach
    void setUp() {
        service = new WorklogImportService(jiraClient, issueRepository, worklogRepository,
                teamMemberRepository, workflowConfigService);
    }

    private JiraIssueEntity createSubtask(String key, String parentKey, String workflowRole) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setParentKey(parentKey);
        entity.setIssueType("Sub-task");
        entity.setWorkflowRole(workflowRole);
        entity.setBoardCategory("SUBTASK");
        return entity;
    }

    private JiraWorklogResponse.WorklogEntry createWorklogEntry(String id, String accountId, int seconds, String started) {
        JiraWorklogResponse.WorklogEntry entry = new JiraWorklogResponse.WorklogEntry();
        entry.setId(id);
        entry.setTimeSpentSeconds(seconds);
        entry.setStarted(started);
        JiraWorklogResponse.Author author = new JiraWorklogResponse.Author();
        author.setAccountId(accountId);
        entry.setAuthor(author);
        return entry;
    }

    @Test
    @DisplayName("should import worklogs with workflow role from subtask")
    void shouldImportWorklogsHappyPath() {
        JiraIssueEntity subtask = createSubtask("PROJ-10", "PROJ-1", "DEV");
        when(issueRepository.findByIssueKey("PROJ-10")).thenReturn(Optional.of(subtask));
        when(jiraClient.fetchIssueWorklogs("PROJ-10")).thenReturn(List.of(
                createWorklogEntry("1001", "user1", 14400, "2025-01-15T10:00:00.000+0000"),
                createWorklogEntry("1002", "user1", 28800, "2025-01-16T09:00:00.000+0000")
        ));

        int count = service.importWorklogsForIssue("PROJ-10");

        assertEquals(2, count);
        verify(worklogRepository).deleteByIssueKey("PROJ-10");

        ArgumentCaptor<IssueWorklogEntity> captor = ArgumentCaptor.forClass(IssueWorklogEntity.class);
        verify(worklogRepository, times(2)).save(captor.capture());

        IssueWorklogEntity first = captor.getAllValues().get(0);
        assertEquals("PROJ-10", first.getIssueKey());
        assertEquals("1001", first.getWorklogId());
        assertEquals("user1", first.getAuthorAccountId());
        assertEquals(14400, first.getTimeSpentSeconds());
        assertEquals(LocalDate.of(2025, 1, 15), first.getStartedDate());
        assertEquals("DEV", first.getRoleCode());
    }

    @Test
    @DisplayName("should fallback to team member role when subtask has no workflow role")
    void shouldFallbackToTeamMemberRole() {
        JiraIssueEntity subtask = createSubtask("PROJ-10", "PROJ-1", null);
        subtask.setAssigneeAccountId("user1");
        when(issueRepository.findByIssueKey("PROJ-10")).thenReturn(Optional.of(subtask));

        TeamMemberEntity member = new TeamMemberEntity();
        member.setRole("QA");
        when(teamMemberRepository.findFirstByJiraAccountIdAndActiveTrue("user1"))
                .thenReturn(Optional.of(member));

        when(jiraClient.fetchIssueWorklogs("PROJ-10")).thenReturn(List.of(
                createWorklogEntry("1001", "user1", 3600, "2025-01-15T10:00:00.000+0000")
        ));

        int count = service.importWorklogsForIssue("PROJ-10");

        assertEquals(1, count);
        ArgumentCaptor<IssueWorklogEntity> captor = ArgumentCaptor.forClass(IssueWorklogEntity.class);
        verify(worklogRepository).save(captor.capture());
        assertEquals("QA", captor.getValue().getRoleCode());
    }

    @Test
    @DisplayName("should return 0 when no worklogs exist")
    void shouldReturnZeroWhenNoWorklogs() {
        when(jiraClient.fetchIssueWorklogs("PROJ-10")).thenReturn(List.of());

        int count = service.importWorklogsForIssue("PROJ-10");

        assertEquals(0, count);
        verify(worklogRepository, never()).deleteByIssueKey(any());
        verify(worklogRepository, never()).save(any());
    }

    @Test
    @DisplayName("should be idempotent - delete existing before insert")
    void shouldBeIdempotent() {
        JiraIssueEntity subtask = createSubtask("PROJ-10", "PROJ-1", "SA");
        when(issueRepository.findByIssueKey("PROJ-10")).thenReturn(Optional.of(subtask));
        when(jiraClient.fetchIssueWorklogs("PROJ-10")).thenReturn(List.of(
                createWorklogEntry("1001", "user1", 3600, "2025-01-15T10:00:00.000+0000")
        ));

        // Import twice
        service.importWorklogsForIssue("PROJ-10");
        service.importWorklogsForIssue("PROJ-10");

        // Should delete existing each time
        verify(worklogRepository, times(2)).deleteByIssueKey("PROJ-10");
    }

    @Test
    @DisplayName("should parse various date formats")
    void shouldParseVariousDateFormats() {
        // ISO offset datetime
        assertEquals(LocalDate.of(2025, 1, 15),
                WorklogImportService.parseStartedDate("2025-01-15T10:00:00.000+0000"));

        // Plain date
        assertEquals(LocalDate.of(2025, 3, 20),
                WorklogImportService.parseStartedDate("2025-03-20"));

        // Null / empty
        assertNull(WorklogImportService.parseStartedDate(null));
        assertNull(WorklogImportService.parseStartedDate(""));
    }

    @Test
    @DisplayName("should resolve role: workflow role takes priority over team member role")
    void shouldResolveRolePriority() {
        JiraIssueEntity subtask = createSubtask("PROJ-10", "PROJ-1", "SA");
        subtask.setAssigneeAccountId("user1");

        String role = service.resolveRole(subtask);

        assertEquals("SA", role);
        // Should NOT look up team member when workflowRole is available
        verify(teamMemberRepository, never()).findFirstByJiraAccountIdAndActiveTrue(any());
    }
}

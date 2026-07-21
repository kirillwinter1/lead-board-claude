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
    @DisplayName("importWorklogsForIssue must be @Transactional so delete+insert is atomic")
    void importWorklogsForIssueIsTransactional() throws NoSuchMethodException {
        // The delete + re-insert must be atomic: a failure mid-insert has to roll back the
        // delete, not leave the issue with its worklogs partially wiped (silently losing
        // logged hours until the subtask's status/time changes again). True rollback is a
        // DB-transaction property that a Mockito unit test cannot exercise; this guards the
        // fix — the @Transactional boundary — against silent removal. The pre-fix
        // non-atomic behavior was demonstrated with an in-memory per-statement-commit model
        // during discovery (see outputs/bug-reproducer-report.md).
        assertTrue(WorklogImportService.class
                        .getMethod("importWorklogsForIssue", String.class)
                        .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class),
                "importWorklogsForIssue must be annotated @Transactional for atomic replace");
    }

    @Test
    @DisplayName("keys arriving while the importer is busy are queued, not dropped (candidate 6)")
    void queuedKeysProcessedAfterBusyImport() {
        // Candidate 6: previously, if importWorklogsForIssuesAsync found an import already running
        // it logged a WARN and dropped the incoming keys. With the timeSpentChanged trigger a
        // dropped key may never come back until that same subtask changes again — silent loss.
        // The importer now queues keys that arrive while busy and drains them before returning.
        WorklogImportService spy = spy(service);
        // Wire the self-proxy to the spy so importBatch -> self.importWorklogsForIssue is intercepted.
        org.springframework.test.util.ReflectionTestUtils.setField(spy, "self", spy);

        List<String> processed = new java.util.ArrayList<>();
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            processed.add(key);
            if ("A".equals(key)) {
                // While we are "busy" importing A, a fresh set of keys arrives (as a concurrent
                // sync would deliver via the timeSpentChanged trigger). The active importer must
                // pick them up rather than drop them.
                spy.importWorklogsForIssuesAsync(List.of("B"));
            }
            return 0;
        }).when(spy).importWorklogsForIssue(anyString());

        spy.importWorklogsForIssuesAsync(new java.util.ArrayList<>(List.of("A")));

        assertTrue(processed.contains("A"), "A must be imported");
        assertTrue(processed.contains("B"),
                "keys arriving while the importer was busy must be processed, not dropped");
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

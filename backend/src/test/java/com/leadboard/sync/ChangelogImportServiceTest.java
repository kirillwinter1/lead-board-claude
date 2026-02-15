package com.leadboard.sync;

import com.leadboard.jira.JiraChangelogResponse;
import com.leadboard.jira.JiraClient;
import com.leadboard.metrics.service.StatusChangelogService;
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
class ChangelogImportServiceTest {

    @Mock
    private JiraClient jiraClient;

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private StatusChangelogService statusChangelogService;

    private ChangelogImportService service;

    @BeforeEach
    void setUp() {
        service = new ChangelogImportService(jiraClient, issueRepository, statusChangelogService);
    }

    @Test
    void importChangelogForIssue_importsStatusChanges() {
        // Given
        var histories = createHistories(
                history("2024-01-10T10:00:00.000+0000", "status", null, "To Do"),
                history("2024-01-15T14:30:00.000+0000", "status", "To Do", "In Progress"),
                history("2024-01-20T09:00:00.000+0000", "status", "In Progress", "Done")
        );

        when(jiraClient.fetchIssueChangelog("TEST-1")).thenReturn(histories);
        when(statusChangelogService.importJiraChangelog(eq("TEST-1"), eq("10001"), eq(histories))).thenReturn(3);

        JiraIssueEntity issue = new JiraIssueEntity();
        issue.setIssueKey("TEST-1");
        issue.setIssueType("Story");
        when(issueRepository.findByIssueKey("TEST-1")).thenReturn(Optional.of(issue));
        when(statusChangelogService.findFirstInProgressTransition("TEST-1", "Story")).thenReturn(Optional.empty());
        when(statusChangelogService.findLastDoneTransition("TEST-1", "Story")).thenReturn(Optional.empty());

        // When
        boolean result = service.importChangelogForIssue("TEST-1", "10001", "Story");

        // Then
        assertTrue(result);
        verify(statusChangelogService).importJiraChangelog("TEST-1", "10001", histories);
    }

    @Test
    void importChangelogForIssue_returnsFalseWhenNoChangelog() {
        // Given
        when(jiraClient.fetchIssueChangelog("TEST-2")).thenReturn(List.of());

        // When
        boolean result = service.importChangelogForIssue("TEST-2", "10002", "Task");

        // Then
        assertFalse(result);
        verify(statusChangelogService, never()).importJiraChangelog(any(), any(), any());
    }

    @Test
    void importChangelogForIssue_fixesStartedAt() {
        // Given
        OffsetDateTime realStartedAt = OffsetDateTime.parse("2024-01-15T14:30:00.000+00:00");

        var histories = createHistories(
                history("2024-01-15T14:30:00.000+0000", "status", "To Do", "In Progress")
        );

        when(jiraClient.fetchIssueChangelog("TEST-3")).thenReturn(histories);
        when(statusChangelogService.importJiraChangelog(eq("TEST-3"), eq("10003"), eq(histories))).thenReturn(1);

        JiraIssueEntity issue = new JiraIssueEntity();
        issue.setIssueKey("TEST-3");
        issue.setIssueType("Эпик");
        issue.setStartedAt(OffsetDateTime.now()); // synthetic timestamp
        when(issueRepository.findByIssueKey("TEST-3")).thenReturn(Optional.of(issue));
        when(statusChangelogService.findFirstInProgressTransition("TEST-3", "Эпик"))
                .thenReturn(Optional.of(realStartedAt));
        when(statusChangelogService.findLastDoneTransition("TEST-3", "Эпик"))
                .thenReturn(Optional.empty());

        // When
        service.importChangelogForIssue("TEST-3", "10003", "Эпик");

        // Then
        assertEquals(realStartedAt, issue.getStartedAt());
        verify(issueRepository).save(issue);
    }

    @Test
    void importChangelogForIssue_fixesDoneAt() {
        // Given
        OffsetDateTime realDoneAt = OffsetDateTime.parse("2024-01-20T09:00:00.000+00:00");

        var histories = createHistories(
                history("2024-01-20T09:00:00.000+0000", "status", "In Progress", "Done")
        );

        when(jiraClient.fetchIssueChangelog("TEST-4")).thenReturn(histories);
        when(statusChangelogService.importJiraChangelog(eq("TEST-4"), eq("10004"), eq(histories))).thenReturn(1);

        JiraIssueEntity issue = new JiraIssueEntity();
        issue.setIssueKey("TEST-4");
        issue.setIssueType("Подзадача");
        issue.setDoneAt(null);
        when(issueRepository.findByIssueKey("TEST-4")).thenReturn(Optional.of(issue));
        when(statusChangelogService.findFirstInProgressTransition("TEST-4", "Подзадача"))
                .thenReturn(Optional.empty());
        when(statusChangelogService.findLastDoneTransition("TEST-4", "Подзадача"))
                .thenReturn(Optional.of(realDoneAt));

        // When
        service.importChangelogForIssue("TEST-4", "10004", "Подзадача");

        // Then
        assertEquals(realDoneAt, issue.getDoneAt());
        verify(issueRepository).save(issue);
    }

    @Test
    void fixStartedAtFromChangelog_fixesMultipleIssues() {
        // Given
        OffsetDateTime realDate1 = OffsetDateTime.parse("2024-01-10T10:00:00.000+00:00");
        OffsetDateTime realDate2 = OffsetDateTime.parse("2024-02-05T08:00:00.000+00:00");

        JiraIssueEntity issue1 = new JiraIssueEntity();
        issue1.setIssueKey("PROJ-1");
        issue1.setIssueType("Story");
        issue1.setStartedAt(OffsetDateTime.now());

        JiraIssueEntity issue2 = new JiraIssueEntity();
        issue2.setIssueKey("PROJ-2");
        issue2.setIssueType("Task");
        issue2.setStartedAt(OffsetDateTime.now());

        when(issueRepository.findByProjectKey("PROJ")).thenReturn(List.of(issue1, issue2));
        when(statusChangelogService.findFirstInProgressTransition("PROJ-1", "Story"))
                .thenReturn(Optional.of(realDate1));
        when(statusChangelogService.findFirstInProgressTransition("PROJ-2", "Task"))
                .thenReturn(Optional.of(realDate2));

        // When
        int fixed = service.fixStartedAtFromChangelog("PROJ");

        // Then
        assertEquals(2, fixed);
        assertEquals(realDate1, issue1.getStartedAt());
        assertEquals(realDate2, issue2.getStartedAt());
    }

    @Test
    void fixDoneAtFromChangelog_fixesMultipleIssues() {
        // Given
        OffsetDateTime realDone = OffsetDateTime.parse("2024-01-25T16:00:00.000+00:00");

        JiraIssueEntity issue1 = new JiraIssueEntity();
        issue1.setIssueKey("PROJ-10");
        issue1.setIssueType("Подзадача");
        issue1.setDoneAt(null);

        when(issueRepository.findByProjectKey("PROJ")).thenReturn(List.of(issue1));
        when(statusChangelogService.findLastDoneTransition("PROJ-10", "Подзадача"))
                .thenReturn(Optional.of(realDone));

        // When
        int fixed = service.fixDoneAtFromChangelog("PROJ");

        // Then
        assertEquals(1, fixed);
        assertEquals(realDone, issue1.getDoneAt());
    }

    // ==================== Helpers ====================

    private List<JiraChangelogResponse.ChangelogHistory> createHistories(
            JiraChangelogResponse.ChangelogHistory... histories) {
        return List.of(histories);
    }

    private JiraChangelogResponse.ChangelogHistory history(String created, String field,
                                                            String fromString, String toString) {
        var item = new JiraChangelogResponse.ChangelogItem();
        item.setField(field);
        item.setFromString(fromString);
        item.setToString(toString);

        var history = new JiraChangelogResponse.ChangelogHistory();
        history.setCreated(created);
        history.setItems(List.of(item));

        return history;
    }
}

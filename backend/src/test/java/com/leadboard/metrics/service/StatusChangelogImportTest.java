package com.leadboard.metrics.service;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraChangelogResponse;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusChangelogImportTest {

    @Mock
    private StatusChangelogRepository repository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    private StatusChangelogService service;

    @BeforeEach
    void setUp() {
        service = new StatusChangelogService(repository, workflowConfigService);
    }

    @Test
    void importJiraChangelog_parsesSingleStatusChange() {
        // Given
        var histories = List.of(
                history("2024-01-15T14:30:00.000+0000", "status", "To Do", "In Progress")
        );

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        int count = service.importJiraChangelog("TEST-1", "10001", histories);

        // Then
        assertEquals(1, count);
        verify(repository).deleteByIssueKey("TEST-1");

        ArgumentCaptor<StatusChangelogEntity> captor = ArgumentCaptor.forClass(StatusChangelogEntity.class);
        verify(repository).save(captor.capture());

        StatusChangelogEntity saved = captor.getValue();
        assertEquals("TEST-1", saved.getIssueKey());
        assertEquals("10001", saved.getIssueId());
        assertEquals("To Do", saved.getFromStatus());
        assertEquals("In Progress", saved.getToStatus());
        assertEquals("JIRA", saved.getSource());
        assertNotNull(saved.getTransitionedAt());
        assertNull(saved.getTimeInPreviousStatusSeconds()); // first transition has no previous
    }

    @Test
    void importJiraChangelog_calculatesTimeInPreviousStatus() {
        // Given: two transitions 5 days apart
        var histories = List.of(
                history("2024-01-10T10:00:00.000+0000", "status", null, "To Do"),
                history("2024-01-15T10:00:00.000+0000", "status", "To Do", "In Progress")
        );

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        int count = service.importJiraChangelog("TEST-2", "10002", histories);

        // Then
        assertEquals(2, count);

        ArgumentCaptor<StatusChangelogEntity> captor = ArgumentCaptor.forClass(StatusChangelogEntity.class);
        verify(repository, times(2)).save(captor.capture());

        List<StatusChangelogEntity> entries = captor.getAllValues();
        assertNull(entries.get(0).getTimeInPreviousStatusSeconds()); // first: no previous
        assertEquals(5 * 24 * 3600L, entries.get(1).getTimeInPreviousStatusSeconds()); // 5 days in seconds
    }

    @Test
    void importJiraChangelog_filtersNonStatusChanges() {
        // Given: mix of status and non-status changes
        var statusItem = new JiraChangelogResponse.ChangelogItem();
        statusItem.setField("status");
        statusItem.setFromString("To Do");
        statusItem.setToString("In Progress");

        var assigneeItem = new JiraChangelogResponse.ChangelogItem();
        assigneeItem.setField("assignee");
        assigneeItem.setFromString(null);
        assigneeItem.setToString("John");

        var history = new JiraChangelogResponse.ChangelogHistory();
        history.setCreated("2024-01-15T14:30:00.000+0000");
        history.setItems(List.of(statusItem, assigneeItem));

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        int count = service.importJiraChangelog("TEST-3", "10003", List.of(history));

        // Then
        assertEquals(1, count); // only status change imported
    }

    @Test
    void importJiraChangelog_returnsZeroForEmptyHistories() {
        // When
        int count = service.importJiraChangelog("TEST-4", "10004", List.of());

        // Then
        assertEquals(0, count);
        verify(repository).deleteByIssueKey("TEST-4");
        verify(repository, never()).save(any());
    }

    @Test
    void importJiraChangelog_handlesMultipleTransitions() {
        // Given: full lifecycle
        var histories = List.of(
                history("2024-01-10T08:00:00.000+0000", "status", null, "To Do"),
                history("2024-01-12T10:00:00.000+0000", "status", "To Do", "In Progress"),
                history("2024-01-14T16:00:00.000+0000", "status", "In Progress", "In Review"),
                history("2024-01-15T09:00:00.000+0000", "status", "In Review", "Done")
        );

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        int count = service.importJiraChangelog("TEST-5", "10005", histories);

        // Then
        assertEquals(4, count);

        ArgumentCaptor<StatusChangelogEntity> captor = ArgumentCaptor.forClass(StatusChangelogEntity.class);
        verify(repository, times(4)).save(captor.capture());

        List<StatusChangelogEntity> entries = captor.getAllValues();
        // Verify all have JIRA source
        entries.forEach(e -> assertEquals("JIRA", e.getSource()));
        // Verify order
        assertEquals("To Do", entries.get(0).getToStatus());
        assertEquals("In Progress", entries.get(1).getToStatus());
        assertEquals("In Review", entries.get(2).getToStatus());
        assertEquals("Done", entries.get(3).getToStatus());
    }

    @Test
    void importJiraChangelog_sortsHistoriesByDate() {
        // Given: histories in wrong order
        var histories = List.of(
                history("2024-01-20T09:00:00.000+0000", "status", "In Progress", "Done"),
                history("2024-01-10T10:00:00.000+0000", "status", null, "To Do"),
                history("2024-01-15T14:30:00.000+0000", "status", "To Do", "In Progress")
        );

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        int count = service.importJiraChangelog("TEST-6", "10006", histories);

        // Then
        assertEquals(3, count);

        ArgumentCaptor<StatusChangelogEntity> captor = ArgumentCaptor.forClass(StatusChangelogEntity.class);
        verify(repository, times(3)).save(captor.capture());

        List<StatusChangelogEntity> entries = captor.getAllValues();
        // Should be sorted chronologically
        assertEquals("To Do", entries.get(0).getToStatus());
        assertEquals("In Progress", entries.get(1).getToStatus());
        assertEquals("Done", entries.get(2).getToStatus());
    }

    // ==================== Helpers ====================

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

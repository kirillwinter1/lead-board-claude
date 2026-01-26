package com.leadboard.metrics.service;

import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.status.StatusMappingService;
import com.leadboard.sync.JiraIssueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusChangelogServiceTest {

    @Mock
    private StatusChangelogRepository repository;

    @Mock
    private StatusMappingService statusMappingService;

    private StatusChangelogService service;

    @BeforeEach
    void setUp() {
        service = new StatusChangelogService(repository, statusMappingService);
    }

    @Test
    void detectAndRecordStatusChange_recordsTransition() {
        // Given
        JiraIssueEntity existing = new JiraIssueEntity();
        existing.setStatus("To Do");
        existing.setUpdatedAt(OffsetDateTime.now().minusHours(5));

        JiraIssueEntity updated = new JiraIssueEntity();
        updated.setIssueKey("TEST-123");
        updated.setIssueId("12345");
        updated.setStatus("In Progress");

        when(repository.findByIssueKeyAndToStatusAndTransitionedAt(any(), any(), any()))
                .thenReturn(Optional.empty());

        // When
        service.detectAndRecordStatusChange(existing, updated);

        // Then
        ArgumentCaptor<StatusChangelogEntity> captor = ArgumentCaptor.forClass(StatusChangelogEntity.class);
        verify(repository).save(captor.capture());

        StatusChangelogEntity saved = captor.getValue();
        assertEquals("TEST-123", saved.getIssueKey());
        assertEquals("12345", saved.getIssueId());
        assertEquals("To Do", saved.getFromStatus());
        assertEquals("In Progress", saved.getToStatus());
        assertNotNull(saved.getTransitionedAt());
        assertNotNull(saved.getTimeInPreviousStatusSeconds());
        assertTrue(saved.getTimeInPreviousStatusSeconds() > 0);
    }

    @Test
    void detectAndRecordStatusChange_ignoresSameStatus() {
        // Given
        JiraIssueEntity existing = new JiraIssueEntity();
        existing.setStatus("In Progress");

        JiraIssueEntity updated = new JiraIssueEntity();
        updated.setIssueKey("TEST-123");
        updated.setStatus("In Progress");

        // When
        service.detectAndRecordStatusChange(existing, updated);

        // Then
        verify(repository, never()).save(any());
    }

    @Test
    void detectAndRecordStatusChange_handlesNewIssue() {
        // Given
        JiraIssueEntity updated = new JiraIssueEntity();
        updated.setIssueKey("TEST-123");
        updated.setIssueId("12345");
        updated.setStatus("To Do");

        when(repository.findByIssueKeyAndToStatusAndTransitionedAt(any(), any(), any()))
                .thenReturn(Optional.empty());

        // When
        service.detectAndRecordStatusChange(null, updated);

        // Then
        ArgumentCaptor<StatusChangelogEntity> captor = ArgumentCaptor.forClass(StatusChangelogEntity.class);
        verify(repository).save(captor.capture());

        StatusChangelogEntity saved = captor.getValue();
        assertEquals("TEST-123", saved.getIssueKey());
        assertNull(saved.getFromStatus());
        assertEquals("To Do", saved.getToStatus());
        assertNull(saved.getTimeInPreviousStatusSeconds());
    }

    @Test
    void updateDoneAtIfNeeded_setsDateOnDoneStatus() {
        // Given
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey("TEST-123");
        entity.setStatus("Done");
        entity.setDoneAt(null);

        when(statusMappingService.isDone("Done", null)).thenReturn(true);

        // When
        service.updateDoneAtIfNeeded(entity);

        // Then
        assertNotNull(entity.getDoneAt());
    }

    @Test
    void updateDoneAtIfNeeded_clearsDateWhenReopened() {
        // Given
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey("TEST-123");
        entity.setStatus("In Progress");
        entity.setDoneAt(OffsetDateTime.now().minusDays(1));

        when(statusMappingService.isDone("In Progress", null)).thenReturn(false);

        // When
        service.updateDoneAtIfNeeded(entity);

        // Then
        assertNull(entity.getDoneAt());
    }

    @Test
    void updateDoneAtIfNeeded_preservesExistingDoneAt() {
        // Given
        OffsetDateTime existingDoneAt = OffsetDateTime.now().minusDays(5);
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey("TEST-123");
        entity.setStatus("Done");
        entity.setDoneAt(existingDoneAt);

        when(statusMappingService.isDone("Done", null)).thenReturn(true);

        // When
        service.updateDoneAtIfNeeded(entity);

        // Then
        assertEquals(existingDoneAt, entity.getDoneAt());
    }
}

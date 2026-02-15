package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.metrics.entity.FlagChangelogEntity;
import com.leadboard.metrics.repository.FlagChangelogRepository;
import com.leadboard.sync.JiraIssueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagChangelogServiceTest {

    @Mock
    private FlagChangelogRepository repository;

    @Mock
    private WorkCalendarService workCalendarService;

    private FlagChangelogService service;

    @BeforeEach
    void setUp() {
        service = new FlagChangelogService(repository, workCalendarService);
    }

    @Test
    void detectFlagChange_flagged_createsEntry() {
        JiraIssueEntity existing = new JiraIssueEntity();
        existing.setIssueKey("PROJ-1");
        existing.setFlagged(false);

        JiraIssueEntity updated = new JiraIssueEntity();
        updated.setIssueKey("PROJ-1");
        updated.setFlagged(true);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.detectAndRecordFlagChange(existing, updated);

        ArgumentCaptor<FlagChangelogEntity> captor = ArgumentCaptor.forClass(FlagChangelogEntity.class);
        verify(repository).save(captor.capture());

        FlagChangelogEntity saved = captor.getValue();
        assertEquals("PROJ-1", saved.getIssueKey());
        assertNotNull(saved.getFlaggedAt());
        assertNull(saved.getUnflaggedAt());
    }

    @Test
    void detectFlagChange_unflagged_closesEntry() {
        JiraIssueEntity existing = new JiraIssueEntity();
        existing.setIssueKey("PROJ-1");
        existing.setFlagged(true);

        JiraIssueEntity updated = new JiraIssueEntity();
        updated.setIssueKey("PROJ-1");
        updated.setFlagged(false);

        FlagChangelogEntity openEntry = new FlagChangelogEntity();
        openEntry.setIssueKey("PROJ-1");
        openEntry.setFlaggedAt(OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC));

        when(repository.findOpenByIssueKey("PROJ-1")).thenReturn(Optional.of(openEntry));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.detectAndRecordFlagChange(existing, updated);

        ArgumentCaptor<FlagChangelogEntity> captor = ArgumentCaptor.forClass(FlagChangelogEntity.class);
        verify(repository).save(captor.capture());

        FlagChangelogEntity saved = captor.getValue();
        assertEquals("PROJ-1", saved.getIssueKey());
        assertNotNull(saved.getUnflaggedAt());
    }

    @Test
    void detectFlagChange_noChange_doesNotSave() {
        JiraIssueEntity existing = new JiraIssueEntity();
        existing.setIssueKey("PROJ-1");
        existing.setFlagged(true);

        JiraIssueEntity updated = new JiraIssueEntity();
        updated.setIssueKey("PROJ-1");
        updated.setFlagged(true);

        service.detectAndRecordFlagChange(existing, updated);

        verify(repository, never()).save(any());
    }

    @Test
    void calculateFlaggedWorkdays_noEntries_returnsZero() {
        when(repository.findByIssueKey("PROJ-1")).thenReturn(Collections.emptyList());

        int result = service.calculateFlaggedWorkdays("PROJ-1",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(0, result);
    }

    @Test
    void calculateFlaggedWorkdays_partialOverlap() {
        // Flag period: Jan 5 - Jan 15, query period: Jan 10 - Jan 20
        // Overlap: Jan 10 - Jan 15
        FlagChangelogEntity entry = new FlagChangelogEntity();
        entry.setIssueKey("PROJ-1");
        entry.setFlaggedAt(OffsetDateTime.of(2025, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC));
        entry.setUnflaggedAt(OffsetDateTime.of(2025, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC));

        when(repository.findByIssueKey("PROJ-1")).thenReturn(List.of(entry));
        when(workCalendarService.countWorkdays(
                LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 15)))
                .thenReturn(4);

        int result = service.calculateFlaggedWorkdays("PROJ-1",
                LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 20));

        assertEquals(4, result);
    }

    @Test
    void calculateFlaggedWorkdays_multiplePeriods() {
        // Two flag periods within query range
        FlagChangelogEntity entry1 = new FlagChangelogEntity();
        entry1.setIssueKey("PROJ-1");
        entry1.setFlaggedAt(OffsetDateTime.of(2025, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC));
        entry1.setUnflaggedAt(OffsetDateTime.of(2025, 1, 8, 0, 0, 0, 0, ZoneOffset.UTC));

        FlagChangelogEntity entry2 = new FlagChangelogEntity();
        entry2.setIssueKey("PROJ-1");
        entry2.setFlaggedAt(OffsetDateTime.of(2025, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC));
        entry2.setUnflaggedAt(OffsetDateTime.of(2025, 1, 17, 0, 0, 0, 0, ZoneOffset.UTC));

        when(repository.findByIssueKey("PROJ-1")).thenReturn(List.of(entry1, entry2));
        when(workCalendarService.countWorkdays(
                LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 8)))
                .thenReturn(2);
        when(workCalendarService.countWorkdays(
                LocalDate.of(2025, 1, 15), LocalDate.of(2025, 1, 17)))
                .thenReturn(3);

        int result = service.calculateFlaggedWorkdays("PROJ-1",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(5, result);
    }

    @Test
    void calculateFlaggedWorkdays_openEntry_usesToday() {
        // Open flag entry (no unflagged_at) — uses today
        FlagChangelogEntity entry = new FlagChangelogEntity();
        entry.setIssueKey("PROJ-1");
        entry.setFlaggedAt(OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC));
        entry.setUnflaggedAt(null); // still flagged

        when(repository.findByIssueKey("PROJ-1")).thenReturn(List.of(entry));
        when(workCalendarService.countWorkdays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(5);

        int result = service.calculateFlaggedWorkdays("PROJ-1",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(5, result);
    }
}

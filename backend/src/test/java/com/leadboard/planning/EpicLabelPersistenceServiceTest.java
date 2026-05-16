package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the persistence side-effects of {@link EpicLabelPersistenceService}.
 *
 * <p>These tests guard the contract that the bean actually loads the entity,
 * overwrites its labels, and calls {@code save()} — historically this lived inside
 * {@link QuarterlyPlanningService} but was silently bypassed by Spring AOP
 * self-invocation. A separate unit-test on the dedicated bean keeps the regression
 * obvious if anyone moves the logic back inline later.</p>
 */
@ExtendWith(MockitoExtension.class)
class EpicLabelPersistenceServiceTest {

    @Mock private JiraIssueRepository issueRepository;

    private EpicLabelPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new EpicLabelPersistenceService(issueRepository);
    }

    @Test
    void mirrorEpicLabels_overwritesLabelsAndSaves() {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("EPIC-1");
        epic.setLabels(new String[]{"old-label", "2025Q4"});

        when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));

        service.mirrorEpicLabels("EPIC-1", List.of("2026Q2", "feature"));

        ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
        verify(issueRepository).save(captor.capture());

        JiraIssueEntity saved = captor.getValue();
        assertArrayEquals(new String[]{"2026Q2", "feature"}, saved.getLabels(),
                "Labels must be replaced wholesale, not appended");
    }

    @Test
    void mirrorEpicLabels_emptyListClearsAllLabels() {
        // Caller may pass an empty list when the user removes the last quarter label;
        // we must not skip the save (otherwise old labels would silently linger in DB).
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("EPIC-1");
        epic.setLabels(new String[]{"2026Q2"});

        when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));

        service.mirrorEpicLabels("EPIC-1", List.of());

        verify(issueRepository).save(epic);
        assertArrayEquals(new String[]{}, epic.getLabels());
    }

    @Test
    void mirrorEpicLabels_missingEpic_throwsEpicNotFoundException() {
        // 404 path: the persistence step must surface a domain exception (not a
        // bare NPE/IllegalStateException) so the controller can map it cleanly.
        when(issueRepository.findByIssueKey("EPIC-X")).thenReturn(Optional.empty());

        assertThrows(EpicNotFoundException.class,
                () -> service.mirrorEpicLabels("EPIC-X", List.of("2026Q2")));

        verify(issueRepository, never()).save(any());
    }
}

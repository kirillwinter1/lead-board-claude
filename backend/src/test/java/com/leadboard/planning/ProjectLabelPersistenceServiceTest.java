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
 * Unit tests for {@link ProjectLabelPersistenceService}.
 *
 * <p>Guards the same contract as {@link EpicLabelPersistenceServiceTest}: the bean
 * must load the entity, overwrite labels, and call {@code save()}. Keeps the
 * REQUIRES_NEW + Spring-proxy pattern obvious so nobody silently re-inlines the
 * write back into the read-only outer service.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectLabelPersistenceServiceTest {

    @Mock private JiraIssueRepository issueRepository;

    private ProjectLabelPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ProjectLabelPersistenceService(issueRepository);
    }

    @Test
    void mirrorProjectLabels_overwritesLabelsAndSaves() {
        JiraIssueEntity project = new JiraIssueEntity();
        project.setIssueKey("PROJ-1");
        project.setLabels(new String[]{"old-label", "2025Q4"});

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));

        service.mirrorProjectLabels("PROJ-1", List.of("2026Q2", "feature"));

        ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
        verify(issueRepository).save(captor.capture());

        JiraIssueEntity saved = captor.getValue();
        assertArrayEquals(new String[]{"2026Q2", "feature"}, saved.getLabels(),
                "Labels must be replaced wholesale, not appended");
    }

    @Test
    void mirrorProjectLabels_emptyListClearsAllLabels() {
        // PM may explicitly clear the desired_quarter — the persistence step must
        // still hit save() so the cleared state reaches the DB.
        JiraIssueEntity project = new JiraIssueEntity();
        project.setIssueKey("PROJ-1");
        project.setLabels(new String[]{"2026Q2"});

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));

        service.mirrorProjectLabels("PROJ-1", List.of());

        verify(issueRepository).save(project);
        assertArrayEquals(new String[]{}, project.getLabels());
    }

    @Test
    void mirrorProjectLabels_missingProject_throwsProjectNotFoundException() {
        // 404 path: surface a domain exception so the controller can map cleanly.
        when(issueRepository.findByIssueKey("PROJ-X")).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class,
                () -> service.mirrorProjectLabels("PROJ-X", List.of("2026Q2")));

        verify(issueRepository, never()).save(any());
    }
}

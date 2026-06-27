package com.leadboard.matrix;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatrixServiceTest {

    private static final Long TEAM_ID = 7L;
    private static final String STORY = "STORY";

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    @InjectMocks
    private MatrixService matrixService;

    private JiraIssueEntity issue(String key, String quadrant) {
        JiraIssueEntity e = new JiraIssueEntity();
        e.setIssueKey(key);
        e.setProjectKey("PROJ");
        e.setSummary("Summary " + key);
        e.setStatus("To Do");
        e.setIssueType("Task");
        e.setBoardCategory(STORY);
        e.setTeamId(TEAM_ID);
        e.setEisenhowerQuadrant(quadrant);
        return e;
    }

    // ==================== getMatrix ====================

    @Test
    void getMatrix_groupsByQuadrant_includingUnassigned() {
        JiraIssueEntity p1 = issue("PROJ-1", "P1");
        JiraIssueEntity p2 = issue("PROJ-2", "P2");
        JiraIssueEntity p3 = issue("PROJ-3", "P3");
        JiraIssueEntity p4 = issue("PROJ-4", "P4");
        JiraIssueEntity none = issue("PROJ-5", null);
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY))
                .thenReturn(List.of(p1, p2, p3, p4, none));
        when(workflowConfigService.isDone(anyString(), anyString(), anyString())).thenReturn(false);

        MatrixViewDto view = matrixService.getMatrix(TEAM_ID);

        assertThat(view.p1()).extracting(MatrixCardDto::issueKey).containsExactly("PROJ-1");
        assertThat(view.p2()).extracting(MatrixCardDto::issueKey).containsExactly("PROJ-2");
        assertThat(view.p3()).extracting(MatrixCardDto::issueKey).containsExactly("PROJ-3");
        assertThat(view.p4()).extracting(MatrixCardDto::issueKey).containsExactly("PROJ-4");
        assertThat(view.unassigned()).extracting(MatrixCardDto::issueKey).containsExactly("PROJ-5");
    }

    @Test
    void getMatrix_filtersOutDoneTasks() {
        JiraIssueEntity open = issue("PROJ-1", "P1");
        JiraIssueEntity done = issue("PROJ-2", "P1");
        done.setStatus("Done");
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY))
                .thenReturn(List.of(open, done));
        when(workflowConfigService.isDone("To Do", "Task", "PROJ")).thenReturn(false);
        when(workflowConfigService.isDone("Done", "Task", "PROJ")).thenReturn(true);

        MatrixViewDto view = matrixService.getMatrix(TEAM_ID);

        assertThat(view.p1()).extracting(MatrixCardDto::issueKey).containsExactly("PROJ-1");
    }

    @Test
    void getMatrix_onlyQueriesOrphanStories_excludingEpicsSubtasksAndChildren() {
        // The repository query itself enforces parent_key IS NULL AND board_category = STORY,
        // so epics/subtasks/non-orphan stories never reach the service. We assert the
        // service delegates with exactly those constraints.
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY))
                .thenReturn(List.of());

        MatrixViewDto view = matrixService.getMatrix(TEAM_ID);

        verify(issueRepository).findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY);
        assertThat(view.p1()).isEmpty();
        assertThat(view.unassigned()).isEmpty();
    }

    @Test
    void getMatrix_computesEstimateHours_andNullWhenNoEstimate() {
        JiraIssueEntity withEstimate = issue("PROJ-1", "P1");
        withEstimate.setOriginalEstimateSeconds(7200L); // 2h
        JiraIssueEntity noEstimate = issue("PROJ-2", "P1");
        noEstimate.setOriginalEstimateSeconds(null);
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY))
                .thenReturn(List.of(withEstimate, noEstimate));
        when(workflowConfigService.isDone(anyString(), anyString(), anyString())).thenReturn(false);

        MatrixViewDto view = matrixService.getMatrix(TEAM_ID);

        assertThat(view.p1()).hasSize(2);
        assertThat(view.p1().get(0).estimateHours()).isEqualTo(2.0);
        assertThat(view.p1().get(1).estimateHours()).isNull();
    }

    // ==================== triage ====================

    @Test
    void triage_setsQuadrant_onValidOrphan() {
        JiraIssueEntity e = issue("PROJ-1", null);
        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(e));

        MatrixCardDto card = matrixService.triage("PROJ-1", "P2");

        assertThat(card.quadrant()).isEqualTo("P2");
        ArgumentCaptor<JiraIssueEntity> captor = ArgumentCaptor.forClass(JiraIssueEntity.class);
        verify(issueRepository).save(captor.capture());
        assertThat(captor.getValue().getEisenhowerQuadrant()).isEqualTo("P2");
    }

    @Test
    void triage_normalizesLowercaseQuadrant() {
        JiraIssueEntity e = issue("PROJ-1", null);
        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(e));

        MatrixCardDto card = matrixService.triage("PROJ-1", "p3");

        assertThat(card.quadrant()).isEqualTo("P3");
    }

    @Test
    void triage_clearsQuadrant_whenNull() {
        JiraIssueEntity e = issue("PROJ-1", "P1");
        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(e));

        MatrixCardDto card = matrixService.triage("PROJ-1", null);

        assertThat(card.quadrant()).isNull();
        assertThat(e.getEisenhowerQuadrant()).isNull();
        verify(issueRepository).save(e);
    }

    @Test
    void triage_rejectsInvalidQuadrant_withoutLoadingOrSaving() {
        assertThatThrownBy(() -> matrixService.triage("PROJ-1", "P9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid quadrant");

        verify(issueRepository, never()).findByIssueKey(anyString());
        verify(issueRepository, never()).save(any());
    }

    @Test
    void triage_throwsNotFound_whenIssueMissing() {
        when(issueRepository.findByIssueKey("PROJ-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matrixService.triage("PROJ-X", "P1"))
                .isInstanceOf(MatrixIssueNotFoundException.class);

        verify(issueRepository, never()).save(any());
    }

    @Test
    void triage_throwsNotFound_whenIssueHasParent() {
        JiraIssueEntity child = issue("PROJ-2", null);
        child.setParentKey("PROJ-1");
        when(issueRepository.findByIssueKey("PROJ-2")).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> matrixService.triage("PROJ-2", "P1"))
                .isInstanceOf(MatrixIssueNotFoundException.class)
                .hasMessageContaining("orphan");

        verify(issueRepository, never()).save(any());
    }

    @Test
    void triage_throwsNotFound_whenIssueNotStoryCategory() {
        JiraIssueEntity epic = issue("PROJ-3", null);
        epic.setBoardCategory("EPIC");
        when(issueRepository.findByIssueKey("PROJ-3")).thenReturn(Optional.of(epic));

        assertThatThrownBy(() -> matrixService.triage("PROJ-3", "P1"))
                .isInstanceOf(MatrixIssueNotFoundException.class);

        verify(issueRepository, never()).save(any());
    }
}

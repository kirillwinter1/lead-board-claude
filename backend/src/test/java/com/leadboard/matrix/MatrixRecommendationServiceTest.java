package com.leadboard.matrix;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.matrix.RecommendationDtos.RecommendationViewDto;
import com.leadboard.matrix.RecommendationDtos.RoleSlice;
import com.leadboard.matrix.RecommendationDtos.StoryRec;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatrixRecommendationServiceTest {

    private static final Long TEAM_ID = 7L;
    private static final String STORY = "STORY";
    private static final String BUG = "BUG";

    @Mock private JiraIssueRepository issueRepository;
    @Mock private WorkflowConfigService workflowConfigService;

    private MatrixRecommendationService build() {
        MatrixService matrixService = new MatrixService(issueRepository, workflowConfigService);
        return new MatrixRecommendationService(issueRepository, matrixService);
    }

    private JiraIssueEntity story(String key, String quadrant, String type) {
        JiraIssueEntity e = new JiraIssueEntity();
        e.setIssueKey(key);
        e.setProjectKey("PROJ");
        e.setSummary("Summary " + key);
        e.setStatus("To Do");
        e.setIssueType(type);
        e.setBoardCategory(STORY);
        e.setTeamId(TEAM_ID);
        e.setEisenhowerQuadrant(quadrant);
        return e;
    }

    private JiraIssueEntity subtask(String key, String parent, String role, Long estimateSeconds) {
        JiraIssueEntity e = new JiraIssueEntity();
        e.setIssueKey(key);
        e.setParentKey(parent);
        e.setWorkflowRole(role);
        e.setOriginalEstimateSeconds(estimateSeconds);
        e.setIssueType("Subtask");
        return e;
    }

    /** Stubs both board-category loads (strict stubbing requires matching every call). */
    private void stubOrphans(List<JiraIssueEntity> stories, List<JiraIssueEntity> bugs) {
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY)).thenReturn(stories);
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, BUG)).thenReturn(bugs);
    }

    @Test
    void zeroBugPolicy_collectsOpenOrphanBugs_excludingDone() {
        JiraIssueEntity bug1 = story("PROJ-9", null, "Bug");
        bug1.setBoardCategory(BUG);
        JiraIssueEntity bug2done = story("PROJ-8", null, "Bug");
        bug2done.setBoardCategory(BUG);
        bug2done.setStatus("Done");
        stubOrphans(List.of(), List.of(bug1, bug2done));
        when(workflowConfigService.isDone("To Do", "Bug", "PROJ")).thenReturn(false);
        when(workflowConfigService.isDone("Done", "Bug", "PROJ")).thenReturn(true);

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        assertThat(view.zeroBugPolicy().openBugCount()).isEqualTo(1);
        assertThat(view.zeroBugPolicy().bugs()).extracting(RecCard::issueKey).containsExactly("PROJ-9");
    }

    @Test
    void recommended_storyWithEstimatedRoleSubtasks_hasRoleCompositionAndTotal() {
        JiraIssueEntity s1 = story("PROJ-1", "P1", "Story");
        stubOrphans(List.of(s1), List.of());
        when(workflowConfigService.isDone(anyString(), anyString(), anyString())).thenReturn(false);
        when(workflowConfigService.isBug(anyString())).thenReturn(false);
        when(issueRepository.findByParentKeyIn(anyList())).thenReturn(List.of(
                subtask("PROJ-1-1", "PROJ-1", "SA", 28800L),   // 8h
                subtask("PROJ-1-2", "PROJ-1", "DEV", 57600L),  // 16h
                subtask("PROJ-1-3", "PROJ-1", "QA", 28800L))); // 8h

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        assertThat(view.needsEstimation()).isEmpty();
        assertThat(view.recommended()).hasSize(1);
        StoryRec rec = view.recommended().get(0);
        assertThat(rec.issueKey()).isEqualTo("PROJ-1");
        assertThat(rec.quadrant()).isEqualTo("P1");
        assertThat(rec.totalHours()).isEqualTo(32.0);
        // roles sorted by code: DEV, QA, SA
        assertThat(rec.roles()).extracting(RoleSlice::roleCode).containsExactly("DEV", "QA", "SA");
        assertThat(rec.roles()).extracting(RoleSlice::hours).containsExactly(16.0, 8.0, 8.0);
        assertThat(rec.roles().get(0).subtaskKey()).isEqualTo("PROJ-1-2");
    }

    @Test
    void needsEstimation_whenStoryNotCut_orHasUnestimatedRoleSubtask() {
        JiraIssueEntity noSubtasks = story("PROJ-1", "P1", "Story");     // not cut into roles
        JiraIssueEntity unestimated = story("PROJ-2", "P2", "Story");    // a role subtask without estimate
        stubOrphans(List.of(noSubtasks, unestimated), List.of());
        when(workflowConfigService.isDone(anyString(), anyString(), anyString())).thenReturn(false);
        when(workflowConfigService.isBug(anyString())).thenReturn(false);
        when(issueRepository.findByParentKeyIn(anyList())).thenReturn(List.of(
                subtask("PROJ-2-1", "PROJ-2", "DEV", null)));

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        assertThat(view.recommended()).isEmpty();
        assertThat(view.needsEstimation()).extracting(RecCard::issueKey)
                .containsExactlyInAnyOrder("PROJ-1", "PROJ-2");
    }

    @Test
    void recommended_sortedByQuadrant() {
        JiraIssueEntity p2 = story("PROJ-2", "P2", "Story");
        JiraIssueEntity p1 = story("PROJ-1", "P1", "Story");
        stubOrphans(List.of(p2, p1), List.of());
        when(workflowConfigService.isDone(anyString(), anyString(), anyString())).thenReturn(false);
        when(workflowConfigService.isBug(anyString())).thenReturn(false);
        when(issueRepository.findByParentKeyIn(anyList())).thenReturn(List.of(
                subtask("PROJ-1-1", "PROJ-1", "DEV", 3600L),
                subtask("PROJ-2-1", "PROJ-2", "DEV", 3600L)));

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        assertThat(view.recommended()).extracting(StoryRec::issueKey).containsExactly("PROJ-1", "PROJ-2");
    }

    @Test
    void untriagedStories_areExcluded_noNpeOnNullQuadrant() {
        JiraIssueEntity triaged = story("PROJ-1", "P1", "Story");
        JiraIssueEntity untriaged = story("PROJ-2", null, "Story"); // null quadrant -> must not crash
        stubOrphans(List.of(triaged, untriaged), List.of());
        when(workflowConfigService.isDone(anyString(), anyString(), anyString())).thenReturn(false);
        when(workflowConfigService.isBug(anyString())).thenReturn(false);
        when(issueRepository.findByParentKeyIn(anyList())).thenReturn(List.of(
                subtask("PROJ-1-1", "PROJ-1", "DEV", 3600L)));

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        assertThat(view.recommended()).extracting(StoryRec::issueKey).containsExactly("PROJ-1");
    }

    @Test
    void emptyTeam_returnsEmptySections() {
        stubOrphans(List.of(), List.of());

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        assertThat(view.recommended()).isEmpty();
        assertThat(view.needsEstimation()).isEmpty();
        assertThat(view.zeroBugPolicy().openBugCount()).isZero();
    }
}

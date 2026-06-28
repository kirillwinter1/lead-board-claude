package com.leadboard.matrix;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.matrix.RecommendationDtos.RecommendationViewDto;
import com.leadboard.matrix.RecommendationDtos.RoleRecommendation;
import com.leadboard.planning.RoleLoadService;
import com.leadboard.planning.dto.RoleLoadResponse;
import com.leadboard.planning.dto.RoleLoadResponse.RoleLoadInfo;
import com.leadboard.planning.dto.RoleLoadResponse.UtilizationStatus;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatrixRecommendationServiceTest {

    private static final Long TEAM_ID = 7L;
    private static final String STORY = "STORY";

    @Mock private JiraIssueRepository issueRepository;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private RoleLoadService roleLoadService;

    private MatrixService matrixService;

    private MatrixRecommendationService build() {
        matrixService = new MatrixService(issueRepository, workflowConfigService);
        return new MatrixRecommendationService(issueRepository, roleLoadService, matrixService);
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

    private RoleLoadResponse load(Map<String, RoleLoadInfo> roles) {
        return new RoleLoadResponse(TEAM_ID, LocalDate.now(), 14, roles, List.of());
    }

    private RoleLoadInfo idle(double capacity, double assigned) {
        return new RoleLoadInfo(2, BigDecimal.valueOf(capacity), BigDecimal.valueOf(assigned),
                BigDecimal.ZERO, UtilizationStatus.IDLE);
    }

    private RoleLoadInfo normal() {
        return new RoleLoadInfo(2, BigDecimal.valueOf(80), BigDecimal.valueOf(80),
                BigDecimal.ZERO, UtilizationStatus.NORMAL);
    }

    @Test
    void zeroBugPolicy_collectsOpenOrphanBugs_excludingDone() {
        when(workflowConfigService.isBug("Bug")).thenReturn(true);
        JiraIssueEntity bug1 = story("PROJ-9", null, "Bug");
        JiraIssueEntity bug2done = story("PROJ-8", null, "Bug");
        bug2done.setStatus("Done");
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY))
                .thenReturn(List.of(bug1, bug2done));
        when(workflowConfigService.isDone("To Do", "Bug", "PROJ")).thenReturn(false);
        when(workflowConfigService.isDone("Done", "Bug", "PROJ")).thenReturn(true);
        when(roleLoadService.calculateRoleLoad(TEAM_ID)).thenReturn(load(Map.of()));

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        assertThat(view.zeroBugPolicy().openBugCount()).isEqualTo(1);
        assertThat(view.zeroBugPolicy().bugs()).extracting(RecCard::issueKey).containsExactly("PROJ-9");
    }

    @Test
    void readyVsNeedsEstimation_splitByRoleSubtaskEstimate() {
        when(workflowConfigService.isBug(anyString())).thenReturn(false);
        JiraIssueEntity s1 = story("PROJ-1", "P1", "Story");
        JiraIssueEntity s2 = story("PROJ-2", "P2", "Story");
        JiraIssueEntity s3 = story("PROJ-3", "P3", "Story");
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY))
                .thenReturn(List.of(s1, s2, s3));
        when(workflowConfigService.isDone(anyString(), anyString(), anyString())).thenReturn(false);
        when(issueRepository.findByParentKeyIn(anyList()))
                .thenReturn(List.of(
                        subtask("PROJ-1-1", "PROJ-1", "QA", 7200L),
                        subtask("PROJ-2-1", "PROJ-2", "QA", null),
                        subtask("PROJ-3-1", "PROJ-3", "DEV", 3600L)));
        when(roleLoadService.calculateRoleLoad(TEAM_ID))
                .thenReturn(load(Map.of("QA", idle(16, 0), "DEV", normal())));

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        assertThat(view.roles()).hasSize(1);
        RoleRecommendation qa = view.roles().get(0);
        assertThat(qa.roleCode()).isEqualTo("QA");
        assertThat(qa.idleHours()).isEqualTo(16.0);
        assertThat(qa.ready()).extracting(RecCard::issueKey).containsExactly("PROJ-1");
        assertThat(qa.ready().get(0).roleEstimateHours()).isEqualTo(2.0);
        assertThat(qa.ready().get(0).cumulativeHours()).isEqualTo(2.0);
        assertThat(qa.ready().get(0).fitsInIdle()).isTrue();
        assertThat(qa.needsEstimation()).extracting(RecCard::issueKey)
                .containsExactlyInAnyOrder("PROJ-2", "PROJ-3");
    }

    @Test
    void ready_sortedByQuadrant_andCumulativeFlagsOverflow() {
        when(workflowConfigService.isBug(anyString())).thenReturn(false);
        JiraIssueEntity p2 = story("PROJ-2", "P2", "Story");
        JiraIssueEntity p1 = story("PROJ-1", "P1", "Story");
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY))
                .thenReturn(List.of(p2, p1));
        when(workflowConfigService.isDone(anyString(), anyString(), anyString())).thenReturn(false);
        when(issueRepository.findByParentKeyIn(anyList()))
                .thenReturn(List.of(
                        subtask("PROJ-1-1", "PROJ-1", "DEV", 18000L),
                        subtask("PROJ-2-1", "PROJ-2", "DEV", 18000L)));
        when(roleLoadService.calculateRoleLoad(TEAM_ID))
                .thenReturn(load(Map.of("DEV", idle(8, 0))));

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        RoleRecommendation dev = view.roles().get(0);
        assertThat(dev.ready()).extracting(RecCard::issueKey).containsExactly("PROJ-1", "PROJ-2");
        assertThat(dev.ready().get(0).cumulativeHours()).isEqualTo(5.0);
        assertThat(dev.ready().get(0).fitsInIdle()).isTrue();
        assertThat(dev.ready().get(1).cumulativeHours()).isEqualTo(10.0);
        assertThat(dev.ready().get(1).fitsInIdle()).isFalse();
    }

    @Test
    void untriagedStories_areExcluded_noNpeOnNullQuadrant() {
        when(workflowConfigService.isBug(anyString())).thenReturn(false);
        JiraIssueEntity triaged = story("PROJ-1", "P1", "Story");
        JiraIssueEntity untriaged = story("PROJ-2", null, "Story"); // null quadrant -> must not crash
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY))
                .thenReturn(List.of(triaged, untriaged));
        when(workflowConfigService.isDone(anyString(), anyString(), anyString())).thenReturn(false);
        when(issueRepository.findByParentKeyIn(List.of("PROJ-1")))
                .thenReturn(List.of(subtask("PROJ-1-1", "PROJ-1", "DEV", 3600L)));
        when(roleLoadService.calculateRoleLoad(TEAM_ID)).thenReturn(load(Map.of("DEV", idle(8, 0))));

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        RoleRecommendation dev = view.roles().get(0);
        assertThat(dev.ready()).extracting(RecCard::issueKey).containsExactly("PROJ-1");
        assertThat(dev.needsEstimation()).isEmpty();
    }

    @Test
    void noIdleRoles_returnsEmptyRoles_butStillComputesBugs() {
        // Orphan list is empty, so isBug is never consulted — keep the stub lenient.
        lenient().when(workflowConfigService.isBug(anyString())).thenReturn(false);
        when(issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(TEAM_ID, STORY))
                .thenReturn(List.of());
        when(roleLoadService.calculateRoleLoad(TEAM_ID))
                .thenReturn(load(Map.of("DEV", normal())));

        RecommendationViewDto view = build().getRecommendations(TEAM_ID);

        assertThat(view.roles()).isEmpty();
        assertThat(view.zeroBugPolicy().openBugCount()).isZero();
    }
}

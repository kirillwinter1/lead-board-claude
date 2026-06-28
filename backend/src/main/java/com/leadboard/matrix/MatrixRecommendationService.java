package com.leadboard.matrix;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.matrix.RecommendationDtos.RecommendationViewDto;
import com.leadboard.matrix.RecommendationDtos.RoleRecommendation;
import com.leadboard.matrix.RecommendationDtos.ZeroBugPolicy;
import com.leadboard.planning.RoleLoadService;
import com.leadboard.planning.dto.RoleLoadResponse;
import com.leadboard.planning.dto.RoleLoadResponse.RoleLoadInfo;
import com.leadboard.planning.dto.RoleLoadResponse.UtilizationStatus;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * F78 Phase A — autoplanner recommendations from the Eisenhower matrix.
 *
 * <p>Read-only. Produces a Zero Bug Policy section (all open orphan bugs, always
 * shown) and, for each underloaded role, the triaged tech-debt stories that can be
 * picked up (matched at the role-subtask level) versus those that still need a
 * role subtask / estimate.</p>
 */
@Service
public class MatrixRecommendationService {

    /** Lower quadrant rank = higher priority. */
    private static final Map<String, Integer> QUADRANT_RANK =
            Map.of("P1", 1, "P2", 2, "P3", 3, "P4", 4);

    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;
    private final RoleLoadService roleLoadService;
    private final MatrixService matrixService;

    public MatrixRecommendationService(JiraIssueRepository issueRepository,
                                       WorkflowConfigService workflowConfigService,
                                       RoleLoadService roleLoadService,
                                       MatrixService matrixService) {
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
        this.roleLoadService = roleLoadService;
        this.matrixService = matrixService;
    }

    @Transactional(readOnly = true)
    public RecommendationViewDto getRecommendations(Long teamId) {
        List<JiraIssueEntity> active = matrixService.loadOrphans(teamId).stream()
                .filter(i -> !matrixService.isDone(i))
                .toList();

        ZeroBugPolicy zeroBugPolicy = buildZeroBugPolicy(active);

        List<JiraIssueEntity> triagedStories = active.stream()
                .filter(i -> !matrixService.isBug(i))
                .filter(i -> QUADRANT_RANK.containsKey(i.getEisenhowerQuadrant()))
                .sorted(storyOrder())
                .toList();

        Map<String, List<JiraIssueEntity>> subtasksByParent = loadSubtasksByParent(triagedStories);

        RoleLoadResponse load = roleLoadService.calculateRoleLoad(teamId);
        List<RoleRecommendation> roles = new ArrayList<>();
        for (Map.Entry<String, RoleLoadInfo> entry : load.roles().entrySet()) {
            RoleLoadInfo info = entry.getValue();
            if (info.status() != UtilizationStatus.IDLE) {
                continue;
            }
            roles.add(buildRoleRecommendation(entry.getKey(), info, triagedStories, subtasksByParent));
        }

        return new RecommendationViewDto(zeroBugPolicy, roles);
    }

    private ZeroBugPolicy buildZeroBugPolicy(List<JiraIssueEntity> active) {
        List<RecCard> bugs = active.stream()
                .filter(matrixService::isBug)
                .sorted(Comparator.comparing(JiraIssueEntity::getIssueKey))
                .map(this::bugCard)
                .toList();
        return new ZeroBugPolicy(bugs.size(), bugs);
    }

    private Map<String, List<JiraIssueEntity>> loadSubtasksByParent(List<JiraIssueEntity> stories) {
        List<String> keys = stories.stream().map(JiraIssueEntity::getIssueKey).toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        return issueRepository.findByParentKeyIn(keys).stream()
                .filter(s -> s.getParentKey() != null)
                .collect(Collectors.groupingBy(JiraIssueEntity::getParentKey));
    }

    private RoleRecommendation buildRoleRecommendation(String roleCode, RoleLoadInfo info,
                                                       List<JiraIssueEntity> triagedStories,
                                                       Map<String, List<JiraIssueEntity>> subtasksByParent) {
        double idleHours = info.totalCapacityHours()
                .subtract(info.totalAssignedHours())
                .max(BigDecimal.ZERO)
                .doubleValue();

        List<RecCard> ready = new ArrayList<>();
        List<RecCard> needsEstimation = new ArrayList<>();
        double cumulative = 0.0;

        for (JiraIssueEntity story : triagedStories) {
            JiraIssueEntity roleSubtask = findEstimatedRoleSubtask(
                    subtasksByParent.getOrDefault(story.getIssueKey(), List.of()), roleCode);
            if (roleSubtask == null) {
                needsEstimation.add(storyCard(story, roleCode, null, null, null, null));
            } else {
                double hours = roleSubtask.getOriginalEstimateSeconds() / 3600.0;
                cumulative += hours;
                ready.add(storyCard(story, roleCode, roleSubtask.getIssueKey(), hours,
                        cumulative, cumulative <= idleHours));
            }
        }
        return new RoleRecommendation(roleCode, idleHours, ready, needsEstimation);
    }

    /** Returns the estimated subtask of the given role, or null if none/unestimated. */
    private JiraIssueEntity findEstimatedRoleSubtask(List<JiraIssueEntity> subtasks, String roleCode) {
        return subtasks.stream()
                .filter(s -> roleCode.equals(s.getWorkflowRole()))
                .filter(s -> s.getOriginalEstimateSeconds() != null && s.getOriginalEstimateSeconds() > 0)
                .findFirst()
                .orElse(null);
    }

    private Comparator<JiraIssueEntity> storyOrder() {
        return Comparator
                .comparingInt((JiraIssueEntity i) -> QUADRANT_RANK.getOrDefault(i.getEisenhowerQuadrant(), 99))
                .thenComparing(JiraIssueEntity::getIssueKey);
    }

    private RecCard bugCard(JiraIssueEntity issue) {
        return new RecCard(
                issue.getIssueKey(), issue.getSummary(), issue.getIssueType(), issue.getPriority(),
                estimateHours(issue), issue.getAssigneeDisplayName(), issue.getStatus(),
                null, issue.getWorkflowRole(), null, null, null, null);
    }

    private RecCard storyCard(JiraIssueEntity issue, String roleCode, String roleSubtaskKey,
                              Double roleEstimateHours, Double cumulativeHours, Boolean fitsInIdle) {
        return new RecCard(
                issue.getIssueKey(), issue.getSummary(), issue.getIssueType(), issue.getPriority(),
                estimateHours(issue), issue.getAssigneeDisplayName(), issue.getStatus(),
                issue.getEisenhowerQuadrant(), roleCode, roleSubtaskKey, roleEstimateHours,
                cumulativeHours, fitsInIdle);
    }

    private Double estimateHours(JiraIssueEntity issue) {
        return issue.getOriginalEstimateSeconds() == null ? null : issue.getOriginalEstimateSeconds() / 3600.0;
    }
}

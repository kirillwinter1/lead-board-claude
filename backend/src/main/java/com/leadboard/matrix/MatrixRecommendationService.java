package com.leadboard.matrix;

import com.leadboard.matrix.RecommendationDtos.RecommendationViewDto;
import com.leadboard.matrix.RecommendationDtos.RoleSlice;
import com.leadboard.matrix.RecommendationDtos.StoryRec;
import com.leadboard.matrix.RecommendationDtos.ZeroBugPolicy;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * F78 Phase A — autoplanner recommendations from the Eisenhower matrix.
 *
 * <p>Read-only. Produces a Zero Bug Policy section (all open orphan bugs) and a
 * single prioritised list of triaged tech-debt stories (P1→P4). A story is executed
 * by the whole SA→DEV→QA pipeline, so each story is shown once with its role
 * composition (subtask per role + hours), NOT split per role. Stories that are not
 * cut into estimated role subtasks land in a "needs estimation" warning list.</p>
 */
@Service
public class MatrixRecommendationService {

    /** Lower quadrant rank = higher priority. */
    private static final Map<String, Integer> QUADRANT_RANK =
            Map.of("P1", 1, "P2", 2, "P3", 3, "P4", 4);

    private final JiraIssueRepository issueRepository;
    private final MatrixService matrixService;

    public MatrixRecommendationService(JiraIssueRepository issueRepository,
                                       MatrixService matrixService) {
        this.issueRepository = issueRepository;
        this.matrixService = matrixService;
    }

    @Transactional(readOnly = true)
    public RecommendationViewDto getRecommendations(Long teamId) {
        ZeroBugPolicy zeroBugPolicy = buildZeroBugPolicy(teamId);

        List<JiraIssueEntity> triagedStories = matrixService.loadOrphans(teamId).stream()
                .filter(i -> !matrixService.isDone(i))
                .filter(i -> !matrixService.isBug(i))
                .filter(i -> i.getEisenhowerQuadrant() != null
                          && QUADRANT_RANK.containsKey(i.getEisenhowerQuadrant()))
                .sorted(storyOrder())
                .toList();

        Map<String, List<JiraIssueEntity>> subtasksByParent = loadSubtasksByParent(triagedStories);

        List<StoryRec> recommended = new ArrayList<>();
        List<RecCard> needsEstimation = new ArrayList<>();
        for (JiraIssueEntity story : triagedStories) {
            List<JiraIssueEntity> subtasks = subtasksByParent.getOrDefault(story.getIssueKey(), List.of());
            List<JiraIssueEntity> roleSubtasks = subtasks.stream()
                    .filter(s -> s.getWorkflowRole() != null && !s.getWorkflowRole().isBlank())
                    .toList();
            boolean cutIntoRoles = !roleSubtasks.isEmpty();
            boolean allEstimated = roleSubtasks.stream()
                    .allMatch(s -> s.getOriginalEstimateSeconds() != null && s.getOriginalEstimateSeconds() > 0);
            if (!cutIntoRoles || !allEstimated) {
                needsEstimation.add(card(story));
            } else {
                recommended.add(toStoryRec(story, roleSubtasks));
            }
        }

        return new RecommendationViewDto(zeroBugPolicy, recommended, needsEstimation);
    }

    /**
     * Zero Bug Policy: all open (non-done) orphan bugs of the team. Bugs are their
     * own board category (not STORY), so they are loaded separately — never via the
     * triaged-stories set.
     */
    private ZeroBugPolicy buildZeroBugPolicy(Long teamId) {
        List<RecCard> bugs = matrixService.loadOrphanBugs(teamId).stream()
                .filter(b -> !matrixService.isDone(b))
                .sorted(Comparator.comparing(JiraIssueEntity::getIssueKey))
                .map(this::card)
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

    private StoryRec toStoryRec(JiraIssueEntity story, List<JiraIssueEntity> roleSubtasks) {
        List<RoleSlice> roles = roleSubtasks.stream()
                .sorted(Comparator.comparing(JiraIssueEntity::getWorkflowRole))
                .map(s -> new RoleSlice(s.getWorkflowRole(), s.getIssueKey(),
                        s.getOriginalEstimateSeconds() / 3600.0))
                .toList();
        double total = roles.stream().mapToDouble(RoleSlice::hours).sum();
        return new StoryRec(
                story.getIssueKey(), story.getSummary(), story.getIssueType(), story.getPriority(),
                story.getStatus(), story.getEisenhowerQuadrant(), roles, total);
    }

    private Comparator<JiraIssueEntity> storyOrder() {
        return Comparator
                .comparingInt((JiraIssueEntity i) -> QUADRANT_RANK.getOrDefault(i.getEisenhowerQuadrant(), 99))
                .thenComparing(JiraIssueEntity::getIssueKey);
    }

    private RecCard card(JiraIssueEntity issue) {
        Double estimateHours = issue.getOriginalEstimateSeconds() == null
                ? null : issue.getOriginalEstimateSeconds() / 3600.0;
        return new RecCard(
                issue.getIssueKey(), issue.getSummary(), issue.getIssueType(), issue.getPriority(),
                estimateHours, issue.getAssigneeDisplayName(), issue.getStatus(),
                issue.getEisenhowerQuadrant(), issue.getWorkflowRole());
    }
}

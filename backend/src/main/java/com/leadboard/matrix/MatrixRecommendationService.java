package com.leadboard.matrix;

import com.leadboard.matrix.RecommendationDtos.RecommendationViewDto;
import com.leadboard.matrix.RecommendationDtos.RoleSlice;
import com.leadboard.matrix.RecommendationDtos.StoryRec;
import com.leadboard.matrix.RecommendationDtos.ZeroBugPolicy;
import com.leadboard.status.StatusAge;
import com.leadboard.status.StatusAgeService;
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
    private final StatusAgeService statusAgeService;

    public MatrixRecommendationService(JiraIssueRepository issueRepository,
                                       MatrixService matrixService,
                                       StatusAgeService statusAgeService) {
        this.issueRepository = issueRepository;
        this.matrixService = matrixService;
        this.statusAgeService = statusAgeService;
    }

    @Transactional(readOnly = true)
    public RecommendationViewDto getRecommendations(Long teamId) {
        // Zero Bug Policy: all open (non-done) orphan bugs of the team. Bugs are their
        // own board category (not STORY), so they are loaded separately — never via the
        // triaged-stories set.
        List<JiraIssueEntity> openBugs = matrixService.loadOrphanBugs(teamId).stream()
                .filter(b -> !matrixService.isDone(b))
                .sorted(Comparator.comparing(JiraIssueEntity::getIssueKey))
                .toList();

        List<JiraIssueEntity> triagedStories = matrixService.loadOrphans(teamId).stream()
                .filter(i -> !matrixService.isDone(i))
                .filter(i -> !matrixService.isBug(i))
                .filter(i -> i.getEisenhowerQuadrant() != null
                          && QUADRANT_RANK.containsKey(i.getEisenhowerQuadrant()))
                .sorted(storyOrder())
                .toList();

        // F79: compute "days in status" once for every issue we card (stories + bugs).
        List<JiraIssueEntity> carded = new ArrayList<>(triagedStories);
        carded.addAll(openBugs);
        Map<String, StatusAge> statusAges = statusAgeService.compute(carded);
        if (statusAges == null) {
            statusAges = Map.of();
        }

        List<RecCard> bugCards = openBugs.stream().map(b -> card(b, statusAges)).toList();
        ZeroBugPolicy zeroBugPolicy = new ZeroBugPolicy(bugCards.size(), bugCards);

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
                needsEstimation.add(card(story, statusAges));
            } else {
                recommended.add(toStoryRec(story, roleSubtasks, statusAges));
            }
        }

        return new RecommendationViewDto(zeroBugPolicy, recommended, needsEstimation);
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

    private StoryRec toStoryRec(JiraIssueEntity story, List<JiraIssueEntity> roleSubtasks,
                                Map<String, StatusAge> statusAges) {
        List<RoleSlice> roles = roleSubtasks.stream()
                .sorted(Comparator.comparing(JiraIssueEntity::getWorkflowRole))
                .map(s -> new RoleSlice(s.getWorkflowRole(), s.getIssueKey(),
                        s.getOriginalEstimateSeconds() / 3600.0))
                .toList();
        double total = roles.stream().mapToDouble(RoleSlice::hours).sum();
        StatusAge age = statusAges.getOrDefault(story.getIssueKey(), StatusAge.normal(null));
        return new StoryRec(
                story.getIssueKey(), story.getSummary(), story.getIssueType(), story.getPriority(),
                story.getStatus(), story.getEisenhowerQuadrant(), roles, total,
                age.daysInStatus(), age.level(), age.reason());
    }

    private Comparator<JiraIssueEntity> storyOrder() {
        return Comparator
                .comparingInt((JiraIssueEntity i) -> QUADRANT_RANK.getOrDefault(i.getEisenhowerQuadrant(), 99))
                .thenComparing(JiraIssueEntity::getIssueKey);
    }

    private RecCard card(JiraIssueEntity issue, Map<String, StatusAge> statusAges) {
        Double estimateHours = issue.getOriginalEstimateSeconds() == null
                ? null : issue.getOriginalEstimateSeconds() / 3600.0;
        StatusAge age = statusAges.getOrDefault(issue.getIssueKey(), StatusAge.normal(null));
        return new RecCard(
                issue.getIssueKey(), issue.getSummary(), issue.getIssueType(), issue.getPriority(),
                estimateHours, issue.getAssigneeDisplayName(), issue.getStatus(),
                issue.getEisenhowerQuadrant(), issue.getWorkflowRole(),
                age.daysInStatus(), age.level(), age.reason());
    }
}

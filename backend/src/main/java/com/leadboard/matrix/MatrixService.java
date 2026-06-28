package com.leadboard.matrix;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * F77 Eisenhower Matrix MVP — backlog triage of orphan tasks.
 *
 * <p>An "orphan" task is a top-level Story/Task ({@code board_category = STORY},
 * {@code parent_key IS NULL}) that is not yet "done". Subtasks and epics never appear
 * in the matrix; bugs are excluded too (F78 — they are handled by recommendations,
 * not triaged here). Tasks are grouped by their manually-assigned Eisenhower quadrant
 * (P1/P2/P3/P4) or land in {@code unassigned} when no quadrant is set.</p>
 *
 * <p>Multi-tenancy: queries run against the tenant schema selected by
 * {@code TenantContext} (search_path) — no schema handling is needed here.</p>
 */
@Service
public class MatrixService {

    private static final String STORY_CATEGORY = "STORY";
    static final Set<String> VALID_QUADRANTS = Set.of("P1", "P2", "P3", "P4");

    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;

    public MatrixService(JiraIssueRepository issueRepository,
                         WorkflowConfigService workflowConfigService) {
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
    }

    /**
     * Builds the Eisenhower matrix for a team: orphan, non-done tasks grouped by quadrant.
     */
    @Transactional(readOnly = true)
    public MatrixViewDto getMatrix(Long teamId) {
        List<JiraIssueEntity> orphans = loadOrphans(teamId);

        List<MatrixCardDto> p1 = new ArrayList<>();
        List<MatrixCardDto> p2 = new ArrayList<>();
        List<MatrixCardDto> p3 = new ArrayList<>();
        List<MatrixCardDto> p4 = new ArrayList<>();
        List<MatrixCardDto> unassigned = new ArrayList<>();

        for (JiraIssueEntity issue : orphans) {
            // "Done" and bug filtering are in-service (config-driven), never in SQL.
            // Bugs are not triaged in the matrix (F78) — they live in recommendations only.
            if (isDone(issue) || isBug(issue)) {
                continue;
            }
            MatrixCardDto card = toCard(issue);
            switch (card.quadrant() == null ? "" : card.quadrant()) {
                case "P1" -> p1.add(card);
                case "P2" -> p2.add(card);
                case "P3" -> p3.add(card);
                case "P4" -> p4.add(card);
                default -> unassigned.add(card);
            }
        }

        return new MatrixViewDto(p1, p2, p3, p4, unassigned);
    }

    /**
     * Sets or clears the Eisenhower quadrant of an orphan task.
     *
     * @param issueKey the task key (must exist and be a valid orphan = no parent, STORY)
     * @param quadrant one of P1/P2/P3/P4, or {@code null} to clear (back to unassigned)
     * @throws IllegalArgumentException        when {@code quadrant} is not a valid value
     * @throws MatrixIssueNotFoundException    when the issue is missing or not a valid orphan
     */
    @Transactional
    public MatrixCardDto triage(String issueKey, String quadrant) {
        String normalized = normalizeQuadrant(quadrant);

        JiraIssueEntity issue = issueRepository.findByIssueKey(issueKey)
                .orElseThrow(() -> new MatrixIssueNotFoundException("Issue not found: " + issueKey));

        if (issue.getParentKey() != null || !STORY_CATEGORY.equals(issue.getBoardCategory())) {
            throw new MatrixIssueNotFoundException(
                    "Issue is not a triageable orphan task: " + issueKey);
        }

        issue.setEisenhowerQuadrant(normalized);
        issueRepository.save(issue);
        return toCard(issue);
    }

    /**
     * Validates the quadrant input. Blank strings are treated as "clear" (null).
     */
    private String normalizeQuadrant(String quadrant) {
        if (quadrant == null || quadrant.isBlank()) {
            return null;
        }
        String upper = quadrant.trim().toUpperCase();
        if (!VALID_QUADRANTS.contains(upper)) {
            throw new IllegalArgumentException(
                    "Invalid quadrant '" + quadrant + "'. Allowed: P1, P2, P3, P4 or null.");
        }
        return upper;
    }

    private MatrixCardDto toCard(JiraIssueEntity issue) {
        Double estimateHours = issue.getOriginalEstimateSeconds() == null
                ? null
                : issue.getOriginalEstimateSeconds() / 3600.0;
        return new MatrixCardDto(
                issue.getIssueKey(),
                issue.getSummary(),
                issue.getIssueType(),
                issue.getPriority(),
                estimateHours,
                issue.getAssigneeDisplayName(),
                issue.getStatus(),
                issue.getEisenhowerQuadrant()
        );
    }

    /** Loads top-level orphan tasks (board_category=STORY, no parent) for the team. */
    List<JiraIssueEntity> loadOrphans(Long teamId) {
        return issueRepository.findByTeamIdAndParentKeyIsNullAndBoardCategory(teamId, STORY_CATEGORY);
    }

    /** Config-driven "done" check for an issue. */
    boolean isDone(JiraIssueEntity issue) {
        return workflowConfigService.isDone(issue.getStatus(), issue.getIssueType(), issue.getProjectKey());
    }

    /** Config-driven "bug" check for an issue (never hardcode the type name). */
    boolean isBug(JiraIssueEntity issue) {
        return workflowConfigService.isBug(issue.getIssueType());
    }
}

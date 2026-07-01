package com.leadboard.status;

import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.entity.IssueWorklogEntity;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F79 — "days in current status" + "stuck epic" signal.
 *
 * <p>For each issue computes how many whole days it has spent in its current status
 * (from {@code status_changelog}, falling back to {@code jira_created_at}) and a
 * coloring {@code level}. Coloring applies only to <em>active</em> statuses (category
 * is neither NEW/backlog nor DONE); thresholds differ per issue type. Active epics
 * are additionally flagged "stuck" when their whole subtree (stories + subtasks) has
 * had no worklog and no status change for too long.</p>
 */
@Service
public class StatusAgeService {

    /** Per board-category [warningDays, criticalDays] for time-in-status. */
    private static final Map<BoardCategory, int[]> THRESHOLDS = Map.of(
            BoardCategory.EPIC, new int[]{21, 45},
            BoardCategory.STORY, new int[]{7, 14},
            BoardCategory.BUG, new int[]{3, 7},
            BoardCategory.SUBTASK, new int[]{3, 7},
            BoardCategory.PROJECT, new int[]{21, 45}
    );
    private static final int[] DEFAULT_THRESHOLD = {7, 14};
    /** Inactivity [warningDays, criticalDays] for the stuck-epic signal. */
    private static final int[] STUCK_EPIC = {14, 30};

    private final StatusChangelogRepository changelogRepository;
    private final IssueWorklogRepository worklogRepository;
    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;

    public StatusAgeService(StatusChangelogRepository changelogRepository,
                            IssueWorklogRepository worklogRepository,
                            JiraIssueRepository issueRepository,
                            WorkflowConfigService workflowConfigService) {
        this.changelogRepository = changelogRepository;
        this.worklogRepository = worklogRepository;
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
    }

    public Map<String, StatusAge> compute(List<JiraIssueEntity> issues) {
        return compute(issues, OffsetDateTime.now());
    }

    /** Package-private overload with an explicit clock for deterministic tests. */
    Map<String, StatusAge> compute(List<JiraIssueEntity> issues, OffsetDateTime now) {
        if (issues == null || issues.isEmpty()) {
            return Map.of();
        }
        List<String> keys = issues.stream().map(JiraIssueEntity::getIssueKey).toList();
        Map<String, OffsetDateTime> latestTransitionToCurrent = latestTransitionPerIssue(keys);
        Map<String, OffsetDateTime> epicLastSubtreeActivity = epicInactivity(issues, now);

        Map<String, StatusAge> result = new HashMap<>();
        for (JiraIssueEntity issue : issues) {
            result.put(issue.getIssueKey(), forIssue(issue, latestTransitionToCurrent, epicLastSubtreeActivity, now));
        }
        return result;
    }

    private StatusAge forIssue(JiraIssueEntity issue,
                               Map<String, OffsetDateTime> latestTransition,
                               Map<String, OffsetDateTime> epicLastActivity,
                               OffsetDateTime now) {
        OffsetDateTime enteredAt = latestTransition.get(issue.getIssueKey());
        if (enteredAt == null) {
            enteredAt = issue.getJiraCreatedAt();
        }
        Integer daysInStatus = enteredAt == null ? null
                : (int) Math.max(0, ChronoUnit.DAYS.between(enteredAt, now));

        StatusCategory category = workflowConfigService
                .categorize(issue.getStatus(), issue.getIssueType(), issue.getProjectKey()).normalized();
        boolean active = category != StatusCategory.NEW && category != StatusCategory.DONE;
        if (!active) {
            return StatusAge.normal(daysInStatus);
        }

        BoardCategory board = workflowConfigService.categorizeIssueType(issue.getIssueType(), issue.getProjectKey());

        // Time-in-status signal.
        int[] t = THRESHOLDS.getOrDefault(board, DEFAULT_THRESHOLD);
        String ageLevel = StatusAge.NORMAL;
        String ageReason = null;
        if (daysInStatus != null && daysInStatus >= t[1]) {
            ageLevel = StatusAge.CRITICAL;
            ageReason = daysInStatus + "д в статусе «" + issue.getStatus() + "»";
        } else if (daysInStatus != null && daysInStatus >= t[0]) {
            ageLevel = StatusAge.WARNING;
            ageReason = daysInStatus + "д в статусе «" + issue.getStatus() + "»";
        }

        // Stuck-epic signal (active epics only).
        String stuckLevel = StatusAge.NORMAL;
        String stuckReason = null;
        if (board == BoardCategory.EPIC) {
            OffsetDateTime lastActivity = epicLastActivity.get(issue.getIssueKey());
            if (lastActivity == null) {
                lastActivity = enteredAt; // count inactivity from when the epic became active
            }
            if (lastActivity != null) {
                int inactivity = (int) Math.max(0, ChronoUnit.DAYS.between(lastActivity, now));
                if (inactivity >= STUCK_EPIC[1]) {
                    stuckLevel = StatusAge.CRITICAL;
                    stuckReason = "эпик завис: " + inactivity + "д без активности по поддереву";
                } else if (inactivity >= STUCK_EPIC[0]) {
                    stuckLevel = StatusAge.WARNING;
                    stuckReason = "эпик завис: " + inactivity + "д без активности по поддереву";
                }
            }
        }

        // Worst of the two; the stuck reason wins on ties (more informative for epics).
        if (rank(stuckLevel) >= rank(ageLevel) && rank(stuckLevel) > 0) {
            return new StatusAge(daysInStatus, stuckLevel, stuckReason);
        }
        return new StatusAge(daysInStatus, ageLevel, ageReason);
    }

    private int rank(String level) {
        return switch (level) {
            case StatusAge.CRITICAL -> 2;
            case StatusAge.WARNING -> 1;
            default -> 0;
        };
    }

    /**
     * When each issue last changed status = the entry into its current status.
     * We deliberately do NOT match {@code to_status} against {@code jira_issues.status}:
     * the two can disagree by localization (e.g. current "Запланировано" vs the changelog's
     * "Planned"), which would otherwise drop back to {@code jira_created_at} and report the
     * total age instead of the current-status age. This mirrors how the F81 status-journey
     * tooltip anchors the current segment, so badge and tooltip stay consistent.
     */
    private Map<String, OffsetDateTime> latestTransitionPerIssue(List<String> keys) {
        Map<String, OffsetDateTime> latest = new HashMap<>();
        for (StatusChangelogEntity c : changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(keys)) {
            OffsetDateTime prev = latest.get(c.getIssueKey());
            if (prev == null || c.getTransitionedAt().isAfter(prev)) {
                latest.put(c.getIssueKey(), c.getTransitionedAt());
            }
        }
        return latest;
    }

    /** For each epic, the most recent worklog/status-change timestamp across its subtree. */
    private Map<String, OffsetDateTime> epicInactivity(List<JiraIssueEntity> issues, OffsetDateTime now) {
        List<String> epicKeys = issues.stream()
                .filter(i -> BoardCategory.EPIC == workflowConfigService.categorizeIssueType(i.getIssueType(), i.getProjectKey()))
                .map(JiraIssueEntity::getIssueKey)
                .toList();
        if (epicKeys.isEmpty()) {
            return Map.of();
        }
        // story key -> epic key
        Map<String, String> storyToEpic = new HashMap<>();
        for (JiraIssueEntity story : issueRepository.findByParentKeyIn(epicKeys)) {
            if (story.getParentKey() != null) {
                storyToEpic.put(story.getIssueKey(), story.getParentKey());
            }
        }
        // subtask key -> epic key (via its story)
        Map<String, String> descendantToEpic = new HashMap<>(storyToEpic);
        if (!storyToEpic.isEmpty()) {
            for (JiraIssueEntity sub : issueRepository.findByParentKeyIn(new ArrayList<>(storyToEpic.keySet()))) {
                String epic = storyToEpic.get(sub.getParentKey());
                if (epic != null) {
                    descendantToEpic.put(sub.getIssueKey(), epic);
                }
            }
        }
        if (descendantToEpic.isEmpty()) {
            return Map.of();
        }
        List<String> descendantKeys = descendantToEpic.keySet().stream().sorted().toList();

        Map<String, OffsetDateTime> epicLast = new HashMap<>();
        // worklog activity
        for (IssueWorklogEntity w : worklogRepository.findByIssueKeyIn(descendantKeys)) {
            if (w.getStartedDate() == null) continue;
            String epic = descendantToEpic.get(w.getIssueKey());
            OffsetDateTime at = w.getStartedDate().atStartOfDay().atOffset(now.getOffset());
            epicLast.merge(epic, at, StatusAgeService::maxTime);
        }
        // status-change activity
        for (StatusChangelogEntity c : changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(descendantKeys)) {
            String epic = descendantToEpic.get(c.getIssueKey());
            if (epic == null) continue;
            epicLast.merge(epic, c.getTransitionedAt(), StatusAgeService::maxTime);
        }
        return epicLast;
    }

    private static OffsetDateTime maxTime(OffsetDateTime a, OffsetDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}

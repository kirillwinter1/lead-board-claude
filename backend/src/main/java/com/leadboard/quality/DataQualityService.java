package com.leadboard.quality;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.rice.RiceAssessmentRepository;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for checking data quality rules on Jira issues.
 */
@Service
public class DataQualityService {

    private static final Logger log = LoggerFactory.getLogger(DataQualityService.class);
    private static final double OVERRUN_THRESHOLD = 1.5;

    private final JiraIssueRepository issueRepository;
    private final TeamMemberRepository memberRepository;
    private final WorkflowConfigService workflowConfigService;
    private final RiceAssessmentRepository riceAssessmentRepository;

    public DataQualityService(
            JiraIssueRepository issueRepository,
            TeamMemberRepository memberRepository,
            WorkflowConfigService workflowConfigService,
            RiceAssessmentRepository riceAssessmentRepository
    ) {
        this.issueRepository = issueRepository;
        this.memberRepository = memberRepository;
        this.workflowConfigService = workflowConfigService;
        this.riceAssessmentRepository = riceAssessmentRepository;
    }

    /**
     * Checks all data quality rules for an Epic.
     *
     * @param epic The epic to check
     * @param children All direct children (Stories/Bugs) of the epic
     * @return List of violations found
     */
    public List<DataQualityViolation> checkEpic(
            JiraIssueEntity epic,
            List<JiraIssueEntity> children
    ) {
        List<DataQualityViolation> violations = new ArrayList<>();

        // EPIC_NO_TEAM - Epic without team
        if (epic.getTeamId() == null) {
            violations.add(DataQualityViolation.of(DataQualityRule.EPIC_NO_TEAM));
        }

        // EPIC_TEAM_NO_MEMBERS - Epic's team has no active members
        if (epic.getTeamId() != null) {
            List<TeamMemberEntity> members = memberRepository.findByTeamIdAndActiveTrue(epic.getTeamId());
            if (members.isEmpty()) {
                violations.add(DataQualityViolation.of(DataQualityRule.EPIC_TEAM_NO_MEMBERS));
            }
        }

        // EPIC_NO_DUE_DATE - Epic without due date (only for epics in Planned or later)
        StatusCategory epicCategory = workflowConfigService.categorizeEpic(epic.getStatus());
        boolean epicPastTodo = epicCategory == StatusCategory.PLANNED
                || epicCategory == StatusCategory.IN_PROGRESS
                || epicCategory == StatusCategory.DONE;
        if (epicPastTodo && epic.getDueDate() == null) {
            violations.add(DataQualityViolation.of(DataQualityRule.EPIC_NO_DUE_DATE));
        }

        // EPIC_OVERDUE - Epic with due date in the past and not Done
        if (epic.getDueDate() != null && epic.getDueDate().isBefore(LocalDate.now())) {
            if (!workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) {
                violations.add(DataQualityViolation.of(
                        DataQualityRule.EPIC_OVERDUE,
                        epic.getDueDate().toString()
                ));
            }
        }

        // EPIC_NO_ESTIMATE - Epic without rough estimate and without detailed subtasks (only for epics in Planned or later)
        if (epicPastTodo) {
            boolean hasRoughEstimate = epic.getRoughEstimates() != null
                    && epic.getRoughEstimates().values().stream()
                            .anyMatch(v -> v != null && v.compareTo(java.math.BigDecimal.ZERO) > 0);

            if (!hasRoughEstimate) {
                // Check if children have estimates
                boolean hasChildEstimates = hasEstimatesInHierarchy(children);
                if (!hasChildEstimates) {
                    violations.add(DataQualityViolation.of(DataQualityRule.EPIC_NO_ESTIMATE));
                }
            }
        }

        // TIME_LOGGED_WRONG_EPIC_STATUS - Time logged on subtasks when epic not in Developing/E2E Testing
        if (!workflowConfigService.isTimeLoggingAllowed(epic.getStatus())) {
            List<String> childKeys = children.stream().map(JiraIssueEntity::getIssueKey).toList();
            List<JiraIssueEntity> subtasks = childKeys.isEmpty() ? List.of() : issueRepository.findByParentKeyIn(childKeys);
            boolean hasLoggedTime = subtasks.stream()
                    .anyMatch(st -> st.getTimeSpentSeconds() != null && st.getTimeSpentSeconds() > 0);
            if (hasLoggedTime) {
                violations.add(DataQualityViolation.of(DataQualityRule.TIME_LOGGED_WRONG_EPIC_STATUS));
            }
        }

        // TIME_LOGGED_NOT_IN_SUBTASK - Time logged directly on Epic
        if (epic.getTimeSpentSeconds() != null && epic.getTimeSpentSeconds() > 0) {
            violations.add(DataQualityViolation.of(
                    DataQualityRule.TIME_LOGGED_NOT_IN_SUBTASK,
                    epic.getIssueType()
            ));
        }

        // EPIC_DONE_OPEN_CHILDREN - Epic is Done but has open children
        if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) {
            long openChildren = children.stream()
                    .filter(c -> !workflowConfigService.isDone(c.getStatus(), c.getIssueType()))
                    .count();
            if (openChildren > 0) {
                violations.add(DataQualityViolation.of(
                        DataQualityRule.EPIC_DONE_OPEN_CHILDREN,
                        openChildren
                ));
            }
        }

        // EPIC_IN_PROGRESS_NO_STORIES - Epic in progress without stories
        if (workflowConfigService.isEpicInProgress(epic.getStatus())) {
            if (children.isEmpty()) {
                violations.add(DataQualityViolation.of(DataQualityRule.EPIC_IN_PROGRESS_NO_STORIES));
            }
        }

        // RICE_MISSING_ASSESSMENT - Epic in Planned+ without RICE assessment
        if (epicPastTodo && !workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) {
            boolean hasRice = riceAssessmentRepository.findByIssueKey(epic.getIssueKey()).isPresent();
            if (!hasRice) {
                // Also check if parent project has RICE (inheritance)
                boolean parentHasRice = false;
                if (epic.getParentKey() != null) {
                    parentHasRice = riceAssessmentRepository.findByIssueKey(epic.getParentKey()).isPresent();
                }
                if (!parentHasRice) {
                    violations.add(DataQualityViolation.of(DataQualityRule.RICE_MISSING_ASSESSMENT));
                }
            }
        }

        return violations;
    }

    /**
     * Checks all data quality rules for a Story/Bug.
     *
     * @param story The story or bug to check
     * @param epic The parent epic (may be null)
     * @param subtasks All subtasks of this story
     * @return List of violations found
     */
    public List<DataQualityViolation> checkStory(
            JiraIssueEntity story,
            JiraIssueEntity epic,
            List<JiraIssueEntity> subtasks
    ) {
        List<DataQualityViolation> violations = new ArrayList<>();

        // TIME_LOGGED_NOT_IN_SUBTASK - Time logged directly on Story/Bug
        if (story.getTimeSpentSeconds() != null && story.getTimeSpentSeconds() > 0) {
            String issueType = story.getIssueType();
            violations.add(DataQualityViolation.of(
                    DataQualityRule.TIME_LOGGED_NOT_IN_SUBTASK,
                    issueType
            ));
        }

        // CHILD_IN_PROGRESS_EPIC_NOT - Story in progress but Epic not in Developing/E2E Testing
        if (workflowConfigService.isInProgress(story.getStatus(), story.getIssueType())) {
            if (epic != null && !workflowConfigService.isEpicInProgress(epic.getStatus())) {
                violations.add(DataQualityViolation.of(
                        DataQualityRule.CHILD_IN_PROGRESS_EPIC_NOT,
                        Map.of("childKey", story.getIssueKey(), "epicKey", epic.getIssueKey()),
                        story.getIssueKey()
                ));
            }
        }

        // STORY_DONE_OPEN_CHILDREN - Story is Done but has open subtasks
        if (workflowConfigService.isDone(story.getStatus(), story.getIssueType())) {
            long openSubtasks = subtasks.stream()
                    .filter(s -> !workflowConfigService.isDone(s.getStatus(), s.getIssueType()))
                    .count();
            if (openSubtasks > 0) {
                violations.add(DataQualityViolation.of(
                        DataQualityRule.STORY_DONE_OPEN_CHILDREN,
                        openSubtasks
                ));
            }
        }

        // STORY_IN_PROGRESS_NO_SUBTASKS - Story in progress without subtasks
        if (workflowConfigService.isInProgress(story.getStatus(), story.getIssueType())) {
            if (subtasks.isEmpty()) {
                violations.add(DataQualityViolation.of(DataQualityRule.STORY_IN_PROGRESS_NO_SUBTASKS));
            }
        }

        // STORY_NO_SUBTASK_ESTIMATES - Story has no subtasks with estimates (not Done stories only)
        if (!workflowConfigService.isDone(story.getStatus(), story.getIssueType())) {
            boolean hasSubtaskEstimates = subtasks.stream()
                    .anyMatch(s -> s.getOriginalEstimateSeconds() != null && s.getOriginalEstimateSeconds() > 0);
            if (!hasSubtaskEstimates) {
                violations.add(DataQualityViolation.of(DataQualityRule.STORY_NO_SUBTASK_ESTIMATES));
            }
        }

        // STORY_BLOCKED_BY_MISSING - Story is blocked by non-existent issue
        List<String> isBlockedBy = story.getIsBlockedBy();
        if (isBlockedBy != null && !isBlockedBy.isEmpty()) {
            for (String blockerKey : isBlockedBy) {
                Optional<JiraIssueEntity> blocker = issueRepository.findByIssueKey(blockerKey);
                if (blocker.isEmpty()) {
                    violations.add(DataQualityViolation.of(
                            DataQualityRule.STORY_BLOCKED_BY_MISSING,
                            blockerKey
                    ));
                }
            }
        }

        // STORY_CIRCULAR_DEPENDENCY - Circular dependency detected
        Set<String> visited = new HashSet<>();
        if (hasCircularDependency(story.getIssueKey(), visited, new HashSet<>())) {
            violations.add(DataQualityViolation.of(
                    DataQualityRule.STORY_CIRCULAR_DEPENDENCY,
                    String.join(" → ", visited)
            ));
        }

        // STORY_BLOCKED_NO_PROGRESS - Story blocked >30 days without progress
        if (isBlockedBy != null && !isBlockedBy.isEmpty()) {
            OffsetDateTime storyCreated = story.getJiraCreatedAt();
            if (storyCreated != null) {
                long daysSinceCreated = ChronoUnit.DAYS.between(storyCreated, OffsetDateTime.now());
                if (daysSinceCreated > 30) {
                    // Check if blocking stories have made progress
                    for (String blockerKey : isBlockedBy) {
                        Optional<JiraIssueEntity> blocker = issueRepository.findByIssueKey(blockerKey);
                        if (blocker.isPresent()) {
                            JiraIssueEntity blockerIssue = blocker.get();
                            // If blocker is not done and has no time logged in subtasks, no progress
                            if (!workflowConfigService.isDone(blockerIssue.getStatus(), blockerIssue.getIssueType())) {
                                List<JiraIssueEntity> blockerSubtasks = issueRepository.findByParentKey(blockerKey);
                                long blockerTimeSpent = blockerSubtasks.stream()
                                        .mapToLong(st -> st.getTimeSpentSeconds() != null ? st.getTimeSpentSeconds() : 0)
                                        .sum();
                                if (blockerTimeSpent == 0) {
                                    violations.add(DataQualityViolation.of(
                                            DataQualityRule.STORY_BLOCKED_NO_PROGRESS,
                                            blockerKey
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }

        return violations;
    }

    /**
     * Checks all data quality rules for a Subtask.
     *
     * @param subtask The subtask to check
     * @param story The parent story (may be null)
     * @param epic The grandparent epic (may be null)
     * @return List of violations found
     */
    public List<DataQualityViolation> checkSubtask(
            JiraIssueEntity subtask,
            JiraIssueEntity story,
            JiraIssueEntity epic
    ) {
        List<DataQualityViolation> violations = new ArrayList<>();

        // SUBTASK_NO_ESTIMATE - Subtask without original estimate
        if (subtask.getOriginalEstimateSeconds() == null || subtask.getOriginalEstimateSeconds() == 0) {
            // Only warn if subtask is not done
            if (!workflowConfigService.isDone(subtask.getStatus(), subtask.getIssueType())) {
                violations.add(DataQualityViolation.of(DataQualityRule.SUBTASK_NO_ESTIMATE));
            }
        }

        // SUBTASK_WORK_NO_ESTIMATE - Subtask with logged time but no estimate
        if (subtask.getTimeSpentSeconds() != null && subtask.getTimeSpentSeconds() > 0) {
            if (subtask.getOriginalEstimateSeconds() == null || subtask.getOriginalEstimateSeconds() == 0) {
                violations.add(DataQualityViolation.of(DataQualityRule.SUBTASK_WORK_NO_ESTIMATE));
            }
        }

        // SUBTASK_OVERRUN - Subtask exceeded estimate by more than 50%
        if (subtask.getOriginalEstimateSeconds() != null && subtask.getOriginalEstimateSeconds() > 0
                && subtask.getTimeSpentSeconds() != null && subtask.getTimeSpentSeconds() > 0) {

            double threshold = subtask.getOriginalEstimateSeconds() * OVERRUN_THRESHOLD;
            if (subtask.getTimeSpentSeconds() > threshold) {
                double loggedHours = subtask.getTimeSpentSeconds() / 3600.0;
                double estimateHours = subtask.getOriginalEstimateSeconds() / 3600.0;
                violations.add(DataQualityViolation.of(
                        DataQualityRule.SUBTASK_OVERRUN,
                        loggedHours,
                        estimateHours
                ));
            }
        }

        // SUBTASK_IN_PROGRESS_STORY_NOT - Subtask in progress but Story not in progress
        if (workflowConfigService.isInProgress(subtask.getStatus(), subtask.getIssueType())) {
            if (story != null && !workflowConfigService.isInProgress(story.getStatus(), story.getIssueType())) {
                violations.add(DataQualityViolation.of(DataQualityRule.SUBTASK_IN_PROGRESS_STORY_NOT));
            }
        }

        // SUBTASK_ACTIVE_STORY_NOT_INPROGRESS - Subtask in active status but Story in TODO
        if (workflowConfigService.isInProgress(subtask.getStatus(), subtask.getIssueType())) {
            if (story != null) {
                boolean storyIsTodo = !workflowConfigService.isInProgress(story.getStatus(), story.getIssueType())
                        && !workflowConfigService.isDone(story.getStatus(), story.getIssueType());
                if (storyIsTodo) {
                    violations.add(DataQualityViolation.of(DataQualityRule.SUBTASK_ACTIVE_STORY_NOT_INPROGRESS));
                }
            }
        }

        // SUBTASK_DONE_NO_TIME_LOGGED - Subtask is Done but has no time logged
        if (workflowConfigService.isDone(subtask.getStatus(), subtask.getIssueType())) {
            boolean noTimeLogged = subtask.getTimeSpentSeconds() == null || subtask.getTimeSpentSeconds() == 0;
            if (noTimeLogged) {
                double estimateHours = subtask.getOriginalEstimateSeconds() != null
                        ? subtask.getOriginalEstimateSeconds() / 3600.0 : 0;
                violations.add(DataQualityViolation.of(
                        DataQualityRule.SUBTASK_DONE_NO_TIME_LOGGED,
                        estimateHours
                ));
            }
        }

        // SUBTASK_TIME_LOGGED_BUT_TODO - Subtask has time logged but still in TODO status
        if (subtask.getTimeSpentSeconds() != null && subtask.getTimeSpentSeconds() > 0) {
            boolean isTodo = !workflowConfigService.isInProgress(subtask.getStatus(), subtask.getIssueType())
                    && !workflowConfigService.isDone(subtask.getStatus(), subtask.getIssueType());
            if (isTodo) {
                double loggedHours = subtask.getTimeSpentSeconds() / 3600.0;
                violations.add(DataQualityViolation.of(
                        DataQualityRule.SUBTASK_TIME_LOGGED_BUT_TODO,
                        loggedHours
                ));
            }
        }

        // CHILD_IN_PROGRESS_EPIC_NOT - Subtask in progress but Epic not in Developing/E2E Testing
        if (workflowConfigService.isInProgress(subtask.getStatus(), subtask.getIssueType())) {
            if (epic != null && !workflowConfigService.isEpicInProgress(epic.getStatus())) {
                violations.add(DataQualityViolation.of(
                        DataQualityRule.CHILD_IN_PROGRESS_EPIC_NOT,
                        Map.of("childKey", subtask.getIssueKey(), "epicKey", epic.getIssueKey()),
                        subtask.getIssueKey()
                ));
            }
        }

        // SUBTASK_TIME_LOGGED_WHILE_EPIC_FLAGGED - Time logged on subtask while Epic is flagged
        if (epic != null && Boolean.TRUE.equals(epic.getFlagged())) {
            if (subtask.getTimeSpentSeconds() != null && subtask.getTimeSpentSeconds() > 0
                    && !workflowConfigService.isDone(subtask.getStatus(), subtask.getIssueType())) {
                violations.add(DataQualityViolation.of(DataQualityRule.SUBTASK_TIME_LOGGED_WHILE_EPIC_FLAGGED));
            }
        }

        return violations;
    }

    /**
     * Checks if an epic has any blocking errors (severity = ERROR).
     * Epics with blocking errors should be excluded from planning.
     */
    public boolean hasBlockingErrors(JiraIssueEntity epic) {
        // Quick checks for blocking errors without loading children

        // EPIC_NO_TEAM is blocking
        if (epic.getTeamId() == null) {
            return true;
        }

        // EPIC_OVERDUE is blocking
        if (epic.getDueDate() != null && epic.getDueDate().isBefore(LocalDate.now())) {
            if (!workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) {
                return true;
            }
        }

        // Load children for additional checks
        List<JiraIssueEntity> children = issueRepository.findByParentKey(epic.getIssueKey());

        // EPIC_DONE_OPEN_CHILDREN is blocking
        if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) {
            boolean hasOpenChildren = children.stream()
                    .anyMatch(c -> !workflowConfigService.isDone(c.getStatus(), c.getIssueType()));
            if (hasOpenChildren) {
                return true;
            }
        }

        // TIME_LOGGED_NOT_IN_SUBTASK on epic is blocking
        if (epic.getTimeSpentSeconds() != null && epic.getTimeSpentSeconds() > 0) {
            return true;
        }

        return false;
    }

    /**
     * Checks if any subtasks in the hierarchy have estimates.
     * Only subtask estimates count - story-level estimates are ignored.
     */
    private boolean hasEstimatesInHierarchy(List<JiraIssueEntity> children) {
        for (JiraIssueEntity child : children) {
            // Check subtasks of this child - only subtask estimates count
            List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(child.getIssueKey());
            for (JiraIssueEntity subtask : subtasks) {
                if (subtask.getOriginalEstimateSeconds() != null && subtask.getOriginalEstimateSeconds() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds EPIC_FORECAST_LATE violation if Expected Done > Due Date.
     * Called by external code that calculates forecast dates.
     */
    public DataQualityViolation checkForecastLate(LocalDate expectedDone, LocalDate dueDate) {
        if (expectedDone != null && dueDate != null && expectedDone.isAfter(dueDate)) {
            return DataQualityViolation.of(
                    DataQualityRule.EPIC_FORECAST_LATE,
                    expectedDone.toString(),
                    dueDate.toString()
            );
        }
        return null;
    }

    /**
     * Checks for circular dependencies using DFS.
     *
     * @param storyKey The story to check
     * @param visited Set of visited stories (for building path)
     * @param recStack Recursion stack for cycle detection
     * @return true if circular dependency detected
     */
    private boolean hasCircularDependency(String storyKey, Set<String> visited, Set<String> recStack) {
        visited.add(storyKey);
        recStack.add(storyKey);

        Optional<JiraIssueEntity> storyOpt = issueRepository.findByIssueKey(storyKey);
        if (storyOpt.isEmpty()) {
            recStack.remove(storyKey);
            return false;
        }

        JiraIssueEntity story = storyOpt.get();
        List<String> isBlockedBy = story.getIsBlockedBy();

        if (isBlockedBy != null) {
            for (String blocker : isBlockedBy) {
                if (!visited.contains(blocker)) {
                    if (hasCircularDependency(blocker, visited, recStack)) {
                        return true;
                    }
                } else if (recStack.contains(blocker)) {
                    // Cycle detected
                    return true;
                }
            }
        }

        recStack.remove(storyKey);
        return false;
    }
}

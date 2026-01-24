package com.leadboard.quality;

import com.leadboard.status.StatusMappingConfig;
import com.leadboard.status.StatusMappingService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
    private final StatusMappingService statusMappingService;

    public DataQualityService(
            JiraIssueRepository issueRepository,
            TeamMemberRepository memberRepository,
            StatusMappingService statusMappingService
    ) {
        this.issueRepository = issueRepository;
        this.memberRepository = memberRepository;
        this.statusMappingService = statusMappingService;
    }

    /**
     * Checks all data quality rules for an Epic.
     *
     * @param epic The epic to check
     * @param children All direct children (Stories/Bugs) of the epic
     * @param statusMapping The status mapping configuration
     * @return List of violations found
     */
    public List<DataQualityViolation> checkEpic(
            JiraIssueEntity epic,
            List<JiraIssueEntity> children,
            StatusMappingConfig statusMapping
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

        // EPIC_NO_DUE_DATE - Epic without due date
        if (epic.getDueDate() == null) {
            violations.add(DataQualityViolation.of(DataQualityRule.EPIC_NO_DUE_DATE));
        }

        // EPIC_OVERDUE - Epic with due date in the past and not Done
        if (epic.getDueDate() != null && epic.getDueDate().isBefore(LocalDate.now())) {
            if (!statusMappingService.isDone(epic.getStatus(), statusMapping)) {
                violations.add(DataQualityViolation.of(
                        DataQualityRule.EPIC_OVERDUE,
                        epic.getDueDate().toString()
                ));
            }
        }

        // EPIC_NO_ESTIMATE - Epic without rough estimate and without detailed subtasks
        boolean hasRoughEstimate = epic.getRoughEstimateSaDays() != null
                || epic.getRoughEstimateDevDays() != null
                || epic.getRoughEstimateQaDays() != null;

        if (!hasRoughEstimate) {
            // Check if children have estimates
            boolean hasChildEstimates = hasEstimatesInHierarchy(children);
            if (!hasChildEstimates) {
                violations.add(DataQualityViolation.of(DataQualityRule.EPIC_NO_ESTIMATE));
            }
        }

        // TIME_LOGGED_WRONG_EPIC_STATUS - Time logged on children when epic not in Developing/E2E Testing
        if (!statusMappingService.isTimeLoggingAllowed(epic.getStatus(), statusMapping)) {
            boolean hasLoggedTime = children.stream()
                    .anyMatch(c -> c.getTimeSpentSeconds() != null && c.getTimeSpentSeconds() > 0);
            if (hasLoggedTime) {
                violations.add(DataQualityViolation.of(DataQualityRule.TIME_LOGGED_WRONG_EPIC_STATUS));
            }
        }

        // TIME_LOGGED_NOT_IN_SUBTASK - Time logged directly on Epic
        if (epic.getTimeSpentSeconds() != null && epic.getTimeSpentSeconds() > 0) {
            violations.add(DataQualityViolation.of(
                    DataQualityRule.TIME_LOGGED_NOT_IN_SUBTASK,
                    "Epic"
            ));
        }

        // EPIC_DONE_OPEN_CHILDREN - Epic is Done but has open children
        if (statusMappingService.isDone(epic.getStatus(), statusMapping)) {
            long openChildren = children.stream()
                    .filter(c -> !statusMappingService.isDone(c.getStatus(), statusMapping))
                    .count();
            if (openChildren > 0) {
                violations.add(DataQualityViolation.of(
                        DataQualityRule.EPIC_DONE_OPEN_CHILDREN,
                        openChildren
                ));
            }
        }

        // EPIC_IN_PROGRESS_NO_STORIES - Epic in progress without stories
        if (statusMappingService.isEpicInProgress(epic.getStatus(), statusMapping)) {
            if (children.isEmpty()) {
                violations.add(DataQualityViolation.of(DataQualityRule.EPIC_IN_PROGRESS_NO_STORIES));
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
     * @param statusMapping The status mapping configuration
     * @return List of violations found
     */
    public List<DataQualityViolation> checkStory(
            JiraIssueEntity story,
            JiraIssueEntity epic,
            List<JiraIssueEntity> subtasks,
            StatusMappingConfig statusMapping
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
        if (statusMappingService.isInProgress(story.getStatus(), statusMapping)) {
            if (epic != null && !statusMappingService.isEpicInProgress(epic.getStatus(), statusMapping)) {
                violations.add(DataQualityViolation.of(
                        DataQualityRule.CHILD_IN_PROGRESS_EPIC_NOT,
                        Map.of("childKey", story.getIssueKey(), "epicKey", epic.getIssueKey()),
                        story.getIssueKey()
                ));
            }
        }

        // STORY_DONE_OPEN_CHILDREN - Story is Done but has open subtasks
        if (statusMappingService.isDone(story.getStatus(), statusMapping)) {
            long openSubtasks = subtasks.stream()
                    .filter(s -> !statusMappingService.isDone(s.getStatus(), statusMapping))
                    .count();
            if (openSubtasks > 0) {
                violations.add(DataQualityViolation.of(
                        DataQualityRule.STORY_DONE_OPEN_CHILDREN,
                        openSubtasks
                ));
            }
        }

        // STORY_IN_PROGRESS_NO_SUBTASKS - Story in progress without subtasks
        if (statusMappingService.isInProgress(story.getStatus(), statusMapping)) {
            if (subtasks.isEmpty()) {
                violations.add(DataQualityViolation.of(DataQualityRule.STORY_IN_PROGRESS_NO_SUBTASKS));
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
     * @param statusMapping The status mapping configuration
     * @return List of violations found
     */
    public List<DataQualityViolation> checkSubtask(
            JiraIssueEntity subtask,
            JiraIssueEntity story,
            JiraIssueEntity epic,
            StatusMappingConfig statusMapping
    ) {
        List<DataQualityViolation> violations = new ArrayList<>();

        // SUBTASK_NO_ESTIMATE - Subtask without original estimate
        if (subtask.getOriginalEstimateSeconds() == null || subtask.getOriginalEstimateSeconds() == 0) {
            // Only warn if subtask is not done
            if (!statusMappingService.isDone(subtask.getStatus(), statusMapping)) {
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
        if (statusMappingService.isInProgress(subtask.getStatus(), statusMapping)) {
            if (story != null && !statusMappingService.isInProgress(story.getStatus(), statusMapping)) {
                violations.add(DataQualityViolation.of(DataQualityRule.SUBTASK_IN_PROGRESS_STORY_NOT));
            }
        }

        // CHILD_IN_PROGRESS_EPIC_NOT - Subtask in progress but Epic not in Developing/E2E Testing
        if (statusMappingService.isInProgress(subtask.getStatus(), statusMapping)) {
            if (epic != null && !statusMappingService.isEpicInProgress(epic.getStatus(), statusMapping)) {
                violations.add(DataQualityViolation.of(
                        DataQualityRule.CHILD_IN_PROGRESS_EPIC_NOT,
                        Map.of("childKey", subtask.getIssueKey(), "epicKey", epic.getIssueKey()),
                        subtask.getIssueKey()
                ));
            }
        }

        return violations;
    }

    /**
     * Checks if an epic has any blocking errors (severity = ERROR).
     * Epics with blocking errors should be excluded from planning.
     */
    public boolean hasBlockingErrors(JiraIssueEntity epic, StatusMappingConfig statusMapping) {
        // Quick checks for blocking errors without loading children

        // EPIC_NO_TEAM is blocking
        if (epic.getTeamId() == null) {
            return true;
        }

        // EPIC_OVERDUE is blocking
        if (epic.getDueDate() != null && epic.getDueDate().isBefore(LocalDate.now())) {
            if (!statusMappingService.isDone(epic.getStatus(), statusMapping)) {
                return true;
            }
        }

        // Load children for additional checks
        List<JiraIssueEntity> children = issueRepository.findByParentKey(epic.getIssueKey());

        // EPIC_DONE_OPEN_CHILDREN is blocking
        if (statusMappingService.isDone(epic.getStatus(), statusMapping)) {
            boolean hasOpenChildren = children.stream()
                    .anyMatch(c -> !statusMappingService.isDone(c.getStatus(), statusMapping));
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
     * Checks if any issues in the hierarchy have estimates.
     */
    private boolean hasEstimatesInHierarchy(List<JiraIssueEntity> children) {
        for (JiraIssueEntity child : children) {
            if (child.getOriginalEstimateSeconds() != null && child.getOriginalEstimateSeconds() > 0) {
                return true;
            }
            // Check subtasks of this child
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
}

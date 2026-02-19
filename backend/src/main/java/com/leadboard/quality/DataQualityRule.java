package com.leadboard.quality;

/**
 * Data quality validation rules.
 * Each rule has a default severity and message template.
 */
public enum DataQualityRule {

    // Time logging rules
    TIME_LOGGED_WRONG_EPIC_STATUS(
            DataQualitySeverity.WARNING,
            "Time logging is only allowed for epics in Developing/E2E Testing status"
    ),

    TIME_LOGGED_NOT_IN_SUBTASK(
            DataQualitySeverity.ERROR,
            "Time is logged directly on %s instead of subtask"
    ),

    // Status consistency rules
    CHILD_IN_PROGRESS_EPIC_NOT(
            DataQualitySeverity.ERROR,
            "Child issue %s is in progress but Epic is not in Developing/E2E Testing"
    ),

    SUBTASK_IN_PROGRESS_STORY_NOT(
            DataQualitySeverity.ERROR,
            "Subtask is in progress but parent Story is not in progress"
    ),

    // Estimate rules
    EPIC_NO_ESTIMATE(
            DataQualitySeverity.WARNING,
            "Epic has no rough estimate and no detailed subtasks with estimates"
    ),

    SUBTASK_NO_ESTIMATE(
            DataQualitySeverity.WARNING,
            "Subtask has no original estimate"
    ),

    SUBTASK_WORK_NO_ESTIMATE(
            DataQualitySeverity.ERROR,
            "Subtask has logged time but no estimate"
    ),

    SUBTASK_OVERRUN(
            DataQualitySeverity.WARNING,
            "Subtask has exceeded estimate by more than 50%% (logged: %.1f hours, estimate: %.1f hours)"
    ),

    // Team rules
    EPIC_NO_TEAM(
            DataQualitySeverity.ERROR,
            "Epic has no team assigned"
    ),

    EPIC_TEAM_NO_MEMBERS(
            DataQualitySeverity.WARNING,
            "Epic's team has no active members"
    ),

    // Due date rules
    EPIC_NO_DUE_DATE(
            DataQualitySeverity.INFO,
            "Epic has no due date set"
    ),

    EPIC_OVERDUE(
            DataQualitySeverity.ERROR,
            "Epic is overdue (due date: %s)"
    ),

    EPIC_FORECAST_LATE(
            DataQualitySeverity.WARNING,
            "Forecast completion date (%s) is after due date (%s)"
    ),

    // Hierarchy consistency rules
    EPIC_DONE_OPEN_CHILDREN(
            DataQualitySeverity.ERROR,
            "Epic is Done but has %d open children"
    ),

    STORY_DONE_OPEN_CHILDREN(
            DataQualitySeverity.ERROR,
            "Story is Done but has %d open subtasks"
    ),

    EPIC_IN_PROGRESS_NO_STORIES(
            DataQualitySeverity.WARNING,
            "Epic is in progress but has no stories"
    ),

    STORY_IN_PROGRESS_NO_SUBTASKS(
            DataQualitySeverity.WARNING,
            "Story is in progress but has no subtasks"
    ),

    // Story estimate rules
    STORY_NO_SUBTASK_ESTIMATES(
            DataQualitySeverity.WARNING,
            "Story has no subtasks with estimates (cannot be planned)"
    ),

    // Story dependency rules
    STORY_BLOCKED_BY_MISSING(
            DataQualitySeverity.ERROR,
            "Story is blocked by non-existent issue: %s"
    ),

    STORY_CIRCULAR_DEPENDENCY(
            DataQualitySeverity.ERROR,
            "Circular dependency detected: %s"
    ),

    STORY_BLOCKED_NO_PROGRESS(
            DataQualitySeverity.WARNING,
            "Story has been blocked for more than 30 days without progress on blocking issue: %s"
    ),

    // Subtask done without time logged
    SUBTASK_DONE_NO_TIME_LOGGED(
            DataQualitySeverity.WARNING,
            "Subtask is Done but has no time logged (estimate: %.1f hours)"
    ),

    // Subtask has time logged but still in TODO status
    SUBTASK_TIME_LOGGED_BUT_TODO(
            DataQualitySeverity.ERROR,
            "Subtask has time logged (%.1f hours) but is still in TODO status"
    ),

    // Time logged while epic is flagged (paused)
    SUBTASK_TIME_LOGGED_WHILE_EPIC_FLAGGED(
            DataQualitySeverity.WARNING,
            "Time logged on subtask while parent Epic is flagged (paused)"
    ),

    // RICE scoring rules
    RICE_MISSING_ASSESSMENT(
            DataQualitySeverity.WARNING,
            "RICE assessment is missing (epic is in Planned+ status)"
    );

    private final DataQualitySeverity severity;
    private final String messageTemplate;

    DataQualityRule(DataQualitySeverity severity, String messageTemplate) {
        this.severity = severity;
        this.messageTemplate = messageTemplate;
    }

    public DataQualitySeverity getSeverity() {
        return severity;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    /**
     * Creates a formatted message using the template and provided arguments.
     */
    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate;
        }
        return String.format(messageTemplate, args);
    }
}

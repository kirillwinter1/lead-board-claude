package com.leadboard.quality;

/**
 * Data quality validation rules.
 * Each rule has a category, a human-readable label, a default severity and a message template.
 */
public enum DataQualityRule {

    // Time logging rules
    TIME_LOGGED_WRONG_EPIC_STATUS(
            DataQualityCategory.TIME_LOGGING,
            "Time logged on wrong epic status",
            DataQualitySeverity.WARNING,
            "Time logging is only allowed for epics in Developing/E2E Testing status"
    ),

    TIME_LOGGED_NOT_IN_SUBTASK(
            DataQualityCategory.TIME_LOGGING,
            "Time logged not in subtask",
            DataQualitySeverity.ERROR,
            "Time is logged directly on %s instead of subtask"
    ),

    SUBTASK_TIME_LOGGED_WHILE_EPIC_FLAGGED(
            DataQualityCategory.TIME_LOGGING,
            "Time logged while epic is flagged",
            DataQualitySeverity.WARNING,
            "Time logged on subtask while parent Epic is flagged (paused)"
    ),

    // Status consistency rules
    CHILD_IN_PROGRESS_EPIC_NOT(
            DataQualityCategory.STATUS_CONSISTENCY,
            "Child in progress, epic is not",
            DataQualitySeverity.ERROR,
            "Child issue %s is in progress but Epic is not in Developing/E2E Testing"
    ),

    SUBTASK_IN_PROGRESS_STORY_NOT(
            DataQualityCategory.STATUS_CONSISTENCY,
            "Subtask in progress, story is not",
            DataQualitySeverity.ERROR,
            "Subtask is in progress but parent Story is not in progress"
    ),

    SUBTASK_TIME_LOGGED_BUT_TODO(
            DataQualityCategory.STATUS_CONSISTENCY,
            "Time logged but subtask in TODO",
            DataQualitySeverity.ERROR,
            "Subtask has time logged (%.1f hours) but is still in TODO status"
    ),

    SUBTASK_DONE_NO_TIME_LOGGED(
            DataQualityCategory.STATUS_CONSISTENCY,
            "Subtask done without time logged",
            DataQualitySeverity.WARNING,
            "Subtask is Done but has no time logged (estimate: %.1f hours)"
    ),

    STORY_TODO_BUT_HAS_WORK(
            DataQualityCategory.STATUS_CONSISTENCY,
            "Story in TODO but subtasks have work",
            DataQualitySeverity.WARNING,
            "Story is in TODO status but subtasks have logged time (%.1f hours)"
    ),

    STORY_FULLY_LOGGED_NOT_DONE(
            DataQualityCategory.STATUS_CONSISTENCY,
            "All time logged but issue not Done",
            DataQualitySeverity.WARNING,
            "All estimated time is logged (100%%) but issue is not Done"
    ),

    // Estimate rules
    EPIC_NO_ESTIMATE(
            DataQualityCategory.ESTIMATES,
            "Epic without estimate",
            DataQualitySeverity.WARNING,
            "Epic has no rough estimate and no detailed subtasks with estimates"
    ),

    SUBTASK_NO_ESTIMATE(
            DataQualityCategory.ESTIMATES,
            "Subtask without estimate",
            DataQualitySeverity.WARNING,
            "Subtask has no original estimate"
    ),

    SUBTASK_WORK_NO_ESTIMATE(
            DataQualityCategory.ESTIMATES,
            "Time logged without estimate",
            DataQualitySeverity.ERROR,
            "Subtask has logged time but no estimate"
    ),

    SUBTASK_OVERRUN(
            DataQualityCategory.ESTIMATES,
            "Subtask estimate exceeded",
            DataQualitySeverity.WARNING,
            "Subtask has exceeded estimate by more than 50%% (logged: %.1f hours, estimate: %.1f hours)"
    ),

    STORY_NO_SUBTASK_ESTIMATES(
            DataQualityCategory.ESTIMATES,
            "Story without subtask estimates",
            DataQualitySeverity.WARNING,
            "Story has no subtasks with estimates (cannot be planned)"
    ),

    SUBTASK_ESTIMATE_TOO_BIG(
            DataQualityCategory.ESTIMATES,
            "Subtask estimate too big",
            DataQualitySeverity.INFO,
            "Estimate is %.1f hours (more than %d) — consider splitting"
    ),

    // Team rules
    EPIC_NO_TEAM(
            DataQualityCategory.TEAM,
            "Epic without team",
            DataQualitySeverity.ERROR,
            "Epic has no team assigned"
    ),

    EPIC_TEAM_NO_MEMBERS(
            DataQualityCategory.TEAM,
            "Epic team has no members",
            DataQualitySeverity.WARNING,
            "Epic's team has no active members"
    ),

    TEAM_FIELD_UNMAPPED(
            DataQualityCategory.TEAM,
            "Team field not mapped",
            DataQualitySeverity.WARNING,
            "Jira team field value \"%s\" is not mapped to any team"
    ),

    // Assignee rules
    IN_PROGRESS_NO_ASSIGNEE(
            DataQualityCategory.ASSIGNEE,
            "In progress without assignee",
            DataQualitySeverity.ERROR,
            "Subtask is in progress but has no assignee"
    ),

    ASSIGNEE_NOT_IN_TEAM(
            DataQualityCategory.ASSIGNEE,
            "Assignee not in epic's team",
            DataQualitySeverity.WARNING,
            "Assignee %s is not an active member of the epic's team"
    ),

    // Due date rules
    EPIC_NO_DUE_DATE(
            DataQualityCategory.DUE_DATES,
            "Epic without due date",
            DataQualitySeverity.INFO,
            "Epic has no due date set"
    ),

    EPIC_OVERDUE(
            DataQualityCategory.DUE_DATES,
            "Epic overdue",
            DataQualitySeverity.ERROR,
            "Epic is overdue (due date: %s)"
    ),

    EPIC_FORECAST_LATE(
            DataQualityCategory.DUE_DATES,
            "Forecast later than due date",
            DataQualitySeverity.WARNING,
            "Forecast completion date (%s) is after due date (%s)"
    ),

    CHILD_DUE_AFTER_EPIC(
            DataQualityCategory.DUE_DATES,
            "Due date after epic's due date",
            DataQualitySeverity.WARNING,
            "Due date (%s) is after the epic's due date (%s)"
    ),

    // Hierarchy consistency rules
    EPIC_DONE_OPEN_CHILDREN(
            DataQualityCategory.HIERARCHY,
            "Epic done, has open children",
            DataQualitySeverity.ERROR,
            "Epic is Done but has %d open children"
    ),

    STORY_DONE_OPEN_CHILDREN(
            DataQualityCategory.HIERARCHY,
            "Story done, has open subtasks",
            DataQualitySeverity.ERROR,
            "Story is Done but has %d open subtasks"
    ),

    EPIC_IN_PROGRESS_NO_STORIES(
            DataQualityCategory.HIERARCHY,
            "Epic in progress without stories",
            DataQualitySeverity.WARNING,
            "Epic is in progress but has no stories"
    ),

    STORY_IN_PROGRESS_NO_SUBTASKS(
            DataQualityCategory.HIERARCHY,
            "Story in progress without subtasks",
            DataQualitySeverity.WARNING,
            "Story is in progress but has no subtasks"
    ),

    // Story dependency rules
    STORY_BLOCKED_BY_MISSING(
            DataQualityCategory.DEPENDENCIES,
            "Blocker not found",
            DataQualitySeverity.ERROR,
            "Story is blocked by non-existent issue: %s"
    ),

    STORY_CIRCULAR_DEPENDENCY(
            DataQualityCategory.DEPENDENCIES,
            "Circular dependency",
            DataQualitySeverity.ERROR,
            "Circular dependency detected: %s"
    ),

    STORY_BLOCKED_NO_PROGRESS(
            DataQualityCategory.DEPENDENCIES,
            "Blocked without progress >30 days",
            DataQualitySeverity.WARNING,
            "Story has been blocked for more than 30 days without progress on blocking issue: %s"
    ),

    // Staleness rules
    BUG_STALE(
            DataQualityCategory.STALENESS,
            "Bug stale >14 days",
            DataQualitySeverity.WARNING,
            "Bug has no progress for %d days"
    ),

    IN_PROGRESS_TOO_LONG(
            DataQualityCategory.STALENESS,
            "In progress too long",
            DataQualitySeverity.WARNING,
            "Issue is in status \"%s\" for %d days"
    ),

    EPIC_FLAGGED_TOO_LONG(
            DataQualityCategory.STALENESS,
            "Epic flagged too long",
            DataQualitySeverity.WARNING,
            "Epic has been flagged (paused) for %d days"
    ),

    // RICE scoring rules
    RICE_MISSING_ASSESSMENT(
            DataQualityCategory.RICE,
            "RICE assessment missing",
            DataQualitySeverity.WARNING,
            "RICE assessment is missing (epic is in Planned+ status)"
    ),

    // Bug SLA rules
    BUG_SLA_BREACH(
            DataQualityCategory.BUG_SLA,
            "Bug SLA breach",
            DataQualitySeverity.ERROR,
            "Bug exceeded SLA: %d hours (limit: %d hours)"
    ),

    BUG_NO_PRIORITY(
            DataQualityCategory.BUG_SLA,
            "Bug without priority",
            DataQualitySeverity.WARNING,
            "Bug has no priority (SLA cannot be applied)"
    ),

    // Content rules
    EPIC_NO_DESCRIPTION(
            DataQualityCategory.CONTENT,
            "Epic without description",
            DataQualitySeverity.INFO,
            "Epic has no description"
    );

    private final DataQualityCategory category;
    private final String label;
    private final DataQualitySeverity severity;
    private final String messageTemplate;

    DataQualityRule(DataQualityCategory category, String label, DataQualitySeverity severity, String messageTemplate) {
        this.category = category;
        this.label = label;
        this.severity = severity;
        this.messageTemplate = messageTemplate;
    }

    public DataQualityCategory getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
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
            return messageTemplate.replace("%%", "%");
        }
        return String.format(messageTemplate, args);
    }
}

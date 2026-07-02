package com.leadboard.quality.fix.dto;

/**
 * A single field change a fix will make, for the preview UI.
 *
 * @param issueKey  the issue this change applies to (may differ from the violation's issue,
 *                  e.g. CHILD_IN_PROGRESS_EPIC_NOT changes the epic)
 * @param summary   the issue summary (for display)
 * @param issueType the issue's Jira type name (for the preview UI to render its type icon)
 * @param field     human label of the field being changed (e.g. "Status", "Team", "Due date")
 * @param from      current value (may be null)
 * @param to        new value (may be null / a placeholder for input-driven values)
 * @param local     true when the change is applied only in Lead Board (no Jira write)
 */
public record FixChange(
        String issueKey,
        String summary,
        String issueType,
        String field,
        String from,
        String to,
        boolean local
) {
    public static FixChange jira(String issueKey, String summary, String issueType, String field, String from, String to) {
        return new FixChange(issueKey, summary, issueType, field, from, to, false);
    }

    public static FixChange local(String issueKey, String summary, String issueType, String field, String from, String to) {
        return new FixChange(issueKey, summary, issueType, field, from, to, true);
    }
}

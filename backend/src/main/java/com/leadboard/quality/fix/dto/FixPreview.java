package com.leadboard.quality.fix.dto;

import java.util.List;

/**
 * Response of GET /api/data-quality/fix-preview.
 *
 * @param issueKey            the issue with the violation
 * @param rule                the {@code DataQualityRule} name
 * @param fixType             UI hint of how to render the fix (e.g. TRANSITION, TEAM_SELECT,
 *                            ESTIMATE, DUE_DATE, PRIORITY, WORKLOG, WORKLOG_MOVE, CHOICE, RICE_FORM)
 * @param title               short human title of the fix
 * @param applicable          false when the fix cannot currently be applied
 * @param notApplicableReason reason shown when {@code applicable == false}
 * @param risky               true for destructive/mass changes (renders a red warning block)
 * @param warning             warning text shown when {@code risky == true}
 * @param authMode            "OAUTH" (attributed to user), "BASIC" (service account), or "LOCAL"
 * @param changes             preview of the changes (for single-choice fixes)
 * @param affectedIssues      extra issue keys affected (for risky/mass fixes)
 * @param inputs              inputs required (for single-choice fixes)
 * @param choices             mutually-exclusive resolution choices (empty for single-choice fixes)
 */
public record FixPreview(
        String issueKey,
        String rule,
        String fixType,
        String title,
        boolean applicable,
        String notApplicableReason,
        boolean risky,
        String warning,
        String authMode,
        List<FixChange> changes,
        List<String> affectedIssues,
        List<FixInput> inputs,
        List<FixChoice> choices
) {
    /** Fluent builder to keep the many optional fields readable in handlers. */
    public static Builder builder(String issueKey, com.leadboard.quality.DataQualityRule rule, String fixType, String title) {
        return new Builder(issueKey, rule.name(), fixType, title);
    }

    /** Returns a copy marked not-applicable with the given reason (keeps fixType/title/authMode). */
    public FixPreview notApplicable(String reason) {
        return new FixPreview(issueKey, rule, fixType, title, false, reason, risky, warning, authMode,
                List.of(), List.of(), List.of(), List.of());
    }

    public static final class Builder {
        private final String issueKey;
        private final String rule;
        private final String fixType;
        private final String title;
        private boolean applicable = true;
        private String notApplicableReason;
        private boolean risky = false;
        private String warning;
        private String authMode = "OAUTH";
        private List<FixChange> changes = List.of();
        private List<String> affectedIssues = List.of();
        private List<FixInput> inputs = List.of();
        private List<FixChoice> choices = List.of();

        private Builder(String issueKey, String rule, String fixType, String title) {
            this.issueKey = issueKey;
            this.rule = rule;
            this.fixType = fixType;
            this.title = title;
        }

        public Builder authMode(String authMode) { this.authMode = authMode; return this; }
        public Builder risky(String warning) { this.risky = true; this.warning = warning; return this; }
        public Builder changes(List<FixChange> changes) { this.changes = changes; return this; }
        public Builder affectedIssues(List<String> affectedIssues) { this.affectedIssues = affectedIssues; return this; }
        public Builder inputs(List<FixInput> inputs) { this.inputs = inputs; return this; }
        public Builder choices(List<FixChoice> choices) { this.choices = choices; return this; }

        public Builder notApplicable(String reason) {
            this.applicable = false;
            this.notApplicableReason = reason;
            return this;
        }

        public FixPreview build() {
            return new FixPreview(issueKey, rule, fixType, title, applicable, notApplicableReason,
                    risky, warning, authMode, changes, affectedIssues, inputs, choices);
        }
    }
}

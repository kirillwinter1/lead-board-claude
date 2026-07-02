package com.leadboard.quality.fix;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.sync.JiraIssueEntity;

import java.util.Map;

/**
 * A fix strategy for a single {@link DataQualityRule}. Handlers are Spring beans; they are
 * auto-collected into {@link FixService}'s registry by {@link #rule()}.
 *
 * <p>Contract: {@link #preview} is read-only and describes what would change (and whether the
 * fix is applicable); {@link #apply} performs the change in Jira (source of truth) and/or the
 * local DB and returns the affected issue keys. Neither method re-checks whether the underlying
 * violation still exists — {@link FixService} does that around both calls.</p>
 */
public interface FixHandler {

    /** The rule this handler fixes. */
    DataQualityRule rule();

    /** Read-only description of the fix for the modal. Must not mutate anything. */
    FixPreview preview(JiraIssueEntity issue);

    /**
     * Apply the fix.
     *
     * @param issue    the issue with the violation (freshly loaded)
     * @param choiceId selected choice id (for multi-choice fixes; null otherwise)
     * @param params   user-supplied input values
     * @return result including the issue keys that were changed
     */
    FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params);

    /**
     * True when the fix only changes Lead Board's local DB (no Jira write). {@link FixService}
     * skips the post-fix Jira re-sync for local fixes (EPIC_NO_TEAM, TEAM_FIELD_UNMAPPED).
     */
    default boolean local() {
        return false;
    }
}

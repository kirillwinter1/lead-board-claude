package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.FixHandler;
import com.leadboard.quality.fix.FixSupport;
import com.leadboard.quality.fix.dto.FixChange;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EPIC_DONE_OPEN_CHILDREN (risky): a Done epic still has open children. Fix closes every open
 * child and its open subtasks, bottom-up (subtasks before their parent), continue-on-error.
 * Partial failures are aggregated into the result message.
 */
@Component
public class EpicDoneOpenChildrenFixHandler implements FixHandler {

    private static final Logger log = LoggerFactory.getLogger(EpicDoneOpenChildrenFixHandler.class);

    private final FixSupport support;

    public EpicDoneOpenChildrenFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.EPIC_DONE_OPEN_CHILDREN;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        List<JiraIssueEntity> openChildren = support.openChildren(issue.getIssueKey());
        List<String> affected = new ArrayList<>();
        List<FixChange> changes = new ArrayList<>();
        for (JiraIssueEntity child : openChildren) {
            for (JiraIssueEntity st : support.openSubtasks(child.getIssueKey())) {
                affected.add(st.getIssueKey());
                changes.add(support.statusChange(st, support.targetStatusName(st, StatusCategory.DONE)));
            }
            affected.add(child.getIssueKey());
            changes.add(support.statusChange(child, support.targetStatusName(child, StatusCategory.DONE)));
        }
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "TRANSITION",
                "Close all open children").authMode(support.authMode());
        if (affected.isEmpty()) {
            return b.notApplicable("No open children to close.").build();
        }
        return b
                .risky("This closes " + affected.size() + " issue(s) — every open child of this epic "
                        + "and their open subtasks will be transitioned to Done.")
                .changes(changes)
                .affectedIssues(affected)
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        List<JiraIssueEntity> openChildren = support.openChildren(issue.getIssueKey());
        List<String> closed = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (JiraIssueEntity child : openChildren) {
            // Bottom-up: close the child's open subtasks first, then the child.
            for (JiraIssueEntity st : support.openSubtasks(child.getIssueKey())) {
                closeToDone(st, closed, failures);
            }
            closeToDone(child, closed, failures);
        }

        String base = "Closed " + closed.size() + " issue(s)";
        if (failures.isEmpty()) {
            return FixResult.ok(base + ".", closed);
        }
        return FixResult.partial(base + "; failed for: " + String.join(", ", failures), closed);
    }

    private void closeToDone(JiraIssueEntity target, List<String> closed, List<String> failures) {
        try {
            String toName = support.targetStatusName(target, StatusCategory.DONE);
            support.jiraWrite().transitionWithFallback(target.getIssueKey(), toName);
            closed.add(target.getIssueKey());
        } catch (Exception e) {
            log.warn("Failed to close {} to Done: {}", target.getIssueKey(), e.getMessage());
            failures.add(target.getIssueKey() + " (" + e.getMessage() + ")");
        }
    }
}

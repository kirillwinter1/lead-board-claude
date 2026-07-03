package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.FixHandler;
import com.leadboard.quality.fix.FixSupport;
import com.leadboard.quality.fix.dto.FixInput;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * CHILD_IN_PROGRESS_EPIC_NOT: a child is in progress but its epic is not. Fix moves the
 * EPIC into IN_PROGRESS (the changes point at the epic, not the child row).
 */
@Component
public class ChildInProgressEpicNotFixHandler implements FixHandler {

    private final FixSupport support;

    public ChildInProgressEpicNotFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.CHILD_IN_PROGRESS_EPIC_NOT;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "TRANSITION",
                "Change epic status").authMode(support.authMode());
        JiraIssueEntity epic = support.resolveEpicOf(issue);
        if (epic == null) {
            return b.notApplicable("Parent epic not found.").build();
        }
        FixSupport.TargetStatusOptions options = support.targetStatusOptions(epic, StatusCategory.IN_PROGRESS);
        if (options.isEmpty()) {
            return b.notApplicable("No In Progress status is available for this epic.").build();
        }
        return b
                .inputs(List.of(FixInput.select("targetStatus", "New status", true,
                        FixSupport.statusOptions(options.names()), options.defaultName())))
                .changes(List.of(support.statusChange(epic, options.defaultName())))
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        JiraIssueEntity epic = support.resolveEpicOf(issue);
        if (epic == null) {
            throw new IllegalArgumentException("Parent epic not found.");
        }
        String target = support.requireTargetStatus(epic, StatusCategory.IN_PROGRESS, params);
        String newStatus = support.jiraWrite().transitionWithFallback(epic.getIssueKey(), target);
        return FixResult.ok(epic.getIssueKey() + " → " + newStatus, List.of(epic.getIssueKey()));
    }
}

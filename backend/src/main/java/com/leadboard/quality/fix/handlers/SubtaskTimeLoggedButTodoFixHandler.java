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
 * SUBTASK_TIME_LOGGED_BUT_TODO: a subtask has logged time but is still in TODO. Fix moves
 * the subtask itself into IN_PROGRESS.
 */
@Component
public class SubtaskTimeLoggedButTodoFixHandler implements FixHandler {

    private final FixSupport support;

    public SubtaskTimeLoggedButTodoFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.SUBTASK_TIME_LOGGED_BUT_TODO;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "TRANSITION",
                "Change subtask status").authMode(support.authMode());
        FixSupport.TargetStatusOptions options = support.targetStatusOptions(issue, StatusCategory.IN_PROGRESS);
        if (options.isEmpty()) {
            return b.notApplicable("No In Progress status is available for this subtask.").build();
        }
        return b
                .inputs(List.of(FixInput.select("targetStatus", "New status", true,
                        FixSupport.statusOptions(options.names()), options.defaultName())))
                .changes(List.of(support.statusChange(issue, options.defaultName())))
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        String target = support.requireTargetStatus(issue, StatusCategory.IN_PROGRESS, params);
        String newStatus = support.jiraWrite().transitionWithFallback(issue.getIssueKey(), target);
        return FixResult.ok(issue.getIssueKey() + " → " + newStatus, List.of(issue.getIssueKey()));
    }
}

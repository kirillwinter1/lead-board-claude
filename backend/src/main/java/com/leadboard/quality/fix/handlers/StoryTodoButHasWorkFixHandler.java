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
 * STORY_TODO_BUT_HAS_WORK: a story is in TODO but its subtasks have logged time. Fix moves
 * the story itself into IN_PROGRESS.
 */
@Component
public class StoryTodoButHasWorkFixHandler implements FixHandler {

    private final FixSupport support;

    public StoryTodoButHasWorkFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.STORY_TODO_BUT_HAS_WORK;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "TRANSITION",
                "Change story status").authMode(support.authMode());
        FixSupport.TargetStatusOptions options = support.targetStatusOptions(issue, StatusCategory.IN_PROGRESS);
        if (options.isEmpty()) {
            return b.notApplicable("No In Progress status is available for this story.").build();
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

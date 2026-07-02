package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.FixHandler;
import com.leadboard.quality.fix.FixSupport;
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
                "Move story to In Progress").authMode(support.authMode());
        String target = support.targetStatusName(issue, StatusCategory.IN_PROGRESS);
        if (target == null) {
            return b.notApplicable("No In Progress status configured.").build();
        }
        return b.changes(List.of(support.statusChange(issue, target))).build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        String target = support.targetStatusName(issue, StatusCategory.IN_PROGRESS);
        String newStatus = support.jiraWrite().transitionWithFallback(issue.getIssueKey(), target);
        return FixResult.ok(issue.getIssueKey() + " → " + newStatus, List.of(issue.getIssueKey()));
    }
}

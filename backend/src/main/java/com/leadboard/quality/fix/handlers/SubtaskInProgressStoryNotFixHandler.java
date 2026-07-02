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
 * SUBTASK_IN_PROGRESS_STORY_NOT: a subtask is in progress but its parent story is not.
 * Fix moves the STORY into IN_PROGRESS.
 */
@Component
public class SubtaskInProgressStoryNotFixHandler implements FixHandler {

    private final FixSupport support;

    public SubtaskInProgressStoryNotFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.SUBTASK_IN_PROGRESS_STORY_NOT;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "TRANSITION",
                "Move story to In Progress").authMode(support.authMode());
        JiraIssueEntity story = story(issue);
        if (story == null) {
            return b.notApplicable("Parent story not found.").build();
        }
        String target = support.targetStatusName(story, StatusCategory.IN_PROGRESS);
        if (target == null) {
            return b.notApplicable("No In Progress status configured.").build();
        }
        return b.changes(List.of(support.statusChange(story, target))).build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        JiraIssueEntity story = story(issue);
        if (story == null) {
            throw new IllegalArgumentException("Parent story not found.");
        }
        String target = support.targetStatusName(story, StatusCategory.IN_PROGRESS);
        String newStatus = support.jiraWrite().transitionWithFallback(story.getIssueKey(), target);
        return FixResult.ok(story.getIssueKey() + " → " + newStatus, List.of(story.getIssueKey()));
    }

    private JiraIssueEntity story(JiraIssueEntity subtask) {
        if (subtask.getParentKey() == null) return null;
        return support.issues().findByIssueKey(subtask.getParentKey()).orElse(null);
    }
}

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
 * STORY_FULLY_LOGGED_NOT_DONE (risky): all estimated time is logged but the story is not Done.
 * Fix transitions the story to Done. Risky because it may skip intermediate statuses.
 */
@Component
public class StoryFullyLoggedNotDoneFixHandler implements FixHandler {

    private final FixSupport support;

    public StoryFullyLoggedNotDoneFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.STORY_FULLY_LOGGED_NOT_DONE;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "TRANSITION",
                "Change story status").authMode(support.authMode());
        FixSupport.TargetStatusOptions options = support.targetStatusOptions(issue, StatusCategory.DONE);
        if (options.isEmpty()) {
            return b.notApplicable("No Done status is available for this story.").build();
        }
        return b
                .risky("This moves the story straight to a Done status and may skip intermediate statuses "
                        + "(e.g. review / testing). Make sure the work is really finished.")
                .inputs(List.of(FixInput.select("targetStatus", "New status", true,
                        FixSupport.statusOptions(options.names()), options.defaultName())))
                .changes(List.of(support.statusChange(issue, options.defaultName())))
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        String target = support.requireTargetStatus(issue, StatusCategory.DONE, params);
        String newStatus = support.jiraWrite().transitionWithFallback(issue.getIssueKey(), target);
        return FixResult.ok(issue.getIssueKey() + " → " + newStatus, List.of(issue.getIssueKey()));
    }
}

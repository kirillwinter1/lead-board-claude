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
 * STORY_DONE_OPEN_CHILDREN (risky): a Done story still has open subtasks. Fix closes every open
 * subtask (transition to Done), continue-on-error, with partial failures aggregated.
 */
@Component
public class StoryDoneOpenChildrenFixHandler implements FixHandler {

    private static final Logger log = LoggerFactory.getLogger(StoryDoneOpenChildrenFixHandler.class);

    private final FixSupport support;

    public StoryDoneOpenChildrenFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.STORY_DONE_OPEN_CHILDREN;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        List<JiraIssueEntity> open = support.openSubtasks(issue.getIssueKey());
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "TRANSITION",
                "Close all open subtasks").authMode(support.authMode());
        if (open.isEmpty()) {
            return b.notApplicable("No open subtasks to close.").build();
        }
        List<FixChange> changes = open.stream()
                .map(st -> support.statusChange(st, support.targetStatusName(st, StatusCategory.DONE)))
                .toList();
        return b
                .risky("This closes " + open.size() + " open subtask(s) of this story — they will be "
                        + "transitioned to Done.")
                .changes(changes)
                .affectedIssues(open.stream().map(JiraIssueEntity::getIssueKey).toList())
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        List<JiraIssueEntity> open = support.openSubtasks(issue.getIssueKey());
        List<String> closed = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (JiraIssueEntity st : open) {
            try {
                String toName = support.targetStatusName(st, StatusCategory.DONE);
                support.jiraWrite().transitionWithFallback(st.getIssueKey(), toName);
                closed.add(st.getIssueKey());
            } catch (Exception e) {
                log.warn("Failed to close subtask {}: {}", st.getIssueKey(), e.getMessage());
                failures.add(st.getIssueKey() + " (" + e.getMessage() + ")");
            }
        }

        String base = "Closed " + closed.size() + " subtask(s)";
        if (failures.isEmpty()) {
            return FixResult.ok(base + ".", closed);
        }
        return FixResult.partial(base + "; failed for: " + String.join(", ", failures), closed);
    }
}

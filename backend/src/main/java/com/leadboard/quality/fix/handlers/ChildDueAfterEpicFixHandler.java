package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.FixHandler;
import com.leadboard.quality.fix.FixSupport;
import com.leadboard.quality.fix.dto.FixChange;
import com.leadboard.quality.fix.dto.FixChoice;
import com.leadboard.quality.fix.dto.FixInput;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.sync.JiraIssueEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * CHILD_DUE_AFTER_EPIC: a story's due date is later than its epic's. Two choices: pull the
 * story's due date in (default = epic's due date), or push the epic's due date out
 * (default = story's due date).
 */
@Component
public class ChildDueAfterEpicFixHandler implements FixHandler {

    private static final String MOVE_STORY = "moveStory";
    private static final String MOVE_EPIC = "moveEpic";

    private final FixSupport support;

    public ChildDueAfterEpicFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.CHILD_DUE_AFTER_EPIC;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "CHOICE",
                "Align due dates").authMode(support.authMode());
        JiraIssueEntity epic = support.resolveEpicOf(issue);
        if (epic == null || epic.getDueDate() == null || issue.getDueDate() == null) {
            return b.notApplicable("Epic or story due date is missing.").build();
        }
        String epicDue = epic.getDueDate().toString();
        String storyDue = issue.getDueDate().toString();

        FixChoice moveStory = new FixChoice(MOVE_STORY, "Pull the story's due date in",
                List.of(FixChange.jira(issue.getIssueKey(), issue.getSummary(), issue.getIssueType(), "Due date", storyDue, epicDue)),
                List.of(FixInput.date("dueDate", "Story due date", true, epicDue)));

        FixChoice moveEpic = new FixChoice(MOVE_EPIC, "Push the epic's due date out",
                List.of(FixChange.jira(epic.getIssueKey(), epic.getSummary(), epic.getIssueType(), "Due date", epicDue, storyDue)),
                List.of(FixInput.date("dueDate", "Epic due date", true, storyDue)));

        return b.choices(List.of(moveStory, moveEpic)).build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        JiraIssueEntity epic = support.resolveEpicOf(issue);
        if (epic == null) {
            throw new IllegalArgumentException("Parent epic not found.");
        }
        LocalDate dueDate = FixSupport.dateParam(params, "dueDate");
        if (dueDate == null) {
            throw new IllegalArgumentException("dueDate is required");
        }
        String choice = choiceId != null ? choiceId : MOVE_STORY;

        if (MOVE_EPIC.equals(choice)) {
            support.jira().updateDueDate(epic.getIssueKey(), dueDate);
            return FixResult.ok("Set epic " + epic.getIssueKey() + " due date to " + dueDate,
                    List.of(epic.getIssueKey()));
        }
        if (MOVE_STORY.equals(choice)) {
            support.jira().updateDueDate(issue.getIssueKey(), dueDate);
            return FixResult.ok("Set " + issue.getIssueKey() + " due date to " + dueDate,
                    List.of(issue.getIssueKey()));
        }
        throw new IllegalArgumentException("Unknown choice: " + choiceId);
    }
}

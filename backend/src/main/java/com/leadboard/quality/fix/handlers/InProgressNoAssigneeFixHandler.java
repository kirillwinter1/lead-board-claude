package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.FixHandler;
import com.leadboard.quality.fix.FixSupport;
import com.leadboard.quality.fix.dto.FixChange;
import com.leadboard.quality.fix.dto.FixInput;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.sync.JiraIssueEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * IN_PROGRESS_NO_ASSIGNEE: an in-progress subtask has no assignee. Fix assigns an active member
 * of the epic's team (selected in the modal) via OAuth-or-BasicAuth.
 */
@Component
public class InProgressNoAssigneeFixHandler implements FixHandler {

    private final FixSupport support;

    public InProgressNoAssigneeFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.IN_PROGRESS_NO_ASSIGNEE;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "ASSIGNEE_SELECT",
                "Assign an active team member").authMode(support.authMode());
        JiraIssueEntity epic = support.resolveEpicOf(issue);
        Long teamId = epic != null ? epic.getTeamId() : null;
        if (teamId == null) {
            return b.notApplicable("Epic has no team — assign a team first.").build();
        }
        List<FixInput.Option> options = support.memberOptions(teamId);
        if (options.isEmpty()) {
            return b.notApplicable("Epic's team has no active members.").build();
        }
        return b
                .inputs(List.of(FixInput.select("accountId", "Assignee", true, options, null)))
                .changes(List.of(FixChange.jira(issue.getIssueKey(), issue.getSummary(), issue.getIssueType(),
                        "Assignee", "Unassigned", "(selected member)")))
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        String accountId = FixSupport.stringParam(params, "accountId");
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId is required");
        }
        JiraIssueEntity epic = support.resolveEpicOf(issue);
        Long teamId = epic != null ? epic.getTeamId() : null;
        if (teamId != null && !support.isActiveMember(teamId, accountId)) {
            throw new IllegalArgumentException("Selected assignee is not an active member of the epic's team");
        }
        support.jiraWrite().assignWithFallback(issue.getIssueKey(), accountId);
        return FixResult.ok("Assigned " + issue.getIssueKey(), List.of(issue.getIssueKey()));
    }
}

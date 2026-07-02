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
import com.leadboard.team.CreateTeamMemberRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ASSIGNEE_NOT_IN_TEAM: a subtask's assignee is not an active member of the epic's team.
 * Two choices: reassign to an active member (Jira write), or add the current assignee to the
 * team (local Lead Board membership).
 */
@Component
public class AssigneeNotInTeamFixHandler implements FixHandler {

    private static final String REASSIGN = "reassign";
    private static final String ADD_TO_TEAM = "addToTeam";

    private final FixSupport support;

    public AssigneeNotInTeamFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.ASSIGNEE_NOT_IN_TEAM;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "CHOICE",
                "Resolve assignee / team mismatch").authMode(support.authMode());
        JiraIssueEntity epic = support.resolveEpicOf(issue);
        Long teamId = epic != null ? epic.getTeamId() : null;
        if (teamId == null) {
            return b.notApplicable("Epic has no team.").build();
        }

        String who = issue.getAssigneeDisplayName() != null
                ? issue.getAssigneeDisplayName() : issue.getAssigneeAccountId();

        FixChoice reassign = new FixChoice(REASSIGN, "Reassign to a team member",
                List.of(FixChange.jira(issue.getIssueKey(), issue.getSummary(),
                        "Assignee", who, "(selected member)")),
                List.of(FixInput.select("accountId", "Assignee", true, support.memberOptions(teamId), null)));

        FixChoice addToTeam = new FixChoice(ADD_TO_TEAM, "Add " + who + " to the team",
                List.of(FixChange.local(issue.getIssueKey(), issue.getSummary(),
                        "Team membership", "Not a member", who + " added")),
                List.of());

        return b.choices(List.of(reassign, addToTeam)).build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        JiraIssueEntity epic = support.resolveEpicOf(issue);
        Long teamId = epic != null ? epic.getTeamId() : null;
        if (teamId == null) {
            throw new IllegalArgumentException("Epic has no team.");
        }
        String choice = choiceId != null ? choiceId : REASSIGN;

        if (ADD_TO_TEAM.equals(choice)) {
            if (issue.getAssigneeAccountId() == null) {
                throw new IllegalArgumentException("Subtask has no assignee to add.");
            }
            support.teamService().addTeamMember(teamId, new CreateTeamMemberRequest(
                    issue.getAssigneeAccountId(), issue.getAssigneeDisplayName(), null, null, null));
            // Local membership change — the subtask itself did not change, so nothing to re-sync.
            return FixResult.ok("Added " + issue.getAssigneeDisplayName() + " to the team", List.of());
        }

        if (REASSIGN.equals(choice)) {
            String accountId = FixSupport.stringParam(params, "accountId");
            if (accountId == null || accountId.isBlank()) {
                throw new IllegalArgumentException("accountId is required to reassign");
            }
            if (!support.isActiveMember(teamId, accountId)) {
                throw new IllegalArgumentException("Selected assignee is not an active member of the epic's team");
            }
            support.jiraWrite().assignWithFallback(issue.getIssueKey(), accountId);
            return FixResult.ok("Reassigned " + issue.getIssueKey(), List.of(issue.getIssueKey()));
        }

        throw new IllegalArgumentException("Unknown choice: " + choiceId);
    }
}

package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.FixHandler;
import com.leadboard.quality.fix.FixSupport;
import com.leadboard.quality.fix.dto.FixChange;
import com.leadboard.quality.fix.dto.FixInput;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.UpdateTeamRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * TEAM_FIELD_UNMAPPED: the Jira team field has a value that maps to no team. Fix maps the value
 * to an existing team by setting that team's {@code jiraTeamValue} (which then links all matching
 * issues). Only teams with a blank Jira value are offered, so we never clobber an existing mapping.
 * Local (Lead Board only): no Jira write, no re-sync.
 */
@Component
public class TeamFieldUnmappedFixHandler implements FixHandler {

    private final FixSupport support;

    public TeamFieldUnmappedFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.TEAM_FIELD_UNMAPPED;
    }

    @Override
    public boolean local() {
        return true;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "TEAM_SELECT",
                "Map the Jira team value to a team").authMode("LOCAL");
        String fieldValue = issue.getTeamFieldValue();
        if (fieldValue == null || fieldValue.isBlank()) {
            return b.notApplicable("Issue has no Jira team field value to map.").build();
        }
        List<FixInput.Option> options = support.teamsWithBlankJiraValue().stream()
                .map(t -> new FixInput.Option(String.valueOf(t.getId()), t.getName()))
                .toList();
        if (options.isEmpty()) {
            return b.notApplicable("No teams without a Jira mapping are available.").build();
        }
        return b
                .inputs(List.of(FixInput.select("teamId", "Team", true, options, null)))
                .changes(List.of(FixChange.local(issue.getIssueKey(), issue.getSummary(),
                        "Team mapping", "\"" + fieldValue + "\" (unmapped)", "→ selected team")))
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        String fieldValue = issue.getTeamFieldValue();
        if (fieldValue == null || fieldValue.isBlank()) {
            throw new IllegalArgumentException("Issue has no Jira team field value to map.");
        }
        Double raw = FixSupport.doubleParam(params, "teamId");
        if (raw == null) {
            throw new IllegalArgumentException("teamId is required");
        }
        Long teamId = raw.longValue();
        TeamEntity team = support.teams().findByIdAndActiveTrue(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        if (team.getJiraTeamValue() != null && !team.getJiraTeamValue().isBlank()) {
            throw new IllegalArgumentException("Team already has a Jira team mapping.");
        }

        // updateTeam sets jiraTeamValue and links all matching issues (incl. this one) to the team.
        support.teamService().updateTeam(teamId, new UpdateTeamRequest(null, fieldValue, null));

        return FixResult.ok("Mapped \"" + fieldValue + "\" to team " + team.getName(),
                List.of(issue.getIssueKey()));
    }
}

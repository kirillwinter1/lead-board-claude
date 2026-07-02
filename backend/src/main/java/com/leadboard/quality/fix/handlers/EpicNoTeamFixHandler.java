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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * EPIC_NO_TEAM: an epic has no team. Fix assigns a team locally (Lead Board only): it sets
 * team_id and marks it manual so the next sync does not null it out (the Jira team field stays
 * empty). No Jira write, no re-sync.
 */
@Component
public class EpicNoTeamFixHandler implements FixHandler {

    private final FixSupport support;

    public EpicNoTeamFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.EPIC_NO_TEAM;
    }

    @Override
    public boolean local() {
        return true;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        List<FixInput.Option> options = support.teams().findByActiveTrue().stream()
                .map(t -> new FixInput.Option(String.valueOf(t.getId()), t.getName(), t.getColor()))
                .toList();
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "TEAM_SELECT",
                "Assign a team (Lead Board only)").authMode("LOCAL");
        if (options.isEmpty()) {
            return b.notApplicable("No teams available — create a team first.").build();
        }
        return b
                .inputs(List.of(FixInput.select("teamId", "Team", true, options, null)))
                .changes(List.of(FixChange.local(issue.getIssueKey(), issue.getSummary(), issue.getIssueType(), "Team", "—", "(selected team)")))
                .build();
    }

    @Override
    @Transactional
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        Long teamId = parseTeamId(params);
        TeamEntity team = support.teams().findByIdAndActiveTrue(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        issue.setTeamId(team.getId());
        issue.setTeamIdManual(true);
        support.issues().save(issue);

        return FixResult.ok("Assigned " + issue.getIssueKey() + " to team " + team.getName(),
                List.of(issue.getIssueKey()));
    }

    private Long parseTeamId(Map<String, Object> params) {
        Double raw = FixSupport.doubleParam(params, "teamId");
        if (raw == null) {
            throw new IllegalArgumentException("teamId is required");
        }
        return raw.longValue();
    }
}

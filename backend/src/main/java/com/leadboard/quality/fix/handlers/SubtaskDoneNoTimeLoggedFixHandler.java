package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.FixHandler;
import com.leadboard.quality.fix.FixSupport;
import com.leadboard.quality.fix.dto.FixChange;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.sync.JiraIssueEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * SUBTASK_DONE_NO_TIME_LOGGED: a Done subtask has no logged time. Fix logs work equal to the
 * original estimate. Not applicable when the subtask has no estimate (fix the estimate first).
 */
@Component
public class SubtaskDoneNoTimeLoggedFixHandler implements FixHandler {

    private final FixSupport support;

    public SubtaskDoneNoTimeLoggedFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.SUBTASK_DONE_NO_TIME_LOGGED;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "WORKLOG",
                "Log work equal to the estimate").authMode(support.authMode());
        Long estimate = issue.getOriginalEstimateSeconds();
        if (estimate == null || estimate == 0) {
            return b.notApplicable("Subtask has no estimate — set an estimate first (SUBTASK_NO_ESTIMATE).").build();
        }
        String hours = formatHours(estimate);
        return b.changes(List.of(FixChange.jira(
                issue.getIssueKey(), issue.getSummary(), "Time logged", "0h", hours))).build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        Long estimate = issue.getOriginalEstimateSeconds();
        if (estimate == null || estimate == 0) {
            throw new IllegalArgumentException("Subtask has no estimate — cannot log matching time.");
        }
        support.jiraWrite().logWorkWithFallback(issue.getIssueKey(), estimate.intValue(), LocalDate.now());
        return FixResult.ok("Logged " + formatHours(estimate) + " on " + issue.getIssueKey(),
                List.of(issue.getIssueKey()));
    }

    private String formatHours(long seconds) {
        double h = seconds / 3600.0;
        return (h == Math.floor(h) ? String.valueOf((long) h) : String.format("%.1f", h)) + "h";
    }
}

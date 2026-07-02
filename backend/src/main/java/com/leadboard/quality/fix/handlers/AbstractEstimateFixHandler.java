package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.fix.FixHandler;
import com.leadboard.quality.fix.FixSupport;
import com.leadboard.quality.fix.dto.FixChange;
import com.leadboard.quality.fix.dto.FixInput;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.sync.JiraIssueEntity;

import java.util.List;
import java.util.Map;

/**
 * Shared base for the two "set the subtask original estimate" fixes (SUBTASK_NO_ESTIMATE and
 * SUBTASK_WORK_NO_ESTIMATE): a number-of-hours input written back via
 * {@code JiraClient.updateEstimate}.
 */
public abstract class AbstractEstimateFixHandler implements FixHandler {

    protected final FixSupport support;

    protected AbstractEstimateFixHandler(FixSupport support) {
        this.support = support;
    }

    /** Default hours pre-filled in the input (subclass-specific). */
    protected abstract double defaultHours(JiraIssueEntity issue);

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        double defaultHours = defaultHours(issue);
        return FixPreview.builder(issue.getIssueKey(), rule(), "ESTIMATE", "Set original estimate")
                .authMode(support.authMode())
                .inputs(List.of(FixInput.number("hours", "Estimate (hours)", true, defaultHours, 0.5, 0.5)))
                .changes(List.of(FixChange.jira(issue.getIssueKey(), issue.getSummary(), issue.getIssueType(),
                        "Original estimate", "—", "(entered hours)")))
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        Double hours = FixSupport.doubleParam(params, "hours");
        if (hours == null || hours <= 0) {
            throw new IllegalArgumentException("hours must be a positive number");
        }
        int seconds = (int) Math.round(hours * 3600);
        support.jira().updateEstimate(issue.getIssueKey(), seconds);
        return FixResult.ok("Set estimate of " + hours + "h on " + issue.getIssueKey(),
                List.of(issue.getIssueKey()));
    }
}

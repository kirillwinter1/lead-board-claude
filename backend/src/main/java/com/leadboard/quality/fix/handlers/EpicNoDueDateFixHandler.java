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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** EPIC_NO_DUE_DATE: epic without a due date. Fix sets the due date (date input). */
@Component
public class EpicNoDueDateFixHandler implements FixHandler {

    private final FixSupport support;

    public EpicNoDueDateFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.EPIC_NO_DUE_DATE;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        return FixPreview.builder(issue.getIssueKey(), rule(), "DUE_DATE", "Set a due date")
                .authMode(support.authMode())
                .inputs(List.of(FixInput.date("dueDate", "Due date", true, null)))
                .changes(List.of(FixChange.jira(issue.getIssueKey(), issue.getSummary(),
                        "Due date", "—", "(selected date)")))
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        LocalDate dueDate = FixSupport.dateParam(params, "dueDate");
        if (dueDate == null) {
            throw new IllegalArgumentException("dueDate is required");
        }
        support.jira().updateDueDate(issue.getIssueKey(), dueDate);
        return FixResult.ok("Set due date " + dueDate + " on " + issue.getIssueKey(),
                List.of(issue.getIssueKey()));
    }
}

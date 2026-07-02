package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.FixSupport;
import com.leadboard.sync.JiraIssueEntity;
import org.springframework.stereotype.Component;

/**
 * SUBTASK_WORK_NO_ESTIMATE: subtask with logged time but no estimate. Fix sets an estimate,
 * defaulting to the already-logged hours.
 */
@Component
public class SubtaskWorkNoEstimateFixHandler extends AbstractEstimateFixHandler {

    public SubtaskWorkNoEstimateFixHandler(FixSupport support) {
        super(support);
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.SUBTASK_WORK_NO_ESTIMATE;
    }

    @Override
    protected double defaultHours(JiraIssueEntity issue) {
        Long spent = issue.getTimeSpentSeconds();
        if (spent != null && spent > 0) {
            return Math.round((spent / 3600.0) * 2) / 2.0; // round to nearest half hour
        }
        return 1.0;
    }
}

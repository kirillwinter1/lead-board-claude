package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.FixSupport;
import com.leadboard.sync.JiraIssueEntity;
import org.springframework.stereotype.Component;

/** SUBTASK_NO_ESTIMATE: subtask without an original estimate. Fix sets one (in hours). */
@Component
public class SubtaskNoEstimateFixHandler extends AbstractEstimateFixHandler {

    public SubtaskNoEstimateFixHandler(FixSupport support) {
        super(support);
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.SUBTASK_NO_ESTIMATE;
    }

    @Override
    protected double defaultHours(JiraIssueEntity issue) {
        return 1.0;
    }
}

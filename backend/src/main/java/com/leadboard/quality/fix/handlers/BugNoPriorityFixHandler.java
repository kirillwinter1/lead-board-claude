package com.leadboard.quality.fix.handlers;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.FixHandler;
import com.leadboard.quality.fix.FixSupport;
import com.leadboard.quality.fix.dto.FixChange;
import com.leadboard.quality.fix.dto.FixInput;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.sync.JiraIssueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** BUG_NO_PRIORITY: bug without a priority. Fix sets one from the Jira priority list. */
@Component
public class BugNoPriorityFixHandler implements FixHandler {

    private static final Logger log = LoggerFactory.getLogger(BugNoPriorityFixHandler.class);

    // Fallback when the Jira priority list can't be fetched (offline / no perms).
    private static final List<String> DEFAULT_PRIORITIES = List.of("Highest", "High", "Medium", "Low", "Lowest");

    private final FixSupport support;

    public BugNoPriorityFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.BUG_NO_PRIORITY;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        List<FixInput.Option> options = priorityNames().stream()
                .map(n -> new FixInput.Option(n, n))
                .toList();
        return FixPreview.builder(issue.getIssueKey(), rule(), "PRIORITY", "Set a priority")
                .authMode(support.authMode())
                .inputs(List.of(FixInput.select("priority", "Priority", true, options, null)))
                .changes(List.of(FixChange.jira(issue.getIssueKey(), issue.getSummary(), issue.getIssueType(),
                        "Priority", "—", "(selected priority)")))
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        String priority = FixSupport.stringParam(params, "priority");
        if (priority == null || priority.isBlank()) {
            throw new IllegalArgumentException("priority is required");
        }
        if (!priorityNames().contains(priority)) {
            throw new IllegalArgumentException("Unknown priority: " + priority);
        }
        support.jira().updatePriority(issue.getIssueKey(), priority);
        return FixResult.ok("Set priority " + priority + " on " + issue.getIssueKey(),
                List.of(issue.getIssueKey()));
    }

    private List<String> priorityNames() {
        try {
            List<String> names = support.metadata().getPriorities().stream()
                    .map(p -> (String) p.get("name"))
                    .filter(n -> n != null && !n.isBlank())
                    .toList();
            if (!names.isEmpty()) return names;
        } catch (Exception e) {
            log.warn("Failed to fetch Jira priorities, falling back to defaults: {}", e.getMessage());
        }
        return DEFAULT_PRIORITIES;
    }
}

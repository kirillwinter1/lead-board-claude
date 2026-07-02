package com.leadboard.quality.fix.handlers;

import com.leadboard.jira.JiraWorklogResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TIME_LOGGED_NOT_IN_SUBTASK (risky): time is logged directly on a story/bug/epic instead of a
 * subtask. Fix moves every worklog to a chosen subtask: for each entry it first re-creates the
 * worklog on the target (preserving the original started timestamp), then deletes it from the
 * source. Add-before-delete means a mid-failure can leave a duplicate but never lose time.
 *
 * <p>Worklog authorship is rewritten to the acting account (OAuth user or service account) — the
 * preview warns about this. A 403 on delete (no permission to remove others' worklogs) surfaces
 * honestly in the result.</p>
 */
@Component
public class TimeLoggedNotInSubtaskFixHandler implements FixHandler {

    private static final Logger log = LoggerFactory.getLogger(TimeLoggedNotInSubtaskFixHandler.class);

    private final FixSupport support;

    public TimeLoggedNotInSubtaskFixHandler(FixSupport support) {
        this.support = support;
    }

    @Override
    public DataQualityRule rule() {
        return DataQualityRule.TIME_LOGGED_NOT_IN_SUBTASK;
    }

    @Override
    public FixPreview preview(JiraIssueEntity issue) {
        FixPreview.Builder b = FixPreview.builder(issue.getIssueKey(), rule(), "WORKLOG_MOVE",
                "Move logged time to a subtask").authMode(support.authMode());

        List<FixInput.Option> targets = subtaskOptions(issue);
        if (targets.isEmpty()) {
            return b.notApplicable("No subtask is available to move the worklog to — create one first.").build();
        }

        List<JiraWorklogResponse.WorklogEntry> worklogs;
        try {
            worklogs = support.jira().fetchIssueWorklogs(issue.getIssueKey());
        } catch (Exception e) {
            log.warn("Failed to fetch worklogs for {}: {}", issue.getIssueKey(), e.getMessage());
            return b.notApplicable("Could not read worklogs from Jira.").build();
        }
        if (worklogs.isEmpty()) {
            return b.notApplicable("No worklog entries found on this issue.").build();
        }

        List<String> affected = worklogs.stream().map(this::describe).toList();
        List<FixChange> changes = List.of(FixChange.jira(issue.getIssueKey(), issue.getSummary(),
                "Worklogs", worklogs.size() + " entr(ies) on " + issue.getIssueKey(), "→ selected subtask"));

        return b
                .risky("Each worklog will be re-created on the subtask and removed from " + issue.getIssueKey()
                        + ". Worklog authorship will be rewritten to the account performing the fix, and dates "
                        + "are preserved. Deleting worklogs of other users may require extra Jira permissions.")
                .inputs(List.of(FixInput.select("targetSubtaskKey", "Target subtask", true, targets, null)))
                .changes(changes)
                .affectedIssues(affected)
                .build();
    }

    @Override
    public FixResult apply(JiraIssueEntity issue, String choiceId, Map<String, Object> params) {
        String targetKey = FixSupport.stringParam(params, "targetSubtaskKey");
        if (targetKey == null || targetKey.isBlank()) {
            throw new IllegalArgumentException("targetSubtaskKey is required");
        }
        // Validate target is really a subtask of this issue.
        boolean validTarget = subtaskOptions(issue).stream().anyMatch(o -> o.value().equals(targetKey));
        if (!validTarget) {
            throw new IllegalArgumentException("Target subtask is not a subtask of " + issue.getIssueKey());
        }

        List<JiraWorklogResponse.WorklogEntry> worklogs = support.jira().fetchIssueWorklogs(issue.getIssueKey());
        if (worklogs.isEmpty()) {
            throw new IllegalArgumentException("No worklog entries to move.");
        }

        int moved = 0;
        List<String> failures = new ArrayList<>();
        for (JiraWorklogResponse.WorklogEntry w : worklogs) {
            try {
                // Add-first (preserving original started), then delete from source.
                support.jira().addWorklogAt(targetKey, w.getTimeSpentSeconds(), w.getStarted());
                support.jira().deleteWorklog(issue.getIssueKey(), w.getId());
                moved++;
            } catch (Exception e) {
                log.warn("Failed to move worklog {} from {} to {}: {}",
                        w.getId(), issue.getIssueKey(), targetKey, e.getMessage());
                failures.add(w.getId() + " (" + e.getMessage() + ")");
            }
        }

        // Best-effort audit comments on both issues (OAuth-only; ignore if unavailable).
        safeComment(targetKey, "Lead Board moved " + moved + " worklog(s) here from " + issue.getIssueKey() + ".");
        safeComment(issue.getIssueKey(), "Lead Board moved " + moved + " worklog(s) to " + targetKey + ".");

        List<String> updated = List.of(issue.getIssueKey(), targetKey);
        String base = "Moved " + moved + " worklog(s) to " + targetKey;
        if (failures.isEmpty()) {
            return FixResult.ok(base + ".", updated);
        }
        return FixResult.partial(base + "; failed for: " + String.join(", ", failures), updated);
    }

    private List<FixInput.Option> subtaskOptions(JiraIssueEntity issue) {
        return support.issues().findByParentKey(issue.getIssueKey()).stream()
                .filter(JiraIssueEntity::isSubtask)
                .map(st -> new FixInput.Option(st.getIssueKey(), st.getIssueKey() + " · " + st.getSummary()))
                .toList();
    }

    private String describe(JiraWorklogResponse.WorklogEntry w) {
        String author = w.getAuthor() != null && w.getAuthor().getDisplayName() != null
                ? w.getAuthor().getDisplayName() : "unknown";
        double hours = w.getTimeSpentSeconds() / 3600.0;
        String date = w.getStarted() != null && w.getStarted().length() >= 10 ? w.getStarted().substring(0, 10) : "";
        return String.format("%s · %.1fh · %s", author, hours, date);
    }

    private void safeComment(String issueKey, String text) {
        try {
            support.jiraWrite().comment(issueKey, text);
        } catch (Exception e) {
            log.info("Skipped audit comment on {}: {}", issueKey, e.getMessage());
        }
    }
}

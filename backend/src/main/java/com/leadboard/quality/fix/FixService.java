package com.leadboard.quality.fix;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixRequest;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.sync.JiraIssueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates Data Quality auto-fixes (F84): resolves the {@link FixHandler} for a rule,
 * re-checks the violation against fresh DB state around preview/apply, and re-syncs the
 * affected issues from Jira after a successful (non-local) fix.
 */
@Service
public class FixService {

    private static final Logger log = LoggerFactory.getLogger(FixService.class);

    private final Map<DataQualityRule, FixHandler> registry = new EnumMap<>(DataQualityRule.class);
    private final Set<DataQualityRule> fixable;
    private final FixSupport support;

    public FixService(List<FixHandler> handlers, FixSupport support) {
        this.support = support;
        for (FixHandler h : handlers) {
            FixHandler prev = registry.put(h.rule(), h);
            if (prev != null) {
                throw new IllegalStateException("Duplicate FixHandler for rule " + h.rule()
                        + ": " + prev.getClass() + " and " + h.getClass());
            }
        }
        // Fixable = every rule with a handler, plus RICE_MISSING_ASSESSMENT which is fixed
        // purely on the frontend via the RICE form (no backend handler / preview).
        EnumSet<DataQualityRule> f = EnumSet.noneOf(DataQualityRule.class);
        f.addAll(registry.keySet());
        f.add(DataQualityRule.RICE_MISSING_ASSESSMENT);
        this.fixable = Set.copyOf(f);
        log.info("FixService initialized with {} handlers, {} fixable rules", registry.size(), fixable.size());
    }

    /** Whether a rule can be auto-fixed (used to set {@code fixable} on each violation DTO). */
    public boolean isFixable(DataQualityRule rule) {
        return rule != null && fixable.contains(rule);
    }

    /** Preview a fix. Throws {@link IllegalArgumentException} (400) for unknown issue/rule. */
    public FixPreview preview(String issueKey, DataQualityRule rule) {
        JiraIssueEntity issue = support.load(issueKey)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueKey));

        // RICE has no backend handler — the frontend opens the RICE form directly.
        if (rule == DataQualityRule.RICE_MISSING_ASSESSMENT && !registry.containsKey(rule)) {
            return FixPreview.builder(issueKey, rule, "RICE_FORM", "Add RICE assessment")
                    .authMode("LOCAL")
                    .build();
        }

        FixHandler handler = registry.get(rule);
        if (handler == null) {
            throw new IllegalArgumentException("Rule is not fixable: " + rule);
        }

        FixPreview preview = handler.preview(issue);
        if (!support.stillViolated(issue, rule)) {
            return preview.notApplicable("This violation is no longer present — it may have been fixed already.");
        }
        return preview;
    }

    /**
     * Apply a fix. Throws {@link IllegalArgumentException} (400) for unknown issue/rule,
     * {@link FixConflictException} (409) when the violation already vanished.
     */
    public FixResult apply(FixRequest request) {
        if (request == null || request.issueKey() == null || request.rule() == null) {
            throw new IllegalArgumentException("issueKey and rule are required");
        }
        DataQualityRule rule = DataQualityRule.valueOf(request.rule()); // 400 on bad name

        JiraIssueEntity issue = support.load(request.issueKey())
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + request.issueKey()));

        FixHandler handler = registry.get(rule);
        if (handler == null) {
            throw new IllegalArgumentException("Rule is not fixable via this endpoint: " + rule);
        }

        // Re-check on fresh state: the violation must still exist, otherwise 409.
        if (!support.stillViolated(issue, rule)) {
            throw new FixConflictException(
                    "This violation is no longer present — it may have been fixed already.");
        }

        FixResult result = handler.apply(issue, request.choiceId(), request.paramsOrEmpty());

        // Jira is the source of truth: pull the changed issues back so the local DB catches up.
        // Local-only fixes (EPIC_NO_TEAM, TEAM_FIELD_UNMAPPED) skip the round-trip.
        if (!handler.local() && result.updatedIssues() != null) {
            for (String key : result.updatedIssues()) {
                try {
                    support.sync().syncSingleIssue(key);
                } catch (Exception e) {
                    log.warn("Post-fix sync failed for {}: {}", key, e.getMessage());
                }
            }
        }
        return result;
    }
}

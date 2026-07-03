package com.leadboard.quality.fix;

import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.service.JiraMetadataService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraTransition;
import com.leadboard.jira.JiraWriteService;
import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.DataQualityService;
import com.leadboard.quality.DataQualityViolation;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.sync.SyncService;
import com.leadboard.quality.fix.dto.FixChange;
import com.leadboard.quality.fix.dto.FixInput;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamMemberRepository;
import com.leadboard.team.TeamRepository;
import com.leadboard.team.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared plumbing for {@link FixHandler}s: Jira write facade (with fallback), workflow-config
 * status resolution, repositories, team helpers, and the single-rule re-check used by
 * {@link FixService} to keep preview/apply honest against fresh DB state.
 *
 * <p>No hardcoding of statuses/types/roles — target statuses always come from
 * {@link WorkflowConfigService#getFirstStatusNameForCategory}.</p>
 */
@Component
public class FixSupport {

    private static final Logger log = LoggerFactory.getLogger(FixSupport.class);

    private final JiraWriteService jiraWriteService;
    private final JiraClient jiraClient;
    private final WorkflowConfigService workflowConfigService;
    private final DataQualityService dataQualityService;
    private final JiraIssueRepository issueRepository;
    private final TeamMemberRepository memberRepository;
    private final TeamRepository teamRepository;
    private final TeamService teamService;
    private final SyncService syncService;
    private final JiraMetadataService jiraMetadataService;

    public FixSupport(JiraWriteService jiraWriteService, JiraClient jiraClient,
                      WorkflowConfigService workflowConfigService, DataQualityService dataQualityService,
                      JiraIssueRepository issueRepository, TeamMemberRepository memberRepository,
                      TeamRepository teamRepository, TeamService teamService, SyncService syncService,
                      JiraMetadataService jiraMetadataService) {
        this.jiraWriteService = jiraWriteService;
        this.jiraClient = jiraClient;
        this.workflowConfigService = workflowConfigService;
        this.dataQualityService = dataQualityService;
        this.issueRepository = issueRepository;
        this.memberRepository = memberRepository;
        this.teamRepository = teamRepository;
        this.teamService = teamService;
        this.syncService = syncService;
        this.jiraMetadataService = jiraMetadataService;
    }

    // ==================== Accessors ====================

    public JiraWriteService jiraWrite() { return jiraWriteService; }
    public JiraClient jira() { return jiraClient; }
    public WorkflowConfigService workflow() { return workflowConfigService; }
    public JiraIssueRepository issues() { return issueRepository; }
    public TeamRepository teams() { return teamRepository; }
    public TeamService teamService() { return teamService; }
    public JiraMetadataService metadata() { return jiraMetadataService; }
    public SyncService sync() { return syncService; }

    /** "OAUTH" when the current user has a token (writes attributed to them), else "BASIC". */
    public String authMode() {
        return jiraWriteService.hasUserCreds() ? "OAUTH" : "BASIC";
    }

    // ==================== Issue / hierarchy helpers ====================

    public Optional<JiraIssueEntity> load(String issueKey) {
        return issueRepository.findByIssueKey(issueKey);
    }

    public BoardCategory boardCategoryOf(JiraIssueEntity issue) {
        return workflowConfigService.categorizeIssueType(issue.getIssueType(), issue.getProjectKey());
    }

    public boolean isDone(JiraIssueEntity issue) {
        return workflowConfigService.isDone(issue.getStatus(), issue.getIssueType(), issue.getProjectKey());
    }

    /** Target status name mapped to a category for this issue's board category (never hardcoded). */
    public String targetStatusName(JiraIssueEntity issue, StatusCategory category) {
        BoardCategory boardCat = boardCategoryOf(issue);
        return workflowConfigService.getFirstStatusNameForCategory(category, boardCat);
    }

    /** Direct children (stories/bugs) of an epic that are not Done. */
    public List<JiraIssueEntity> openChildren(String epicKey) {
        return issueRepository.findByParentKey(epicKey).stream()
                .filter(c -> !isDone(c))
                .toList();
    }

    /** Subtasks of a story/bug that are not Done. */
    public List<JiraIssueEntity> openSubtasks(String storyKey) {
        return issueRepository.findByParentKey(storyKey).stream()
                .filter(s -> !isDone(s))
                .toList();
    }

    /** Open subtasks of several parents, grouped by parent key (one query, avoids N+1). */
    public Map<String, List<JiraIssueEntity>> openSubtasksByParent(List<String> parentKeys) {
        if (parentKeys.isEmpty()) return Map.of();
        return issueRepository.findByParentKeyIn(parentKeys).stream()
                .filter(s -> !isDone(s))
                .collect(java.util.stream.Collectors.groupingBy(JiraIssueEntity::getParentKey));
    }

    /** The epic a story/bug/subtask ultimately belongs to (walks up one or two levels). */
    public JiraIssueEntity resolveEpicOf(JiraIssueEntity issue) {
        if (issue == null || issue.getParentKey() == null) return null;
        JiraIssueEntity parent = issueRepository.findByIssueKey(issue.getParentKey()).orElse(null);
        if (parent == null) return null;
        BoardCategory parentCat = boardCategoryOf(parent);
        if (parentCat == BoardCategory.EPIC) return parent;
        // parent is a story/bug — go one more level up to the epic
        if (parent.getParentKey() != null) {
            return issueRepository.findByIssueKey(parent.getParentKey()).orElse(null);
        }
        return null;
    }

    /** A FixChange describing a Jira status transition of the given issue to {@code toName}. */
    public FixChange statusChange(JiraIssueEntity issue, String toName) {
        return FixChange.jira(issue.getIssueKey(), issue.getSummary(), issue.getIssueType(), "Status", issue.getStatus(), toName);
    }

    // ==================== Transition target statuses (F84 user choice) ====================

    /**
     * The statuses a transition fix can actually move {@code issue} to, within a target category,
     * plus the default (pipeline-first) option.
     *
     * @param names       ordered option list (may be empty when nothing is available)
     * @param defaultName the option to pre-select (pipeline-first), or null when {@code names} is empty
     */
    public record TargetStatusOptions(List<String> names, String defaultName) {
        public boolean isEmpty() { return names.isEmpty(); }
        public boolean contains(String name) { return name != null && names.contains(name); }
    }

    /**
     * Computes the selectable destination statuses for a transition fix on {@code issue}, filtered
     * to {@code category} (IN_PROGRESS / DONE). Primary source is the issue's live Jira transitions
     * (attributed to the current user via OAuth, else the BasicAuth service account); when that call
     * fails or yields nothing, falls back to the statuses mapped to {@code category} for the issue's
     * board category in workflow config. Options are ordered pipeline-first; the default is the
     * first option.
     */
    public TargetStatusOptions targetStatusOptions(JiraIssueEntity issue, StatusCategory category) {
        BoardCategory boardCat = boardCategoryOf(issue);
        List<String> live = liveTransitionTargets(issue, category);
        List<String> names = live.isEmpty()
                ? workflowConfigService.getStatusNamesForCategory(category, boardCat)
                : workflowConfigService.orderStatusNames(boardCat, live);
        String def = names.isEmpty() ? null : names.get(0);
        return new TargetStatusOptions(names, def);
    }

    /** Wraps status names as select {@link FixInput.Option}s (value == label == status name). */
    public static List<FixInput.Option> statusOptions(List<String> names) {
        return names.stream().map(n -> new FixInput.Option(n, n)).toList();
    }

    /**
     * Reads the user-selected {@code targetStatus} param, re-validates it against the options
     * recomputed server-side (same pattern as priority validation) and returns it. Throws
     * {@link IllegalArgumentException} when missing or not one of the current options.
     */
    public String requireTargetStatus(JiraIssueEntity issue, StatusCategory category, Map<String, Object> params) {
        String target = stringParam(params, "targetStatus");
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("targetStatus is required");
        }
        TargetStatusOptions options = targetStatusOptions(issue, category);
        if (!options.contains(target)) {
            throw new IllegalArgumentException("Invalid target status: " + target);
        }
        return target;
    }

    /**
     * Distinct target status names of {@code issue}'s live Jira transitions whose configured
     * category equals {@code category}. Returns an empty list (not an error) when the Jira call
     * fails — callers fall back to config-mapped statuses.
     */
    private List<String> liveTransitionTargets(JiraIssueEntity issue, StatusCategory category) {
        try {
            return jiraWriteService.listTransitionsWithFallback(issue.getIssueKey()).stream()
                    .map(t -> t.to() != null ? t.to().name() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .filter(name -> workflowConfigService.categorize(
                            name, issue.getIssueType(), issue.getProjectKey()) == category)
                    .toList();
        } catch (Exception e) {
            log.warn("Live Jira transitions unavailable for {} (category {}), using config fallback: {}",
                    issue.getIssueKey(), category, e.getMessage());
            return List.of();
        }
    }

    /** Active team members as select options (value = Jira account id, label = display name). */
    public List<FixInput.Option> memberOptions(Long teamId) {
        if (teamId == null) return List.of();
        return memberRepository.findByTeamIdAndActiveTrue(teamId).stream()
                .filter(m -> m.getJiraAccountId() != null)
                .map(m -> new FixInput.Option(
                        m.getJiraAccountId(),
                        m.getDisplayName() != null ? m.getDisplayName() : m.getJiraAccountId()))
                .toList();
    }

    /** True when the given account id is an active member of the team. */
    public boolean isActiveMember(Long teamId, String accountId) {
        return teamId != null && accountId != null
                && memberRepository.existsByJiraAccountIdAndTeamIdAndActiveTrue(accountId, teamId);
    }

    /** Active teams whose Jira team field value is blank (candidates for TEAM_FIELD_UNMAPPED). */
    public List<TeamEntity> teamsWithBlankJiraValue() {
        return teamRepository.findByActiveTrue().stream()
                .filter(t -> t.getJiraTeamValue() == null || t.getJiraTeamValue().isBlank())
                .toList();
    }

    // ==================== Single-rule re-check ====================

    /**
     * Re-run the data-quality checks relevant to this issue on fresh DB state and return the
     * violations. Used to confirm a fix is still applicable (preview) / still needed (apply).
     */
    public List<DataQualityViolation> runRule(JiraIssueEntity issue) {
        BoardCategory cat = boardCategoryOf(issue);
        if (cat == null) return List.of();
        return switch (cat) {
            case EPIC -> dataQualityService.checkEpic(issue, issueRepository.findByParentKey(issue.getIssueKey()));
            case STORY -> dataQualityService.checkStory(issue, parentOf(issue), issueRepository.findByParentKey(issue.getIssueKey()));
            case BUG -> dataQualityService.checkBug(issue, parentOf(issue), issueRepository.findByParentKey(issue.getIssueKey()));
            case SUBTASK -> {
                JiraIssueEntity story = parentOf(issue);
                JiraIssueEntity epic = story != null ? parentOf(story) : null;
                yield dataQualityService.checkSubtask(issue, story, epic);
            }
            default -> List.of();
        };
    }

    private JiraIssueEntity parentOf(JiraIssueEntity issue) {
        if (issue == null || issue.getParentKey() == null) return null;
        return issueRepository.findByIssueKey(issue.getParentKey()).orElse(null);
    }

    // ==================== Param parsing ====================

    public static String stringParam(Map<String, Object> params, String name) {
        Object v = params == null ? null : params.get(name);
        return v != null ? String.valueOf(v) : null;
    }

    public static Double doubleParam(Map<String, Object> params, String name) {
        Object v = params == null ? null : params.get(name);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate dateParam(Map<String, Object> params, String name) {
        String s = stringParam(params, name);
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date value for '" + name + "': " + s);
        }
    }

    /** True when {@code rule} still fires for {@code issue} on fresh state. */
    public boolean stillViolated(JiraIssueEntity issue, DataQualityRule rule) {
        return runRule(issue).stream().anyMatch(v -> v.rule() == rule);
    }
}

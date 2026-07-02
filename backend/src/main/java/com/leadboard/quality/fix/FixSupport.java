package com.leadboard.quality.fix;

import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.service.JiraMetadataService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
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
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import com.leadboard.team.TeamRepository;
import com.leadboard.team.TeamService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
        return FixChange.jira(issue.getIssueKey(), issue.getSummary(), "Status", issue.getStatus(), toName);
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

package com.leadboard.chat.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.auth.AppRole;
import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.board.BoardNode;
import com.leadboard.board.BoardResponse;
import com.leadboard.board.BoardService;
import com.leadboard.chat.embedding.EmbeddingService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.epic.EpicService;
import com.leadboard.epic.RoughEstimateRequestDto;
import com.leadboard.insight.InsightEngine;
import com.leadboard.jira.JiraWriteService;
import com.leadboard.matrix.MatrixService;
import com.leadboard.metrics.dto.BugMetricsResponse;
import com.leadboard.metrics.dto.TeamMetricsSummary;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.planning.ForecastService;
import com.leadboard.planning.QuarterlyPlanningService;
import com.leadboard.team.WorklogTimelineService;
import com.leadboard.metrics.service.BugMetricsService;
import com.leadboard.metrics.service.TeamMetricsService;
import com.leadboard.project.ProjectService;
import com.leadboard.quality.BugSlaService;
import com.leadboard.rice.RiceAssessmentService;
import com.leadboard.rice.dto.RiceRankingEntryDto;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ChatToolExecutor.class);

    private final JiraIssueRepository issueRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamMetricsService teamMetricsService;
    private final WorkflowConfigService workflowConfigService;
    private final AuthorizationService authorizationService;
    private final BoardService boardService;
    private final BugMetricsService bugMetricsService;
    private final ProjectService projectService;
    private final RiceAssessmentService riceAssessmentService;
    private final AbsenceService absenceService;
    private final BugSlaService bugSlaService;
    private final EmbeddingService embeddingService;
    private final InsightEngine insightEngine;
    private final JiraWriteService jiraWriteService;
    private final QuarterlyPlanningService quarterlyPlanningService;
    private final ForecastService forecastService;
    private final WorklogTimelineService worklogTimelineService;
    private final MatrixService matrixService;
    private final EpicService epicService;
    private final StatusChangelogRepository statusChangelogRepository;
    private final ObjectMapper objectMapper;

    public ChatToolExecutor(
            JiraIssueRepository issueRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamMetricsService teamMetricsService,
            WorkflowConfigService workflowConfigService,
            AuthorizationService authorizationService,
            BoardService boardService,
            BugMetricsService bugMetricsService,
            ProjectService projectService,
            RiceAssessmentService riceAssessmentService,
            AbsenceService absenceService,
            BugSlaService bugSlaService,
            EmbeddingService embeddingService,
            InsightEngine insightEngine,
            JiraWriteService jiraWriteService,
            QuarterlyPlanningService quarterlyPlanningService,
            ForecastService forecastService,
            WorklogTimelineService worklogTimelineService,
            MatrixService matrixService,
            EpicService epicService,
            StatusChangelogRepository statusChangelogRepository,
            ObjectMapper objectMapper
    ) {
        this.issueRepository = issueRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamMetricsService = teamMetricsService;
        this.workflowConfigService = workflowConfigService;
        this.authorizationService = authorizationService;
        this.boardService = boardService;
        this.bugMetricsService = bugMetricsService;
        this.projectService = projectService;
        this.riceAssessmentService = riceAssessmentService;
        this.absenceService = absenceService;
        this.bugSlaService = bugSlaService;
        this.embeddingService = embeddingService;
        this.insightEngine = insightEngine;
        this.jiraWriteService = jiraWriteService;
        this.quarterlyPlanningService = quarterlyPlanningService;
        this.forecastService = forecastService;
        this.worklogTimelineService = worklogTimelineService;
        this.matrixService = matrixService;
        this.epicService = epicService;
        this.statusChangelogRepository = statusChangelogRepository;
        this.objectMapper = objectMapper;
    }

    public String executeTool(String toolName, String argsJson) {
        try {
            JsonNode args = argsJson != null && !argsJson.isBlank()
                    ? objectMapper.readTree(argsJson)
                    : objectMapper.createObjectNode();

            return switch (toolName) {
                case "board_summary" -> boardSummary(args);
                case "team_list" -> teamList();
                case "team_metrics" -> teamMetrics(args);
                case "task_count" -> taskCount(args);
                case "task_search" -> taskSearch(args);
                case "data_quality_summary" -> dataQualitySummary(args);
                case "bug_metrics" -> bugMetrics(args);
                case "project_list" -> projectList();
                case "rice_ranking" -> riceRanking();
                case "member_absences" -> memberAbsences(args);
                case "bug_sla_settings" -> bugSlaSettings();
                case "task_details" -> taskDetails(args);
                case "team_members" -> teamMembers(args);
                case "epic_progress" -> epicProgress(args);
                case "team_readiness_briefing" -> teamReadinessBriefing(args);
                // --- F80 write tools (modify Jira; client must confirm before calling) ---
                case "transition_issue" -> transitionIssue(args);
                case "log_work" -> logWork(args);
                case "create_issue" -> createIssueTool(args);
                case "add_comment" -> addCommentTool(args);
                case "assign_issue" -> assignIssueTool(args);
                // --- F80 read: planning / forecast / load ---
                case "quarterly_capacity" -> quarterlyCapacity(args);
                case "quarterly_demand" -> quarterlyDemand(args);
                case "team_forecast" -> teamForecast(args);
                case "team_worklog_timeline" -> teamWorklogTimeline(args);
                case "my_open_tasks_with_worklog" -> openTasksWithWorklog(args);
                case "closed_tasks" -> closedTasks(args);
                // --- F80 write: board ---
                case "triage_matrix" -> triageMatrix(args);
                case "assign_epic_quarter" -> assignEpicQuarter(args);
                case "set_epic_boost" -> setEpicBoost(args);
                case "set_rough_estimate" -> setRoughEstimate(args);
                default -> toJson(Map.of("error", "Unknown tool: " + toolName));
            };
        } catch (Exception e) {
            log.error("Tool execution error for {}: {}", toolName, e.getMessage(), e);
            return toJson(Map.of("error", "Tool execution failed: " + e.getMessage()));
        }
    }

    private String boardSummary(JsonNode args) {
        Long teamId = getTeamIdParam(args);

        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }

        List<JiraIssueEntity> epics = fetchByCategoryScoped("EPIC", teamId);

        Map<String, Long> epicsByStatus = epics.stream()
                .collect(Collectors.groupingBy(
                        e -> categorizeStatus(e.getIssueType(), e.getStatus()),
                        Collectors.counting()
                ));

        List<String> epicKeys = epics.stream().map(JiraIssueEntity::getIssueKey).toList();
        List<JiraIssueEntity> stories = new ArrayList<>();
        if (!epicKeys.isEmpty()) {
            stories = issueRepository.findByParentKeyIn(epicKeys);
        }

        Map<String, Long> storiesByStatus = stories.stream()
                .collect(Collectors.groupingBy(
                        e -> categorizeStatus(e.getIssueType(), e.getStatus()),
                        Collectors.counting()
                ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEpics", epics.size());
        result.put("epicsByStatus", epicsByStatus);
        result.put("totalStories", stories.size());
        result.put("storiesByStatus", storiesByStatus);
        if (teamId != null) {
            result.put("teamId", teamId);
        }

        return toJson(result);
    }

    private String teamList() {
        List<TeamEntity> teams = teamRepository.findByActiveTrue();

        List<Map<String, Object>> teamData = teams.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("color", t.getColor());
            long memberCount = teamMemberRepository.findByTeamIdAndActiveTrue(t.getId()).size();
            m.put("memberCount", memberCount);
            return m;
        }).toList();

        return toJson(Map.of("teams", teamData, "totalTeams", teams.size()));
    }

    private String teamMetrics(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        if (teamId == null) {
            return toJson(Map.of("error", "teamId is required"));
        }

        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's metrics"));
        }

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);

        try {
            TeamMetricsSummary summary = teamMetricsService.getSummary(teamId, from, to, null, null);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("teamId", teamId);
            result.put("period", Map.of("from", from.toString(), "to", to.toString()));

            if (summary.throughput() != null) {
                result.put("throughput", Map.of(
                        "total", summary.throughput().total(),
                        "epics", summary.throughput().totalEpics(),
                        "stories", summary.throughput().totalStories(),
                        "subtasks", summary.throughput().totalSubtasks()
                ));
            }

            if (summary.leadTime() != null) {
                result.put("leadTime", Map.of(
                        "avgDays", summary.leadTime().avgDays(),
                        "medianDays", summary.leadTime().medianDays()
                ));
            }

            if (summary.cycleTime() != null) {
                result.put("cycleTime", Map.of(
                        "avgDays", summary.cycleTime().avgDays(),
                        "medianDays", summary.cycleTime().medianDays()
                ));
            }

            if (summary.byAssignee() != null) {
                result.put("assigneeCount", summary.byAssignee().size());
            }

            return toJson(result);
        } catch (Exception e) {
            log.error("Failed to get team metrics for team {}: {}", teamId, e.getMessage());
            return toJson(Map.of("error", "Failed to fetch metrics: " + e.getMessage()));
        }
    }

    private String taskCount(JsonNode args) {
        String statusFilter = args.has("status") ? args.get("status").asText() : null;
        Long teamId = getTeamIdParam(args);
        String typeFilter = args.has("type") ? args.get("type").asText() : null;

        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }

        List<JiraIssueEntity> issues;
        if (typeFilter != null) {
            issues = fetchByCategoryScoped(typeFilter, teamId);
        } else {
            // All main types
            issues = new ArrayList<>();
            issues.addAll(fetchByCategoryScoped("EPIC", teamId));
            issues.addAll(fetchByCategoryScoped("STORY", teamId));
            issues.addAll(fetchByCategoryScoped("BUG", teamId));
        }

        // Filter by status category if specified
        if (statusFilter != null) {
            issues = issues.stream()
                    .filter(e -> statusFilter.equals(categorizeStatus(e.getIssueType(), e.getStatus())))
                    .toList();
        }

        Map<String, Long> byCategory = issues.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getBoardCategory() != null ? e.getBoardCategory() : "OTHER",
                        Collectors.counting()
                ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", issues.size());
        result.put("byCategory", byCategory);
        if (statusFilter != null) result.put("statusFilter", statusFilter);
        if (teamId != null) result.put("teamId", teamId);
        if (typeFilter != null) result.put("typeFilter", typeFilter);

        return toJson(result);
    }

    private String taskSearch(JsonNode args) {
        String statusFilter = args.has("status") ? args.get("status").asText() : null;
        Long teamId = getTeamIdParam(args);
        String typeFilter = args.has("type") ? args.get("type").asText() : null;
        String query = args.has("query") ? args.get("query").asText() : null;

        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }

        // Try semantic search first if embedding is enabled and query is present
        List<JiraIssueEntity> issues = null;
        boolean usedSemantic = false;

        if (query != null && !query.isBlank()) {
            List<JiraIssueEntity> semanticResults = semanticSearchScoped(query, teamId);
            if (!semanticResults.isEmpty()) {
                issues = new ArrayList<>(semanticResults);
                usedSemantic = true;

                // Apply type filter on semantic results
                if (typeFilter != null) {
                    issues = issues.stream()
                            .filter(e -> typeFilter.equals(e.getBoardCategory()))
                            .toList();
                }

                // Apply status filter on semantic results
                if (statusFilter != null) {
                    issues = issues.stream()
                            .filter(e -> statusFilter.equals(categorizeStatus(e.getIssueType(), e.getStatus())))
                            .toList();
                }
            }
        }

        // Fallback to substring match
        if (issues == null || issues.isEmpty()) {
            if (typeFilter != null) {
                issues = fetchByCategoryScoped(typeFilter, teamId);
            } else {
                issues = new ArrayList<>();
                issues.addAll(fetchByCategoryScoped("EPIC", teamId));
                issues.addAll(fetchByCategoryScoped("STORY", teamId));
                issues.addAll(fetchByCategoryScoped("BUG", teamId));
            }

            // Filter by status category
            if (statusFilter != null) {
                issues = issues.stream()
                        .filter(e -> statusFilter.equals(categorizeStatus(e.getIssueType(), e.getStatus())))
                        .toList();
            }

            // Filter by query (substring match with ё→е normalization for Russian)
            if (query != null && !query.isBlank()) {
                String q = normalizeRussian(query.toLowerCase());
                issues = issues.stream()
                        .filter(e -> (e.getIssueKey() != null && e.getIssueKey().toLowerCase().contains(q))
                                || (e.getSummary() != null && normalizeRussian(e.getSummary().toLowerCase()).contains(q)))
                        .toList();
            }

            usedSemantic = false;
        }

        // Limit to 20 results
        List<Map<String, Object>> results = issues.stream()
                .limit(20)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", e.getIssueKey());
                    m.put("title", e.getSummary());
                    m.put("status", e.getStatus());
                    m.put("type", e.getIssueType());
                    m.put("category", e.getBoardCategory());
                    if (e.getAssigneeDisplayName() != null) m.put("assignee", e.getAssigneeDisplayName());
                    if (e.getTeamId() != null) m.put("teamId", e.getTeamId());
                    return m;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalFound", issues.size());
        result.put("showing", results.size());
        result.put("searchMode", usedSemantic ? "semantic" : "substring");
        result.put("tasks", results);
        return toJson(result);
    }

    private String dataQualitySummary(JsonNode args) {
        Long teamId = getTeamIdParam(args);

        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }

        // Reuse the same logic as DataQualityController but return a simplified summary
        List<JiraIssueEntity> epics = fetchByCategoryScoped("EPIC", teamId);

        List<String> epicKeys = epics.stream().map(JiraIssueEntity::getIssueKey).toList();
        List<JiraIssueEntity> stories = new ArrayList<>();
        if (!epicKeys.isEmpty()) {
            stories = issueRepository.findByParentKeyIn(epicKeys);
        }

        // Simplified quality checks based on common patterns
        int epicsWithoutTeam = (int) epics.stream().filter(e -> e.getTeamId() == null).count();
        int epicsOverdue = (int) epics.stream()
                .filter(e -> e.getDueDate() != null
                        && e.getDueDate().isBefore(LocalDate.now())
                        && !workflowConfigService.isDone(e.getStatus(), e.getIssueType()))
                .count();
        int epicsNoDueDate = (int) epics.stream().filter(e -> e.getDueDate() == null).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEpicsChecked", epics.size());
        result.put("epicsWithoutTeam", epicsWithoutTeam);
        result.put("epicsOverdue", epicsOverdue);
        result.put("epicsNoDueDate", epicsNoDueDate);
        result.put("totalStoriesChecked", stories.size());
        if (teamId != null) result.put("teamId", teamId);

        return toJson(result);
    }

    private String bugMetrics(JsonNode args) {
        Long teamId = getTeamIdParam(args);

        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }

        try {
            BugMetricsResponse metrics = fetchBugMetricsScoped(teamId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("openBugs", metrics.openBugs());
            result.put("resolvedBugs", metrics.resolvedBugs());
            result.put("staleBugs", metrics.staleBugs());
            result.put("avgResolutionHours", metrics.avgResolutionHours());
            result.put("slaCompliancePercent", metrics.slaCompliancePercent());

            List<Map<String, Object>> priorities = metrics.byPriority().stream().map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("priority", p.priority());
                m.put("open", p.openCount());
                m.put("resolved", p.resolvedCount());
                m.put("avgResolutionHours", p.avgResolutionHours());
                m.put("slaCompliancePercent", p.slaCompliancePercent());
                return m;
            }).toList();
            result.put("byPriority", priorities);

            List<Map<String, Object>> openList = metrics.openBugList().stream().limit(20).map(b -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", b.issueKey());
                m.put("summary", b.summary());
                m.put("priority", b.priority());
                m.put("status", b.status());
                m.put("ageDays", b.ageDays());
                m.put("slaBreach", b.slaBreach());
                return m;
            }).toList();
            result.put("openBugList", openList);

            if (teamId != null) result.put("teamId", teamId);
            return toJson(result);
        } catch (Exception e) {
            log.error("Failed to get bug metrics: {}", e.getMessage());
            return toJson(Map.of("error", "Failed to fetch bug metrics: " + e.getMessage()));
        }
    }

    private String projectList() {
        try {
            var projects = projectService.listProjects();

            // F81 security fix (SECURITY_AUDIT.md MEDIUM #6): project_list had no
            // team scoping at all. A project itself carries no teamId (it can span
            // several teams), so non-admin/PM visibility is derived from whether
            // any of the project's child epics belongs to one of the caller's teams.
            Set<Long> scope = scopeForOmittedTeamId();
            if (scope != null) {
                if (scope.isEmpty()) {
                    return toJson(Map.of("projects", List.of(), "totalProjects", 0));
                }
                projects = projects.stream()
                        .filter(p -> projectHasEpicInScope(p.issueKey(), scope))
                        .toList();
            }

            List<Map<String, Object>> projectData = projects.stream().map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", p.issueKey());
                m.put("summary", p.summary());
                m.put("status", p.status());
                m.put("progressPercent", p.progressPercent());
                m.put("childEpicCount", p.childEpicCount());
                m.put("completedEpicCount", p.completedEpicCount());
                if (p.expectedDone() != null) m.put("expectedDone", p.expectedDone().toString());
                if (p.riceScore() != null) m.put("riceScore", p.riceScore());
                if (p.assigneeDisplayName() != null) m.put("assignee", p.assigneeDisplayName());
                return m;
            }).toList();

            return toJson(Map.of("projects", projectData, "totalProjects", projects.size()));
        } catch (Exception e) {
            log.error("Failed to get project list: {}", e.getMessage());
            return toJson(Map.of("error", "Failed to fetch projects: " + e.getMessage()));
        }
    }

    private String riceRanking() {
        try {
            var ranking = riceAssessmentService.getRanking(null);

            // F81 security fix (SECURITY_AUDIT.md MEDIUM #6): rice_ranking had no
            // team scoping at all. Entries are epics (team on the issue itself) or
            // top-level projects (team derived from child epics, like project_list).
            Set<Long> scope = scopeForOmittedTeamId();
            if (scope != null) {
                if (scope.isEmpty()) {
                    return toJson(Map.of("ranking", List.of(), "totalRanked", 0, "showing", 0));
                }
                Set<String> keys = ranking.stream().map(RiceRankingEntryDto::issueKey).collect(Collectors.toSet());
                Map<String, JiraIssueEntity> issueMap = keys.isEmpty() ? Map.of()
                        : issueRepository.findByIssueKeyIn(new ArrayList<>(keys)).stream()
                                .collect(Collectors.toMap(JiraIssueEntity::getIssueKey, i -> i));
                ranking = ranking.stream()
                        .filter(r -> isRankingEntryInScope(issueMap.get(r.issueKey()), scope))
                        .toList();
            }

            List<Map<String, Object>> entries = ranking.stream().limit(20).map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", r.issueKey());
                m.put("summary", r.summary());
                m.put("status", r.status());
                m.put("riceScore", r.riceScore());
                m.put("normalizedScore", r.normalizedScore());
                m.put("reach", r.reach());
                m.put("impact", r.impact());
                m.put("confidence", r.confidence());
                m.put("effort", r.effort());
                return m;
            }).toList();

            return toJson(Map.of("ranking", entries, "totalRanked", ranking.size(), "showing", entries.size()));
        } catch (Exception e) {
            log.error("Failed to get RICE ranking: {}", e.getMessage());
            return toJson(Map.of("error", "Failed to fetch RICE ranking: " + e.getMessage()));
        }
    }

    private String memberAbsences(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        if (teamId == null) {
            return toJson(Map.of("error", "teamId is required"));
        }

        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }

        try {
            LocalDate from = LocalDate.now();
            LocalDate to = from.plusDays(90);
            var absences = absenceService.getAbsencesForTeam(teamId, from, to);

            List<Map<String, Object>> absenceData = absences.stream().map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("memberId", a.memberId());
                m.put("type", a.absenceType().name());
                m.put("startDate", a.startDate().toString());
                m.put("endDate", a.endDate().toString());
                if (a.comment() != null) m.put("comment", a.comment());
                return m;
            }).toList();

            return toJson(Map.of("absences", absenceData, "totalAbsences", absences.size(), "teamId", teamId,
                    "period", Map.of("from", from.toString(), "to", to.toString())));
        } catch (Exception e) {
            log.error("Failed to get member absences: {}", e.getMessage());
            return toJson(Map.of("error", "Failed to fetch absences: " + e.getMessage()));
        }
    }

    private String bugSlaSettings() {
        try {
            var configs = bugSlaService.getAllSlaConfigs();

            List<Map<String, Object>> slaData = configs.stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("priority", c.getPriority());
                m.put("maxResolutionHours", c.getMaxResolutionHours());
                return m;
            }).toList();

            return toJson(Map.of("slaConfigs", slaData, "totalConfigs", configs.size()));
        } catch (Exception e) {
            log.error("Failed to get bug SLA settings: {}", e.getMessage());
            return toJson(Map.of("error", "Failed to fetch SLA settings: " + e.getMessage()));
        }
    }

    private String taskDetails(JsonNode args) {
        String issueKey = args.has("issueKey") ? args.get("issueKey").asText() : null;
        if (issueKey == null || issueKey.isBlank()) {
            return toJson(Map.of("error", "issueKey is required"));
        }

        Optional<JiraIssueEntity> issueOpt = issueRepository.findByIssueKey(issueKey);
        if (issueOpt.isEmpty()) {
            return toJson(Map.of("error", "Task not found: " + issueKey));
        }

        JiraIssueEntity issue = issueOpt.get();

        // RBAC: check team access for the task's team
        if (issue.getTeamId() != null && !checkTeamAccess(issue.getTeamId())) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", issue.getIssueKey());
        result.put("summary", issue.getSummary());
        result.put("status", issue.getStatus());
        result.put("type", issue.getIssueType());
        result.put("category", issue.getBoardCategory());
        if (issue.getAssigneeDisplayName() != null) result.put("assignee", issue.getAssigneeDisplayName());
        if (issue.getTeamId() != null) result.put("teamId", issue.getTeamId());
        if (issue.getPriority() != null) result.put("priority", issue.getPriority());
        if (issue.getDueDate() != null) result.put("dueDate", issue.getDueDate().toString());
        if (issue.getParentKey() != null) result.put("parentKey", issue.getParentKey());
        result.put("statusCategory", categorizeStatus(issue.getIssueType(), issue.getStatus()));

        return toJson(result);
    }

    private String epicProgress(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        String query = args.has("query") ? args.get("query").asText() : null;

        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }

        try {
            List<Long> teamIds;
            if (teamId != null) {
                teamIds = List.of(teamId);
            } else {
                Set<Long> scope = scopeForOmittedTeamId();
                if (scope == null) {
                    teamIds = null;
                } else if (scope.isEmpty()) {
                    return toJson(Map.of("epics", List.of(), "totalEpics", 0, "showing", 0));
                } else {
                    teamIds = new ArrayList<>(scope);
                }
            }
            BoardResponse board = boardService.getBoard(query, null, teamIds, 0, 20, false);

            List<Map<String, Object>> epics = board.getItems().stream().map(epic -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", epic.getIssueKey());
                m.put("title", epic.getTitle());
                m.put("status", epic.getStatus());
                m.put("progress", epic.getProgress());
                if (epic.getTeamName() != null) m.put("team", epic.getTeamName());
                if (epic.getAutoScore() != null) m.put("autoScore", epic.getAutoScore());

                // Estimate in days (8h per day)
                if (epic.getEstimateSeconds() != null && epic.getEstimateSeconds() > 0) {
                    m.put("estimateDays", String.format("%.1f", epic.getEstimateSeconds() / 28800.0));
                    m.put("loggedDays", String.format("%.1f", (epic.getLoggedSeconds() != null ? epic.getLoggedSeconds() : 0) / 28800.0));
                }

                // Role-based progress
                if (epic.getRoleProgress() != null && !epic.getRoleProgress().isEmpty()) {
                    Map<String, Object> roles = new LinkedHashMap<>();
                    epic.getRoleProgress().forEach((roleCode, rm) -> {
                        Map<String, Object> roleData = new LinkedHashMap<>();
                        roleData.put("progress", rm.getProgress());
                        if (rm.getEstimateSeconds() > 0) {
                            roleData.put("estimateDays", String.format("%.1f", rm.getEstimateSeconds() / 28800.0));
                            roleData.put("loggedDays", String.format("%.1f", rm.getLoggedSeconds() / 28800.0));
                        }
                        roles.put(roleCode, roleData);
                    });
                    m.put("roleProgress", roles);
                }

                // Story-level summary: count + expected done dates
                if (!epic.getChildren().isEmpty()) {
                    m.put("storyCount", epic.getChildren().size());
                    long doneStories = epic.getChildren().stream()
                            .filter(s -> {
                                String cat = categorizeStatus(s.getIssueType(), s.getStatus());
                                return "DONE".equals(cat);
                            })
                            .count();
                    m.put("doneStories", doneStories);
                }

                return m;
            }).toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("epics", epics);
            result.put("totalEpics", board.getTotal());
            result.put("showing", epics.size());
            return toJson(result);
        } catch (Exception e) {
            log.error("Failed to get epic progress: {}", e.getMessage());
            return toJson(Map.of("error", "Failed to fetch epic progress: " + e.getMessage()));
        }
    }

    private String teamMembers(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        if (teamId == null) {
            return toJson(Map.of("error", "teamId is required"));
        }

        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }

        List<TeamMemberEntity> members = teamMemberRepository.findByTeamIdAndActiveTrue(teamId);

        List<Map<String, Object>> memberData = members.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("displayName", m.getDisplayName());
            map.put("role", m.getRole());
            map.put("grade", m.getGrade() != null ? m.getGrade().name() : null);
            return map;
        }).toList();

        return toJson(Map.of("members", memberData, "totalMembers", members.size(), "teamId", teamId));
    }

    private String teamReadinessBriefing(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }
        if (teamId != null) {
            return toJson(insightEngine.briefing(teamId));
        }
        Set<Long> scope = scopeForOmittedTeamId();
        if (scope == null) {
            return toJson(insightEngine.briefing(null));
        }
        if (scope.isEmpty()) {
            return toJson(Map.of("error", "No accessible teams"));
        }
        if (scope.size() == 1) {
            return toJson(insightEngine.briefing(scope.iterator().next()));
        }
        // Multiple team memberships: readiness lenses (RED/YELLOW/GREEN) are
        // per-team by design, so return one briefing per team rather than
        // guessing how to merge qualitative levels across unrelated teams.
        List<Object> readinessByTeam = scope.stream()
                .<Object>map(insightEngine::briefing)
                .toList();
        return toJson(Map.of("readinessByTeam", readinessByTeam));
    }

    // ===================== F80 write tools =====================

    private String strParam(JsonNode args, String name) {
        return args.has(name) && !args.get(name).isNull() ? args.get(name).asText() : null;
    }

    /**
     * Minimum bar for Jira-write tools (transition/log-work/create/comment/assign).
     * These have no dedicated REST endpoint of their own to mirror, so the rule
     * (SECURITY_AUDIT.md HIGH #3) is: must be authenticated, and read-only VIEWER
     * may never write. Returns {@code null} when allowed, or the error message
     * to surface to the caller otherwise.
     */
    private String requireJiraWriteAccess() {
        if (!authorizationService.isAuthenticated()) {
            return "Authentication required";
        }
        if (!authorizationService.canWriteJira()) {
            return "Forbidden: your role is read-only and cannot modify Jira";
        }
        return null;
    }

    /**
     * Role gate for planning/board write tools that DO have a REST equivalent
     * (MatrixController.triage, QuarterlyPlanningController.assignEpicToQuarter/
     * setEpicBoost, EpicController.updateRoughEstimate) — all of them require
     * {@code hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')}. Replicated here
     * so chat/MCP cannot bypass the role check the REST controller enforces
     * (SECURITY_AUDIT.md HIGH #3).
     */
    private String requirePlanningWriteAccess() {
        if (!authorizationService.isAuthenticated()) {
            return "Authentication required";
        }
        if (!authorizationService.hasAnyRole(AppRole.ADMIN, AppRole.PROJECT_MANAGER, AppRole.TEAM_LEAD)) {
            return "Forbidden: requires ADMIN, PROJECT_MANAGER, or TEAM_LEAD role";
        }
        return null;
    }

    private String transitionIssue(JsonNode args) {
        String denied = requireJiraWriteAccess();
        if (denied != null) {
            return toJson(Map.of("error", denied));
        }
        String issueKey = strParam(args, "issueKey");
        String target = strParam(args, "targetStatus");
        if (issueKey == null || target == null) {
            return toJson(Map.of("error", "issueKey and targetStatus are required"));
        }
        try {
            String newStatus = jiraWriteService.transition(issueKey, target);
            return toJson(Map.of("ok", true, "issueKey", issueKey, "newStatus", newStatus));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String logWork(JsonNode args) {
        String denied = requireJiraWriteAccess();
        if (denied != null) {
            return toJson(Map.of("error", denied));
        }
        String issueKey = strParam(args, "issueKey");
        if (issueKey == null || !args.has("hours")) {
            return toJson(Map.of("error", "issueKey and hours are required"));
        }
        double hours = args.get("hours").asDouble();
        if (hours <= 0) {
            return toJson(Map.of("error", "hours must be positive"));
        }
        LocalDate date = LocalDate.now();
        String dateStr = strParam(args, "date");
        if (dateStr != null) {
            try {
                date = LocalDate.parse(dateStr);
            } catch (Exception e) {
                return toJson(Map.of("error", "date must be ISO yyyy-MM-dd"));
            }
        }
        try {
            int seconds = (int) Math.round(hours * 3600);
            jiraWriteService.logWork(issueKey, seconds, date);
            return toJson(Map.of("ok", true, "issueKey", issueKey, "hours", hours, "date", date.toString()));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String createIssueTool(JsonNode args) {
        String denied = requireJiraWriteAccess();
        if (denied != null) {
            return toJson(Map.of("error", denied));
        }
        String kind = strParam(args, "kind");
        String summary = strParam(args, "summary");
        String parentEpicKey = strParam(args, "parentEpicKey");
        if (summary == null || summary.isBlank()) {
            return toJson(Map.of("error", "summary is required"));
        }
        try {
            String key = jiraWriteService.createIssue(kind != null ? kind : "story", summary, parentEpicKey);
            return toJson(Map.of("ok", true, "issueKey", key));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String addCommentTool(JsonNode args) {
        String denied = requireJiraWriteAccess();
        if (denied != null) {
            return toJson(Map.of("error", denied));
        }
        String issueKey = strParam(args, "issueKey");
        String text = strParam(args, "text");
        if (issueKey == null || text == null || text.isBlank()) {
            return toJson(Map.of("error", "issueKey and text are required"));
        }
        try {
            jiraWriteService.comment(issueKey, text);
            return toJson(Map.of("ok", true, "issueKey", issueKey));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String assignIssueTool(JsonNode args) {
        String denied = requireJiraWriteAccess();
        if (denied != null) {
            return toJson(Map.of("error", denied));
        }
        String issueKey = strParam(args, "issueKey");
        String accountId = strParam(args, "accountId");
        if (issueKey == null) {
            return toJson(Map.of("error", "issueKey is required"));
        }
        try {
            jiraWriteService.assign(issueKey, accountId);
            return toJson(Map.of("ok", true, "issueKey", issueKey, "accountId", accountId == null ? "unassigned" : accountId));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    // ===================== F80 read: planning / forecast / load =====================

    private String resolveQuarter(JsonNode args) {
        String q = strParam(args, "quarter");
        return q != null ? q : com.leadboard.planning.QuarterRange.currentQuarterLabel();
    }

    private String quarterlyCapacity(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        if (teamId == null) {
            return toJson(Map.of("error", "teamId is required"));
        }
        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }
        try {
            return toJson(quarterlyPlanningService.getTeamCapacity(teamId, resolveQuarter(args)));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String quarterlyDemand(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        if (teamId == null) {
            return toJson(Map.of("error", "teamId is required"));
        }
        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }
        try {
            return toJson(quarterlyPlanningService.getTeamDemand(teamId, resolveQuarter(args)));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String teamForecast(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        if (teamId == null) {
            return toJson(Map.of("error", "teamId is required"));
        }
        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }
        try {
            return toJson(forecastService.calculateForecast(teamId));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String teamWorklogTimeline(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        if (teamId == null) {
            return toJson(Map.of("error", "teamId is required"));
        }
        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);
        String fromStr = strParam(args, "from");
        String toStr = strParam(args, "to");
        try {
            if (fromStr != null) from = LocalDate.parse(fromStr);
            if (toStr != null) to = LocalDate.parse(toStr);
        } catch (Exception e) {
            return toJson(Map.of("error", "from/to must be ISO yyyy-MM-dd"));
        }
        try {
            return toJson(worklogTimelineService.getWorklogTimeline(teamId, from, to));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String openTasksWithWorklog(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }
        List<Map<String, Object>> tasks = fetchWithWorklogScoped(teamId).stream()
                .filter(i -> !isDoneIssue(i))
                .limit(30)
                .map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", i.getIssueKey());
                    m.put("title", i.getSummary());
                    m.put("status", i.getStatus());
                    m.put("type", i.getIssueType());
                    long secs = i.getTimeSpentSeconds() != null ? i.getTimeSpentSeconds() : 0L;
                    m.put("loggedHours", hours(secs));
                    Long rem = i.getRemainingEstimateSeconds();
                    Long orig = i.getOriginalEstimateSeconds();
                    m.put("originalEstimateHours", orig != null ? hours(orig) : null);
                    m.put("remainingEstimateHours", rem != null ? hours(rem) : null);
                    // hasEstimate=false => оценки не было (remaining NULL) — решение о закрытии за человеком.
                    boolean hasEstimate = rem != null;
                    m.put("hasEstimate", hasEstimate);
                    // Кандидат на закрытие: есть списание, есть оценка, остаток == 0.
                    m.put("readyToClose", secs > 0 && hasEstimate && rem == 0L);
                    if (i.getAssigneeDisplayName() != null) m.put("assignee", i.getAssigneeDisplayName());
                    return m;
                })
                .toList();
        return toJson(Map.of("tasks", tasks, "count", tasks.size()));
    }

    private String closedTasks(JsonNode args) {
        Long teamId = getTeamIdParam(args);
        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }
        boolean mineOnly = args.has("mineOnly") && args.get("mineOnly").asBoolean();

        java.time.OffsetDateTime to = java.time.OffsetDateTime.now();
        java.time.OffsetDateTime from = to.minusDays(7);
        try {
            String fromStr = strParam(args, "from");
            String toStr = strParam(args, "to");
            java.time.ZoneOffset off = to.getOffset();
            if (fromStr != null) from = LocalDate.parse(fromStr).atStartOfDay().atOffset(off);
            if (toStr != null) to = LocalDate.parse(toStr).plusDays(1).atStartOfDay().atOffset(off);
        } catch (Exception e) {
            return toJson(Map.of("error", "from/to must be ISO yyyy-MM-dd"));
        }

        String myAccountId = null;
        if (mineOnly) {
            LeadBoardAuthentication auth = authorizationService.getCurrentAuth();
            myAccountId = auth != null ? auth.getAtlassianAccountId() : null;
        }

        List<JiraIssueEntity> closed = fetchClosedBetweenScoped(from, to, teamId);
        List<Map<String, Object>> tasks = new ArrayList<>();
        int total = 0;
        for (JiraIssueEntity i : closed) {
            // closedBy = автор ПОСЛЕДНЕГО перехода в статус КАТЕГОРИИ Done (не по точному имени статуса:
            // текущий status может быть «Готово», а changelog-переход — в «Done»).
            String closedBy = statusChangelogRepository
                    .findByIssueKeyOrderByTransitionedAtDesc(i.getIssueKey()).stream()
                    .filter(c -> {
                        try {
                            return workflowConfigService.isDone(c.getToStatus(), i.getIssueType());
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .map(com.leadboard.metrics.entity.StatusChangelogEntity::getAuthorAccountId)
                    .filter(a -> a != null)
                    .findFirst()
                    .orElse(null);
            if (mineOnly && (myAccountId == null || !myAccountId.equals(closedBy))) {
                continue;
            }
            total++;
            if (tasks.size() < 50) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", i.getIssueKey());
                m.put("title", i.getSummary());
                m.put("type", i.getIssueType());
                m.put("resolvedAt", i.getDoneAt() != null ? i.getDoneAt().toString() : null);
                m.put("closedBy", closedBy);
                if (i.getAssigneeDisplayName() != null) m.put("assignee", i.getAssigneeDisplayName());
                tasks.add(m);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", total);
        result.put("showing", tasks.size());
        result.put("mineOnly", mineOnly);
        result.put("period", Map.of("from", from.toLocalDate().toString(), "to", to.toLocalDate().toString()));
        result.put("tasks", tasks);
        return toJson(result);
    }

    private double hours(long seconds) {
        return Math.round(seconds / 3600.0 * 10) / 10.0;
    }

    private boolean isDoneIssue(JiraIssueEntity i) {
        try {
            return workflowConfigService.isDone(i.getStatus(), i.getIssueType());
        } catch (Exception e) {
            return false;
        }
    }

    // ===================== F80 write: board =====================

    private String triageMatrix(JsonNode args) {
        String denied = requirePlanningWriteAccess();
        if (denied != null) {
            return toJson(Map.of("error", denied));
        }
        String issueKey = strParam(args, "issueKey");
        String quadrant = strParam(args, "quadrant");
        if (issueKey == null || quadrant == null) {
            return toJson(Map.of("error", "issueKey and quadrant are required"));
        }
        try {
            return toJson(matrixService.triage(issueKey, quadrant));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String assignEpicQuarter(JsonNode args) {
        String denied = requirePlanningWriteAccess();
        if (denied != null) {
            return toJson(Map.of("error", denied));
        }
        String epicKey = strParam(args, "epicKey");
        String quarter = strParam(args, "quarter");
        if (epicKey == null) {
            return toJson(Map.of("error", "epicKey is required"));
        }
        try {
            return toJson(quarterlyPlanningService.assignEpicToQuarter(epicKey, quarter));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String setEpicBoost(JsonNode args) {
        String denied = requirePlanningWriteAccess();
        if (denied != null) {
            return toJson(Map.of("error", denied));
        }
        String epicKey = strParam(args, "epicKey");
        if (epicKey == null || !args.has("boost")) {
            return toJson(Map.of("error", "epicKey and boost are required"));
        }
        try {
            return toJson(quarterlyPlanningService.setEpicBoost(epicKey, args.get("boost").asInt()));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String setRoughEstimate(JsonNode args) {
        String denied = requirePlanningWriteAccess();
        if (denied != null) {
            return toJson(Map.of("error", denied));
        }
        String epicKey = strParam(args, "epicKey");
        String role = strParam(args, "role");
        if (epicKey == null || role == null || !args.has("days")) {
            return toJson(Map.of("error", "epicKey, role and days are required"));
        }
        LeadBoardAuthentication auth = authorizationService.getCurrentAuth();
        String updatedBy = auth != null ? auth.getAtlassianAccountId() : "mcp";
        try {
            BigDecimal days = new BigDecimal(args.get("days").asText());
            return toJson(epicService.updateRoughEstimate(epicKey, role, new RoughEstimateRequestDto(days, updatedBy)));
        } catch (Exception e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    private String categorizeStatus(String issueType, String status) {
        try {
            StatusCategory category = workflowConfigService.categorize(status, issueType);
            return category != null ? category.name() : "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private Long getTeamIdParam(JsonNode args) {
        if (args.has("teamId") && !args.get("teamId").isNull()) {
            return args.get("teamId").asLong();
        }
        return null;
    }

    /**
     * Gate for an EXPLICIT {@code teamId} argument only: is the caller allowed
     * to see that specific team's data? {@code teamId == null} is intentionally
     * "no explicit filter requested" here, NOT "no restriction" — every read
     * tool that accepts an optional teamId must additionally consult
     * {@link #scopeForOmittedTeamId()} to decide what "no filter" means for a
     * non-admin caller (F81 fix for SECURITY_AUDIT.md MEDIUM #6: previously
     * {@code teamId == null} bypassed team-scoping entirely for every role).
     */
    private boolean checkTeamAccess(Long teamId) {
        if (teamId == null) {
            return true; // No explicit filter = OK; scoping handled by scopeForOmittedTeamId()
        }
        if (authorizationService.isAdmin() || authorizationService.isProjectManager()) {
            return true;
        }
        Set<Long> userTeamIds = authorizationService.getUserTeamIds();
        return userTeamIds.contains(teamId);
    }

    /**
     * Effective team scope for read tools when the caller omitted {@code teamId}.
     * ADMIN/PROJECT_MANAGER get the unrestricted (whole tenant) view, matching
     * {@link #checkTeamAccess}; every other role (TEAM_LEAD/MEMBER/VIEWER) is
     * confined to the teams they belong to.
     *
     * @return {@code null} when the caller may see every team (no filter should
     *         be applied); otherwise the (possibly empty) set of team ids the
     *         caller may see. An empty set means "no accessible teams" and
     *         callers should short-circuit to an empty/zeroed result instead of
     *         querying with an empty IN-clause.
     */
    private Set<Long> scopeForOmittedTeamId() {
        if (authorizationService.isAdmin() || authorizationService.isProjectManager()) {
            return null;
        }
        return authorizationService.getUserTeamIds();
    }

    /**
     * Fetches issues of a board category, honoring an explicit teamId or,
     * absent one, the caller's team scope (see {@link #scopeForOmittedTeamId()}).
     * Used by board_summary/task_count/task_search/data_quality_summary so the
     * "no teamId given" path can no longer see every team's data for non-admins.
     */
    private List<JiraIssueEntity> fetchByCategoryScoped(String category, Long teamId) {
        if (teamId != null) {
            return issueRepository.findByBoardCategoryAndTeamId(category, teamId);
        }
        Set<Long> scope = scopeForOmittedTeamId();
        if (scope == null) {
            return issueRepository.findByBoardCategory(category);
        }
        if (scope.isEmpty()) {
            return List.of();
        }
        return issueRepository.findByBoardCategoryAndTeamIdIn(category, scope);
    }

    /**
     * Semantic (embedding) search scoped like {@link #fetchByCategoryScoped}.
     * {@link EmbeddingService#search} only accepts a single nullable teamId, so
     * a caller scoped to more than one team cannot be served semantically —
     * falls back to {@code List.of()} so the caller's substring-search fallback
     * (already scoped via {@link #fetchByCategoryScoped}) takes over instead of
     * silently searching every team.
     */
    private List<JiraIssueEntity> semanticSearchScoped(String query, Long teamId) {
        if (teamId != null) {
            return embeddingService.search(query, teamId, 20);
        }
        Set<Long> scope = scopeForOmittedTeamId();
        if (scope == null) {
            return embeddingService.search(query, null, 20);
        }
        if (scope.size() == 1) {
            return embeddingService.search(query, scope.iterator().next(), 20);
        }
        return List.of(); // empty scope, or multi-team — defer to scoped substring fallback
    }

    /**
     * Bug metrics scoped like {@link #fetchByCategoryScoped}. {@link BugMetricsService}
     * only accepts a single nullable teamId, so a caller belonging to more than
     * one team gets a merged result (per-team calls combined; see
     * {@link #mergeBugMetrics}).
     */
    private BugMetricsResponse fetchBugMetricsScoped(Long teamId) {
        if (teamId != null) {
            return bugMetricsService.getBugMetrics(teamId);
        }
        Set<Long> scope = scopeForOmittedTeamId();
        if (scope == null) {
            return bugMetricsService.getBugMetrics(null);
        }
        if (scope.isEmpty()) {
            return new BugMetricsResponse(0, 0, 0, 0, 0.0, List.of(), List.of());
        }
        List<BugMetricsResponse> parts = scope.stream().map(bugMetricsService::getBugMetrics).toList();
        return mergeBugMetrics(parts);
    }

    /**
     * Combines per-team {@link BugMetricsResponse} results for a multi-team
     * caller. Counts/lists are summed/concatenated exactly; rate-like fields
     * (avgResolutionHours, slaCompliancePercent) are a simple (unweighted)
     * average across teams — an acceptable approximation since multi-team
     * membership is the rare case (most callers hit the single-team branch).
     */
    private BugMetricsResponse mergeBugMetrics(List<BugMetricsResponse> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        int openBugs = 0;
        int resolvedBugs = 0;
        int staleBugs = 0;
        long avgResolutionHoursSum = 0;
        double slaSum = 0;
        Map<String, BugMetricsResponse.PriorityMetrics> byPriority = new LinkedHashMap<>();
        List<BugMetricsResponse.OpenBugDto> openBugList = new ArrayList<>();
        for (BugMetricsResponse p : parts) {
            openBugs += p.openBugs();
            resolvedBugs += p.resolvedBugs();
            staleBugs += p.staleBugs();
            avgResolutionHoursSum += p.avgResolutionHours();
            slaSum += p.slaCompliancePercent();
            openBugList.addAll(p.openBugList());
            for (BugMetricsResponse.PriorityMetrics pm : p.byPriority()) {
                byPriority.merge(pm.priority(), pm, (a, b) -> new BugMetricsResponse.PriorityMetrics(
                        a.priority(),
                        a.openCount() + b.openCount(),
                        a.resolvedCount() + b.resolvedCount(),
                        (a.avgResolutionHours() + b.avgResolutionHours()) / 2,
                        a.slaLimitHours() != null ? a.slaLimitHours() : b.slaLimitHours(),
                        (a.slaCompliancePercent() + b.slaCompliancePercent()) / 2));
            }
        }
        int teamCount = parts.size();
        return new BugMetricsResponse(openBugs, resolvedBugs, staleBugs,
                avgResolutionHoursSum / teamCount, slaSum / teamCount,
                new ArrayList<>(byPriority.values()), openBugList);
    }

    /** Issues with worklog, scoped like {@link #fetchByCategoryScoped}. */
    private List<JiraIssueEntity> fetchWithWorklogScoped(Long teamId) {
        if (teamId != null) {
            return issueRepository.findWithWorklog(teamId);
        }
        Set<Long> scope = scopeForOmittedTeamId();
        if (scope == null) {
            return issueRepository.findWithWorklog(null);
        }
        if (scope.isEmpty()) {
            return List.of();
        }
        List<JiraIssueEntity> merged = new ArrayList<>();
        for (Long id : scope) {
            merged.addAll(issueRepository.findWithWorklog(id));
        }
        return merged;
    }

    /** Closed issues in a period, scoped like {@link #fetchByCategoryScoped}. */
    private List<JiraIssueEntity> fetchClosedBetweenScoped(java.time.OffsetDateTime from, java.time.OffsetDateTime to, Long teamId) {
        if (teamId != null) {
            return issueRepository.findClosedBetween(from, to, teamId);
        }
        Set<Long> scope = scopeForOmittedTeamId();
        if (scope == null) {
            return issueRepository.findClosedBetween(from, to, null);
        }
        if (scope.isEmpty()) {
            return List.of();
        }
        List<JiraIssueEntity> merged = new ArrayList<>();
        for (Long id : scope) {
            merged.addAll(issueRepository.findClosedBetween(from, to, id));
        }
        return merged;
    }

    /**
     * Whether a top-level project (board_category PROJECT, no teamId of its
     * own) is visible to a non-admin caller: true if any of its child epics
     * belongs to one of the caller's teams. Used by project_list.
     */
    private boolean projectHasEpicInScope(String projectKey, Set<Long> scope) {
        return issueRepository.findByParentKeyAndBoardCategory(projectKey, "EPIC").stream()
                .anyMatch(e -> e.getTeamId() != null && scope.contains(e.getTeamId()));
    }

    /**
     * Whether a RICE ranking entry is visible to a non-admin caller: the
     * underlying issue's own teamId is in scope (covers epics), or — if the
     * issue is a top-level project (no teamId of its own) — one of its child
     * epics is (see {@link #projectHasEpicInScope}). Unknown/untracked issues
     * are excluded (conservative default). Used by rice_ranking.
     */
    private boolean isRankingEntryInScope(JiraIssueEntity issue, Set<Long> scope) {
        if (issue == null) {
            return false;
        }
        if (issue.getTeamId() != null) {
            return scope.contains(issue.getTeamId());
        }
        if ("PROJECT".equals(issue.getBoardCategory())) {
            return projectHasEpicInScope(issue.getIssueKey(), scope);
        }
        return false;
    }

    private static String normalizeRussian(String text) {
        return text.replace('ё', 'е').replace('Ё', 'Е');
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"JSON serialization failed\"}";
        }
    }
}

package com.leadboard.chat.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.auth.AuthorizationService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.dto.TeamMetricsSummary;
import com.leadboard.metrics.service.BugMetricsService;
import com.leadboard.metrics.service.TeamMetricsService;
import com.leadboard.project.ProjectService;
import com.leadboard.quality.BugSlaService;
import com.leadboard.rice.RiceAssessmentService;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final BugMetricsService bugMetricsService;
    private final ProjectService projectService;
    private final RiceAssessmentService riceAssessmentService;
    private final AbsenceService absenceService;
    private final BugSlaService bugSlaService;
    private final ObjectMapper objectMapper;

    public ChatToolExecutor(
            JiraIssueRepository issueRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamMetricsService teamMetricsService,
            WorkflowConfigService workflowConfigService,
            AuthorizationService authorizationService,
            BugMetricsService bugMetricsService,
            ProjectService projectService,
            RiceAssessmentService riceAssessmentService,
            AbsenceService absenceService,
            BugSlaService bugSlaService,
            ObjectMapper objectMapper
    ) {
        this.issueRepository = issueRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamMetricsService = teamMetricsService;
        this.workflowConfigService = workflowConfigService;
        this.authorizationService = authorizationService;
        this.bugMetricsService = bugMetricsService;
        this.projectService = projectService;
        this.riceAssessmentService = riceAssessmentService;
        this.absenceService = absenceService;
        this.bugSlaService = bugSlaService;
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

        List<JiraIssueEntity> epics;
        if (teamId != null) {
            epics = issueRepository.findByBoardCategoryAndTeamId("EPIC", teamId);
        } else {
            epics = issueRepository.findByBoardCategory("EPIC");
        }

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
        if (teamId != null && typeFilter != null) {
            issues = issueRepository.findByBoardCategoryAndTeamId(typeFilter, teamId);
        } else if (teamId != null) {
            List<JiraIssueEntity> epics = issueRepository.findByBoardCategoryAndTeamId("EPIC", teamId);
            List<JiraIssueEntity> stories = issueRepository.findByBoardCategoryAndTeamId("STORY", teamId);
            List<JiraIssueEntity> bugs = issueRepository.findByBoardCategoryAndTeamId("BUG", teamId);
            issues = new ArrayList<>();
            issues.addAll(epics);
            issues.addAll(stories);
            issues.addAll(bugs);
        } else if (typeFilter != null) {
            issues = issueRepository.findByBoardCategory(typeFilter);
        } else {
            // All main types
            List<JiraIssueEntity> epics = issueRepository.findByBoardCategory("EPIC");
            List<JiraIssueEntity> stories = issueRepository.findByBoardCategory("STORY");
            List<JiraIssueEntity> bugs = issueRepository.findByBoardCategory("BUG");
            issues = new ArrayList<>();
            issues.addAll(epics);
            issues.addAll(stories);
            issues.addAll(bugs);
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

        List<JiraIssueEntity> issues;
        if (teamId != null && typeFilter != null) {
            issues = issueRepository.findByBoardCategoryAndTeamId(typeFilter, teamId);
        } else if (teamId != null) {
            List<JiraIssueEntity> epics = issueRepository.findByBoardCategoryAndTeamId("EPIC", teamId);
            List<JiraIssueEntity> stories = issueRepository.findByBoardCategoryAndTeamId("STORY", teamId);
            List<JiraIssueEntity> bugs = issueRepository.findByBoardCategoryAndTeamId("BUG", teamId);
            issues = new ArrayList<>();
            issues.addAll(epics);
            issues.addAll(stories);
            issues.addAll(bugs);
        } else if (typeFilter != null) {
            issues = issueRepository.findByBoardCategory(typeFilter);
        } else {
            issues = new ArrayList<>();
            issues.addAll(issueRepository.findByBoardCategory("EPIC"));
            issues.addAll(issueRepository.findByBoardCategory("STORY"));
            issues.addAll(issueRepository.findByBoardCategory("BUG"));
        }

        // Filter by status category
        if (statusFilter != null) {
            issues = issues.stream()
                    .filter(e -> statusFilter.equals(categorizeStatus(e.getIssueType(), e.getStatus())))
                    .toList();
        }

        // Filter by query
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase();
            issues = issues.stream()
                    .filter(e -> (e.getIssueKey() != null && e.getIssueKey().toLowerCase().contains(q))
                            || (e.getSummary() != null && e.getSummary().toLowerCase().contains(q)))
                    .toList();
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
        result.put("tasks", results);
        return toJson(result);
    }

    private String dataQualitySummary(JsonNode args) {
        Long teamId = getTeamIdParam(args);

        if (!checkTeamAccess(teamId)) {
            return toJson(Map.of("error", "Access denied: you can only view your own team's data"));
        }

        // Reuse the same logic as DataQualityController but return a simplified summary
        List<JiraIssueEntity> epics;
        if (teamId != null) {
            epics = issueRepository.findByBoardCategoryAndTeamId("EPIC", teamId);
        } else {
            epics = issueRepository.findByBoardCategory("EPIC");
        }

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
            var metrics = bugMetricsService.getBugMetrics(teamId);

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

    private boolean checkTeamAccess(Long teamId) {
        if (teamId == null) {
            return true; // No filter = OK (will see all accessible data)
        }
        if (authorizationService.isAdmin() || authorizationService.isProjectManager()) {
            return true;
        }
        Set<Long> userTeamIds = authorizationService.getUserTeamIds();
        return userTeamIds.contains(teamId);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"JSON serialization failed\"}";
        }
    }
}

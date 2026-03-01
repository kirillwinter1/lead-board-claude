package com.leadboard.chat.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.auth.AuthorizationService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.dto.TeamMetricsSummary;
import com.leadboard.metrics.service.TeamMetricsService;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamMemberRepository;
import com.leadboard.team.TeamRepository;
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
    private final ObjectMapper objectMapper;

    public ChatToolExecutor(
            JiraIssueRepository issueRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamMetricsService teamMetricsService,
            WorkflowConfigService workflowConfigService,
            AuthorizationService authorizationService,
            ObjectMapper objectMapper
    ) {
        this.issueRepository = issueRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamMetricsService = teamMetricsService;
        this.workflowConfigService = workflowConfigService;
        this.authorizationService = authorizationService;
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
                case "data_quality_summary" -> dataQualitySummary(args);
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

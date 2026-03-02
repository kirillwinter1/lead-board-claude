package com.leadboard.chat.tools;

import com.leadboard.chat.llm.LlmToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ChatToolRegistry {

    public List<LlmToolDefinition> getToolDefinitions() {
        return List.of(
                new LlmToolDefinition(
                        "board_summary",
                        "Get board summary: epics and stories grouped by status. Optionally filter by team.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "teamId", Map.of("type", "integer", "description", "Team ID to filter by")
                                ),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "team_list",
                        "Get list of all active teams with their IDs, names, and member count.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "team_metrics",
                        "Get team performance metrics: DSR, throughput, lead time, cycle time for the last 30 days.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "teamId", Map.of("type", "integer", "description", "Team ID (required)")
                                ),
                                "required", List.of("teamId")
                        )
                ),
                new LlmToolDefinition(
                        "task_count",
                        "Count tasks by various filters: status category, team, board category (EPIC/STORY/BUG/SUBTASK).",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "status", Map.of("type", "string", "description", "Status category: TODO, IN_PROGRESS, DONE"),
                                        "teamId", Map.of("type", "integer", "description", "Team ID to filter by"),
                                        "type", Map.of("type", "string", "description", "Board category: EPIC, STORY, BUG, SUBTASK")
                                ),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "task_search",
                        "Search tasks and return their details (key, title, status, type, assignee). Use for questions like 'which stories are in progress', 'list epics for team'. Returns up to 20 results.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "status", Map.of("type", "string", "description", "Status category: TODO, IN_PROGRESS, DONE"),
                                        "teamId", Map.of("type", "integer", "description", "Team ID to filter by"),
                                        "type", Map.of("type", "string", "description", "Board category: EPIC, STORY, BUG"),
                                        "query", Map.of("type", "string", "description", "Search in issue key or title (case-insensitive)")
                                ),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "data_quality_summary",
                        "Get data quality summary: count of errors, warnings, info violations. Optionally filter by team.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "teamId", Map.of("type", "integer", "description", "Team ID to filter by")
                                ),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "bug_metrics",
                        "Get bug metrics: open/resolved/stale bugs, SLA compliance, priority breakdown. Optionally filter by team.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "teamId", Map.of("type", "integer", "description", "Team ID to filter by")
                                ),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "project_list",
                        "Get list of all projects with progress percentage, expected completion date, and RICE score.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "rice_ranking",
                        "Get RICE score ranking of tasks, sorted by score descending. Returns top 20.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "member_absences",
                        "Get team member absences (vacations, sick leave, days off) for the next 90 days.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "teamId", Map.of("type", "integer", "description", "Team ID (required)")
                                ),
                                "required", List.of("teamId")
                        )
                ),
                new LlmToolDefinition(
                        "bug_sla_settings",
                        "Get Bug SLA configuration: resolution time limits (hours) by priority level.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "task_details",
                        "Get full details of a specific task by its Jira key (e.g. PROJ-123).",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "issueKey", Map.of("type", "string", "description", "Jira issue key (e.g. PROJ-123)")
                                ),
                                "required", List.of("issueKey")
                        )
                ),
                new LlmToolDefinition(
                        "team_members",
                        "Get list of team members with their names, roles, and grades.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "teamId", Map.of("type", "integer", "description", "Team ID (required)")
                                ),
                                "required", List.of("teamId")
                        )
                )
        );
    }
}

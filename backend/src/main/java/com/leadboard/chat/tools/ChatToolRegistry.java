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
                ),
                new LlmToolDefinition(
                        "epic_progress",
                        "Get epic progress details from the board: overall progress %, role-based progress (SA/DEV/QA), estimate/logged days, story count, done stories. Use for questions about epic progress, estimates, completion. Supports search by name.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "teamId", Map.of("type", "integer", "description", "Team ID to filter by"),
                                        "query", Map.of("type", "string", "description", "Search by epic key or name (e.g. 'автоматизация' or 'LB-202')")
                                ),
                                "required", List.of()
                        )
                ),
                new LlmToolDefinition(
                        "team_readiness_briefing",
                        "Morning readiness briefing for a team: planning (quarter labels coverage), load (assignees/worklog reliability), data-quality blockers (missing estimates/assignees), and flow bottlenecks. Numbers are deterministic — explain them in plain language. Use for 'is the team ready', 'what should we fix', 'what's blocking planning'.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "teamId", Map.of("type", "integer", "description", "Team ID (optional; omit for all accessible teams)")
                                ),
                                "required", List.of()
                        )
                ),
                // ===================== F80 WRITE tools =====================
                // Each MODIFIES Jira. ALWAYS confirm the exact action with the user before calling.
                new LlmToolDefinition(
                        "transition_issue",
                        "⚠️ WRITE — moves an issue to another status in Jira (e.g. take in progress, close). ALWAYS confirm with the user before calling. targetStatus can be a status name or intent like 'in progress'/'done'.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "issueKey", Map.of("type", "string", "description", "Issue key, e.g. LB-123"),
                                        "targetStatus", Map.of("type", "string", "description", "Target status name or intent (in progress / done)")
                                ),
                                "required", List.of("issueKey", "targetStatus")
                        )
                ),
                new LlmToolDefinition(
                        "log_work",
                        "⚠️ WRITE — logs work time on an issue in Jira under the current user. ALWAYS confirm with the user before calling. hours is decimal (e.g. 1.5). date optional (ISO yyyy-MM-dd, default today).",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "issueKey", Map.of("type", "string", "description", "Issue key, e.g. LB-123"),
                                        "hours", Map.of("type", "number", "description", "Hours worked (decimal), e.g. 5 or 1.5"),
                                        "date", Map.of("type", "string", "description", "Work date ISO yyyy-MM-dd (default: today)")
                                ),
                                "required", List.of("issueKey", "hours")
                        )
                ),
                new LlmToolDefinition(
                        "create_issue",
                        "⚠️ WRITE — creates a Story or Bug in Jira (Epics/Projects are NOT allowed). ALWAYS confirm with the user before calling. parentEpicKey optional (links Story to an Epic and sets the project).",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "kind", Map.of("type", "string", "description", "story or bug"),
                                        "summary", Map.of("type", "string", "description", "Title of the issue"),
                                        "parentEpicKey", Map.of("type", "string", "description", "Optional epic key to link to, e.g. LB-100")
                                ),
                                "required", List.of("summary")
                        )
                ),
                new LlmToolDefinition(
                        "add_comment",
                        "⚠️ WRITE — adds a comment to an issue in Jira. ALWAYS confirm with the user before calling.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "issueKey", Map.of("type", "string", "description", "Issue key, e.g. LB-123"),
                                        "text", Map.of("type", "string", "description", "Comment text")
                                ),
                                "required", List.of("issueKey", "text")
                        )
                ),
                new LlmToolDefinition(
                        "assign_issue",
                        "⚠️ WRITE — assigns an issue to a user in Jira (omit accountId to unassign). ALWAYS confirm with the user before calling.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "issueKey", Map.of("type", "string", "description", "Issue key, e.g. LB-123"),
                                        "accountId", Map.of("type", "string", "description", "Atlassian accountId of the assignee (omit to unassign)")
                                ),
                                "required", List.of("issueKey")
                        )
                ),
                // ===================== F80 read: planning / forecast / load =====================
                new LlmToolDefinition(
                        "quarterly_capacity",
                        "Team capacity for a quarter: available working days minus absences, by role, adjusted for grades. quarter optional (default current, e.g. 2026Q3).",
                        Map.of("type", "object", "properties", Map.of(
                                "teamId", Map.of("type", "integer", "description", "Team ID (required)"),
                                "quarter", Map.of("type", "string", "description", "Quarter label YYYYQn (default: current)")
                        ), "required", List.of("teamId"))
                ),
                new LlmToolDefinition(
                        "quarterly_demand",
                        "Team demand for a quarter: planned work (rough estimates + risk buffer) by project/epic vs capacity. quarter optional (default current).",
                        Map.of("type", "object", "properties", Map.of(
                                "teamId", Map.of("type", "integer", "description", "Team ID (required)"),
                                "quarter", Map.of("type", "string", "description", "Quarter label YYYYQn (default: current)")
                        ), "required", List.of("teamId"))
                ),
                new LlmToolDefinition(
                        "team_forecast",
                        "Forecast of epic completion dates for a team (expected done, days remaining), based on pipeline SA→DEV→QA and capacity.",
                        Map.of("type", "object", "properties", Map.of(
                                "teamId", Map.of("type", "integer", "description", "Team ID (required)")
                        ), "required", List.of("teamId"))
                ),
                new LlmToolDefinition(
                        "team_worklog_timeline",
                        "Day-by-day logged time per team member vs capacity (real load). from/to optional ISO dates (default last 30 days).",
                        Map.of("type", "object", "properties", Map.of(
                                "teamId", Map.of("type", "integer", "description", "Team ID (required)"),
                                "from", Map.of("type", "string", "description", "Start date ISO (default: 30 days ago)"),
                                "to", Map.of("type", "string", "description", "End date ISO (default: today)")
                        ), "required", List.of("teamId"))
                ),
                new LlmToolDefinition(
                        "my_open_tasks_with_worklog",
                        "Tasks that have logged time but are NOT yet done — candidates to close. Each task has loggedHours, originalEstimateHours, remainingEstimateHours, hasEstimate, and readyToClose. readyToClose=true means logged>0 AND estimate existed AND remaining=0 (safe to close). hasEstimate=false means the task was never estimated (remaining is null) — needs manual review, NOT auto-close. teamId optional.",
                        Map.of("type", "object", "properties", Map.of(
                                "teamId", Map.of("type", "integer", "description", "Team ID to filter by (optional)")
                        ), "required", List.of())
                ),
                new LlmToolDefinition(
                        "closed_tasks",
                        "Tasks closed (moved to Done) in a period, with resolvedAt and closedBy (who moved it to Done, from changelog — NOT assignee). Use for 'how many did I/we close this week'. Params: from/to (ISO yyyy-MM-dd, default last 7 days), teamId optional, mineOnly=true to count only tasks closed by the current user. Returns count + tasks.",
                        Map.of("type", "object", "properties", Map.of(
                                "from", Map.of("type", "string", "description", "Start date ISO (default 7 days ago)"),
                                "to", Map.of("type", "string", "description", "End date ISO inclusive (default today)"),
                                "teamId", Map.of("type", "integer", "description", "Team ID (optional)"),
                                "mineOnly", Map.of("type", "boolean", "description", "true = only tasks closed by the current user")
                        ), "required", List.of())
                ),
                // ===================== F80 WRITE: board (confirm before calling) =====================
                new LlmToolDefinition(
                        "triage_matrix",
                        "⚠️ WRITE — sets the Eisenhower quadrant of an issue (P1..P4). ALWAYS confirm with the user before calling. quadrant: P1/P2/P3/P4.",
                        Map.of("type", "object", "properties", Map.of(
                                "issueKey", Map.of("type", "string", "description", "Issue key"),
                                "quadrant", Map.of("type", "string", "description", "P1, P2, P3 or P4")
                        ), "required", List.of("issueKey", "quadrant"))
                ),
                new LlmToolDefinition(
                        "assign_epic_quarter",
                        "⚠️ WRITE — assigns an epic to a quarter (writes YYYYQn label in Jira; omit quarter to clear). ALWAYS confirm with the user before calling.",
                        Map.of("type", "object", "properties", Map.of(
                                "epicKey", Map.of("type", "string", "description", "Epic key"),
                                "quarter", Map.of("type", "string", "description", "Quarter YYYYQn (omit to remove)")
                        ), "required", List.of("epicKey"))
                ),
                new LlmToolDefinition(
                        "set_epic_boost",
                        "⚠️ WRITE — sets manual priority boost for an epic (-50..50). ALWAYS confirm with the user before calling.",
                        Map.of("type", "object", "properties", Map.of(
                                "epicKey", Map.of("type", "string", "description", "Epic key"),
                                "boost", Map.of("type", "integer", "description", "Boost value -50..50")
                        ), "required", List.of("epicKey", "boost"))
                ),
                new LlmToolDefinition(
                        "set_rough_estimate",
                        "⚠️ WRITE — sets the rough estimate (in days) of an epic for a role (SA/DEV/QA). ALWAYS confirm with the user before calling.",
                        Map.of("type", "object", "properties", Map.of(
                                "epicKey", Map.of("type", "string", "description", "Epic key"),
                                "role", Map.of("type", "string", "description", "Role code: SA, DEV or QA"),
                                "days", Map.of("type", "number", "description", "Estimate in days")
                        ), "required", List.of("epicKey", "role", "days"))
                )
        );
    }
}

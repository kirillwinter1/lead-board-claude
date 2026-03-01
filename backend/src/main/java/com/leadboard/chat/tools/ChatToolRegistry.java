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
                        "data_quality_summary",
                        "Get data quality summary: count of errors, warnings, info violations. Optionally filter by team.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "teamId", Map.of("type", "integer", "description", "Team ID to filter by")
                                ),
                                "required", List.of()
                        )
                )
        );
    }
}

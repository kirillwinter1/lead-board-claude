package com.leadboard.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.chat.llm.LlmToolDefinition;
import com.leadboard.chat.tools.ChatToolExecutor;
import com.leadboard.chat.tools.ChatToolRegistry;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpToolAdapterTest {

    private final McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());

    private ChatToolRegistry registryWith(LlmToolDefinition... defs) {
        ChatToolRegistry registry = mock(ChatToolRegistry.class);
        when(registry.getToolDefinitions()).thenReturn(List.of(defs));
        return registry;
    }

    @Test
    void buildsOneSpecPerRegistryDefinition() {
        ChatToolRegistry registry = registryWith(
                new LlmToolDefinition("team_list", "List teams",
                        Map.of("type", "object", "properties", Map.of(), "required", List.of()))
        );
        ChatToolExecutor executor = mock(ChatToolExecutor.class);

        McpToolAdapter adapter = new McpToolAdapter(registry, executor, jsonMapper, new ObjectMapper(), mock(com.leadboard.config.JiraConfigResolver.class));
        List<SyncToolSpecification> specs = adapter.buildSpecifications();

        assertEquals(1, specs.size());
        assertEquals("team_list", specs.get(0).tool().name());
        assertEquals("List teams", specs.get(0).tool().description());
    }

    @Test
    void callHandlerDelegatesToExecutorAndReturnsText() {
        ChatToolRegistry registry = registryWith(
                new LlmToolDefinition("team_list", "List teams",
                        Map.of("type", "object", "properties", Map.of(), "required", List.of()))
        );
        ChatToolExecutor executor = mock(ChatToolExecutor.class);
        when(executor.executeTool(eq("team_list"), anyString()))
                .thenReturn("{\"teams\":[],\"totalTeams\":0}");

        McpToolAdapter adapter = new McpToolAdapter(registry, executor, jsonMapper, new ObjectMapper(), mock(com.leadboard.config.JiraConfigResolver.class));
        SyncToolSpecification spec = adapter.buildSpecifications().get(0);

        CallToolResult result = spec.callHandler().apply(null,
                new CallToolRequest("team_list", Map.of("teamId", 1)));

        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertFalse(result.content().isEmpty());
        verify(executor).executeTool(eq("team_list"), anyString());
    }
}

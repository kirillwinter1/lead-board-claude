package com.leadboard.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import com.leadboard.chat.tools.ChatToolExecutor;
import com.leadboard.chat.tools.ChatToolRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Поднимает remote MCP-сервер на {@code /mcp} (Streamable HTTP транспорт через servlet).
 * Активен только при {@code mcp.enabled=true}.
 *
 * <p>Инструменты F52 подключаются адаптером {@link McpToolAdapter} (см. mcpSyncServer).</p>
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
@ConditionalOnProperty(prefix = "mcp", name = "enabled", havingValue = "true")
public class McpServerConfig {

    private static final String EMPTY_OBJECT_SCHEMA =
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}";

    @Bean
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider(McpJsonMapper jsonMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> reg =
                new ServletRegistrationBean<>(transportProvider, "/mcp", "/mcp/*");
        reg.setName("mcpServlet");
        reg.setAsyncSupported(true);
        return reg;
    }

    @Bean
    public McpToolAdapter mcpToolAdapter(ChatToolRegistry registry,
                                         ChatToolExecutor executor,
                                         McpJsonMapper jsonMapper) {
        return new McpToolAdapter(registry, executor, jsonMapper);
    }

    @Bean
    public McpSyncServer mcpSyncServer(HttpServletStreamableServerTransportProvider transportProvider,
                                       McpJsonMapper jsonMapper,
                                       McpToolAdapter toolAdapter) {
        SyncToolSpecification ping = SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("ping")
                        .description("Health check: returns pong.")
                        .inputSchema(jsonMapper, EMPTY_OBJECT_SCHEMA)
                        .build())
                .callHandler((exchange, request) -> CallToolResult.builder()
                        .addTextContent("pong")
                        .build())
                .build();

        List<SyncToolSpecification> specs = new ArrayList<>();
        specs.add(ping);
        specs.addAll(toolAdapter.buildSpecifications());

        return McpServer.sync(transportProvider)
                .serverInfo("lead-board", "0.80.0")
                .tools(specs)
                .build();
    }
}

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
import io.modelcontextprotocol.common.McpTransportContext;
import com.leadboard.chat.tools.ChatToolExecutor;
import com.leadboard.chat.tools.ChatToolRegistry;
import com.leadboard.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Поднимает remote MCP-сервер на {@code /mcp} (Streamable HTTP транспорт через servlet).
 * Активен только при {@code mcp.enabled=true}.
 *
 * <p>Инструменты F52 подключаются адаптером {@link McpToolAdapter} (см. mcpSyncServer).</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "mcp", name = "enabled", havingValue = "true")
public class McpServerConfig {

    private static final String EMPTY_OBJECT_SCHEMA =
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}";

    /** Правила поведения компаньона — Claude читает при подключении (для всех пользователей). */
    private static final String SERVER_INSTRUCTIONS = """
            Ты — ИИ-компаньон тимлида в Lead Board (доска задач + Jira). Помогаешь планировать, \
            подсказываешь что брать в работу, что горит и что застряло.

            Язык: отвечай по-русски, кратко и по делу, дружелюбно.

            Действия:
            - Инструменты только для чтения (брифинг, метрики, списки, closed_tasks, поиск) вызывай \
              свободно, БЕЗ запроса разрешения — они безопасны (помечены readOnly).
            - Изменяющие действия (перевод статуса, создание задачи, логирование времени, комментарии, \
              изменения на доске — помечены как destructive) ВСЕГДА сначала кратко покажи что сделаешь \
              и дождись явного «да» пользователя. Одно подтверждение на одно действие.

            Данные: все цифры детерминированы (движок Lead Board). Не выдумывай числа. Если инструмент \
            вернул ошибку или пусто — так и скажи, не додумывай.

            Визуализация: когда данные — это тренды, распределения или сравнения (загрузка, throughput, \
            burndown, capacity vs demand), по возможности покажи их наглядно (таблица или график/artifact), \
            а не только текстом.

            Тон компаньона: ты «второй мозг» лида — подсвечивай риски (горящие сроки, перегруз, застрявшие \
            эпики) даже если о них прямо не спросили, но без навязчивости.
            """;

    @Bean
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider(McpJsonMapper jsonMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/mcp")
                // Захват tenant + auth в потоке HTTP-запроса (где их установил McpDebugAuthFilter)
                // и проброс в McpTransportContext, т.к. tool-handler выполняется в другом потоке.
                .contextExtractor((HttpServletRequest request) -> {
                    Map<String, Object> ctx = new HashMap<>();
                    Long tenantId = TenantContext.getCurrentTenantId();
                    if (tenantId != null) {
                        ctx.put(McpToolAdapter.CTX_TENANT_ID, tenantId);
                        ctx.put(McpToolAdapter.CTX_SCHEMA, TenantContext.getCurrentSchema());
                    }
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null) {
                        ctx.put(McpToolAdapter.CTX_AUTH, auth);
                    }
                    return McpTransportContext.create(ctx);
                })
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
                                         McpJsonMapper jsonMapper,
                                         ObjectMapper objectMapper,
                                         com.leadboard.config.JiraConfigResolver jiraConfigResolver) {
        return new McpToolAdapter(registry, executor, jsonMapper, objectMapper, jiraConfigResolver);
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
                .instructions(SERVER_INSTRUCTIONS)
                .tools(specs)
                .build();
    }
}

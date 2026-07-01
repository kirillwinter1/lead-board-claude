package com.leadboard.mcp;

import com.leadboard.chat.llm.LlmToolDefinition;
import com.leadboard.chat.tools.ChatToolExecutor;
import com.leadboard.chat.tools.ChatToolRegistry;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import com.leadboard.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

/**
 * Превращает инструменты F52 ({@link ChatToolRegistry}) в MCP {@link SyncToolSpecification},
 * делегируя выполнение в {@link ChatToolExecutor}. RBAC и tenant-изоляция уже встроены
 * в executor — адаптер их не дублирует.
 *
 * <p>Не {@code @Component}: создаётся как bean в {@link McpServerConfig}, поэтому существует
 * только при {@code mcp.enabled=true} (вместе с {@link McpJsonMapper}).</p>
 */
public class McpToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

    /** Ключи проброса контекста запроса в поток выполнения инструмента (см. McpServerConfig.contextExtractor). */
    public static final String CTX_TENANT_ID = "leadboard.tenantId";
    public static final String CTX_SCHEMA = "leadboard.schema";
    public static final String CTX_AUTH = "leadboard.auth";

    /** Write-инструменты (меняют данные) — destructiveHint=true, требуют подтверждения. Остальные readOnly. */
    private static final Set<String> WRITE_TOOLS = Set.of(
            "transition_issue", "log_work", "create_issue", "add_comment", "assign_issue",
            "triage_matrix", "assign_epic_quarter", "set_epic_boost", "set_rough_estimate");

    private final ChatToolRegistry registry;
    private final ChatToolExecutor executor;
    private final McpJsonMapper jsonMapper;

    public McpToolAdapter(ChatToolRegistry registry, ChatToolExecutor executor, McpJsonMapper jsonMapper) {
        this.registry = registry;
        this.executor = executor;
        this.jsonMapper = jsonMapper;
    }

    /** Строит по одной MCP-спецификации на каждое определение инструмента F52. */
    public List<SyncToolSpecification> buildSpecifications() {
        return registry.getToolDefinitions().stream()
                .map(this::toSpec)
                .toList();
    }

    private SyncToolSpecification toSpec(LlmToolDefinition def) {
        boolean write = WRITE_TOOLS.contains(def.name());
        // ToolAnnotations(title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint, returnDirect)
        ToolAnnotations annotations = new ToolAnnotations(
                null, !write, write, null, null, null);
        Tool tool = Tool.builder()
                .name(def.name())
                .description(def.description())
                .inputSchema(jsonMapper, writeJson(def.parameters()))
                .annotations(annotations)
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    // Восстанавливаем tenant + auth в потоке выполнения инструмента
                    // (ThreadLocal'ы фильтра здесь не видны — проброшены через transportContext).
                    boolean ctxSet = applyContext(exchange);
                    long startNanos = System.nanoTime();
                    String user = currentUser();
                    Long tenant = TenantContext.getCurrentTenantId();
                    String argsJson = writeJson(request.arguments());
                    log.info("MCP tool call: tool={} user={} tenant={} args={}",
                            request.name(), user, tenant, truncate(argsJson));
                    boolean ok = false;
                    try {
                        String resultJson = executor.executeTool(request.name(), argsJson);
                        ok = resultJson == null || !resultJson.contains("\"error\"");
                        return CallToolResult.builder()
                                .addTextContent(resultJson)
                                .build();
                    } finally {
                        long ms = (System.nanoTime() - startNanos) / 1_000_000;
                        log.info("MCP tool done: tool={} user={} tenant={} ms={} ok={}",
                                request.name(), user, tenant, ms, ok);
                        if (ctxSet) {
                            TenantContext.clear();
                            SecurityContextHolder.clearContext();
                        }
                    }
                })
                .build();
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof com.leadboard.auth.LeadBoardAuthentication lba) {
            return lba.getAtlassianAccountId();
        }
        return "anonymous";
    }

    /** Обрезает длинные аргументы в логе (например, текст семантического запроса). */
    private String truncate(String s) {
        if (s == null) {
            return "{}";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }

    private boolean applyContext(io.modelcontextprotocol.server.McpSyncServerExchange exchange) {
        if (exchange == null || exchange.transportContext() == null) {
            return false;
        }
        var ctx = exchange.transportContext();
        Object tenantId = ctx.get(CTX_TENANT_ID);
        Object schema = ctx.get(CTX_SCHEMA);
        Object auth = ctx.get(CTX_AUTH);
        boolean set = false;
        if (tenantId instanceof Long tid && schema instanceof String s) {
            TenantContext.setTenant(tid, s);
            set = true;
        }
        if (auth instanceof Authentication a) {
            SecurityContextHolder.getContext().setAuthentication(a);
            set = true;
        }
        return set;
    }

    private String writeJson(Object obj) {
        try {
            return jsonMapper.writeValueAsString(obj == null ? java.util.Map.of() : obj);
        } catch (Exception e) {
            log.error("MCP arg/schema serialization failed: {}", e.getMessage());
            return "{}";
        }
    }
}

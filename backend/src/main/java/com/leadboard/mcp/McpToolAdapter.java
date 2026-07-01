package com.leadboard.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.chat.llm.LlmToolDefinition;
import com.leadboard.chat.tools.ChatToolExecutor;
import com.leadboard.chat.tools.ChatToolRegistry;
import com.leadboard.config.JiraConfigResolver;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.ResourceLink;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import com.leadboard.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Iterator;
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
    private final ObjectMapper objectMapper;
    private final JiraConfigResolver jiraConfigResolver;

    public McpToolAdapter(ChatToolRegistry registry, ChatToolExecutor executor, McpJsonMapper jsonMapper,
                          ObjectMapper objectMapper, JiraConfigResolver jiraConfigResolver) {
        this.registry = registry;
        this.executor = executor;
        this.jsonMapper = jsonMapper;
        this.objectMapper = objectMapper;
        this.jiraConfigResolver = jiraConfigResolver;
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
                                .content(buildContent(resultJson))
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

    /**
     * Строит контент ответа: текст (для контекста Claude) + resource-link карточки на каждую задачу
     * (Claude/claude.ai рендерит их красивыми карточками с ссылкой на Jira, как Atlassian Rovo).
     */
    private List<Content> buildContent(String resultJson) {
        List<Content> out = new ArrayList<>();
        out.add(new TextContent(resultJson));
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            JsonNode tasks = firstTaskArray(root);
            if (tasks != null) {
                String base = jiraBaseUrl();
                int added = 0;
                for (JsonNode t : tasks) {
                    if (added >= 25) break; // не заваливать карточками
                    if (!t.hasNonNull("key")) continue;
                    String key = t.get("key").asText();
                    String title = firstText(t, "title", "summary", "name");
                    if (title == null) title = key;
                    String desc = buildDesc(t, key);
                    ResourceLink.Builder rl = ResourceLink.builder()
                            .name(key)
                            .title(title)
                            .description(desc)
                            .mimeType("application/vnd.jira.issue");
                    if (base != null) rl.uri(base + "/browse/" + key);
                    else rl.uri("urn:leadboard:issue:" + key);
                    out.add(rl.build());
                    added++;
                }
            }
        } catch (Exception e) {
            log.debug("buildContent: no task cards ({})", e.getMessage());
        }
        return out;
    }

    /** Находит первый массив объектов-задач (элементы содержат "key"). */
    private JsonNode firstTaskArray(JsonNode root) {
        if (root == null || !root.isObject()) return null;
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            JsonNode f = root.get(names.next());
            if (f != null && f.isArray() && f.size() > 0 && f.get(0).isObject() && f.get(0).has("key")) {
                return f;
            }
        }
        return null;
    }

    private String buildDesc(JsonNode t, String key) {
        StringBuilder sb = new StringBuilder(key);
        String status = firstText(t, "status");
        String assignee = firstText(t, "assignee", "closedBy");
        String type = firstText(t, "type");
        if (type != null) sb.append(" · ").append(type);
        if (status != null) sb.append(" · ").append(status);
        sb.append(assignee != null ? " · " + assignee : " · Unassigned");
        return sb.toString();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.hasNonNull(f)) {
                String v = node.get(f).asText();
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    private String jiraBaseUrl() {
        try {
            String base = jiraConfigResolver.getBaseUrl();
            return (base != null && !base.isBlank()) ? base.replaceAll("/+$", "") : null;
        } catch (Exception e) {
            return null;
        }
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

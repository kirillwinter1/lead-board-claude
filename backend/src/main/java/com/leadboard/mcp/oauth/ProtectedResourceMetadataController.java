package com.leadboard.mcp.oauth;

import com.leadboard.mcp.McpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Protected Resource Metadata (RFC 9728) для MCP-сервера (F80 Plan 2).
 *
 * <p>Spring Authorization Server отдаёт metadata только для самого AS
 * ({@code /.well-known/oauth-authorization-server}); метаданные защищённого ресурса
 * (этот MCP-сервер) нужно отдавать самим. claude.ai читает их, чтобы найти AS.</p>
 */
@RestController
@ConditionalOnProperty(prefix = "mcp", name = "oauth-enabled", havingValue = "true")
public class ProtectedResourceMetadataController {

    private final McpProperties props;

    public ProtectedResourceMetadataController(McpProperties props) {
        this.props = props;
    }

    // claude.ai пробует и корневой, и path-aware вариант
    @GetMapping({"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"})
    public Map<String, Object> metadata() {
        return Map.of(
                "resource", props.resourceUri(),
                "authorization_servers", List.of(props.getIssuer()),
                "scopes_supported", List.of("mcp.read", "mcp.invoke"),
                "bearer_methods_supported", List.of("header")
        );
    }
}

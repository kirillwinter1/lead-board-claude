package com.leadboard.mcp;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Startup guard for the MCP debug-token backdoor (SECURITY_AUDIT.md #10 / раздел 10).
 *
 * <p>{@code mcp.debug-token} is a static, never-rotated bearer secret that
 * {@link McpDebugAuthFilter} resolves into a FIXED user + tenant (possibly ADMIN role) —
 * acceptable for local debugging (MCP Inspector / Claude Code CLI on localhost), but never
 * safe to expose remotely without OAuth 2.1 in front of it.</p>
 *
 * <p>Under the {@code prod} Spring profile, this bean hard-fails application startup if
 * {@code mcp.enabled=true} while {@code mcp.oauth-enabled=false} — i.e. if {@code /mcp}
 * would be reachable and guarded only by the static debug token. Non-prod profiles
 * (local/dev) are unaffected: the debug token keeps working there.</p>
 */
@Component
public class McpSecurityGuard implements InitializingBean {

    static final String PROD_PROFILE = "prod";

    private final McpProperties props;
    private final Environment environment;

    public McpSecurityGuard(McpProperties props, Environment environment) {
        this.props = props;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validate(props.isEnabled(), props.isOauthEnabled(), environment.getActiveProfiles());
    }

    /**
     * Side-effect-free validation, extracted so it can be unit-tested without booting a
     * Spring context.
     *
     * @throws IllegalStateException if the {@code prod} profile is active and MCP is
     *                                enabled with the static debug-token auth (OAuth off)
     */
    static void validate(boolean mcpEnabled, boolean oauthEnabled, String[] activeProfiles) {
        boolean prod = Arrays.asList(activeProfiles).contains(PROD_PROFILE);
        if (prod && mcpEnabled && !oauthEnabled) {
            throw new IllegalStateException(
                    "MCP configuration is unsafe for production: mcp.enabled=true but "
                            + "mcp.oauth-enabled=false. This would expose /mcp behind a single "
                            + "static debug bearer token (mcp.debug-token) resolving to a fixed "
                            + "user/tenant. Fix by either setting MCP_OAUTH_ENABLED=true (require "
                            + "OAuth 2.1 for /mcp) or MCP_ENABLED=false (disable MCP) in this "
                            + "environment. See ai-ru/SECURITY_AUDIT.md, section 10.");
        }
    }
}

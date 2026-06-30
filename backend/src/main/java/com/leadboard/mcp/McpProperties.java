package com.leadboard.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация MCP-сервера (F80).
 *
 * <p>Фаза 1 (личное использование / отладка): аутентификация по статическому
 * bearer-токену. Полный OAuth 2.1 — отдельный план (План 2).</p>
 */
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    /** Включён ли MCP-сервер. */
    private boolean enabled = false;

    /** Отладочный bearer-токен (фаза 1). */
    private String debugToken = "";

    /** Tenant slug для отладочного пользователя. */
    private String debugTenantSlug = "";

    /** atlassian_account_id отладочного пользователя. */
    private String debugAccountId = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDebugToken() {
        return debugToken;
    }

    public void setDebugToken(String debugToken) {
        this.debugToken = debugToken;
    }

    public String getDebugTenantSlug() {
        return debugTenantSlug;
    }

    public void setDebugTenantSlug(String debugTenantSlug) {
        this.debugTenantSlug = debugTenantSlug;
    }

    public String getDebugAccountId() {
        return debugAccountId;
    }

    public void setDebugAccountId(String debugAccountId) {
        this.debugAccountId = debugAccountId;
    }
}

package com.leadboard.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Конфигурация MCP-сервера (F80).
 *
 * <p>Фаза 1 (личное использование / отладка): аутентификация по статическому
 * bearer-токену. Полный OAuth 2.1 — отдельный план (План 2).</p>
 *
 * <p>{@code @Component}, а не {@code @EnableConfigurationProperties}: бин нужен
 * всегда (в т.ч. при {@code mcp.enabled=false}), т.к. от него зависит
 * {@link McpDebugAuthFilter}, который регистрируется в SecurityConfig безусловно.</p>
 */
@Component
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

    /** Включён ли OAuth 2.1 (для подключения с телефона/claude.ai). Если true — /mcp защищён JWT, а не debug-токеном. */
    private boolean oauthEnabled = false;

    /** Публичный issuer OAuth Authorization Server (напр. https://leadboard.ru). Локально http://localhost:8080. */
    private String issuer = "http://localhost:8080";

    /** Pre-registered client_id для claude.ai. */
    private String clientId = "claude-ai";

    /** Pre-registered client_secret для claude.ai (впишется в Advanced settings коннектора). */
    private String clientSecret = "";

    /** Канонический URI MCP-ресурса для audience (обычно issuer + /mcp). */
    public String resourceUri() {
        return issuer.endsWith("/") ? issuer + "mcp" : issuer + "/mcp";
    }

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

    public boolean isOauthEnabled() {
        return oauthEnabled;
    }

    public void setOauthEnabled(boolean oauthEnabled) {
        this.oauthEnabled = oauthEnabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}

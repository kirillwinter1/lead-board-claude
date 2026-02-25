package com.leadboard.config;

import com.leadboard.tenant.TenantContext;
import com.leadboard.tenant.TenantJiraConfigEntity;
import com.leadboard.tenant.TenantJiraConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves Jira configuration from the appropriate source:
 * - Tenant context present → reads from tenant_jira_config (DB)
 * - No tenant context → falls back to JiraProperties (.env)
 *
 * This replaces all direct jiraProperties.getXxx() calls throughout the codebase.
 */
@Service
public class JiraConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(JiraConfigResolver.class);

    private final JiraProperties jiraProperties;
    private final TenantJiraConfigRepository tenantJiraConfigRepository;

    public JiraConfigResolver(JiraProperties jiraProperties,
                              TenantJiraConfigRepository tenantJiraConfigRepository) {
        this.jiraProperties = jiraProperties;
        this.tenantJiraConfigRepository = tenantJiraConfigRepository;
    }

    public String getBaseUrl() {
        return getTenantConfig()
                .map(TenantJiraConfigEntity::getJiraBaseUrl)
                .filter(s -> s != null && !s.isBlank())
                .orElse(jiraProperties.getBaseUrl());
    }

    public String getEmail() {
        return getTenantConfig()
                .map(TenantJiraConfigEntity::getJiraEmail)
                .filter(s -> s != null && !s.isBlank())
                .orElse(jiraProperties.getEmail());
    }

    public String getApiToken() {
        return getTenantConfig()
                .map(TenantJiraConfigEntity::getJiraApiToken)
                .filter(s -> s != null && !s.isBlank())
                .orElse(jiraProperties.getApiToken());
    }

    public String getProjectKey() {
        return getTenantConfig()
                .map(c -> {
                    var keys = c.getProjectKeysList();
                    return keys.isEmpty() ? null : keys.get(0);
                })
                .filter(s -> s != null && !s.isBlank())
                .orElse(jiraProperties.getProjectKey());
    }

    public String getTeamFieldId() {
        return getTenantConfig()
                .map(TenantJiraConfigEntity::getTeamFieldId)
                .filter(s -> s != null && !s.isBlank())
                .orElse(jiraProperties.getTeamFieldId());
    }

    public String getOrganizationId() {
        return getTenantConfig()
                .map(TenantJiraConfigEntity::getOrganizationId)
                .filter(s -> s != null && !s.isBlank())
                .orElse(jiraProperties.getOrganizationId());
    }

    public boolean isManualTeamManagement() {
        return getTenantConfig()
                .map(TenantJiraConfigEntity::isManualTeamManagement)
                .orElse(jiraProperties.isManualTeamManagement());
    }

    /**
     * Check if Jira is configured (either via tenant config or .env).
     */
    public boolean isConfigured() {
        String projectKey = getProjectKey();
        return projectKey != null && !projectKey.isBlank();
    }

    /**
     * Check if Basic Auth credentials are available.
     */
    public boolean hasBasicAuthCredentials() {
        String email = getEmail();
        String token = getApiToken();
        String baseUrl = getBaseUrl();
        return email != null && !email.isBlank()
                && token != null && !token.isBlank()
                && baseUrl != null && !baseUrl.isBlank();
    }

    private Optional<TenantJiraConfigEntity> getTenantConfig() {
        if (!TenantContext.hasTenant()) {
            return Optional.empty();
        }
        try {
            return tenantJiraConfigRepository.findActive();
        } catch (Exception e) {
            // Fallback if tenant schema doesn't have the table yet
            log.debug("Failed to read tenant jira config: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

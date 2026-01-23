package com.leadboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "jira")
public class JiraProperties {

    private String baseUrl;
    private String email;
    private String apiToken;
    private String projectKey;
    private int syncIntervalSeconds = 300;
    private Map<String, String> subtaskRoles = new HashMap<>();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public int getSyncIntervalSeconds() {
        return syncIntervalSeconds;
    }

    public void setSyncIntervalSeconds(int syncIntervalSeconds) {
        this.syncIntervalSeconds = syncIntervalSeconds;
    }

    public Map<String, String> getSubtaskRoles() {
        return subtaskRoles;
    }

    public void setSubtaskRoles(Map<String, String> subtaskRoles) {
        this.subtaskRoles = subtaskRoles;
    }

    public String getRoleForSubtaskType(String subtaskType) {
        // Try config first
        String role = subtaskRoles.get(subtaskType);
        if (role != null) return role;

        // Fallback to hardcoded mapping for Russian names
        if (subtaskType == null) return null;
        String lower = subtaskType.toLowerCase();
        if (lower.contains("аналитик") || lower.contains("analyt")) return "ANALYTICS";
        if (lower.contains("разработ") || lower.contains("develop")) return "DEVELOPMENT";
        if (lower.contains("тестир") || lower.contains("test") || lower.contains("qa")) return "TESTING";

        return null;
    }
}

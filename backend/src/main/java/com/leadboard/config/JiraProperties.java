package com.leadboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jira")
public class JiraProperties {

    private String baseUrl;
    private String email;
    private String apiToken;
    private String projectKey;
    private int syncIntervalSeconds = 300;
    private String teamFieldId; // e.g. customfield_12345
    private String organizationId; // Atlassian Organization ID for Teams API
    private boolean manualTeamManagement = false; // If true, allow manual team creation/deletion

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

    public String getTeamFieldId() {
        return teamFieldId;
    }

    public void setTeamFieldId(String teamFieldId) {
        this.teamFieldId = teamFieldId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public boolean isManualTeamManagement() {
        return manualTeamManagement;
    }

    public void setManualTeamManagement(boolean manualTeamManagement) {
        this.manualTeamManagement = manualTeamManagement;
    }
}

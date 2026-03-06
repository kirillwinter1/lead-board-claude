package com.leadboard.tenant;

import com.leadboard.config.EncryptedStringConverter;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Per-tenant Jira connection configuration.
 * Lives in tenant schema (no explicit schema annotation).
 */
@Entity
@Table(name = "tenant_jira_config")
public class TenantJiraConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jira_cloud_id", length = 100)
    private String jiraCloudId;

    @Column(name = "jira_base_url", length = 500)
    private String jiraBaseUrl;

    @Column(name = "project_keys", nullable = false)
    private String projectKeys;

    @Column(name = "team_field_id", length = 100)
    private String teamFieldId;

    @Column(name = "jira_email", length = 255)
    private String jiraEmail;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "jira_api_token", length = 500)
    private String jiraApiToken;

    @Column(name = "organization_id", length = 100)
    private String organizationId;

    @Column(name = "manual_team_management", nullable = false)
    private boolean manualTeamManagement = false;

    @Column(name = "sync_interval_seconds", nullable = false)
    private int syncIntervalSeconds = 300;

    @Column(name = "connected_by_user_id")
    private Long connectedByUserId;

    @Column(name = "setup_completed", nullable = false)
    private boolean setupCompleted = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public List<String> getProjectKeysList() {
        if (projectKeys == null || projectKeys.isBlank()) return List.of();
        return Arrays.stream(projectKeys.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJiraCloudId() { return jiraCloudId; }
    public void setJiraCloudId(String jiraCloudId) { this.jiraCloudId = jiraCloudId; }

    public String getJiraBaseUrl() { return jiraBaseUrl; }
    public void setJiraBaseUrl(String jiraBaseUrl) { this.jiraBaseUrl = jiraBaseUrl; }

    public String getProjectKeys() { return projectKeys; }
    public void setProjectKeys(String projectKeys) { this.projectKeys = projectKeys; }

    public String getTeamFieldId() { return teamFieldId; }
    public void setTeamFieldId(String teamFieldId) { this.teamFieldId = teamFieldId; }

    public String getJiraEmail() { return jiraEmail; }
    public void setJiraEmail(String jiraEmail) { this.jiraEmail = jiraEmail; }

    public String getJiraApiToken() { return jiraApiToken; }
    public void setJiraApiToken(String jiraApiToken) { this.jiraApiToken = jiraApiToken; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public boolean isManualTeamManagement() { return manualTeamManagement; }
    public void setManualTeamManagement(boolean manualTeamManagement) { this.manualTeamManagement = manualTeamManagement; }

    public int getSyncIntervalSeconds() { return syncIntervalSeconds; }
    public void setSyncIntervalSeconds(int syncIntervalSeconds) { this.syncIntervalSeconds = syncIntervalSeconds; }

    public Long getConnectedByUserId() { return connectedByUserId; }
    public void setConnectedByUserId(Long connectedByUserId) { this.connectedByUserId = connectedByUserId; }

    public boolean isSetupCompleted() { return setupCompleted; }
    public void setSetupCompleted(boolean setupCompleted) { this.setupCompleted = setupCompleted; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

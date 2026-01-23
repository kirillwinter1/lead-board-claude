package com.leadboard.sync;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "jira_sync_state")
public class JiraSyncStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, unique = true, length = 50)
    private String projectKey;

    @Column(name = "last_sync_started_at")
    private OffsetDateTime lastSyncStartedAt;

    @Column(name = "last_sync_completed_at")
    private OffsetDateTime lastSyncCompletedAt;

    @Column(name = "last_sync_issues_count")
    private Integer lastSyncIssuesCount = 0;

    @Column(name = "sync_in_progress", nullable = false)
    private boolean syncInProgress = false;

    @Column(name = "last_error", length = 1000)
    private String lastError;

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

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public OffsetDateTime getLastSyncStartedAt() {
        return lastSyncStartedAt;
    }

    public void setLastSyncStartedAt(OffsetDateTime lastSyncStartedAt) {
        this.lastSyncStartedAt = lastSyncStartedAt;
    }

    public OffsetDateTime getLastSyncCompletedAt() {
        return lastSyncCompletedAt;
    }

    public void setLastSyncCompletedAt(OffsetDateTime lastSyncCompletedAt) {
        this.lastSyncCompletedAt = lastSyncCompletedAt;
    }

    public Integer getLastSyncIssuesCount() {
        return lastSyncIssuesCount;
    }

    public void setLastSyncIssuesCount(Integer lastSyncIssuesCount) {
        this.lastSyncIssuesCount = lastSyncIssuesCount;
    }

    public boolean isSyncInProgress() {
        return syncInProgress;
    }

    public void setSyncInProgress(boolean syncInProgress) {
        this.syncInProgress = syncInProgress;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

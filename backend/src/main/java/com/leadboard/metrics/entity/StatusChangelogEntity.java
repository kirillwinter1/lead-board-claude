package com.leadboard.metrics.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "status_changelog")
public class StatusChangelogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_key", nullable = false, length = 50)
    private String issueKey;

    @Column(name = "issue_id", nullable = false, length = 50)
    private String issueId;

    @Column(name = "from_status", length = 100)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 100)
    private String toStatus;

    @Column(name = "transitioned_at", nullable = false)
    private OffsetDateTime transitionedAt;

    @Column(name = "time_in_previous_status_seconds")
    private Long timeInPreviousStatusSeconds;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String issueId) {
        this.issueId = issueId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public OffsetDateTime getTransitionedAt() {
        return transitionedAt;
    }

    public void setTransitionedAt(OffsetDateTime transitionedAt) {
        this.transitionedAt = transitionedAt;
    }

    public Long getTimeInPreviousStatusSeconds() {
        return timeInPreviousStatusSeconds;
    }

    public void setTimeInPreviousStatusSeconds(Long timeInPreviousStatusSeconds) {
        this.timeInPreviousStatusSeconds = timeInPreviousStatusSeconds;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

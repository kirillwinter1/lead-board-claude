package com.leadboard.metrics.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "flag_changelog")
public class FlagChangelogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_key", nullable = false, length = 50)
    private String issueKey;

    @Column(name = "flagged_at", nullable = false)
    private OffsetDateTime flaggedAt;

    @Column(name = "unflagged_at")
    private OffsetDateTime unflaggedAt;

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

    public OffsetDateTime getFlaggedAt() {
        return flaggedAt;
    }

    public void setFlaggedAt(OffsetDateTime flaggedAt) {
        this.flaggedAt = flaggedAt;
    }

    public OffsetDateTime getUnflaggedAt() {
        return unflaggedAt;
    }

    public void setUnflaggedAt(OffsetDateTime unflaggedAt) {
        this.unflaggedAt = unflaggedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

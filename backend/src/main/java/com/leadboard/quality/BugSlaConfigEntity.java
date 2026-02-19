package com.leadboard.quality;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bug_sla_config")
public class BugSlaConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "priority", nullable = false, unique = true, length = 50)
    private String priority;

    @Column(name = "max_resolution_hours", nullable = false)
    private int maxResolutionHours;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public int getMaxResolutionHours() { return maxResolutionHours; }
    public void setMaxResolutionHours(int maxResolutionHours) { this.maxResolutionHours = maxResolutionHours; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package com.leadboard.config.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "project_configurations")
public class ProjectConfigurationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "project_key", length = 50)
    private String projectKey;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "status_score_weights", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String statusScoreWeights;

    @Column(name = "planning_allowed_categories", length = 255)
    private String planningAllowedCategories;

    @Column(name = "time_logging_allowed_categories", length = 255)
    private String timeLoggingAllowedCategories;

    @Column(name = "epic_link_type", length = 20)
    private String epicLinkType;

    @Column(name = "epic_link_name", length = 100)
    private String epicLinkName;

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
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public String getStatusScoreWeights() { return statusScoreWeights; }
    public void setStatusScoreWeights(String statusScoreWeights) { this.statusScoreWeights = statusScoreWeights; }

    public String getPlanningAllowedCategories() { return planningAllowedCategories; }
    public void setPlanningAllowedCategories(String planningAllowedCategories) { this.planningAllowedCategories = planningAllowedCategories; }

    public String getTimeLoggingAllowedCategories() { return timeLoggingAllowedCategories; }
    public void setTimeLoggingAllowedCategories(String timeLoggingAllowedCategories) { this.timeLoggingAllowedCategories = timeLoggingAllowedCategories; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getEpicLinkType() { return epicLinkType; }
    public void setEpicLinkType(String epicLinkType) { this.epicLinkType = epicLinkType; }

    public String getEpicLinkName() { return epicLinkName; }
    public void setEpicLinkName(String epicLinkName) { this.epicLinkName = epicLinkName; }
}

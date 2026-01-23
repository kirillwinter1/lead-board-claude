package com.leadboard.sync;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "jira_issues")
public class JiraIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_key", nullable = false, unique = true, length = 50)
    private String issueKey;

    @Column(name = "issue_id", nullable = false, length = 50)
    private String issueId;

    @Column(name = "project_key", nullable = false, length = 50)
    private String projectKey;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "status", nullable = false, length = 100)
    private String status;

    @Column(name = "issue_type", nullable = false, length = 100)
    private String issueType;

    @Column(name = "is_subtask", nullable = false)
    private boolean subtask;

    @Column(name = "parent_key", length = 50)
    private String parentKey;

    @Column(name = "original_estimate_seconds")
    private Long originalEstimateSeconds;

    @Column(name = "time_spent_seconds")
    private Long timeSpentSeconds;

    @Column(name = "team_field_value", length = 255)
    private String teamFieldValue;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "rough_estimate_sa_days", precision = 10, scale = 1)
    private BigDecimal roughEstimateSaDays;

    @Column(name = "rough_estimate_dev_days", precision = 10, scale = 1)
    private BigDecimal roughEstimateDevDays;

    @Column(name = "rough_estimate_qa_days", precision = 10, scale = 1)
    private BigDecimal roughEstimateQaDays;

    @Column(name = "rough_estimate_updated_at")
    private OffsetDateTime roughEstimateUpdatedAt;

    @Column(name = "rough_estimate_updated_by", length = 255)
    private String roughEstimateUpdatedBy;

    @Column(name = "priority", length = 50)
    private String priority;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "jira_created_at")
    private OffsetDateTime jiraCreatedAt;

    @Column(name = "manual_priority_boost")
    private Integer manualPriorityBoost = 0;

    @Column(name = "auto_score", precision = 5, scale = 2)
    private BigDecimal autoScore;

    @Column(name = "auto_score_calculated_at")
    private OffsetDateTime autoScoreCalculatedAt;

    @Column(name = "jira_updated_at")
    private OffsetDateTime jiraUpdatedAt;

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

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public boolean isSubtask() {
        return subtask;
    }

    public void setSubtask(boolean subtask) {
        this.subtask = subtask;
    }

    public String getParentKey() {
        return parentKey;
    }

    public void setParentKey(String parentKey) {
        this.parentKey = parentKey;
    }

    public Long getOriginalEstimateSeconds() {
        return originalEstimateSeconds;
    }

    public void setOriginalEstimateSeconds(Long originalEstimateSeconds) {
        this.originalEstimateSeconds = originalEstimateSeconds;
    }

    public Long getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public void setTimeSpentSeconds(Long timeSpentSeconds) {
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public String getTeamFieldValue() {
        return teamFieldValue;
    }

    public void setTeamFieldValue(String teamFieldValue) {
        this.teamFieldValue = teamFieldValue;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public BigDecimal getRoughEstimateSaDays() {
        return roughEstimateSaDays;
    }

    public void setRoughEstimateSaDays(BigDecimal roughEstimateSaDays) {
        this.roughEstimateSaDays = roughEstimateSaDays;
    }

    public BigDecimal getRoughEstimateDevDays() {
        return roughEstimateDevDays;
    }

    public void setRoughEstimateDevDays(BigDecimal roughEstimateDevDays) {
        this.roughEstimateDevDays = roughEstimateDevDays;
    }

    public BigDecimal getRoughEstimateQaDays() {
        return roughEstimateQaDays;
    }

    public void setRoughEstimateQaDays(BigDecimal roughEstimateQaDays) {
        this.roughEstimateQaDays = roughEstimateQaDays;
    }

    public OffsetDateTime getRoughEstimateUpdatedAt() {
        return roughEstimateUpdatedAt;
    }

    public void setRoughEstimateUpdatedAt(OffsetDateTime roughEstimateUpdatedAt) {
        this.roughEstimateUpdatedAt = roughEstimateUpdatedAt;
    }

    public String getRoughEstimateUpdatedBy() {
        return roughEstimateUpdatedBy;
    }

    public void setRoughEstimateUpdatedBy(String roughEstimateUpdatedBy) {
        this.roughEstimateUpdatedBy = roughEstimateUpdatedBy;
    }

    public OffsetDateTime getJiraUpdatedAt() {
        return jiraUpdatedAt;
    }

    public void setJiraUpdatedAt(OffsetDateTime jiraUpdatedAt) {
        this.jiraUpdatedAt = jiraUpdatedAt;
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

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public OffsetDateTime getJiraCreatedAt() {
        return jiraCreatedAt;
    }

    public void setJiraCreatedAt(OffsetDateTime jiraCreatedAt) {
        this.jiraCreatedAt = jiraCreatedAt;
    }

    public Integer getManualPriorityBoost() {
        return manualPriorityBoost;
    }

    public void setManualPriorityBoost(Integer manualPriorityBoost) {
        this.manualPriorityBoost = manualPriorityBoost;
    }

    public BigDecimal getAutoScore() {
        return autoScore;
    }

    public void setAutoScore(BigDecimal autoScore) {
        this.autoScore = autoScore;
    }

    public OffsetDateTime getAutoScoreCalculatedAt() {
        return autoScoreCalculatedAt;
    }

    public void setAutoScoreCalculatedAt(OffsetDateTime autoScoreCalculatedAt) {
        this.autoScoreCalculatedAt = autoScoreCalculatedAt;
    }
}

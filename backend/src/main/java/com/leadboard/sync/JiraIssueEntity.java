package com.leadboard.sync;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Column(name = "remaining_estimate_seconds")
    private Long remainingEstimateSeconds;

    @Column(name = "time_spent_seconds")
    private Long timeSpentSeconds;

    @Column(name = "team_field_value", length = 255)
    private String teamFieldValue;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "rough_estimates", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, BigDecimal> roughEstimates;

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

    @Column(name = "auto_score", precision = 5, scale = 2)
    private BigDecimal autoScore;

    @Column(name = "auto_score_calculated_at")
    private OffsetDateTime autoScoreCalculatedAt;

    @Column(name = "flagged")
    private Boolean flagged = false;

    @Column(name = "blocks", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> blocks;

    @Column(name = "is_blocked_by", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> isBlockedBy;

    @Column(name = "assignee_account_id", length = 255)
    private String assigneeAccountId;

    @Column(name = "assignee_display_name", length = 255)
    private String assigneeDisplayName;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "done_at")
    private OffsetDateTime doneAt;

    @Column(name = "manual_order")
    private Integer manualOrder;

    @Column(name = "jira_updated_at")
    private OffsetDateTime jiraUpdatedAt;

    @Column(name = "board_category", length = 20)
    private String boardCategory;

    @Column(name = "workflow_role", length = 50)
    private String workflowRole;

    @Column(name = "components", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] components;

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

    // ==================== Rough Estimate Helpers ====================

    public BigDecimal getRoughEstimate(String roleCode) {
        if (roughEstimates == null || roleCode == null) return null;
        return roughEstimates.get(roleCode);
    }

    public void setRoughEstimate(String roleCode, BigDecimal days) {
        if (roughEstimates == null) {
            roughEstimates = new HashMap<>();
        }
        if (days == null || days.compareTo(BigDecimal.ZERO) == 0) {
            roughEstimates.remove(roleCode);
        } else {
            roughEstimates.put(roleCode, days);
        }
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIssueKey() { return issueKey; }
    public void setIssueKey(String issueKey) { this.issueKey = issueKey; }

    public String getIssueId() { return issueId; }
    public void setIssueId(String issueId) { this.issueId = issueId; }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    public boolean isSubtask() { return subtask; }
    public void setSubtask(boolean subtask) { this.subtask = subtask; }

    public String getParentKey() { return parentKey; }
    public void setParentKey(String parentKey) { this.parentKey = parentKey; }

    public Long getOriginalEstimateSeconds() { return originalEstimateSeconds; }
    public void setOriginalEstimateSeconds(Long originalEstimateSeconds) { this.originalEstimateSeconds = originalEstimateSeconds; }

    public Long getRemainingEstimateSeconds() { return remainingEstimateSeconds; }
    public void setRemainingEstimateSeconds(Long remainingEstimateSeconds) { this.remainingEstimateSeconds = remainingEstimateSeconds; }

    public Long getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(Long timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

    public String getTeamFieldValue() { return teamFieldValue; }
    public void setTeamFieldValue(String teamFieldValue) { this.teamFieldValue = teamFieldValue; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Map<String, BigDecimal> getRoughEstimates() { return roughEstimates; }
    public void setRoughEstimates(Map<String, BigDecimal> roughEstimates) { this.roughEstimates = roughEstimates; }

    public OffsetDateTime getRoughEstimateUpdatedAt() { return roughEstimateUpdatedAt; }
    public void setRoughEstimateUpdatedAt(OffsetDateTime roughEstimateUpdatedAt) { this.roughEstimateUpdatedAt = roughEstimateUpdatedAt; }

    public String getRoughEstimateUpdatedBy() { return roughEstimateUpdatedBy; }
    public void setRoughEstimateUpdatedBy(String roughEstimateUpdatedBy) { this.roughEstimateUpdatedBy = roughEstimateUpdatedBy; }

    public OffsetDateTime getJiraUpdatedAt() { return jiraUpdatedAt; }
    public void setJiraUpdatedAt(OffsetDateTime jiraUpdatedAt) { this.jiraUpdatedAt = jiraUpdatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public OffsetDateTime getJiraCreatedAt() { return jiraCreatedAt; }
    public void setJiraCreatedAt(OffsetDateTime jiraCreatedAt) { this.jiraCreatedAt = jiraCreatedAt; }

    public BigDecimal getAutoScore() { return autoScore; }
    public void setAutoScore(BigDecimal autoScore) { this.autoScore = autoScore; }

    public OffsetDateTime getAutoScoreCalculatedAt() { return autoScoreCalculatedAt; }
    public void setAutoScoreCalculatedAt(OffsetDateTime autoScoreCalculatedAt) { this.autoScoreCalculatedAt = autoScoreCalculatedAt; }

    public Boolean getFlagged() { return flagged; }
    public void setFlagged(Boolean flagged) { this.flagged = flagged; }

    public List<String> getBlocks() { return blocks; }
    public void setBlocks(List<String> blocks) { this.blocks = blocks; }

    public List<String> getIsBlockedBy() { return isBlockedBy; }
    public void setIsBlockedBy(List<String> isBlockedBy) { this.isBlockedBy = isBlockedBy; }

    public String getAssigneeAccountId() { return assigneeAccountId; }
    public void setAssigneeAccountId(String assigneeAccountId) { this.assigneeAccountId = assigneeAccountId; }

    public String getAssigneeDisplayName() { return assigneeDisplayName; }
    public void setAssigneeDisplayName(String assigneeDisplayName) { this.assigneeDisplayName = assigneeDisplayName; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getDoneAt() { return doneAt; }
    public void setDoneAt(OffsetDateTime doneAt) { this.doneAt = doneAt; }

    public Integer getManualOrder() { return manualOrder; }
    public void setManualOrder(Integer manualOrder) { this.manualOrder = manualOrder; }

    public String getBoardCategory() { return boardCategory; }
    public void setBoardCategory(String boardCategory) { this.boardCategory = boardCategory; }

    public String getWorkflowRole() { return workflowRole; }
    public void setWorkflowRole(String workflowRole) { this.workflowRole = workflowRole; }

    public String[] getComponents() { return components; }
    public void setComponents(String[] components) { this.components = components; }

    // ==================== Derived/Computed Methods ====================

    public long getEffectiveEstimateSeconds() {
        if (remainingEstimateSeconds != null) {
            long spent = timeSpentSeconds != null ? timeSpentSeconds : 0;
            return spent + remainingEstimateSeconds;
        }
        return originalEstimateSeconds != null ? originalEstimateSeconds : 0;
    }
}

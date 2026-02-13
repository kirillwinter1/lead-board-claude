package com.leadboard.config.entity;

import com.leadboard.status.StatusCategory;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "status_mappings")
public class StatusMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_id", nullable = false)
    private Long configId;

    @Column(name = "jira_status_name", nullable = false, length = 200)
    private String jiraStatusName;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_category", nullable = false, length = 20)
    private BoardCategory issueCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_category", nullable = false, length = 20)
    private StatusCategory statusCategory;

    @Column(name = "workflow_role_code", length = 50)
    private String workflowRoleCode;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "score_weight")
    private int scoreWeight;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }

    public String getJiraStatusName() { return jiraStatusName; }
    public void setJiraStatusName(String jiraStatusName) { this.jiraStatusName = jiraStatusName; }

    public BoardCategory getIssueCategory() { return issueCategory; }
    public void setIssueCategory(BoardCategory issueCategory) { this.issueCategory = issueCategory; }

    public StatusCategory getStatusCategory() { return statusCategory; }
    public void setStatusCategory(StatusCategory statusCategory) { this.statusCategory = statusCategory; }

    public String getWorkflowRoleCode() { return workflowRoleCode; }
    public void setWorkflowRoleCode(String workflowRoleCode) { this.workflowRoleCode = workflowRoleCode; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public int getScoreWeight() { return scoreWeight; }
    public void setScoreWeight(int scoreWeight) { this.scoreWeight = scoreWeight; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

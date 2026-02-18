package com.leadboard.config.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "issue_type_mappings")
public class IssueTypeMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_id", nullable = false)
    private Long configId;

    @Column(name = "jira_type_name", nullable = false, length = 200)
    private String jiraTypeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_category", nullable = true, length = 20)
    private BoardCategory boardCategory;

    @Column(name = "workflow_role_code", length = 50)
    private String workflowRoleCode;

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

    public String getJiraTypeName() { return jiraTypeName; }
    public void setJiraTypeName(String jiraTypeName) { this.jiraTypeName = jiraTypeName; }

    public BoardCategory getBoardCategory() { return boardCategory; }
    public void setBoardCategory(BoardCategory boardCategory) { this.boardCategory = boardCategory; }

    public String getWorkflowRoleCode() { return workflowRoleCode; }
    public void setWorkflowRoleCode(String workflowRoleCode) { this.workflowRoleCode = workflowRoleCode; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

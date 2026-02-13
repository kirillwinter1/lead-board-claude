package com.leadboard.config.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "link_type_mappings")
public class LinkTypeMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_id", nullable = false)
    private Long configId;

    @Column(name = "jira_link_type_name", nullable = false, length = 200)
    private String jiraLinkTypeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_category", nullable = false, length = 20)
    private LinkCategory linkCategory;

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

    public String getJiraLinkTypeName() { return jiraLinkTypeName; }
    public void setJiraLinkTypeName(String jiraLinkTypeName) { this.jiraLinkTypeName = jiraLinkTypeName; }

    public LinkCategory getLinkCategory() { return linkCategory; }
    public void setLinkCategory(LinkCategory linkCategory) { this.linkCategory = linkCategory; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

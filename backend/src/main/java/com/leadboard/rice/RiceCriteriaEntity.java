package com.leadboard.rice;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rice_criteria")
public class RiceCriteriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private RiceTemplateEntity template;

    @Column(nullable = false)
    private String parameter; // REACH, IMPACT, CONFIDENCE, EFFORT

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "selection_type", nullable = false)
    private String selectionType; // SINGLE, MULTI

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "criteria", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<RiceCriteriaOptionEntity> options = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RiceTemplateEntity getTemplate() { return template; }
    public void setTemplate(RiceTemplateEntity template) { this.template = template; }

    public String getParameter() { return parameter; }
    public void setParameter(String parameter) { this.parameter = parameter; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSelectionType() { return selectionType; }
    public void setSelectionType(String selectionType) { this.selectionType = selectionType; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<RiceCriteriaOptionEntity> getOptions() { return options; }
    public void setOptions(List<RiceCriteriaOptionEntity> options) { this.options = options; }
}

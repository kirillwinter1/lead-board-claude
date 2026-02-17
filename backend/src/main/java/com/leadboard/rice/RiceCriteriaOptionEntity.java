package com.leadboard.rice;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "rice_criteria_options")
public class RiceCriteriaOptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_id", nullable = false)
    private RiceCriteriaEntity criteria;

    @Column(nullable = false)
    private String label;

    @Column
    private String description;

    @Column(nullable = false)
    private BigDecimal score;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RiceCriteriaEntity getCriteria() { return criteria; }
    public void setCriteria(RiceCriteriaEntity criteria) { this.criteria = criteria; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}

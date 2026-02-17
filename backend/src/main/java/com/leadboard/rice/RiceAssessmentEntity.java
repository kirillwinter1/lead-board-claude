package com.leadboard.rice;

import com.leadboard.auth.UserEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rice_assessments")
public class RiceAssessmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_key", nullable = false, unique = true)
    private String issueKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private RiceTemplateEntity template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessed_by")
    private UserEntity assessedBy;

    @Column
    private BigDecimal confidence;

    @Column(name = "effort_manual")
    private String effortManual; // S, M, L, XL

    @Column(name = "effort_auto")
    private BigDecimal effortAuto;

    @Column(name = "total_reach")
    private BigDecimal totalReach;

    @Column(name = "total_impact")
    private BigDecimal totalImpact;

    @Column(name = "effective_effort")
    private BigDecimal effectiveEffort;

    @Column(name = "rice_score")
    private BigDecimal riceScore;

    @Column(name = "normalized_score")
    private BigDecimal normalizedScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RiceAssessmentAnswerEntity> answers = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIssueKey() { return issueKey; }
    public void setIssueKey(String issueKey) { this.issueKey = issueKey; }

    public RiceTemplateEntity getTemplate() { return template; }
    public void setTemplate(RiceTemplateEntity template) { this.template = template; }

    public UserEntity getAssessedBy() { return assessedBy; }
    public void setAssessedBy(UserEntity assessedBy) { this.assessedBy = assessedBy; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public String getEffortManual() { return effortManual; }
    public void setEffortManual(String effortManual) { this.effortManual = effortManual; }

    public BigDecimal getEffortAuto() { return effortAuto; }
    public void setEffortAuto(BigDecimal effortAuto) { this.effortAuto = effortAuto; }

    public BigDecimal getTotalReach() { return totalReach; }
    public void setTotalReach(BigDecimal totalReach) { this.totalReach = totalReach; }

    public BigDecimal getTotalImpact() { return totalImpact; }
    public void setTotalImpact(BigDecimal totalImpact) { this.totalImpact = totalImpact; }

    public BigDecimal getEffectiveEffort() { return effectiveEffort; }
    public void setEffectiveEffort(BigDecimal effectiveEffort) { this.effectiveEffort = effectiveEffort; }

    public BigDecimal getRiceScore() { return riceScore; }
    public void setRiceScore(BigDecimal riceScore) { this.riceScore = riceScore; }

    public BigDecimal getNormalizedScore() { return normalizedScore; }
    public void setNormalizedScore(BigDecimal normalizedScore) { this.normalizedScore = normalizedScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public List<RiceAssessmentAnswerEntity> getAnswers() { return answers; }
    public void setAnswers(List<RiceAssessmentAnswerEntity> answers) { this.answers = answers; }
}

package com.leadboard.rice;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "rice_assessment_answers")
public class RiceAssessmentAnswerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private RiceAssessmentEntity assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_id", nullable = false)
    private RiceCriteriaEntity criteria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    private RiceCriteriaOptionEntity option;

    @Column(nullable = false)
    private BigDecimal score;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RiceAssessmentEntity getAssessment() { return assessment; }
    public void setAssessment(RiceAssessmentEntity assessment) { this.assessment = assessment; }

    public RiceCriteriaEntity getCriteria() { return criteria; }
    public void setCriteria(RiceCriteriaEntity criteria) { this.criteria = criteria; }

    public RiceCriteriaOptionEntity getOption() { return option; }
    public void setOption(RiceCriteriaOptionEntity option) { this.option = option; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
}

package com.leadboard.rice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RiceAssessmentRepository extends JpaRepository<RiceAssessmentEntity, Long> {

    Optional<RiceAssessmentEntity> findByIssueKey(String issueKey);

    List<RiceAssessmentEntity> findByIssueKeyIn(Collection<String> issueKeys);

    List<RiceAssessmentEntity> findByNormalizedScoreIsNotNullOrderByNormalizedScoreDesc();

    List<RiceAssessmentEntity> findByTemplateIdAndNormalizedScoreIsNotNullOrderByNormalizedScoreDesc(Long templateId);
}

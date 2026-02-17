package com.leadboard.rice;

import com.leadboard.rice.dto.RiceAssessmentDto;
import com.leadboard.rice.dto.RiceAssessmentDto.AnswerDto;
import com.leadboard.rice.dto.RiceAssessmentRequest;
import com.leadboard.rice.dto.RiceRankingEntryDto;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RiceAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(RiceAssessmentService.class);

    private static final BigDecimal MIN_EFFORT = new BigDecimal("0.5");

    private static final Map<String, BigDecimal> TSHIRT_VALUES = Map.of(
            "S", BigDecimal.ONE,
            "M", new BigDecimal("2"),
            "L", new BigDecimal("4"),
            "XL", new BigDecimal("8")
    );

    private final RiceAssessmentRepository assessmentRepository;
    private final RiceTemplateRepository templateRepository;
    private final RiceTemplateService templateService;
    private final JiraIssueRepository issueRepository;

    public RiceAssessmentService(RiceAssessmentRepository assessmentRepository,
                                 RiceTemplateRepository templateRepository,
                                 RiceTemplateService templateService,
                                 JiraIssueRepository issueRepository) {
        this.assessmentRepository = assessmentRepository;
        this.templateRepository = templateRepository;
        this.templateService = templateService;
        this.issueRepository = issueRepository;
    }

    public RiceAssessmentDto getAssessment(String issueKey) {
        RiceAssessmentEntity entity = assessmentRepository.findByIssueKey(issueKey)
                .orElse(null);
        return entity != null ? toDto(entity) : null;
    }

    public Map<String, RiceAssessmentDto> getAssessments(Collection<String> issueKeys) {
        return assessmentRepository.findByIssueKeyIn(issueKeys).stream()
                .collect(Collectors.toMap(RiceAssessmentEntity::getIssueKey, this::toDto));
    }

    /**
     * Returns all assessed issues ranked by normalized score (descending).
     * Optionally filtered by templateId.
     */
    public List<RiceRankingEntryDto> getRanking(Long templateId) {
        List<RiceAssessmentEntity> assessments = templateId != null
                ? assessmentRepository.findByTemplateIdAndNormalizedScoreIsNotNullOrderByNormalizedScoreDesc(templateId)
                : assessmentRepository.findByNormalizedScoreIsNotNullOrderByNormalizedScoreDesc();

        // Batch load issue data for summaries and statuses
        Set<String> issueKeys = assessments.stream()
                .map(RiceAssessmentEntity::getIssueKey)
                .collect(Collectors.toSet());
        Map<String, JiraIssueEntity> issueMap = issueKeys.isEmpty()
                ? Map.of()
                : issueRepository.findByIssueKeyIn(new ArrayList<>(issueKeys)).stream()
                        .collect(Collectors.toMap(JiraIssueEntity::getIssueKey, i -> i));

        return assessments.stream().map(a -> {
            JiraIssueEntity issue = issueMap.get(a.getIssueKey());
            return new RiceRankingEntryDto(
                    a.getIssueKey(),
                    issue != null ? issue.getSummary() : null,
                    issue != null ? issue.getStatus() : null,
                    a.getTemplate().getName(),
                    a.getRiceScore(),
                    a.getNormalizedScore(),
                    a.getTotalReach(),
                    a.getTotalImpact(),
                    a.getConfidence(),
                    a.getEffectiveEffort()
            );
        }).toList();
    }

    /**
     * Compute effort_auto from real subtask estimates for an issue.
     * E = totalEstimateHours / (8 hours × 20 working days)
     * Returns person-months, or null if no estimates found.
     */
    public BigDecimal computeEffortAuto(String issueKey) {
        // Find children (stories for epics, epics for projects)
        List<JiraIssueEntity> children = issueRepository.findByParentKey(issueKey);
        if (children.isEmpty()) return null;

        long totalEstimateSeconds = 0;

        for (JiraIssueEntity child : children) {
            if (child.getBoardCategory() != null && "EPIC".equals(child.getBoardCategory())) {
                // Project → Epic: aggregate from stories → subtasks
                List<JiraIssueEntity> stories = issueRepository.findByParentKey(child.getIssueKey());
                for (JiraIssueEntity story : stories) {
                    List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());
                    for (JiraIssueEntity subtask : subtasks) {
                        totalEstimateSeconds += subtask.getEffectiveEstimateSeconds();
                    }
                }
            } else {
                // Epic → Story: aggregate from subtasks
                List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(child.getIssueKey());
                for (JiraIssueEntity subtask : subtasks) {
                    totalEstimateSeconds += subtask.getEffectiveEstimateSeconds();
                }
            }
        }

        if (totalEstimateSeconds <= 0) return null;

        // Convert seconds → person-months (8h/day, 20 days/month = 160h/month)
        double personMonths = totalEstimateSeconds / 3600.0 / 160.0;
        BigDecimal result = BigDecimal.valueOf(personMonths).setScale(2, RoundingMode.HALF_UP);

        return result.compareTo(MIN_EFFORT) < 0 ? MIN_EFFORT : result;
    }

    @Transactional
    public RiceAssessmentDto saveAssessment(RiceAssessmentRequest request) {
        RiceTemplateEntity template = templateRepository.findById(request.templateId())
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + request.templateId()));

        // Build lookup maps for criteria and options
        Map<Long, RiceCriteriaEntity> criteriaMap = template.getCriteria().stream()
                .collect(Collectors.toMap(RiceCriteriaEntity::getId, c -> c));
        Map<Long, RiceCriteriaOptionEntity> optionMap = template.getCriteria().stream()
                .flatMap(c -> c.getOptions().stream())
                .collect(Collectors.toMap(RiceCriteriaOptionEntity::getId, o -> o));

        // Find or create assessment
        RiceAssessmentEntity assessment = assessmentRepository.findByIssueKey(request.issueKey())
                .orElseGet(() -> {
                    RiceAssessmentEntity a = new RiceAssessmentEntity();
                    a.setIssueKey(request.issueKey());
                    return a;
                });

        assessment.setTemplate(template);
        assessment.setEffortManual(request.effortManual());

        // Clear old answers and add new ones
        assessment.getAnswers().clear();

        BigDecimal reachSum = BigDecimal.ZERO;
        BigDecimal impactSum = BigDecimal.ZERO;
        BigDecimal confidence = null;
        BigDecimal effortFromAnswers = null;

        if (request.answers() != null) {
            for (RiceAssessmentRequest.AnswerEntry entry : request.answers()) {
                RiceCriteriaEntity criteria = criteriaMap.get(entry.criteriaId());
                if (criteria == null) continue;

                for (Long optionId : entry.optionIds()) {
                    RiceCriteriaOptionEntity option = optionMap.get(optionId);
                    if (option == null) continue;

                    RiceAssessmentAnswerEntity answer = new RiceAssessmentAnswerEntity();
                    answer.setAssessment(assessment);
                    answer.setCriteria(criteria);
                    answer.setOption(option);
                    answer.setScore(option.getScore());
                    assessment.getAnswers().add(answer);

                    // Aggregate by parameter
                    switch (criteria.getParameter()) {
                        case "REACH" -> reachSum = reachSum.add(option.getScore());
                        case "IMPACT" -> impactSum = impactSum.add(option.getScore());
                        case "CONFIDENCE" -> confidence = option.getScore();
                        case "EFFORT" -> effortFromAnswers = option.getScore();
                    }
                }
            }
        }

        assessment.setTotalReach(reachSum);
        assessment.setTotalImpact(impactSum);
        assessment.setConfidence(confidence);

        // Auto-compute effort from real subtask estimates
        BigDecimal effortAuto = computeEffortAuto(request.issueKey());
        assessment.setEffortAuto(effortAuto);

        // Effective effort: auto > T-shirt answers > manual T-shirt
        BigDecimal effectiveEffort = resolveEffort(effortAuto, effortFromAnswers, request.effortManual());
        assessment.setEffectiveEffort(effectiveEffort);

        // Calculate RICE Score
        BigDecimal riceScore = calculateRiceScore(reachSum, impactSum, confidence, effectiveEffort);
        assessment.setRiceScore(riceScore);

        // Normalize (applies strategic weight)
        BigDecimal normalized = normalize(riceScore, template);
        assessment.setNormalizedScore(normalized);

        RiceAssessmentEntity saved = assessmentRepository.save(assessment);
        log.info("Saved RICE assessment for {}: score={}, normalized={}", request.issueKey(), riceScore, normalized);

        return toDto(saved);
    }

    private BigDecimal resolveEffort(BigDecimal auto, BigDecimal fromAnswers, String manual) {
        // Priority: auto estimate > answer from EFFORT criteria > manual T-shirt
        if (auto != null && auto.compareTo(BigDecimal.ZERO) > 0) {
            return auto.max(MIN_EFFORT);
        }
        if (fromAnswers != null && fromAnswers.compareTo(BigDecimal.ZERO) > 0) {
            return fromAnswers.max(MIN_EFFORT);
        }
        if (manual != null) {
            BigDecimal tshirt = TSHIRT_VALUES.get(manual.toUpperCase());
            if (tshirt != null) return tshirt;
        }
        return null;
    }

    static BigDecimal calculateRiceScore(BigDecimal reach, BigDecimal impact,
                                          BigDecimal confidence, BigDecimal effort) {
        if (reach == null || impact == null || confidence == null || effort == null
                || effort.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return reach.multiply(impact).multiply(confidence)
                .divide(effort, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalize(BigDecimal rawScore, RiceTemplateEntity template) {
        if (rawScore == null) return null;

        try {
            RiceTemplateService.RiceScoreRange range = templateService.getScoreRange(template.getId());
            double min = range.minScore();
            double max = range.maxScore();

            if (max <= min) return BigDecimal.ZERO;

            double normalized = (rawScore.doubleValue() - min) / (max - min) * 100;
            normalized = Math.max(0, Math.min(100, normalized));

            // Apply strategic weight (e.g., Business ×1.0, Technical ×0.8)
            BigDecimal weight = template.getStrategicWeight();
            if (weight != null && weight.compareTo(BigDecimal.ONE) != 0) {
                normalized = normalized * weight.doubleValue();
                normalized = Math.max(0, Math.min(100, normalized));
            }

            return BigDecimal.valueOf(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("Failed to normalize RICE score for template {}: {}", template.getId(), e.getMessage());
            return null;
        }
    }

    private RiceAssessmentDto toDto(RiceAssessmentEntity entity) {
        // Group answers by criteria
        Map<Long, List<RiceAssessmentAnswerEntity>> answersByCriteria = entity.getAnswers().stream()
                .collect(Collectors.groupingBy(a -> a.getCriteria().getId()));

        List<AnswerDto> answerDtos = answersByCriteria.entrySet().stream()
                .map(e -> {
                    RiceCriteriaEntity criteria = e.getValue().get(0).getCriteria();
                    List<Long> optionIds = e.getValue().stream()
                            .map(a -> a.getOption().getId())
                            .toList();
                    return new AnswerDto(
                            criteria.getId(),
                            criteria.getName(),
                            criteria.getParameter(),
                            optionIds
                    );
                })
                .toList();

        return new RiceAssessmentDto(
                entity.getId(),
                entity.getIssueKey(),
                entity.getTemplate().getId(),
                entity.getTemplate().getName(),
                entity.getAssessedBy() != null ? entity.getAssessedBy().getDisplayName() : null,
                entity.getTotalReach(),
                entity.getTotalImpact(),
                entity.getConfidence(),
                entity.getEffortManual(),
                entity.getEffortAuto(),
                entity.getEffectiveEffort(),
                entity.getRiceScore(),
                entity.getNormalizedScore(),
                answerDtos
        );
    }
}

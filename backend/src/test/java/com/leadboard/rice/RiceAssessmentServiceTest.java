package com.leadboard.rice;

import com.leadboard.rice.dto.RiceAssessmentDto;
import com.leadboard.rice.dto.RiceAssessmentRequest;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiceAssessmentServiceTest {

    @Mock
    private RiceAssessmentRepository assessmentRepository;

    @Mock
    private RiceTemplateRepository templateRepository;

    @Mock
    private RiceTemplateService templateService;

    @Mock
    private JiraIssueRepository issueRepository;

    private RiceAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new RiceAssessmentService(assessmentRepository, templateRepository, templateService, issueRepository);
    }

    @Test
    void calculateRiceScore_basicFormula() {
        // R=10, I=5, C=0.8, E=2 => 10*5*0.8/2 = 20
        BigDecimal result = RiceAssessmentService.calculateRiceScore(
                BigDecimal.TEN, new BigDecimal("5"), new BigDecimal("0.8"), new BigDecimal("2"));
        assertEquals(0, new BigDecimal("20.00").compareTo(result));
    }

    @Test
    void calculateRiceScore_returnsNullIfAnyParamIsNull() {
        assertNull(RiceAssessmentService.calculateRiceScore(null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertNull(RiceAssessmentService.calculateRiceScore(BigDecimal.ONE, null, BigDecimal.ONE, BigDecimal.ONE));
        assertNull(RiceAssessmentService.calculateRiceScore(BigDecimal.ONE, BigDecimal.ONE, null, BigDecimal.ONE));
        assertNull(RiceAssessmentService.calculateRiceScore(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, null));
    }

    @Test
    void calculateRiceScore_returnsNullIfEffortIsZero() {
        assertNull(RiceAssessmentService.calculateRiceScore(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO));
    }

    @Test
    void saveAssessment_createsNewAssessment() {
        RiceTemplateEntity template = buildTemplateWithCriteria();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(assessmentRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.empty());
        when(assessmentRepository.save(any())).thenAnswer(inv -> {
            RiceAssessmentEntity e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });

        // Score range for normalization
        when(templateService.getScoreRange(1L))
                .thenReturn(new RiceTemplateService.RiceScoreRange(0.1, 72.0));

        // Select REACH option (score=3), IMPACT option (score=2), CONFIDENCE (score=0.8), EFFORT (score=2)
        RiceAssessmentRequest request = new RiceAssessmentRequest(
                "PROJ-1", 1L, null,
                List.of(
                        new RiceAssessmentRequest.AnswerEntry(10L, List.of(100L)),  // REACH: 3
                        new RiceAssessmentRequest.AnswerEntry(20L, List.of(200L)),  // IMPACT: 2
                        new RiceAssessmentRequest.AnswerEntry(30L, List.of(300L)),  // CONFIDENCE: 0.8
                        new RiceAssessmentRequest.AnswerEntry(40L, List.of(400L))   // EFFORT: 2
                )
        );

        RiceAssessmentDto result = service.saveAssessment(request);

        assertNotNull(result);
        assertEquals("PROJ-1", result.issueKey());

        // Verify the entity was saved correctly
        ArgumentCaptor<RiceAssessmentEntity> captor = ArgumentCaptor.forClass(RiceAssessmentEntity.class);
        verify(assessmentRepository).save(captor.capture());
        RiceAssessmentEntity saved = captor.getValue();

        assertEquals(0, new BigDecimal("3").compareTo(saved.getTotalReach()));
        assertEquals(0, new BigDecimal("2").compareTo(saved.getTotalImpact()));
        assertEquals(0, new BigDecimal("0.8").compareTo(saved.getConfidence()));
        assertEquals(0, new BigDecimal("2").compareTo(saved.getEffectiveEffort()));

        // RICE = 3 * 2 * 0.8 / 2 = 2.40
        assertEquals(0, new BigDecimal("2.40").compareTo(saved.getRiceScore()));
    }

    @Test
    void saveAssessment_updatesExistingAssessment() {
        RiceTemplateEntity template = buildTemplateWithCriteria();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        RiceAssessmentEntity existing = new RiceAssessmentEntity();
        existing.setId(50L);
        existing.setIssueKey("PROJ-1");
        when(assessmentRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(existing));
        when(assessmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(templateService.getScoreRange(1L))
                .thenReturn(new RiceTemplateService.RiceScoreRange(0.1, 72.0));

        RiceAssessmentRequest request = new RiceAssessmentRequest(
                "PROJ-1", 1L, null,
                List.of(
                        new RiceAssessmentRequest.AnswerEntry(10L, List.of(100L)),
                        new RiceAssessmentRequest.AnswerEntry(20L, List.of(200L)),
                        new RiceAssessmentRequest.AnswerEntry(30L, List.of(300L)),
                        new RiceAssessmentRequest.AnswerEntry(40L, List.of(400L))
                )
        );

        RiceAssessmentDto result = service.saveAssessment(request);

        // Should reuse existing entity, not create a new one
        assertEquals(50L, result.id());
    }

    @Test
    void saveAssessment_throwsForUnknownTemplate() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        RiceAssessmentRequest request = new RiceAssessmentRequest(
                "PROJ-1", 99L, null, List.of());

        assertThrows(IllegalArgumentException.class, () -> service.saveAssessment(request));
    }

    @Test
    void saveAssessment_multiCriteriaReachSumsCorrectly() {
        RiceTemplateEntity template = buildTemplateWithMultiReach();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(assessmentRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.empty());
        when(assessmentRepository.save(any())).thenAnswer(inv -> {
            RiceAssessmentEntity e = inv.getArgument(0);
            e.setId(101L);
            return e;
        });
        when(templateService.getScoreRange(1L))
                .thenReturn(new RiceTemplateService.RiceScoreRange(0.1, 100.0));

        // Select two REACH criteria (3 + 2 = 5), IMPACT=1, CONFIDENCE=1, EFFORT=1
        RiceAssessmentRequest request = new RiceAssessmentRequest(
                "PROJ-1", 1L, null,
                List.of(
                        new RiceAssessmentRequest.AnswerEntry(10L, List.of(100L)),  // REACH: 3
                        new RiceAssessmentRequest.AnswerEntry(11L, List.of(110L)),  // REACH: 2
                        new RiceAssessmentRequest.AnswerEntry(20L, List.of(200L)),  // IMPACT: 1
                        new RiceAssessmentRequest.AnswerEntry(30L, List.of(300L)),  // CONFIDENCE: 1
                        new RiceAssessmentRequest.AnswerEntry(40L, List.of(400L))   // EFFORT: 1
                )
        );

        service.saveAssessment(request);

        ArgumentCaptor<RiceAssessmentEntity> captor = ArgumentCaptor.forClass(RiceAssessmentEntity.class);
        verify(assessmentRepository).save(captor.capture());
        RiceAssessmentEntity saved = captor.getValue();

        // REACH = 3 + 2 = 5
        assertEquals(0, new BigDecimal("5").compareTo(saved.getTotalReach()));
        // RICE = 5 * 1 * 1 / 1 = 5
        assertEquals(0, new BigDecimal("5.00").compareTo(saved.getRiceScore()));
    }

    @Test
    void saveAssessment_effortManualTshirtFallback() {
        RiceTemplateEntity template = buildTemplateWithoutEffort();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(assessmentRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.empty());
        when(assessmentRepository.save(any())).thenAnswer(inv -> {
            RiceAssessmentEntity e = inv.getArgument(0);
            e.setId(102L);
            return e;
        });
        when(templateService.getScoreRange(1L))
                .thenReturn(new RiceTemplateService.RiceScoreRange(0.1, 100.0));

        // No EFFORT answer, but manual T-shirt = "L" (= 4)
        RiceAssessmentRequest request = new RiceAssessmentRequest(
                "PROJ-1", 1L, "L",
                List.of(
                        new RiceAssessmentRequest.AnswerEntry(10L, List.of(100L)),  // REACH: 3
                        new RiceAssessmentRequest.AnswerEntry(20L, List.of(200L)),  // IMPACT: 2
                        new RiceAssessmentRequest.AnswerEntry(30L, List.of(300L))   // CONFIDENCE: 0.8
                )
        );

        service.saveAssessment(request);

        ArgumentCaptor<RiceAssessmentEntity> captor = ArgumentCaptor.forClass(RiceAssessmentEntity.class);
        verify(assessmentRepository).save(captor.capture());
        RiceAssessmentEntity saved = captor.getValue();

        assertEquals(0, new BigDecimal("4").compareTo(saved.getEffectiveEffort()));
        // RICE = 3 * 2 * 0.8 / 4 = 1.20
        assertEquals(0, new BigDecimal("1.20").compareTo(saved.getRiceScore()));
    }

    @Test
    void getAssessment_returnsNullIfNotFound() {
        when(assessmentRepository.findByIssueKey("UNKNOWN")).thenReturn(Optional.empty());
        assertNull(service.getAssessment("UNKNOWN"));
    }

    @Test
    void getAssessment_returnsDtoForExisting() {
        RiceAssessmentEntity entity = buildAssessmentEntity();
        when(assessmentRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(entity));

        RiceAssessmentDto result = service.getAssessment("PROJ-1");

        assertNotNull(result);
        assertEquals("PROJ-1", result.issueKey());
        assertEquals(0, new BigDecimal("2.40").compareTo(result.riceScore()));
        assertEquals(1, result.answers().size());
    }

    @Test
    void getAssessments_returnsBatchResults() {
        RiceAssessmentEntity e1 = buildAssessmentEntity();
        RiceAssessmentEntity e2 = buildAssessmentEntity();
        e2.setIssueKey("PROJ-2");
        e2.setId(51L);

        when(assessmentRepository.findByIssueKeyIn(Set.of("PROJ-1", "PROJ-2")))
                .thenReturn(List.of(e1, e2));

        Map<String, RiceAssessmentDto> result = service.getAssessments(Set.of("PROJ-1", "PROJ-2"));

        assertEquals(2, result.size());
        assertTrue(result.containsKey("PROJ-1"));
        assertTrue(result.containsKey("PROJ-2"));
    }

    // ==================== Helper Methods ====================

    private RiceTemplateEntity buildTemplateWithCriteria() {
        RiceTemplateEntity template = new RiceTemplateEntity();
        template.setId(1L);
        template.setName("Business");
        template.setCode("business");
        template.setStrategicWeight(BigDecimal.ONE);
        template.setActive(true);

        addCriteria(template, 10L, "REACH", "Type", "SINGLE",
                new Object[][]{{100L, "Product", 3}, {101L, "Specific", 1}});
        addCriteria(template, 20L, "IMPACT", "Level", "SINGLE",
                new Object[][]{{200L, "High", 2}, {201L, "Low", 1}});
        addCriteria(template, 30L, "CONFIDENCE", "Confidence", "SINGLE",
                new Object[][]{{300L, "High", 0.8}, {301L, "Low", 0.4}});
        addCriteria(template, 40L, "EFFORT", "Size", "SINGLE",
                new Object[][]{{400L, "M", 2}, {401L, "XL", 8}});

        return template;
    }

    private RiceTemplateEntity buildTemplateWithMultiReach() {
        RiceTemplateEntity template = new RiceTemplateEntity();
        template.setId(1L);
        template.setName("Business");
        template.setCode("business");
        template.setStrategicWeight(BigDecimal.ONE);
        template.setActive(true);

        addCriteria(template, 10L, "REACH", "Users", "SINGLE",
                new Object[][]{{100L, "Product", 3}});
        addCriteria(template, 11L, "REACH", "Teams", "SINGLE",
                new Object[][]{{110L, "Many", 2}});
        addCriteria(template, 20L, "IMPACT", "Level", "SINGLE",
                new Object[][]{{200L, "High", 1}});
        addCriteria(template, 30L, "CONFIDENCE", "Confidence", "SINGLE",
                new Object[][]{{300L, "High", 1}});
        addCriteria(template, 40L, "EFFORT", "Size", "SINGLE",
                new Object[][]{{400L, "S", 1}});

        return template;
    }

    private RiceTemplateEntity buildTemplateWithoutEffort() {
        RiceTemplateEntity template = new RiceTemplateEntity();
        template.setId(1L);
        template.setName("No Effort Criteria");
        template.setCode("no-effort");
        template.setStrategicWeight(BigDecimal.ONE);
        template.setActive(true);

        addCriteria(template, 10L, "REACH", "Users", "SINGLE",
                new Object[][]{{100L, "Product", 3}});
        addCriteria(template, 20L, "IMPACT", "Level", "SINGLE",
                new Object[][]{{200L, "High", 2}});
        addCriteria(template, 30L, "CONFIDENCE", "Confidence", "SINGLE",
                new Object[][]{{300L, "High", 0.8}});
        // No EFFORT criteria — will use manual T-shirt

        return template;
    }

    private void addCriteria(RiceTemplateEntity template, Long id, String parameter,
                             String name, String selectionType, Object[][] options) {
        RiceCriteriaEntity criteria = new RiceCriteriaEntity();
        criteria.setId(id);
        criteria.setTemplate(template);
        criteria.setParameter(parameter);
        criteria.setName(name);
        criteria.setSelectionType(selectionType);
        criteria.setSortOrder(template.getCriteria().size());
        criteria.setActive(true);

        for (Object[] opt : options) {
            RiceCriteriaOptionEntity option = new RiceCriteriaOptionEntity();
            option.setId((Long) opt[0]);
            option.setCriteria(criteria);
            option.setLabel((String) opt[1]);
            option.setScore(BigDecimal.valueOf(((Number) opt[2]).doubleValue()));
            option.setSortOrder(criteria.getOptions().size());
            criteria.getOptions().add(option);
        }

        template.getCriteria().add(criteria);
    }

    private RiceAssessmentEntity buildAssessmentEntity() {
        RiceTemplateEntity template = new RiceTemplateEntity();
        template.setId(1L);
        template.setName("Business");

        RiceCriteriaEntity criteria = new RiceCriteriaEntity();
        criteria.setId(10L);
        criteria.setName("Type");
        criteria.setParameter("REACH");

        RiceCriteriaOptionEntity option = new RiceCriteriaOptionEntity();
        option.setId(100L);
        option.setLabel("Product");
        option.setScore(new BigDecimal("3"));

        RiceAssessmentEntity entity = new RiceAssessmentEntity();
        entity.setId(50L);
        entity.setIssueKey("PROJ-1");
        entity.setTemplate(template);
        entity.setTotalReach(new BigDecimal("3"));
        entity.setTotalImpact(new BigDecimal("2"));
        entity.setConfidence(new BigDecimal("0.8"));
        entity.setEffectiveEffort(new BigDecimal("2"));
        entity.setRiceScore(new BigDecimal("2.40"));
        entity.setNormalizedScore(new BigDecimal("50.00"));

        RiceAssessmentAnswerEntity answer = new RiceAssessmentAnswerEntity();
        answer.setAssessment(entity);
        answer.setCriteria(criteria);
        answer.setOption(option);
        answer.setScore(new BigDecimal("3"));
        entity.getAnswers().add(answer);

        return entity;
    }
}

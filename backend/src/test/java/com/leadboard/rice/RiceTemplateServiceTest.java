package com.leadboard.rice;

import com.leadboard.rice.dto.RiceTemplateDto;
import com.leadboard.rice.dto.RiceTemplateListDto;
import com.leadboard.rice.dto.RiceTemplateUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiceTemplateServiceTest {

    @Mock
    private RiceTemplateRepository templateRepository;

    private RiceTemplateService service;

    @BeforeEach
    void setUp() {
        service = new RiceTemplateService(templateRepository);
    }

    @Test
    void listTemplates_returnsActiveTemplates() {
        RiceTemplateEntity t1 = createTemplate(1L, "Business", "business");
        RiceTemplateEntity t2 = createTemplate(2L, "Technical", "technical");
        t2.setStrategicWeight(new BigDecimal("0.80"));

        when(templateRepository.findByActiveTrue()).thenReturn(List.of(t1, t2));

        List<RiceTemplateListDto> result = service.listTemplates();

        assertEquals(2, result.size());
        assertEquals("Business", result.get(0).name());
        assertEquals("business", result.get(0).code());
        assertEquals(BigDecimal.ONE, result.get(0).strategicWeight());
        assertEquals("Technical", result.get(1).name());
        assertEquals(new BigDecimal("0.80"), result.get(1).strategicWeight());
    }

    @Test
    void getTemplate_returnsDtoWithCriteriaAndOptions() {
        RiceTemplateEntity template = createTemplate(1L, "Business", "business");
        addCriteriaWithOptions(template, 10L, "REACH", "Тип фичи", "SINGLE",
                new String[]{"Продуктовая", "Специфичная"},
                new double[]{3, 1});

        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        RiceTemplateDto result = service.getTemplate(1L);

        assertEquals("Business", result.name());
        assertEquals(1, result.criteria().size());
        assertEquals("REACH", result.criteria().get(0).parameter());
        assertEquals("Тип фичи", result.criteria().get(0).name());
        assertEquals("SINGLE", result.criteria().get(0).selectionType());
        assertEquals(2, result.criteria().get(0).options().size());
        assertEquals("Продуктовая", result.criteria().get(0).options().get(0).label());
        assertEquals(0, new BigDecimal("3").compareTo(result.criteria().get(0).options().get(0).score()));
    }

    @Test
    void getTemplate_filtersInactiveCriteria() {
        RiceTemplateEntity template = createTemplate(1L, "Business", "business");
        addCriteriaWithOptions(template, 10L, "REACH", "Active", "SINGLE",
                new String[]{"Option"}, new double[]{1});
        RiceCriteriaEntity inactive = addCriteriaWithOptions(template, 11L, "REACH", "Inactive", "SINGLE",
                new String[]{"Option"}, new double[]{1});
        inactive.setActive(false);

        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        RiceTemplateDto result = service.getTemplate(1L);

        assertEquals(1, result.criteria().size());
        assertEquals("Active", result.criteria().get(0).name());
    }

    @Test
    void getTemplate_throwsForUnknownId() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getTemplate(99L));
    }

    @Test
    void createTemplate_savesNewTemplate() {
        when(templateRepository.findByCode("custom")).thenReturn(Optional.empty());
        when(templateRepository.save(any())).thenAnswer(inv -> {
            RiceTemplateEntity e = inv.getArgument(0);
            e.setId(3L);
            return e;
        });

        RiceTemplateDto result = service.createTemplate(
                new RiceTemplateUpdateRequest("Custom", "custom", new BigDecimal("0.90"), true));

        assertEquals("Custom", result.name());
        assertEquals("custom", result.code());
        assertEquals(new BigDecimal("0.90"), result.strategicWeight());
    }

    @Test
    void createTemplate_throwsForDuplicateCode() {
        when(templateRepository.findByCode("business")).thenReturn(Optional.of(new RiceTemplateEntity()));

        assertThrows(IllegalArgumentException.class, () ->
                service.createTemplate(new RiceTemplateUpdateRequest("Dup", "business", null, null)));
    }

    @Test
    void updateTemplate_updatesFields() {
        RiceTemplateEntity entity = createTemplate(1L, "Business", "business");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiceTemplateDto result = service.updateTemplate(1L,
                new RiceTemplateUpdateRequest("Business v2", null, new BigDecimal("1.20"), null));

        assertEquals("Business v2", result.name());
        assertEquals("business", result.code()); // unchanged
        assertEquals(new BigDecimal("1.20"), result.strategicWeight());
    }

    @Test
    void getScoreRange_computesMinMaxForBusinessTemplate() {
        RiceTemplateEntity template = createTemplate(1L, "Business", "business");

        // REACH: SINGLE (min=1, max=5) + SINGLE (min=1, max=3)
        addCriteriaWithOptions(template, 1L, "REACH", "Users", "SINGLE",
                new String[]{"Few", "Many"}, new double[]{1, 5});
        addCriteriaWithOptions(template, 2L, "REACH", "Teams", "SINGLE",
                new String[]{"Few", "Many"}, new double[]{1, 3});

        // IMPACT: SINGLE (min=1, max=3) + MULTI (sum=6)
        addCriteriaWithOptions(template, 3L, "IMPACT", "Type", "SINGLE",
                new String[]{"Low", "High"}, new double[]{1, 3});
        addCriteriaWithOptions(template, 4L, "IMPACT", "Goals", "MULTI",
                new String[]{"Team", "Company"}, new double[]{1, 5});

        // CONFIDENCE: single
        addCriteriaWithOptions(template, 5L, "CONFIDENCE", "Confidence", "SINGLE",
                new String[]{"High", "Low"}, new double[]{1.0, 0.4});

        // EFFORT: single
        addCriteriaWithOptions(template, 6L, "EFFORT", "Size", "SINGLE",
                new String[]{"S", "XL"}, new double[]{1, 8});

        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        RiceTemplateService.RiceScoreRange range = service.getScoreRange(1L);

        // min: R(1+1) * I(1+0) * C(0.4) / E(8) = 2*1*0.4/8 = 0.1
        assertEquals(0.1, range.minScore(), 0.01);
        // max: R(5+3) * I(3+6) * C(1.0) / E(0.5 min clamp from 1) = 8*9*1/1 = 72
        assertEquals(72.0, range.maxScore(), 0.01);
    }

    // ==================== Helper Methods ====================

    private RiceTemplateEntity createTemplate(Long id, String name, String code) {
        RiceTemplateEntity entity = new RiceTemplateEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setCode(code);
        entity.setStrategicWeight(BigDecimal.ONE);
        entity.setActive(true);
        return entity;
    }

    private RiceCriteriaEntity addCriteriaWithOptions(RiceTemplateEntity template, Long id,
                                                       String parameter, String name, String selectionType,
                                                       String[] labels, double[] scores) {
        RiceCriteriaEntity criteria = new RiceCriteriaEntity();
        criteria.setId(id);
        criteria.setTemplate(template);
        criteria.setParameter(parameter);
        criteria.setName(name);
        criteria.setSelectionType(selectionType);
        criteria.setSortOrder(template.getCriteria().size());
        criteria.setActive(true);

        for (int i = 0; i < labels.length; i++) {
            RiceCriteriaOptionEntity option = new RiceCriteriaOptionEntity();
            option.setId((long) (id * 100 + i));
            option.setCriteria(criteria);
            option.setLabel(labels[i]);
            option.setScore(BigDecimal.valueOf(scores[i]));
            option.setSortOrder(i);
            criteria.getOptions().add(option);
        }

        template.getCriteria().add(criteria);
        return criteria;
    }
}

package com.leadboard.rice;

import com.leadboard.rice.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RiceTemplateService {

    private final RiceTemplateRepository templateRepository;

    public RiceTemplateService(RiceTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public List<RiceTemplateListDto> listTemplates() {
        return templateRepository.findByActiveTrue().stream()
                .map(t -> new RiceTemplateListDto(
                        t.getId(),
                        t.getName(),
                        t.getCode(),
                        t.getStrategicWeight(),
                        Boolean.TRUE.equals(t.getActive()),
                        t.getCriteria().size()
                ))
                .toList();
    }

    public RiceTemplateDto getTemplate(Long id) {
        RiceTemplateEntity template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        return toDto(template);
    }

    public RiceTemplateDto getTemplateByCode(String code) {
        RiceTemplateEntity template = templateRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + code));
        return toDto(template);
    }

    @Transactional
    public RiceTemplateDto createTemplate(RiceTemplateUpdateRequest request) {
        if (templateRepository.findByCode(request.code()).isPresent()) {
            throw new IllegalArgumentException("Template with code already exists: " + request.code());
        }

        RiceTemplateEntity entity = new RiceTemplateEntity();
        entity.setName(request.name());
        entity.setCode(request.code());
        if (request.strategicWeight() != null) {
            entity.setStrategicWeight(request.strategicWeight());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        return toDto(templateRepository.save(entity));
    }

    @Transactional
    public RiceTemplateDto updateTemplate(Long id, RiceTemplateUpdateRequest request) {
        RiceTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));

        if (request.name() != null) entity.setName(request.name());
        if (request.code() != null) entity.setCode(request.code());
        if (request.strategicWeight() != null) entity.setStrategicWeight(request.strategicWeight());
        if (request.active() != null) entity.setActive(request.active());

        return toDto(templateRepository.save(entity));
    }

    /**
     * Computes min/max possible scores for normalization.
     * For REACH and IMPACT: sum of min/max scores across all active SINGLE/MULTI criteria.
     * For CONFIDENCE: min/max from options (0.4 - 1.0 by default).
     * For EFFORT: min/max from T-shirt options (1 - 8 by default).
     */
    public RiceScoreRange getScoreRange(Long templateId) {
        RiceTemplateEntity template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        double reachMin = 0, reachMax = 0;
        double impactMin = 0, impactMax = 0;
        double confidenceMin = 0.4, confidenceMax = 1.0;
        double effortMin = 0.5, effortMax = 8;

        for (RiceCriteriaEntity c : template.getCriteria()) {
            if (!Boolean.TRUE.equals(c.getActive()) || c.getOptions().isEmpty()) continue;

            List<Double> scores = c.getOptions().stream()
                    .map(o -> o.getScore().doubleValue())
                    .toList();

            switch (c.getParameter()) {
                case "REACH" -> {
                    if ("MULTI".equals(c.getSelectionType())) {
                        reachMax += scores.stream().mapToDouble(d -> d).sum();
                    } else {
                        reachMin += scores.stream().mapToDouble(d -> d).min().orElse(0);
                        reachMax += scores.stream().mapToDouble(d -> d).max().orElse(0);
                    }
                }
                case "IMPACT" -> {
                    if ("MULTI".equals(c.getSelectionType())) {
                        impactMax += scores.stream().mapToDouble(d -> d).sum();
                    } else {
                        impactMin += scores.stream().mapToDouble(d -> d).min().orElse(0);
                        impactMax += scores.stream().mapToDouble(d -> d).max().orElse(0);
                    }
                }
                case "CONFIDENCE" -> {
                    confidenceMin = scores.stream().mapToDouble(d -> d).min().orElse(0.4);
                    confidenceMax = scores.stream().mapToDouble(d -> d).max().orElse(1.0);
                }
                case "EFFORT" -> {
                    effortMin = Math.max(0.5, scores.stream().mapToDouble(d -> d).min().orElse(0.5));
                    effortMax = scores.stream().mapToDouble(d -> d).max().orElse(8);
                }
            }
        }

        double rawMin = (reachMin * impactMin * confidenceMin) / effortMax;
        double rawMax = (reachMax * impactMax * confidenceMax) / effortMin;

        return new RiceScoreRange(rawMin, rawMax);
    }

    public record RiceScoreRange(double minScore, double maxScore) {}

    private RiceTemplateDto toDto(RiceTemplateEntity entity) {
        List<RiceCriteriaDto> criteriaDtos = entity.getCriteria().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .map(c -> new RiceCriteriaDto(
                        c.getId(),
                        c.getParameter(),
                        c.getName(),
                        c.getDescription(),
                        c.getSelectionType(),
                        c.getSortOrder(),
                        c.getOptions().stream()
                                .map(o -> new RiceCriteriaOptionDto(
                                        o.getId(),
                                        o.getLabel(),
                                        o.getDescription(),
                                        o.getScore(),
                                        o.getSortOrder()
                                ))
                                .toList()
                ))
                .toList();

        return new RiceTemplateDto(
                entity.getId(),
                entity.getName(),
                entity.getCode(),
                entity.getStrategicWeight(),
                Boolean.TRUE.equals(entity.getActive()),
                criteriaDtos
        );
    }
}

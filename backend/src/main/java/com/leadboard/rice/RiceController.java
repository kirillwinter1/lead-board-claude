package com.leadboard.rice;

import com.leadboard.rice.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rice")
public class RiceController {

    private final RiceTemplateService templateService;
    private final RiceAssessmentService assessmentService;

    public RiceController(RiceTemplateService templateService,
                          RiceAssessmentService assessmentService) {
        this.templateService = templateService;
        this.assessmentService = assessmentService;
    }

    @GetMapping("/templates")
    public ResponseEntity<List<RiceTemplateListDto>> listTemplates() {
        return ResponseEntity.ok(templateService.listTemplates());
    }

    @GetMapping("/templates/{id}")
    public ResponseEntity<RiceTemplateDto> getTemplate(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(templateService.getTemplate(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/templates/by-code/{code}")
    public ResponseEntity<RiceTemplateDto> getTemplateByCode(@PathVariable String code) {
        try {
            return ResponseEntity.ok(templateService.getTemplateByCode(code));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD')")
    @PostMapping("/templates")
    public ResponseEntity<RiceTemplateDto> createTemplate(@RequestBody RiceTemplateUpdateRequest request) {
        try {
            return ResponseEntity.ok(templateService.createTemplate(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD')")
    @PutMapping("/templates/{id}")
    public ResponseEntity<RiceTemplateDto> updateTemplate(@PathVariable Long id,
                                                          @RequestBody RiceTemplateUpdateRequest request) {
        try {
            return ResponseEntity.ok(templateService.updateTemplate(id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/templates/{id}/score-range")
    public ResponseEntity<RiceTemplateService.RiceScoreRange> getScoreRange(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(templateService.getScoreRange(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== Assessment endpoints ====================

    @GetMapping("/assessments/{issueKey}")
    public ResponseEntity<RiceAssessmentDto> getAssessment(@PathVariable String issueKey) {
        RiceAssessmentDto dto = assessmentService.getAssessment(issueKey);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD')")
    @PostMapping("/assessments")
    public ResponseEntity<RiceAssessmentDto> saveAssessment(@RequestBody RiceAssessmentRequest request) {
        try {
            return ResponseEntity.ok(assessmentService.saveAssessment(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ==================== Ranking ====================

    @GetMapping("/ranking")
    public ResponseEntity<List<RiceRankingEntryDto>> getRanking(
            @RequestParam(required = false) Long templateId) {
        return ResponseEntity.ok(assessmentService.getRanking(templateId));
    }
}

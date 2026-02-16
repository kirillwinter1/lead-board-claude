package com.leadboard.competency;

import com.leadboard.competency.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/competencies")
public class CompetencyController {

    private final CompetencyService competencyService;

    public CompetencyController(CompetencyService competencyService) {
        this.competencyService = competencyService;
    }

    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<CompetencyLevelDto>> getMemberCompetencies(@PathVariable Long memberId) {
        return ResponseEntity.ok(competencyService.getMemberCompetencies(memberId));
    }

    @PutMapping("/member/{memberId}")
    public ResponseEntity<List<CompetencyLevelDto>> updateMemberCompetencies(
            @PathVariable Long memberId,
            @RequestBody List<CompetencyLevelDto> competencies) {
        return ResponseEntity.ok(competencyService.updateMemberCompetencies(memberId, competencies));
    }

    @GetMapping("/team/{teamId}")
    public ResponseEntity<TeamCompetencyMatrixDto> getTeamMatrix(@PathVariable Long teamId) {
        return ResponseEntity.ok(competencyService.getTeamMatrix(teamId));
    }

    @GetMapping("/team/{teamId}/bus-factor")
    public ResponseEntity<List<BusFactorAlertDto>> getBusFactor(@PathVariable Long teamId) {
        return ResponseEntity.ok(competencyService.getBusFactor(teamId));
    }

    @GetMapping("/components")
    public ResponseEntity<List<String>> getComponents() {
        return ResponseEntity.ok(competencyService.getAvailableComponents());
    }
}

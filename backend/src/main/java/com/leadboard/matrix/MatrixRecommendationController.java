package com.leadboard.matrix;

import com.leadboard.matrix.RecommendationDtos.RecommendationViewDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * F78 — autoplanner recommendations REST API. Read-only, team-scoped via
 * {@code AuthorizationService.canManageTeam} (same gate as the matrix GET).
 */
@RestController
@RequestMapping("/api/matrix")
public class MatrixRecommendationController {

    private final MatrixRecommendationService recommendationService;

    public MatrixRecommendationController(MatrixRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommendations")
    @PreAuthorize("@authorizationService.canManageTeam(#teamId)")
    public ResponseEntity<RecommendationViewDto> getRecommendations(@RequestParam Long teamId) {
        return ResponseEntity.ok(recommendationService.getRecommendations(teamId));
    }
}

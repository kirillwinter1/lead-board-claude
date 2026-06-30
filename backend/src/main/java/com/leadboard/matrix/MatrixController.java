package com.leadboard.matrix;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * F77 Eisenhower Matrix MVP REST API.
 *
 * <ul>
 *   <li>{@code GET /api/matrix?teamId=} — read the matrix; team-scoped via
 *       {@code AuthorizationService.canManageTeam}.</li>
 *   <li>{@code PUT /api/matrix/triage} — set/clear a task's quadrant; TeamLead+.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/matrix")
public class MatrixController {

    private final MatrixService matrixService;

    public MatrixController(MatrixService matrixService) {
        this.matrixService = matrixService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.canManageTeam(#teamId)")
    public ResponseEntity<MatrixViewDto> getMatrix(@RequestParam Long teamId) {
        return ResponseEntity.ok(matrixService.getMatrix(teamId));
    }

    @PutMapping("/triage")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_MANAGER','TEAM_LEAD')")
    public ResponseEntity<MatrixCardDto> triage(@RequestBody Map<String, String> body) {
        String issueKey = body != null ? body.get("issueKey") : null;
        if (issueKey == null || issueKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // quadrant may be null/absent → clears the task back to unassigned.
        String quadrant = body.get("quadrant");
        return ResponseEntity.ok(matrixService.triage(issueKey, quadrant));
    }
}

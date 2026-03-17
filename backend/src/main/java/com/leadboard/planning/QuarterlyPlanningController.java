package com.leadboard.planning;

import com.leadboard.planning.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quarterly-planning")
@PreAuthorize("isAuthenticated()")
public class QuarterlyPlanningController {

    private final QuarterlyPlanningService planningService;

    public QuarterlyPlanningController(QuarterlyPlanningService planningService) {
        this.planningService = planningService;
    }

    @GetMapping("/capacity")
    public ResponseEntity<QuarterlyCapacityDto> getCapacity(
            @RequestParam Long teamId,
            @RequestParam String quarter) {
        return ResponseEntity.ok(planningService.getTeamCapacity(teamId, quarter));
    }

    @GetMapping("/demand")
    public ResponseEntity<QuarterlyDemandDto> getDemand(
            @RequestParam Long teamId,
            @RequestParam String quarter) {
        return ResponseEntity.ok(planningService.getTeamDemand(teamId, quarter));
    }

    @GetMapping("/summary")
    public ResponseEntity<QuarterlySummaryDto> getSummary(@RequestParam String quarter) {
        return ResponseEntity.ok(planningService.getSummary(quarter));
    }

    @GetMapping("/project-view")
    public ResponseEntity<ProjectViewDto> getProjectView(
            @RequestParam String projectKey,
            @RequestParam String quarter) {
        return ResponseEntity.ok(planningService.getProjectView(projectKey, quarter));
    }

    @GetMapping("/quarters")
    public ResponseEntity<List<String>> getAvailableQuarters() {
        return ResponseEntity.ok(planningService.getAvailableQuarters());
    }

    @GetMapping("/projects-overview")
    public ResponseEntity<QuarterlyProjectsResponse> getProjectsOverview(@RequestParam String quarter) {
        return ResponseEntity.ok(planningService.getProjectsOverview(quarter));
    }

    @GetMapping("/teams-overview")
    public ResponseEntity<List<QuarterlyTeamOverviewDto>> getTeamsOverview(@RequestParam String quarter) {
        return ResponseEntity.ok(planningService.getTeamsOverview(quarter));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @PutMapping("/projects/{key}/boost")
    public ResponseEntity<Void> updateProjectBoost(
            @PathVariable String key,
            @RequestBody Map<String, Integer> body) {
        Integer boost = body.get("boost");
        if (boost == null) {
            return ResponseEntity.badRequest().build();
        }
        planningService.updateProjectBoost(key, boost);
        return ResponseEntity.ok().build();
    }
}

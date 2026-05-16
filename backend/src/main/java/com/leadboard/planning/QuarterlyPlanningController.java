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

    // ==================== F69: Quarterly Planning Redesign (Kanban) ====================

    @GetMapping("/quarters/{quarter}/epics")
    public ResponseEntity<QuarterlyEpicsResponse> getEpicsForQuarter(@PathVariable String quarter) {
        return ResponseEntity.ok(planningService.getEpicsForQuarter(quarter));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @PostMapping("/epics/{epicKey}/quarter")
    public ResponseEntity<PlanningEpicDto> assignEpicToQuarter(
            @PathVariable String epicKey,
            @RequestBody Map<String, String> body) {
        // null quarter explicitly removes the YYYYQn label from Jira
        String quarter = body != null ? body.get("quarter") : null;
        PlanningEpicDto result = planningService.assignEpicToQuarter(epicKey, quarter);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @PostMapping("/epics/{epicKey}/boost")
    public ResponseEntity<PlanningEpicDto> setEpicBoost(
            @PathVariable String epicKey,
            @RequestBody Map<String, Integer> body) {
        Integer boost = body != null ? body.get("boost") : null;
        if (boost == null) {
            return ResponseEntity.badRequest().build();
        }
        PlanningEpicDto result = planningService.setEpicBoost(epicKey, boost);
        return ResponseEntity.ok(result);
    }
}

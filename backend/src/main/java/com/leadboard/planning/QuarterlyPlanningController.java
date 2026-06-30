package com.leadboard.planning;

import com.leadboard.auth.AuthorizationService;
import com.leadboard.planning.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/quarterly-planning")
@PreAuthorize("isAuthenticated()")
public class QuarterlyPlanningController {

    private final QuarterlyPlanningService planningService;
    private final AuthorizationService authorizationService;

    public QuarterlyPlanningController(QuarterlyPlanningService planningService,
                                       AuthorizationService authorizationService) {
        this.planningService = planningService;
        this.authorizationService = authorizationService;
    }

    /**
     * Returns the set of team ids the current user is allowed to see on the
     * planning board, or {@code null} for "no scope — show every team".
     *
     * <p>Only TEAM_LEAD is scoped. ADMIN/PROJECT_MANAGER need a cross-team view
     * to balance capacity; MEMBER/VIEWER keep the unscoped view by design
     * (the page is read-only for them anyway via the mutate-endpoint guards).</p>
     *
     * <p>A TEAM_LEAD without any team membership receives an empty set, which
     * makes both team-overview and epic queries return empty lists — the
     * intentional outcome until the user is added to a team.</p>
     */
    private Set<Long> scopedTeamIds() {
        if (authorizationService.isTeamLead()) {
            return authorizationService.getUserTeamIds();
        }
        return null;
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
        return ResponseEntity.ok(planningService.getTeamsOverview(quarter, scopedTeamIds()));
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

    /**
     * Backlog/in-quarter epics for the team-lead view.
     *
     * <p>F70 introduces the {@code onlyDesired} query parameter (default {@code true}):
     * when enabled, only epics whose parent project has {@code desired_quarter == quarter}
     * — plus all standalone epics (no parent project) — are returned. With {@code false}
     * the original F69 behaviour is restored (all epics across every project).</p>
     *
     * <p>The default is {@code true} because the team-lead UI is the primary
     * consumer of this endpoint and F70 makes the customer-driven filter the
     * intended baseline.</p>
     */
    @GetMapping("/quarters/{quarter}/epics")
    public ResponseEntity<QuarterlyEpicsResponse> getEpicsForQuarter(
            @PathVariable String quarter,
            @RequestParam(name = "onlyDesired", defaultValue = "true") boolean onlyDesired) {
        return ResponseEntity.ok(planningService.getEpicsForQuarter(quarter, onlyDesired, scopedTeamIds()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD')")
    @PostMapping("/epics/{epicKey}/quarter")
    public ResponseEntity<PlanningEpicDto> assignEpicToQuarter(
            @PathVariable String epicKey,
            @RequestBody Map<String, String> body) {
        // null quarter explicitly removes the YYYYQn label from Jira
        String quarter = body != null ? body.get("quarter") : null;
        PlanningEpicDto result = planningService.assignEpicToQuarter(epicKey, quarter);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD')")
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

    // ==================== F70: Customer-Driven Quarter Planning ====================

    /**
     * Set or clear the project's desired quarter (PM-facing).
     *
     * <p>Body: {@code { "quarter": "2026Q2" }} to set; {@code { "quarter": null }}
     * to remove. Returns the up-to-date commitment view aggregated by team so the
     * UI can refresh in a single round-trip.</p>
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @PostMapping("/projects/{projectKey}/desired-quarter")
    public ResponseEntity<ProjectQuarterCommitmentDto> setProjectDesiredQuarter(
            @PathVariable String projectKey,
            @RequestBody(required = false) Map<String, String> body) {
        String quarter = body != null ? body.get("quarter") : null;
        ProjectQuarterCommitmentDto result = planningService.setProjectDesiredQuarter(projectKey, quarter);
        return ResponseEntity.ok(result);
    }

    /**
     * Read-only commitment view for a project: which teams have how many epics
     * committed to the project's desired_quarter, how many moved to another
     * quarter, and how many are uncommitted.
     */
    @GetMapping("/projects/{projectKey}/quarter-commitment")
    public ResponseEntity<ProjectQuarterCommitmentDto> getProjectQuarterCommitment(
            @PathVariable String projectKey) {
        return ResponseEntity.ok(planningService.getProjectCommitment(projectKey));
    }
}

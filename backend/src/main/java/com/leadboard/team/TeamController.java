package com.leadboard.team;

import com.leadboard.auth.AuthorizationService;
import com.leadboard.config.JiraProperties;
import com.leadboard.team.dto.AbsenceDto;
import com.leadboard.team.dto.CreateAbsenceRequest;
import com.leadboard.team.dto.MemberProfileResponse;
import com.leadboard.team.dto.PlanningConfigDto;
import com.leadboard.team.dto.UpdateAbsenceRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;
    private final TeamSyncService teamSyncService;
    private final MemberProfileService memberProfileService;
    private final AbsenceService absenceService;
    private final JiraProperties jiraProperties;
    private final AuthorizationService authorizationService;

    public TeamController(TeamService teamService, TeamSyncService teamSyncService,
                          MemberProfileService memberProfileService, AbsenceService absenceService,
                          JiraProperties jiraProperties, AuthorizationService authorizationService) {
        this.teamService = teamService;
        this.teamSyncService = teamSyncService;
        this.memberProfileService = memberProfileService;
        this.absenceService = absenceService;
        this.jiraProperties = jiraProperties;
        this.authorizationService = authorizationService;
    }

    // ==================== Config Endpoint ====================

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return Map.of(
                "manualTeamManagement", jiraProperties.isManualTeamManagement(),
                "organizationId", jiraProperties.getOrganizationId() != null ? jiraProperties.getOrganizationId() : ""
        );
    }

    // ==================== Sync Endpoints ====================

    @GetMapping("/sync/status")
    public TeamSyncService.TeamSyncStatus getSyncStatus() {
        return teamSyncService.getStatus();
    }

    @PostMapping("/sync/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public TeamSyncService.TeamSyncStatus triggerSync() {
        return teamSyncService.syncTeams();
    }

    // ==================== Team Endpoints ====================

    @GetMapping
    public List<TeamDto> getAllTeams() {
        return teamService.getAllTeams();
    }

    @GetMapping("/{id}")
    public TeamDto getTeam(@PathVariable Long id) {
        return teamService.getTeam(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeamDto> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        TeamDto team = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(team);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageTeam(#id)")
    public TeamDto updateTeam(@PathVariable Long id, @Valid @RequestBody UpdateTeamRequest request) {
        return teamService.updateTeam(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivateTeam(@PathVariable Long id) {
        teamService.deactivateTeam(id);
    }

    // ==================== Team Member Endpoints ====================

    @GetMapping("/{teamId}/members")
    public List<TeamMemberDto> getTeamMembers(@PathVariable Long teamId) {
        return teamService.getTeamMembers(teamId);
    }

    @PostMapping("/{teamId}/members")
    @PreAuthorize("@authorizationService.canManageTeam(#teamId)")
    public ResponseEntity<TeamMemberDto> addTeamMember(
            @PathVariable Long teamId,
            @Valid @RequestBody CreateTeamMemberRequest request) {
        TeamMemberDto member = teamService.addTeamMember(teamId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PutMapping("/{teamId}/members/{memberId}")
    @PreAuthorize("@authorizationService.canManageTeam(#teamId)")
    public TeamMemberDto updateTeamMember(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateTeamMemberRequest request) {
        return teamService.updateTeamMember(teamId, memberId, request);
    }

    @PostMapping("/{teamId}/members/{memberId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.canManageTeam(#teamId)")
    public void deactivateTeamMember(
            @PathVariable Long teamId,
            @PathVariable Long memberId) {
        teamService.deactivateTeamMember(teamId, memberId);
    }

    // ==================== Planning Config Endpoints ====================

    /**
     * Получить конфигурацию планирования команды.
     */
    @GetMapping("/{teamId}/planning-config")
    public PlanningConfigDto getPlanningConfig(@PathVariable Long teamId) {
        return teamService.getPlanningConfig(teamId);
    }

    /**
     * Обновить конфигурацию планирования команды.
     */
    @PutMapping("/{teamId}/planning-config")
    @PreAuthorize("@authorizationService.canManageTeam(#teamId)")
    public PlanningConfigDto updatePlanningConfig(
            @PathVariable Long teamId,
            @RequestBody PlanningConfigDto config) {
        return teamService.updatePlanningConfig(teamId, config);
    }

    // ==================== Member Profile Endpoint ====================

    @GetMapping("/{teamId}/members/{memberId}/profile")
    public MemberProfileResponse getMemberProfile(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return memberProfileService.getMemberProfile(teamId, memberId, from, to);
    }

    // ==================== Absence Endpoints ====================

    @GetMapping("/{teamId}/absences")
    public List<AbsenceDto> getTeamAbsences(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return absenceService.getAbsencesForTeam(teamId, from, to);
    }

    @GetMapping("/{teamId}/members/{memberId}/absences/upcoming")
    public List<AbsenceDto> getUpcomingAbsences(
            @PathVariable Long teamId,
            @PathVariable Long memberId) {
        return absenceService.getUpcomingAbsences(memberId);
    }

    @PostMapping("/{teamId}/members/{memberId}/absences")
    @PreAuthorize("@authorizationService.canManageTeam(#teamId)")
    public ResponseEntity<AbsenceDto> createAbsence(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @Valid @RequestBody CreateAbsenceRequest request) {
        AbsenceDto dto = absenceService.createAbsence(teamId, memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{teamId}/members/{memberId}/absences/{absenceId}")
    @PreAuthorize("@authorizationService.canManageTeam(#teamId)")
    public AbsenceDto updateAbsence(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @PathVariable Long absenceId,
            @Valid @RequestBody UpdateAbsenceRequest request) {
        return absenceService.updateAbsence(teamId, memberId, absenceId, request);
    }

    @DeleteMapping("/{teamId}/members/{memberId}/absences/{absenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.canManageTeam(#teamId)")
    public void deleteAbsence(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @PathVariable Long absenceId) {
        absenceService.deleteAbsence(teamId, memberId, absenceId);
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(TeamService.TeamNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTeamNotFound(TeamService.TeamNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(TeamService.TeamAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleTeamAlreadyExists(TeamService.TeamAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(TeamService.TeamMemberNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleMemberNotFound(TeamService.TeamMemberNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(TeamService.TeamMemberAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleMemberAlreadyExists(TeamService.TeamMemberAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(TeamService.InvalidPlanningConfigException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPlanningConfig(TeamService.InvalidPlanningConfigException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AbsenceService.AbsenceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAbsenceNotFound(AbsenceService.AbsenceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AbsenceService.AbsenceOverlapException.class)
    public ResponseEntity<Map<String, String>> handleAbsenceOverlap(AbsenceService.AbsenceOverlapException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AbsenceService.InvalidAbsenceDatesException.class)
    public ResponseEntity<Map<String, String>> handleInvalidAbsenceDates(AbsenceService.InvalidAbsenceDatesException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }
}

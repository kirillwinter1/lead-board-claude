package com.leadboard.team;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
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
    public ResponseEntity<TeamDto> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        TeamDto team = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(team);
    }

    @PutMapping("/{id}")
    public TeamDto updateTeam(@PathVariable Long id, @Valid @RequestBody UpdateTeamRequest request) {
        return teamService.updateTeam(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateTeam(@PathVariable Long id) {
        teamService.deactivateTeam(id);
    }

    // ==================== Team Member Endpoints ====================

    @GetMapping("/{teamId}/members")
    public List<TeamMemberDto> getTeamMembers(@PathVariable Long teamId) {
        return teamService.getTeamMembers(teamId);
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamMemberDto> addTeamMember(
            @PathVariable Long teamId,
            @Valid @RequestBody CreateTeamMemberRequest request) {
        TeamMemberDto member = teamService.addTeamMember(teamId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PutMapping("/{teamId}/members/{memberId}")
    public TeamMemberDto updateTeamMember(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateTeamMemberRequest request) {
        return teamService.updateTeamMember(teamId, memberId, request);
    }

    @PostMapping("/{teamId}/members/{memberId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateTeamMember(
            @PathVariable Long teamId,
            @PathVariable Long memberId) {
        teamService.deactivateTeamMember(teamId, memberId);
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
}

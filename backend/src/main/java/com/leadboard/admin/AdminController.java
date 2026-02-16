package com.leadboard.admin;

import com.leadboard.auth.AppRole;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.auth.UserEntity;
import com.leadboard.auth.UserRepository;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin endpoints for user management.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UserRepository userRepository;
    private final JiraIssueRepository jiraIssueRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final WorkflowConfigService workflowConfigService;
    private final JiraClient jiraClient;

    public AdminController(UserRepository userRepository,
                           JiraIssueRepository jiraIssueRepository,
                           TeamMemberRepository teamMemberRepository,
                           WorkflowConfigService workflowConfigService,
                           JiraClient jiraClient) {
        this.userRepository = userRepository;
        this.jiraIssueRepository = jiraIssueRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.workflowConfigService = workflowConfigService;
        this.jiraClient = jiraClient;
    }

    /**
     * Get all users with their roles.
     */
    @GetMapping("/users")
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Update user role.
     */
    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserDto> updateUserRole(
            @PathVariable Long id,
            @RequestBody UpdateRoleRequest request) {

        // Prevent admin from changing their own role (self-lockout protection)
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof LeadBoardAuthentication lbAuth) {
            if (lbAuth.getUser().getId().equals(id)) {
                throw new InvalidRoleException("Cannot change your own role");
            }
        }

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));

        try {
            AppRole newRole = AppRole.valueOf(request.role());
            user.setAppRole(newRole);
            userRepository.save(user);
            return ResponseEntity.ok(toDto(user));
        } catch (IllegalArgumentException e) {
            throw new InvalidRoleException("Invalid role: " + request.role());
        }
    }

    private UserDto toDto(UserEntity user) {
        return new UserDto(
                user.getId(),
                user.getAtlassianAccountId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getAppRole().name()
        );
    }

    public record UserDto(
            Long id,
            String accountId,
            String displayName,
            String email,
            String avatarUrl,
            String role
    ) {}

    public record UpdateRoleRequest(String role) {}

    /**
     * Auto-assign unassigned subtasks in Jira based on issue_type → role → team_member mapping.
     */
    @PostMapping("/auto-assign-subtasks")
    public ResponseEntity<Map<String, Object>> autoAssignSubtasks(@RequestParam Long teamId) {
        List<JiraIssueEntity> unassigned = jiraIssueRepository.findUnassignedSubtasksByTeam(teamId);
        if (unassigned.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No unassigned subtasks found", "assigned", 0));
        }

        // Build role → team_member map (only active members)
        List<TeamMemberEntity> members = teamMemberRepository.findByTeamIdAndActiveTrue(teamId);
        Map<String, TeamMemberEntity> roleToMember = new HashMap<>();
        for (TeamMemberEntity m : members) {
            roleToMember.put(m.getRole(), m);
        }

        int assigned = 0;
        int skipped = 0;
        int failed = 0;
        Map<String, Integer> byRole = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (JiraIssueEntity issue : unassigned) {
            String role = workflowConfigService.getSubtaskRole(issue.getIssueType());
            TeamMemberEntity member = roleToMember.get(role);

            if (member == null) {
                skipped++;
                log.warn("No team member for role '{}' (issue {} type '{}')", role, issue.getIssueKey(), issue.getIssueType());
                continue;
            }

            try {
                jiraClient.assignIssueBasicAuth(issue.getIssueKey(), member.getJiraAccountId());
                issue.setAssigneeAccountId(member.getJiraAccountId());
                issue.setAssigneeDisplayName(member.getDisplayName());
                jiraIssueRepository.save(issue);
                assigned++;
                byRole.merge(role, 1, Integer::sum);
                log.info("Assigned {} → {} ({})", issue.getIssueKey(), member.getDisplayName(), role);
            } catch (Exception e) {
                failed++;
                String error = issue.getIssueKey() + ": " + e.getMessage();
                errors.add(error);
                log.error("Failed to assign {}: {}", issue.getIssueKey(), e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_unassigned", unassigned.size());
        result.put("assigned", assigned);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("by_role", byRole);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return ResponseEntity.ok(result);
    }

    @ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public static class InvalidRoleException extends RuntimeException {
        public InvalidRoleException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRole(InvalidRoleException e) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }
}

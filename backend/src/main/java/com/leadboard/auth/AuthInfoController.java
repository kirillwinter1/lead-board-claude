package com.leadboard.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Per-user authorization metadata. Frontends use these endpoints to gate
 * UI affordances (e.g. drag-and-drop) against the same rules the backend
 * applies in {@link AuthorizationService}.
 */
@RestController
@RequestMapping("/api/auth")
@PreAuthorize("isAuthenticated()")
public class AuthInfoController {

    private final AuthorizationService authorizationService;

    public AuthInfoController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * Team ids the current user can manage. ADMIN can manage every team, so
     * {@code admin=true} tells the frontend to ignore the {@code teamIds} list
     * and treat all teams as manageable. For TEAM_LEAD the list is the set of
     * teams they belong to (via team_members.jira_account_id).
     */
    @GetMapping("/my-teams")
    public ResponseEntity<MyTeamsResponse> getMyTeams() {
        boolean admin = authorizationService.isAdmin();
        Set<Long> teamIds = admin ? Set.of() : authorizationService.getUserTeamIds();
        return ResponseEntity.ok(new MyTeamsResponse(admin, teamIds));
    }

    public record MyTeamsResponse(boolean admin, Set<Long> teamIds) {}
}

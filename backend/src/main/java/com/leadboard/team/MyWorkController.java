package com.leadboard.team;

import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.team.dto.MyWorkResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Personal work desk — F88 "My Work". Serves the current user's active/upcoming
 * tasks, team queue, worklog calendar and personal analytics, resolved from the
 * Jira account id of the authenticated session (never a client-supplied id).
 */
@RestController
@RequestMapping("/api/me")
@PreAuthorize("isAuthenticated()")
public class MyWorkController {
    private final MyWorkService myWorkService;
    private final AuthorizationService authorizationService;

    public MyWorkController(MyWorkService myWorkService, AuthorizationService authorizationService) {
        this.myWorkService = myWorkService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/work")
    public ResponseEntity<MyWorkResponse> getMyWork(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long teamId) {
        LeadBoardAuthentication auth = authorizationService.getCurrentAuth();
        if (auth == null || auth.getAtlassianAccountId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(myWorkService.getMyWork(auth.getAtlassianAccountId(), from, to, teamId));
    }
}

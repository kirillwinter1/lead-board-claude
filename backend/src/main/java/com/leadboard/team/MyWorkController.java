package com.leadboard.team;

import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.jira.JiraWriteService;
import com.leadboard.team.dto.MyWorkResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

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
    private final MyWorklogService myWorklogService;
    private final AuthorizationService authorizationService;

    public MyWorkController(MyWorkService myWorkService, MyWorklogService myWorklogService,
                            AuthorizationService authorizationService) {
        this.myWorkService = myWorkService;
        this.myWorklogService = myWorklogService;
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

    public record LogTimeRequest(String issueKey, LocalDate date, BigDecimal hours, String comment) {}

    @PostMapping("/worklog")
    public ResponseEntity<Map<String, String>> logTime(@RequestBody LogTimeRequest req) {
        LeadBoardAuthentication auth = authorizationService.getCurrentAuth();
        if (auth == null || auth.getAtlassianAccountId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            String worklogId = myWorklogService.logTime(
                    auth.getAtlassianAccountId(), req.issueKey(), req.date(), req.hours(), req.comment());
            return ResponseEntity.ok(Map.of("worklogId", worklogId));
        } catch (MyWorklogService.LogTimeValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (MyWorklogService.LogTimeForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (JiraWriteService.NoUserTokenException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Jira session expired — re-login via Atlassian"));
        } catch (MyWorklogService.JiraNoIdException e) {
            // Distinct from the generic Jira-error branch below: warns the client not to
            // blindly retry, since the write may have already landed on Jira's side.
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        } catch (WebClientException e) {
            // Parent of WebClientResponseException (Jira 4xx/5xx) AND WebClientRequestException
            // (DNS failure, connect/read timeout) — both are "Jira unreachable/erroring" from
            // the caller's perspective.
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Jira API error"));
        }
    }
}

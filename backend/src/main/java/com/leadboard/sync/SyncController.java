package com.leadboard.sync;

import com.leadboard.config.JiraConfigResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final ChangelogImportService changelogImportService;
    private final JiraConfigResolver jiraConfigResolver;

    public SyncController(SyncService syncService,
                          ChangelogImportService changelogImportService,
                          JiraConfigResolver jiraConfigResolver) {
        this.syncService = syncService;
        this.changelogImportService = changelogImportService;
        this.jiraConfigResolver = jiraConfigResolver;
    }

    @GetMapping("/status")
    public ResponseEntity<SyncService.SyncStatus> getStatus() {
        return ResponseEntity.ok(syncService.getSyncStatus());
    }

    @GetMapping("/issue-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getIssueCount(
            @RequestParam(required = false) Integer months) {
        // BUG-45/BUG-65: Validate months parameter
        if (months != null && months <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "months must be > 0"));
        }
        if (months != null && months > 120) {
            return ResponseEntity.badRequest().body(Map.of("error", "months must be <= 120"));
        }
        return ResponseEntity.ok(syncService.countIssuesInJira(months));
    }

    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> triggerSync(
            @RequestParam(required = false) Integer months) {
        // BUG-45/BUG-65: Validate months parameter
        if (months != null && months <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "months must be > 0"));
        }
        if (months != null && months > 120) {
            return ResponseEntity.badRequest().body(Map.of("error", "months must be <= 120"));
        }
        return ResponseEntity.ok(syncService.triggerSync(months));
    }

    @GetMapping("/import-changelogs/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> countIssuesForImport(
            @RequestParam(required = false) Integer months) {
        String projectKey = jiraConfigResolver.getProjectKey();
        if (projectKey == null || projectKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project key not configured"));
        }

        return ResponseEntity.ok(changelogImportService.countIssuesForImport(projectKey, months));
    }

    @PostMapping("/import-changelogs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> importChangelogs(
            @RequestParam(required = false) Integer months) {
        String projectKey = jiraConfigResolver.getProjectKey();
        if (projectKey == null || projectKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project key not configured"));
        }

        changelogImportService.importAllChangelogsAsync(projectKey, months);

        String period = months != null && months > 0
                ? "updated in last " + months + " months"
                : "all issues";
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Changelog import started for project " + projectKey + " (" + period + ")"
        ));
    }
}

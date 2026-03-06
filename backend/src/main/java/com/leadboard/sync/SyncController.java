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
    private final WorklogImportService worklogImportService;
    private final JiraConfigResolver jiraConfigResolver;

    public SyncController(SyncService syncService,
                          ChangelogImportService changelogImportService,
                          WorklogImportService worklogImportService,
                          JiraConfigResolver jiraConfigResolver) {
        this.syncService = syncService;
        this.changelogImportService = changelogImportService;
        this.worklogImportService = worklogImportService;
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
        java.util.List<String> allKeys = jiraConfigResolver.getActiveProjectKeys();
        if (allKeys.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project key not configured"));
        }

        // Sum counts across all projects
        int totalIssueCount = 0;
        int totalIssues = 0;
        for (String key : allKeys) {
            Map<String, Object> counts = changelogImportService.countIssuesForImport(key, months);
            totalIssueCount += ((Number) counts.getOrDefault("issueCount", 0)).intValue();
            totalIssues += ((Number) counts.getOrDefault("totalIssues", 0)).intValue();
        }
        return ResponseEntity.ok(Map.of("issueCount", totalIssueCount, "totalIssues", totalIssues));
    }

    @PostMapping("/import-changelogs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> importChangelogs(
            @RequestParam(required = false) Integer months) {
        java.util.List<String> allKeys = jiraConfigResolver.getActiveProjectKeys();
        if (allKeys.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project key not configured"));
        }

        for (String key : allKeys) {
            changelogImportService.importAllChangelogsAsync(key, months);
        }

        String period = months != null && months > 0
                ? "updated in last " + months + " months"
                : "all issues";
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Changelog import started for projects " + allKeys + " (" + period + ")"
        ));
    }

    @PostMapping("/import-worklogs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> importWorklogs() {
        java.util.List<String> allKeys = jiraConfigResolver.getActiveProjectKeys();
        if (allKeys.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project key not configured"));
        }

        for (String key : allKeys) {
            worklogImportService.importAllWorklogsAsync(key);
        }

        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Worklog import started for projects " + allKeys
        ));
    }

    @GetMapping("/projects")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<SyncService.ProjectSyncStatus>> getPerProjectSyncStatus() {
        return ResponseEntity.ok(syncService.getPerProjectSyncStatus());
    }
}

package com.leadboard.quality;

import com.leadboard.sync.JiraIssueRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bug-sla")
public class BugSlaController {

    private final BugSlaService bugSlaService;
    private final JiraIssueRepository jiraIssueRepository;

    public BugSlaController(BugSlaService bugSlaService, JiraIssueRepository jiraIssueRepository) {
        this.bugSlaService = bugSlaService;
        this.jiraIssueRepository = jiraIssueRepository;
    }

    @GetMapping
    public ResponseEntity<List<BugSlaConfigEntity>> getAllSlaConfigs() {
        return ResponseEntity.ok(bugSlaService.getAllSlaConfigs());
    }

    /**
     * Returns all unique priority names from issues in the database.
     */
    @GetMapping("/priorities")
    public ResponseEntity<List<String>> getAvailablePriorities() {
        return ResponseEntity.ok(jiraIssueRepository.findDistinctPriorities());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<BugSlaConfigEntity> createSla(@RequestBody Map<String, Object> body) {
        String priority = (String) body.get("priority");
        Object hoursObj = body.get("maxResolutionHours");
        int hours;
        if (hoursObj instanceof Integer i) {
            hours = i;
        } else if (hoursObj instanceof Number n) {
            hours = n.intValue();
        } else {
            return ResponseEntity.badRequest().build();
        }
        if (priority == null || priority.isBlank() || hours < 1) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(bugSlaService.createSla(priority, hours));
    }

    @PutMapping("/{priority}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<BugSlaConfigEntity> updateSla(
            @PathVariable String priority,
            @RequestBody Map<String, Integer> body
    ) {
        Integer hours = body.get("maxResolutionHours");
        if (hours == null || hours < 1) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(bugSlaService.updateSla(priority, hours));
    }

    @DeleteMapping("/{priority}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<Void> deleteSla(@PathVariable String priority) {
        bugSlaService.deleteSla(priority);
        return ResponseEntity.noContent().build();
    }
}

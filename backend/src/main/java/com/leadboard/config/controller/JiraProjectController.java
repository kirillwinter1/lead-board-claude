package com.leadboard.config.controller;

import com.leadboard.config.entity.JiraProjectEntity;
import com.leadboard.config.service.JiraProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/jira-projects")
@PreAuthorize("hasRole('ADMIN')")
public class JiraProjectController {

    private final JiraProjectService jiraProjectService;

    public JiraProjectController(JiraProjectService jiraProjectService) {
        this.jiraProjectService = jiraProjectService;
    }

    @GetMapping
    public List<JiraProjectEntity> listAll() {
        return jiraProjectService.listAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        String projectKey = body.get("projectKey");
        if (projectKey == null || projectKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectKey is required"));
        }
        String displayName = body.get("displayName");
        try {
            JiraProjectEntity entity = jiraProjectService.create(projectKey, displayName);
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String displayName = body.get("displayName") != null ? body.get("displayName").toString() : null;
        Boolean active = body.get("active") instanceof Boolean b ? b : null;
        Boolean syncEnabled = body.get("syncEnabled") instanceof Boolean b2 ? b2 : null;
        try {
            JiraProjectEntity entity = jiraProjectService.update(id, displayName, active, syncEnabled);
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        jiraProjectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

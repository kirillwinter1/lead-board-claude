package com.leadboard.config.controller;

import com.leadboard.config.dto.JiraProjectUpdateRequest;
import com.leadboard.config.entity.JiraProjectEntity;
import com.leadboard.config.service.JiraProjectService;
import jakarta.validation.Valid;
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
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody JiraProjectUpdateRequest request) {
        try {
            JiraProjectEntity entity = jiraProjectService.update(
                    id, request.displayName(), request.active(), request.syncEnabled());
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

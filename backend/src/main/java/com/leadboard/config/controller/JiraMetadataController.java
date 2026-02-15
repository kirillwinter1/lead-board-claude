package com.leadboard.config.controller;

import com.leadboard.config.service.JiraMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Admin API for fetching Jira metadata (issue types, statuses, link types).
 * Used for workflow configuration setup.
 */
@RestController
@RequestMapping("/api/admin/jira-metadata")
public class JiraMetadataController {

    private final JiraMetadataService metadataService;

    public JiraMetadataController(JiraMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/issue-types")
    public ResponseEntity<List<Map<String, Object>>> getIssueTypes() {
        return ResponseEntity.ok(metadataService.getIssueTypes());
    }

    @GetMapping("/statuses")
    public ResponseEntity<List<Map<String, Object>>> getStatuses() {
        return ResponseEntity.ok(metadataService.getStatuses());
    }

    @GetMapping("/link-types")
    public ResponseEntity<List<Map<String, Object>>> getLinkTypes() {
        return ResponseEntity.ok(metadataService.getLinkTypes());
    }

    @GetMapping("/workflows")
    public ResponseEntity<List<Map<String, Object>>> getWorkflows() {
        return ResponseEntity.ok(metadataService.getWorkflows());
    }
}

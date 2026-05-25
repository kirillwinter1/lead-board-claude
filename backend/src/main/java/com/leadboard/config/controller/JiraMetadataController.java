package com.leadboard.config.controller;

import com.leadboard.config.service.JiraMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Issue types power the issue-type icons rendered on every screen for every
     * authenticated user (see SecurityConfig carve-out). It is intentionally NOT
     * gated by @PreAuthorize — see {@link SecurityConfig} filter chain.
     */
    @GetMapping("/issue-types")
    public ResponseEntity<List<Map<String, Object>>> getIssueTypes() {
        return ResponseEntity.ok(metadataService.getIssueTypes());
    }

    @GetMapping("/statuses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getStatuses() {
        return ResponseEntity.ok(metadataService.getStatuses());
    }

    @GetMapping("/link-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getLinkTypes() {
        return ResponseEntity.ok(metadataService.getLinkTypes());
    }

    @GetMapping("/priorities")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getPriorities() {
        return ResponseEntity.ok(metadataService.getPriorities());
    }

    @GetMapping("/custom-fields")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getCustomFields(
            @RequestParam(required = false, defaultValue = "") String keyword) {
        return ResponseEntity.ok(metadataService.getCustomFields(keyword));
    }
}

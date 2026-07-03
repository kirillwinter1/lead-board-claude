package com.leadboard.config.controller;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.dto.*;
import com.leadboard.config.repository.*;
import com.leadboard.config.service.MappingAutoDetectService;
import com.leadboard.config.service.MappingValidationService;
import com.leadboard.config.service.WorkflowConfigAdminService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.sync.JiraIssueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin API for workflow configuration management.
 * All endpoints require ADMIN role (secured via SecurityConfig).
 *
 * <p>Routing, authorization and request/response translation only — mapping/repository
 * access and business rules live in {@link WorkflowConfigAdminService}.</p>
 */
@RestController
@RequestMapping("/api/admin/workflow-config")
@PreAuthorize("hasRole('ADMIN')")
public class WorkflowConfigController {

    private final WorkflowConfigAdminService adminService;

    public WorkflowConfigController(
            ProjectConfigurationRepository configRepo,
            WorkflowRoleRepository roleRepo,
            IssueTypeMappingRepository issueTypeRepo,
            StatusMappingRepository statusMappingRepo,
            LinkTypeMappingRepository linkTypeRepo,
            WorkflowConfigService workflowConfigService,
            MappingValidationService validationService,
            MappingAutoDetectService autoDetectService,
            ObjectMapper objectMapper,
            JiraIssueRepository jiraIssueRepository,
            JiraConfigResolver jiraConfigResolver,
            JiraClient jiraClient
    ) {
        this.adminService = new WorkflowConfigAdminService(
                configRepo,
                roleRepo,
                issueTypeRepo,
                statusMappingRepo,
                linkTypeRepo,
                workflowConfigService,
                validationService,
                autoDetectService,
                objectMapper,
                jiraIssueRepository,
                jiraConfigResolver,
                jiraClient
        );
    }

    // ==================== Project Configs List ====================

    @GetMapping("/projects")
    public ResponseEntity<List<Map<String, Object>>> getProjectConfigs() {
        return ResponseEntity.ok(adminService.getProjectConfigs());
    }

    // ==================== Full Config ====================

    @GetMapping
    public ResponseEntity<WorkflowConfigResponse> getConfig(
            @RequestParam(required = false) String projectKey) {
        WorkflowConfigResponse response = adminService.getConfig(projectKey);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<WorkflowConfigResponse> updateProjectConfig(
            @RequestParam(required = false) String projectKey,
            @RequestBody ProjectConfigUpdateRequest request) {
        try {
            adminService.updateProjectConfig(projectKey, request);
        } catch (WorkflowConfigAdminService.InvalidRequestException e) {
            return ResponseEntity.badRequest().build();
        }
        return getConfig(projectKey);
    }

    // ==================== Roles ====================

    @GetMapping("/roles")
    public ResponseEntity<List<WorkflowRoleDto>> getRoles(
            @RequestParam(required = false) String projectKey) {
        return ResponseEntity.ok(adminService.getRoles(projectKey));
    }

    @Transactional
    @PutMapping("/roles")
    public ResponseEntity<List<WorkflowRoleDto>> updateRoles(
            @RequestParam(required = false) String projectKey,
            @RequestBody List<WorkflowRoleDto> roles) {
        if (roles == null || roles.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(adminService.updateRoles(projectKey, roles));
    }

    // ==================== Issue Types ====================

    @GetMapping("/issue-types")
    public ResponseEntity<List<IssueTypeMappingDto>> getIssueTypes(
            @RequestParam(required = false) String projectKey) {
        return ResponseEntity.ok(adminService.getIssueTypes(projectKey));
    }

    @Transactional
    @PutMapping("/issue-types")
    public ResponseEntity<List<IssueTypeMappingDto>> updateIssueTypes(
            @RequestParam(required = false) String projectKey,
            @RequestBody List<IssueTypeMappingDto> issueTypes) {
        if (issueTypes == null || issueTypes.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(adminService.updateIssueTypes(projectKey, issueTypes));
    }

    // ==================== Statuses ====================

    @GetMapping("/statuses")
    public ResponseEntity<List<StatusMappingDto>> getStatuses(
            @RequestParam(required = false) String projectKey) {
        return ResponseEntity.ok(adminService.getStatuses(projectKey));
    }

    @Transactional
    @PutMapping("/statuses")
    public ResponseEntity<List<StatusMappingDto>> updateStatuses(
            @RequestParam(required = false) String projectKey,
            @RequestBody List<StatusMappingDto> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(adminService.updateStatuses(projectKey, statuses));
    }

    // ==================== Status Issue Counts ====================

    @GetMapping("/status-issue-counts")
    public ResponseEntity<List<Map<String, Object>>> getStatusIssueCounts() {
        return ResponseEntity.ok(adminService.getStatusIssueCounts());
    }

    // ==================== Link Types ====================

    @GetMapping("/link-types")
    public ResponseEntity<List<LinkTypeMappingDto>> getLinkTypes(
            @RequestParam(required = false) String projectKey) {
        return ResponseEntity.ok(adminService.getLinkTypes(projectKey));
    }

    @Transactional
    @PutMapping("/link-types")
    public ResponseEntity<List<LinkTypeMappingDto>> updateLinkTypes(
            @RequestParam(required = false) String projectKey,
            @RequestBody List<LinkTypeMappingDto> linkTypes) {
        if (linkTypes == null || linkTypes.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(adminService.updateLinkTypes(projectKey, linkTypes));
    }

    // ==================== Detect statuses for a single issue type ====================

    @PostMapping("/issue-types/{typeName}/detect-statuses")
    @Transactional
    public ResponseEntity<Map<String, Object>> detectStatusesForType(
            @PathVariable String typeName,
            @RequestParam(required = false) String projectKey,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adminService.detectStatusesForType(typeName, projectKey, body));
    }

    @PostMapping("/statuses/resort-by-category")
    @Transactional
    public ResponseEntity<Map<String, Object>> resortStatusesByCategory(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adminService.resortStatusesByCategory(body));
    }

    // ==================== Validation ====================

    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validate() {
        return ResponseEntity.ok(adminService.validate());
    }

    // ==================== Auto-detect ====================

    @PostMapping("/auto-detect")
    public ResponseEntity<MappingAutoDetectService.AutoDetectResult> autoDetect(
            @RequestParam(required = false) String projectKey) {
        return ResponseEntity.ok(adminService.autoDetect(projectKey));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConfigStatus() {
        return ResponseEntity.ok(adminService.getConfigStatus());
    }

    // ==================== Epic Link Auto-Detect ====================

    @GetMapping("/detect-epic-link")
    public ResponseEntity<Map<String, Object>> detectEpicLinkMode() {
        return ResponseEntity.ok(adminService.detectEpicLinkMode());
    }
}

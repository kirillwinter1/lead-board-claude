package com.leadboard.config.controller;

import com.leadboard.config.dto.WorkflowRoleDto;
import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.entity.IssueTypeMappingEntity;
import com.leadboard.config.entity.StatusMappingEntity;
import com.leadboard.config.entity.WorkflowRoleEntity;
import com.leadboard.config.repository.IssueTypeMappingRepository;
import com.leadboard.config.repository.StatusMappingRepository;
import com.leadboard.config.repository.WorkflowRoleRepository;
import com.leadboard.config.service.StatusColorResolver;
import com.leadboard.config.service.WorkflowConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public (no auth required) endpoints for workflow configuration.
 * Used by frontend to dynamically render roles, types, etc.
 */
@RestController
@RequestMapping("/api/config/workflow")
public class PublicConfigController {

    private final WorkflowConfigService workflowConfigService;
    private final IssueTypeMappingRepository issueTypeRepo;
    private final StatusMappingRepository statusMappingRepo;
    private final WorkflowRoleRepository workflowRoleRepo;

    public PublicConfigController(
            WorkflowConfigService workflowConfigService,
            IssueTypeMappingRepository issueTypeRepo,
            StatusMappingRepository statusMappingRepo,
            WorkflowRoleRepository workflowRoleRepo
    ) {
        this.workflowConfigService = workflowConfigService;
        this.issueTypeRepo = issueTypeRepo;
        this.statusMappingRepo = statusMappingRepo;
        this.workflowRoleRepo = workflowRoleRepo;
    }

    @GetMapping("/roles")
    public ResponseEntity<List<WorkflowRoleDto>> getRoles() {
        List<WorkflowRoleEntity> roles = workflowConfigService.getRolesInPipelineOrder();
        List<WorkflowRoleDto> dtos = roles.stream()
                .map(e -> new WorkflowRoleDto(e.getId(), e.getCode(), e.getDisplayName(),
                        e.getColor(), e.getSortOrder(), e.isDefault()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/issue-type-categories")
    public ResponseEntity<Map<String, String>> getIssueTypeCategories() {
        List<Long> configIds = workflowConfigService.getAllConfigIds();
        if (configIds.isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Long configId : configIds) {
            for (IssueTypeMappingEntity m : issueTypeRepo.findByConfigId(configId)) {
                if (m.getBoardCategory() == null) continue; // Skip unmapped types (BUG-66)
                result.putIfAbsent(m.getJiraTypeName(), m.getBoardCategory().name());
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status-styles")
    public ResponseEntity<Map<String, Map<String, String>>> getStatusStyles() {
        List<Long> configIds = workflowConfigService.getAllConfigIds();
        if (configIds.isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        // The map is keyed by status name only, but the same name can be mapped in several
        // scopes (e.g. "Waiting SA" in both BUG and STORY). Timeline/board consumers care
        // about the STORY semantics, so a STORY row replaces a same-named row from any
        // other scope; otherwise first-wins as before.
        Set<String> fromStory = new HashSet<>();
        for (Long configId : configIds) {
            Map<String, String> roleColors = new LinkedHashMap<>();
            for (WorkflowRoleEntity role : workflowRoleRepo.findByConfigIdOrderBySortOrderAsc(configId)) {
                roleColors.put(role.getCode(), role.getColor());
            }
            for (StatusMappingEntity s : statusMappingRepo.findByConfigId(configId)) {
                String name = s.getJiraStatusName();
                boolean isStory = s.getIssueCategory() == BoardCategory.STORY;
                if (!result.containsKey(name) || (isStory && !fromStory.contains(name))) {
                    Map<String, String> style = new LinkedHashMap<>();
                    style.put("color", StatusColorResolver.resolve(s, roleColors));
                    style.put("statusCategory", s.getStatusCategory().name());
                    style.put("statusKind", s.getStatusKind() == null ? null : s.getStatusKind().name());
                    result.put(name, style);
                    if (isStory) fromStory.add(name);
                }
            }
        }
        return ResponseEntity.ok(result);
    }
}

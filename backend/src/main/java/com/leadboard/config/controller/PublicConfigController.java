package com.leadboard.config.controller;

import com.leadboard.config.dto.WorkflowRoleDto;
import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.entity.IssueTypeMappingEntity;
import com.leadboard.config.entity.StatusMappingEntity;
import com.leadboard.config.entity.WorkflowRoleEntity;
import com.leadboard.config.repository.IssueTypeMappingRepository;
import com.leadboard.config.repository.StatusMappingRepository;
import com.leadboard.config.service.WorkflowConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public PublicConfigController(
            WorkflowConfigService workflowConfigService,
            IssueTypeMappingRepository issueTypeRepo,
            StatusMappingRepository statusMappingRepo
    ) {
        this.workflowConfigService = workflowConfigService;
        this.issueTypeRepo = issueTypeRepo;
        this.statusMappingRepo = statusMappingRepo;
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
        Long configId = workflowConfigService.getDefaultConfigId();
        if (configId == null) {
            return ResponseEntity.ok(Map.of());
        }
        List<IssueTypeMappingEntity> mappings = issueTypeRepo.findByConfigId(configId);
        Map<String, String> result = new LinkedHashMap<>();
        for (IssueTypeMappingEntity m : mappings) {
            result.put(m.getJiraTypeName(), m.getBoardCategory().name());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status-styles")
    public ResponseEntity<Map<String, Map<String, String>>> getStatusStyles() {
        Long configId = workflowConfigService.getDefaultConfigId();
        if (configId == null) {
            return ResponseEntity.ok(Map.of());
        }
        List<StatusMappingEntity> statuses = statusMappingRepo.findByConfigId(configId);
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (StatusMappingEntity s : statuses) {
            String name = s.getJiraStatusName();
            if (!result.containsKey(name)) {
                Map<String, String> style = new LinkedHashMap<>();
                style.put("color", s.getColor());
                style.put("statusCategory", s.getStatusCategory().name());
                result.put(name, style);
            }
        }
        return ResponseEntity.ok(result);
    }
}

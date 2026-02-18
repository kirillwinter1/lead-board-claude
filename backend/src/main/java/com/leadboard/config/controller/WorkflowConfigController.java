package com.leadboard.config.controller;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.dto.*;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.config.service.MappingAutoDetectService;
import com.leadboard.config.service.MappingValidationService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin API for workflow configuration management.
 * All endpoints require ADMIN role (secured via SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/workflow-config")
public class WorkflowConfigController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConfigController.class);

    private final ProjectConfigurationRepository configRepo;
    private final WorkflowRoleRepository roleRepo;
    private final IssueTypeMappingRepository issueTypeRepo;
    private final StatusMappingRepository statusMappingRepo;
    private final LinkTypeMappingRepository linkTypeRepo;
    private final WorkflowConfigService workflowConfigService;
    private final MappingValidationService validationService;
    private final MappingAutoDetectService autoDetectService;
    private final ObjectMapper objectMapper;
    private final JiraIssueRepository jiraIssueRepository;
    private final JiraProperties jiraProperties;

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
            JiraProperties jiraProperties
    ) {
        this.configRepo = configRepo;
        this.roleRepo = roleRepo;
        this.issueTypeRepo = issueTypeRepo;
        this.statusMappingRepo = statusMappingRepo;
        this.linkTypeRepo = linkTypeRepo;
        this.workflowConfigService = workflowConfigService;
        this.validationService = validationService;
        this.autoDetectService = autoDetectService;
        this.objectMapper = objectMapper;
        this.jiraIssueRepository = jiraIssueRepository;
        this.jiraProperties = jiraProperties;
    }

    // ==================== Full Config ====================

    @GetMapping
    public ResponseEntity<WorkflowConfigResponse> getConfig() {
        ProjectConfigurationEntity config = getDefaultConfig();
        if (config == null) {
            return ResponseEntity.notFound().build();
        }

        Long configId = config.getId();
        Map<String, Integer> scoreWeights = parseScoreWeights(config.getStatusScoreWeights());

        return ResponseEntity.ok(new WorkflowConfigResponse(
                configId,
                config.getName(),
                config.getProjectKey(),
                mapRoles(roleRepo.findByConfigIdOrderBySortOrderAsc(configId)),
                mapIssueTypes(issueTypeRepo.findByConfigId(configId)),
                mapStatuses(statusMappingRepo.findByConfigId(configId)),
                mapLinkTypes(linkTypeRepo.findByConfigId(configId)),
                scoreWeights,
                config.getPlanningAllowedCategories(),
                config.getTimeLoggingAllowedCategories(),
                config.getEpicLinkType(),
                config.getEpicLinkName()
        ));
    }

    @PutMapping
    public ResponseEntity<WorkflowConfigResponse> updateProjectConfig(@RequestBody ProjectConfigUpdateRequest request) {
        ProjectConfigurationEntity config = getOrCreateDefaultConfig();

        if (request.name() != null) {
            config.setName(request.name());
        }
        if (request.statusScoreWeights() != null) {
            try {
                config.setStatusScoreWeights(objectMapper.writeValueAsString(request.statusScoreWeights()));
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }
        if (request.planningAllowedCategories() != null) {
            config.setPlanningAllowedCategories(request.planningAllowedCategories());
        }
        if (request.timeLoggingAllowedCategories() != null) {
            config.setTimeLoggingAllowedCategories(request.timeLoggingAllowedCategories());
        }
        if (request.epicLinkType() != null) {
            config.setEpicLinkType(request.epicLinkType());
        }
        if (request.epicLinkName() != null) {
            config.setEpicLinkName(request.epicLinkName());
        }

        configRepo.save(config);
        workflowConfigService.clearCache();
        log.info("Project configuration updated: {}", config.getName());

        return getConfig();
    }

    // ==================== Roles ====================

    @GetMapping("/roles")
    public ResponseEntity<List<WorkflowRoleDto>> getRoles() {
        ProjectConfigurationEntity config = getDefaultConfig();
        if (config == null) return ResponseEntity.ok(List.of());

        return ResponseEntity.ok(mapRoles(roleRepo.findByConfigIdOrderBySortOrderAsc(config.getId())));
    }

    @Transactional
    @PutMapping("/roles")
    public ResponseEntity<List<WorkflowRoleDto>> updateRoles(@RequestBody List<WorkflowRoleDto> roles) {
        ProjectConfigurationEntity config = getOrCreateDefaultConfig();
        Long configId = config.getId();

        // Delete existing and replace
        roleRepo.deleteByConfigId(configId);
        roleRepo.flush();

        for (WorkflowRoleDto dto : roles) {
            WorkflowRoleEntity entity = new WorkflowRoleEntity();
            entity.setConfigId(configId);
            entity.setCode(dto.code());
            entity.setDisplayName(dto.displayName());
            entity.setColor(dto.color());
            entity.setSortOrder(dto.sortOrder());
            entity.setDefault(dto.isDefault());
            roleRepo.save(entity);
        }

        workflowConfigService.clearCache();
        log.info("Updated {} workflow roles", roles.size());

        return ResponseEntity.ok(mapRoles(roleRepo.findByConfigIdOrderBySortOrderAsc(configId)));
    }

    // ==================== Issue Types ====================

    @GetMapping("/issue-types")
    public ResponseEntity<List<IssueTypeMappingDto>> getIssueTypes() {
        ProjectConfigurationEntity config = getDefaultConfig();
        if (config == null) return ResponseEntity.ok(List.of());

        return ResponseEntity.ok(mapIssueTypes(issueTypeRepo.findByConfigId(config.getId())));
    }

    @Transactional
    @PutMapping("/issue-types")
    public ResponseEntity<List<IssueTypeMappingDto>> updateIssueTypes(@RequestBody List<IssueTypeMappingDto> issueTypes) {
        ProjectConfigurationEntity config = getOrCreateDefaultConfig();
        Long configId = config.getId();

        issueTypeRepo.deleteByConfigId(configId);
        issueTypeRepo.flush();

        for (IssueTypeMappingDto dto : issueTypes) {
            IssueTypeMappingEntity entity = new IssueTypeMappingEntity();
            entity.setConfigId(configId);
            entity.setJiraTypeName(dto.jiraTypeName());
            entity.setBoardCategory(dto.boardCategory());
            entity.setWorkflowRoleCode(dto.workflowRoleCode());
            issueTypeRepo.save(entity);
        }

        workflowConfigService.clearCache();
        log.info("Updated {} issue type mappings", issueTypes.size());

        return ResponseEntity.ok(mapIssueTypes(issueTypeRepo.findByConfigId(configId)));
    }

    // ==================== Statuses ====================

    @GetMapping("/statuses")
    public ResponseEntity<List<StatusMappingDto>> getStatuses() {
        ProjectConfigurationEntity config = getDefaultConfig();
        if (config == null) return ResponseEntity.ok(List.of());

        return ResponseEntity.ok(mapStatuses(statusMappingRepo.findByConfigId(config.getId())));
    }

    @Transactional
    @PutMapping("/statuses")
    public ResponseEntity<List<StatusMappingDto>> updateStatuses(@RequestBody List<StatusMappingDto> statuses) {
        ProjectConfigurationEntity config = getOrCreateDefaultConfig();
        Long configId = config.getId();

        statusMappingRepo.deleteByConfigId(configId);
        statusMappingRepo.flush();

        for (StatusMappingDto dto : statuses) {
            StatusMappingEntity entity = new StatusMappingEntity();
            entity.setConfigId(configId);
            entity.setJiraStatusName(dto.jiraStatusName());
            entity.setIssueCategory(dto.issueCategory());
            entity.setStatusCategory(dto.statusCategory());
            entity.setWorkflowRoleCode(dto.workflowRoleCode());
            entity.setSortOrder(dto.sortOrder());
            entity.setScoreWeight(dto.scoreWeight());
            entity.setColor(dto.color());
            statusMappingRepo.save(entity);
        }

        workflowConfigService.clearCache();
        log.info("Updated {} status mappings", statuses.size());

        return ResponseEntity.ok(mapStatuses(statusMappingRepo.findByConfigId(configId)));
    }

    // ==================== Status Issue Counts ====================

    @GetMapping("/status-issue-counts")
    public ResponseEntity<List<Map<String, Object>>> getStatusIssueCounts() {
        List<Object[]> rows = jiraIssueRepository.countByStatusAndBoardCategory();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(Map.of(
                    "jiraStatusName", row[0],
                    "issueCategory", row[1],
                    "count", row[2]
            ));
        }
        return ResponseEntity.ok(result);
    }

    // ==================== Link Types ====================

    @GetMapping("/link-types")
    public ResponseEntity<List<LinkTypeMappingDto>> getLinkTypes() {
        ProjectConfigurationEntity config = getDefaultConfig();
        if (config == null) return ResponseEntity.ok(List.of());

        return ResponseEntity.ok(mapLinkTypes(linkTypeRepo.findByConfigId(config.getId())));
    }

    @Transactional
    @PutMapping("/link-types")
    public ResponseEntity<List<LinkTypeMappingDto>> updateLinkTypes(@RequestBody List<LinkTypeMappingDto> linkTypes) {
        ProjectConfigurationEntity config = getOrCreateDefaultConfig();
        Long configId = config.getId();

        linkTypeRepo.deleteByConfigId(configId);
        linkTypeRepo.flush();

        for (LinkTypeMappingDto dto : linkTypes) {
            LinkTypeMappingEntity entity = new LinkTypeMappingEntity();
            entity.setConfigId(configId);
            entity.setJiraLinkTypeName(dto.jiraLinkTypeName());
            entity.setLinkCategory(dto.linkCategory());
            linkTypeRepo.save(entity);
        }

        workflowConfigService.clearCache();
        log.info("Updated {} link type mappings", linkTypes.size());

        return ResponseEntity.ok(mapLinkTypes(linkTypeRepo.findByConfigId(configId)));
    }

    // ==================== Detect statuses for a single issue type ====================

    @PostMapping("/issue-types/{typeName}/detect-statuses")
    @Transactional
    public ResponseEntity<Map<String, Object>> detectStatusesForType(
            @PathVariable String typeName,
            @RequestBody Map<String, String> body) {

        String categoryStr = body.get("boardCategory");
        if (categoryStr == null || categoryStr.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "boardCategory is required"));
        }

        BoardCategory category;
        try {
            category = BoardCategory.valueOf(categoryStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid boardCategory: " + categoryStr));
        }

        ProjectConfigurationEntity config = getOrCreateDefaultConfig();
        IssueTypeMappingEntity mapping = issueTypeRepo
                .findByConfigIdAndJiraTypeName(config.getId(), typeName)
                .orElseThrow(() -> new IllegalArgumentException("Issue type not found: " + typeName));

        mapping.setBoardCategory(category);
        if (category == BoardCategory.SUBTASK) {
            mapping.setWorkflowRoleCode(autoDetectService.detectRoleFromSubtaskName(typeName));
        } else {
            mapping.setWorkflowRoleCode(null);
        }
        issueTypeRepo.save(mapping);

        // Detect statuses for this type
        int statusCount = autoDetectService.detectStatusesForIssueType(typeName, category);
        workflowConfigService.clearCache();

        return ResponseEntity.ok(Map.of(
                "typeName", typeName,
                "boardCategory", category.name(),
                "statusesDetected", statusCount
        ));
    }

    // ==================== Validation ====================

    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validate() {
        ProjectConfigurationEntity config = getDefaultConfig();
        if (config == null) {
            return ResponseEntity.ok(ValidationResult.withIssues(
                    List.of("No default configuration found"), List.of()));
        }

        Long configId = config.getId();
        ValidationResult result = validationService.validate(
                mapRoles(roleRepo.findByConfigIdOrderBySortOrderAsc(configId)),
                mapIssueTypes(issueTypeRepo.findByConfigId(configId)),
                mapStatuses(statusMappingRepo.findByConfigId(configId)),
                mapLinkTypes(linkTypeRepo.findByConfigId(configId))
        );

        return ResponseEntity.ok(result);
    }

    // ==================== Auto-detect ====================

    @PostMapping("/auto-detect")
    public ResponseEntity<MappingAutoDetectService.AutoDetectResult> autoDetect() {
        log.info("Manual auto-detect triggered via API");
        var result = autoDetectService.autoDetect();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConfigStatus() {
        boolean configured = !autoDetectService.isConfigEmpty();
        return ResponseEntity.ok(Map.of("configured", configured));
    }

    // ==================== Helpers ====================

    private ProjectConfigurationEntity getDefaultConfig() {
        String envProjectKey = jiraProperties.getProjectKey();
        if (envProjectKey != null && !envProjectKey.isBlank()) {
            var byKey = configRepo.findByProjectKey(envProjectKey);
            if (byKey.isPresent()) return byKey.get();
        }
        return configRepo.findByIsDefaultTrue().orElse(null);
    }

    private ProjectConfigurationEntity getOrCreateDefaultConfig() {
        String envProjectKey = jiraProperties.getProjectKey();
        if (envProjectKey != null && !envProjectKey.isBlank()) {
            var byKey = configRepo.findByProjectKey(envProjectKey);
            if (byKey.isPresent()) return byKey.get();
        }
        return configRepo.findByIsDefaultTrue().orElseGet(() -> {
            ProjectConfigurationEntity config = new ProjectConfigurationEntity();
            config.setName("Default");
            config.setDefault(true);
            if (envProjectKey != null && !envProjectKey.isBlank()) {
                config.setProjectKey(envProjectKey);
            }
            return configRepo.save(config);
        });
    }

    private Map<String, Integer> parseScoreWeights(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<WorkflowRoleDto> mapRoles(List<WorkflowRoleEntity> entities) {
        return entities.stream()
                .map(e -> new WorkflowRoleDto(e.getId(), e.getCode(), e.getDisplayName(),
                        e.getColor(), e.getSortOrder(), e.isDefault()))
                .toList();
    }

    private List<IssueTypeMappingDto> mapIssueTypes(List<IssueTypeMappingEntity> entities) {
        return entities.stream()
                .map(e -> new IssueTypeMappingDto(e.getId(), e.getJiraTypeName(),
                        e.getBoardCategory(), e.getWorkflowRoleCode()))
                .toList();
    }

    private List<StatusMappingDto> mapStatuses(List<StatusMappingEntity> entities) {
        return entities.stream()
                .map(e -> new StatusMappingDto(e.getId(), e.getJiraStatusName(),
                        e.getIssueCategory(), e.getStatusCategory(),
                        e.getWorkflowRoleCode(), e.getSortOrder(), e.getScoreWeight(),
                        e.getColor()))
                .toList();
    }

    private List<LinkTypeMappingDto> mapLinkTypes(List<LinkTypeMappingEntity> entities) {
        return entities.stream()
                .map(e -> new LinkTypeMappingDto(e.getId(), e.getJiraLinkTypeName(), e.getLinkCategory()))
                .toList();
    }
}

package com.leadboard.config.service;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.dto.*;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraIssue;
import com.leadboard.sync.JiraIssueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository access and business logic backing {@link com.leadboard.config.controller.WorkflowConfigController}.
 *
 * <p>Deliberately NOT a Spring {@code @Service} bean: {@code WorkflowConfigController} is exercised by
 * {@code @WebMvcTest} slice tests ({@code WorkflowConfigControllerTest}, {@code WorkflowConfigControllerSecurityTest})
 * that {@code @MockBean} the repositories/services directly and verify interactions on those mocks
 * (e.g. {@code verify(roleRepo).deleteByConfigId(...)}). Making this class an injectable bean would require
 * those tests to additionally mock it, bypassing the repository verifications they rely on. Instead the
 * controller constructs this instance itself from the exact same Spring-managed collaborators it already
 * receives, so the mocked beans in those tests keep seeing the calls made on their behalf.</p>
 */
public class WorkflowConfigAdminService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConfigAdminService.class);

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
    private final JiraConfigResolver jiraConfigResolver;
    private final JiraClient jiraClient;

    public WorkflowConfigAdminService(
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
        this.jiraConfigResolver = jiraConfigResolver;
        this.jiraClient = jiraClient;
    }

    /**
     * Thrown for request-shape problems that must surface as an empty-bodied 400
     * (matching the controller's pre-refactor behavior), as opposed to
     * {@link IllegalArgumentException}, which {@code GlobalExceptionHandler} turns
     * into a 400 with a {@code {"error": message}} body.
     */
    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    // ==================== Project Configs List ====================

    public List<Map<String, Object>> getProjectConfigs() {
        List<String> allKeys = jiraConfigResolver.getAllProjectKeys();
        List<Map<String, Object>> result = new ArrayList<>();

        for (String key : allKeys) {
            var config = configRepo.findByProjectKey(key).orElse(null);
            var entry = new LinkedHashMap<String, Object>();
            entry.put("projectKey", key);
            entry.put("configured", config != null
                    && !issueTypeRepo.findByConfigId(config.getId()).isEmpty());
            entry.put("isDefault", config != null && config.isDefault());
            entry.put("configId", config != null ? config.getId() : null);
            result.add(entry);
        }

        return result;
    }

    // ==================== Full Config ====================

    /**
     * @return the config for {@code projectKey} (or the default config if null), or {@code null} if none exists.
     */
    public WorkflowConfigResponse getConfig(String projectKey) {
        ProjectConfigurationEntity config = projectKey != null
                ? getConfigForProject(projectKey)
                : getDefaultConfig();
        if (config == null) {
            return null;
        }

        Long configId = config.getId();
        Map<String, Integer> scoreWeights = parseScoreWeights(config.getStatusScoreWeights());

        return new WorkflowConfigResponse(
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
        );
    }

    /**
     * @throws InvalidRequestException if {@code request.statusScoreWeights()} cannot be serialized.
     */
    public void updateProjectConfig(String projectKey, ProjectConfigUpdateRequest request) {
        ProjectConfigurationEntity config = projectKey != null
                ? getOrCreateConfigForProject(projectKey)
                : getOrCreateDefaultConfig();

        if (request.name() != null) {
            config.setName(request.name());
        }
        if (request.statusScoreWeights() != null) {
            try {
                config.setStatusScoreWeights(objectMapper.writeValueAsString(request.statusScoreWeights()));
            } catch (Exception e) {
                throw new InvalidRequestException("Invalid statusScoreWeights");
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
    }

    // ==================== Roles ====================

    public List<WorkflowRoleDto> getRoles(String projectKey) {
        ProjectConfigurationEntity config = projectKey != null
                ? getConfigForProject(projectKey) : getDefaultConfig();
        if (config == null) return List.of();

        return mapRoles(roleRepo.findByConfigIdOrderBySortOrderAsc(config.getId()));
    }

    public List<WorkflowRoleDto> updateRoles(String projectKey, List<WorkflowRoleDto> roles) {
        ProjectConfigurationEntity config = projectKey != null
                ? getOrCreateConfigForProject(projectKey) : getOrCreateDefaultConfig();
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

        return mapRoles(roleRepo.findByConfigIdOrderBySortOrderAsc(configId));
    }

    // ==================== Issue Types ====================

    public List<IssueTypeMappingDto> getIssueTypes(String projectKey) {
        ProjectConfigurationEntity config = projectKey != null
                ? getConfigForProject(projectKey) : getDefaultConfig();
        if (config == null) return List.of();

        return mapIssueTypes(issueTypeRepo.findByConfigId(config.getId()));
    }

    public List<IssueTypeMappingDto> updateIssueTypes(String projectKey, List<IssueTypeMappingDto> issueTypes) {
        ProjectConfigurationEntity config = projectKey != null
                ? getOrCreateConfigForProject(projectKey) : getOrCreateDefaultConfig();
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

        return mapIssueTypes(issueTypeRepo.findByConfigId(configId));
    }

    // ==================== Statuses ====================

    public List<StatusMappingDto> getStatuses(String projectKey) {
        ProjectConfigurationEntity config = projectKey != null
                ? getConfigForProject(projectKey) : getDefaultConfig();
        if (config == null) return List.of();

        return mapStatuses(statusMappingRepo.findByConfigId(config.getId()));
    }

    public List<StatusMappingDto> updateStatuses(String projectKey, List<StatusMappingDto> statuses) {
        ProjectConfigurationEntity config = projectKey != null
                ? getOrCreateConfigForProject(projectKey) : getOrCreateDefaultConfig();
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
            entity.setStatusKind(dto.statusKind());
            statusMappingRepo.save(entity);
        }

        workflowConfigService.clearCache();
        log.info("Updated {} status mappings", statuses.size());

        return mapStatuses(statusMappingRepo.findByConfigId(configId));
    }

    // ==================== Status Issue Counts ====================

    public List<Map<String, Object>> getStatusIssueCounts() {
        List<Object[]> rows = jiraIssueRepository.countByStatusAndBoardCategory();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(Map.of(
                    "jiraStatusName", row[0],
                    "issueCategory", row[1],
                    "count", row[2]
            ));
        }
        return result;
    }

    // ==================== Link Types ====================

    public List<LinkTypeMappingDto> getLinkTypes(String projectKey) {
        ProjectConfigurationEntity config = projectKey != null
                ? getConfigForProject(projectKey) : getDefaultConfig();
        if (config == null) return List.of();

        return mapLinkTypes(linkTypeRepo.findByConfigId(config.getId()));
    }

    public List<LinkTypeMappingDto> updateLinkTypes(String projectKey, List<LinkTypeMappingDto> linkTypes) {
        ProjectConfigurationEntity config = projectKey != null
                ? getOrCreateConfigForProject(projectKey) : getOrCreateDefaultConfig();
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

        return mapLinkTypes(linkTypeRepo.findByConfigId(configId));
    }

    // ==================== Detect statuses for a single issue type ====================

    /**
     * @throws IllegalArgumentException on missing/invalid {@code boardCategory} or unknown issue type
     *         — mapped to a 400 with {@code {"error": message}} by {@code GlobalExceptionHandler}.
     */
    public Map<String, Object> detectStatusesForType(String typeName, String projectKey, Map<String, String> body) {
        String categoryStr = body.get("boardCategory");
        if (categoryStr == null || categoryStr.isEmpty()) {
            throw new IllegalArgumentException("boardCategory is required");
        }

        BoardCategory category;
        try {
            category = BoardCategory.valueOf(categoryStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid boardCategory: " + categoryStr);
        }

        ProjectConfigurationEntity config = projectKey != null
                ? getOrCreateConfigForProject(projectKey) : getOrCreateDefaultConfig();
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

        return Map.of(
                "typeName", typeName,
                "boardCategory", category.name(),
                "statusesDetected", statusCount
        );
    }

    /**
     * @throws IllegalArgumentException on missing/invalid {@code boardCategory} or missing configuration
     *         — mapped to a 400 with {@code {"error": message}} by {@code GlobalExceptionHandler}.
     */
    public Map<String, Object> resortStatusesByCategory(Map<String, String> body) {
        String categoryStr = body.get("boardCategory");
        if (categoryStr == null || categoryStr.isEmpty()) {
            throw new IllegalArgumentException("boardCategory is required");
        }
        BoardCategory category;
        try {
            category = BoardCategory.valueOf(categoryStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid boardCategory: " + categoryStr);
        }
        ProjectConfigurationEntity config = getDefaultConfig();
        if (config == null) {
            throw new IllegalArgumentException("No configuration found");
        }
        autoDetectService.resortStatusesByCategory(config.getId(), category);
        return Map.of("resorted", true, "boardCategory", category.name());
    }

    // ==================== Validation ====================

    public ValidationResult validate() {
        ProjectConfigurationEntity config = getDefaultConfig();
        if (config == null) {
            return ValidationResult.withIssues(
                    List.of("No default configuration found"), List.of());
        }

        Long configId = config.getId();
        return validationService.validate(
                mapRoles(roleRepo.findByConfigIdOrderBySortOrderAsc(configId)),
                mapIssueTypes(issueTypeRepo.findByConfigId(configId)),
                mapStatuses(statusMappingRepo.findByConfigId(configId)),
                mapLinkTypes(linkTypeRepo.findByConfigId(configId))
        );
    }

    // ==================== Auto-detect ====================

    public MappingAutoDetectService.AutoDetectResult autoDetect(String projectKey) {
        if (projectKey != null) {
            log.info("Manual auto-detect triggered via API for project {}", projectKey);
            return autoDetectService.autoDetectForProject(projectKey);
        }
        log.info("Manual auto-detect triggered via API");
        return autoDetectService.autoDetect();
    }

    public Map<String, Object> getConfigStatus() {
        boolean configured = !autoDetectService.isConfigEmpty();
        return Map.of("configured", configured);
    }

    // ==================== Epic Link Auto-Detect ====================

    public Map<String, Object> detectEpicLinkMode() {
        var projects = jiraIssueRepository.findByBoardCategory("PROJECT");
        if (projects.isEmpty()) {
            return Map.of("detected", false, "reason", "No PROJECT issues found");
        }

        var epicKeys = new HashSet<String>();
        for (var epic : jiraIssueRepository.findByBoardCategory("EPIC")) {
            epicKeys.add(epic.getIssueKey());
        }

        var projectKeys = projects.stream().map(p -> p.getIssueKey()).toList();
        var epicsByParentKey = jiraIssueRepository.findByParentKeyIn(projectKeys).stream()
                .collect(java.util.stream.Collectors.groupingBy(epic -> epic.getParentKey()));

        // Check parent mode: any EPIC with parentKey pointing to a PROJECT?
        int parentCount = 0;
        for (var proj : projects) {
            for (var epic : epicsByParentKey.getOrDefault(proj.getIssueKey(), List.of())) {
                if (epicKeys.contains(epic.getIssueKey())) {
                    parentCount++;
                }
            }
        }

        // Check issuelink mode: any PROJECT with childEpicKeys containing EPICs?
        int linkCount = 0;
        String sampleProjectKey = null;
        for (var proj : projects) {
            String[] linked = proj.getChildEpicKeys();
            if (linked != null) {
                for (String key : linked) {
                    if (epicKeys.contains(key)) {
                        linkCount++;
                        if (sampleProjectKey == null) {
                            sampleProjectKey = proj.getIssueKey();
                        }
                    }
                }
            }
        }

        if (parentCount > 0 && parentCount >= linkCount) {
            return Map.of(
                "detected", true,
                "epicLinkType", "parent",
                "parentCount", parentCount,
                "linkCount", linkCount
            );
        } else if (linkCount > 0) {
            // Detect specific link type name by fetching the PROJECT from Jira
            String detectedLinkName = detectLinkTypeName(sampleProjectKey, epicKeys);

            var result = new java.util.HashMap<String, Object>();
            result.put("detected", true);
            result.put("epicLinkType", "issuelink");
            result.put("parentCount", parentCount);
            result.put("linkCount", linkCount);
            if (detectedLinkName != null) {
                result.put("epicLinkName", detectedLinkName);
            }
            return result;
        }

        return Map.of(
            "detected", false,
            "reason", "No Project→Epic relationships found in synced data",
            "parentCount", parentCount,
            "linkCount", linkCount
        );
    }

    /**
     * Fetch a PROJECT issue from Jira and find which link type connects it to EPICs.
     * Returns the link direction label (e.g. "Epic Link: is child of") or null.
     */
    private String detectLinkTypeName(String projectKey, Set<String> epicKeys) {
        try {
            var response = jiraClient.search("key = " + projectKey, 1, null);
            if (response == null || response.getIssues() == null || response.getIssues().isEmpty()) {
                return null;
            }
            var issue = response.getIssues().get(0);
            var links = issue.getFields().getIssuelinks();
            if (links == null) return null;

            for (JiraIssue.JiraIssueLink link : links) {
                if (link.getType() == null) continue;
                // Check outward direction: PROJECT --outward--> EPIC
                if (link.getOutwardIssue() != null && epicKeys.contains(link.getOutwardIssue().getKey())) {
                    return link.getType().getOutward();
                }
                // Check inward direction: EPIC --inward--> PROJECT
                if (link.getInwardIssue() != null && epicKeys.contains(link.getInwardIssue().getKey())) {
                    return link.getType().getInward();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to detect link type name from Jira for {}: {}", projectKey, e.getMessage());
        }
        return null;
    }

    // ==================== Helpers ====================

    private ProjectConfigurationEntity getConfigForProject(String projectKey) {
        if (projectKey == null) return getDefaultConfig();
        return configRepo.findByProjectKey(projectKey).orElse(null);
    }

    private ProjectConfigurationEntity getOrCreateConfigForProject(String projectKey) {
        if (projectKey == null) return getOrCreateDefaultConfig();
        return configRepo.findByProjectKey(projectKey).orElseGet(() -> {
            boolean hasAny = configRepo.findByIsDefaultTrue().isPresent();
            ProjectConfigurationEntity config = new ProjectConfigurationEntity();
            config.setName(projectKey);
            config.setProjectKey(projectKey);
            config.setDefault(!hasAny);
            return configRepo.save(config);
        });
    }

    private ProjectConfigurationEntity getDefaultConfig() {
        String envProjectKey = jiraConfigResolver.getProjectKey();
        if (envProjectKey != null && !envProjectKey.isBlank()) {
            var byKey = configRepo.findByProjectKey(envProjectKey);
            if (byKey.isPresent()) return byKey.get();
        }
        return configRepo.findByIsDefaultTrue().orElse(null);
    }

    private ProjectConfigurationEntity getOrCreateDefaultConfig() {
        String envProjectKey = jiraConfigResolver.getProjectKey();
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
                        e.getColor(), e.getStatusKind()))
                .toList();
    }

    private List<LinkTypeMappingDto> mapLinkTypes(List<LinkTypeMappingEntity> entities) {
        return entities.stream()
                .map(e -> new LinkTypeMappingDto(e.getId(), e.getJiraLinkTypeName(), e.getLinkCategory()))
                .toList();
    }
}

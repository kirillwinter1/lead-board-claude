package com.leadboard.config.service;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.status.StatusCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Auto-detects workflow configuration from Jira project metadata.
 * Uses issue types, statuses, and link types fetched via JiraMetadataService
 * to populate workflow_roles, issue_type_mappings, status_mappings, and link_type_mappings.
 */
@Service
public class MappingAutoDetectService {

    private static final Logger log = LoggerFactory.getLogger(MappingAutoDetectService.class);

    private final JiraMetadataService jiraMetadataService;
    private final ProjectConfigurationRepository configRepo;
    private final WorkflowRoleRepository roleRepo;
    private final IssueTypeMappingRepository issueTypeRepo;
    private final StatusMappingRepository statusMappingRepo;
    private final LinkTypeMappingRepository linkTypeRepo;
    private final WorkflowConfigService workflowConfigService;
    private final JiraProperties jiraProperties;

    public MappingAutoDetectService(
            JiraMetadataService jiraMetadataService,
            ProjectConfigurationRepository configRepo,
            WorkflowRoleRepository roleRepo,
            IssueTypeMappingRepository issueTypeRepo,
            StatusMappingRepository statusMappingRepo,
            LinkTypeMappingRepository linkTypeRepo,
            WorkflowConfigService workflowConfigService,
            JiraProperties jiraProperties
    ) {
        this.jiraMetadataService = jiraMetadataService;
        this.configRepo = configRepo;
        this.roleRepo = roleRepo;
        this.issueTypeRepo = issueTypeRepo;
        this.statusMappingRepo = statusMappingRepo;
        this.linkTypeRepo = linkTypeRepo;
        this.workflowConfigService = workflowConfigService;
        this.jiraProperties = jiraProperties;
    }

    /**
     * Returns true if workflow config is empty (no roles AND no issue type mappings).
     */
    public boolean isConfigEmpty() {
        Long configId = getDefaultConfigId();
        if (configId == null) return true;
        return roleRepo.findByConfigIdOrderBySortOrderAsc(configId).isEmpty()
                && issueTypeRepo.findByConfigId(configId).isEmpty();
    }

    /**
     * Main auto-detect method. Fetches Jira metadata and populates all mapping tables.
     * Clears existing mappings before inserting (idempotent).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @SuppressWarnings("unchecked")
    public AutoDetectResult autoDetect() {
        log.info("Starting auto-detection of workflow configuration from Jira...");
        List<String> warnings = new ArrayList<>();

        Long configId = getOrCreateDefaultConfigId();

        // Fetch Jira metadata
        List<Map<String, Object>> issueTypes = jiraMetadataService.getIssueTypes();
        List<Map<String, Object>> statusesByType = jiraMetadataService.getStatuses();
        List<Map<String, Object>> linkTypes = jiraMetadataService.getLinkTypes();

        if (issueTypes.isEmpty()) {
            warnings.add("No issue types returned from Jira — check project configuration");
        }

        // Clear existing mappings and flush to avoid unique constraint violations
        roleRepo.deleteByConfigId(configId);
        issueTypeRepo.deleteByConfigId(configId);
        statusMappingRepo.deleteByConfigId(configId);
        linkTypeRepo.deleteByConfigId(configId);
        roleRepo.flush();
        issueTypeRepo.flush();
        statusMappingRepo.flush();
        linkTypeRepo.flush();

        // 1. Detect issue types and roles from subtask names
        Set<String> detectedRoles = new LinkedHashSet<>();
        Map<String, String> subtaskToRole = new LinkedHashMap<>();
        int typeCount = 0;

        for (Map<String, Object> it : issueTypes) {
            String name = (String) it.get("name");
            Boolean isSubtask = (Boolean) it.get("subtask");
            if (name == null) continue;

            BoardCategory category = detectBoardCategory(name, Boolean.TRUE.equals(isSubtask));

            String roleCode = null;
            if (category == BoardCategory.SUBTASK) {
                roleCode = detectRoleFromSubtaskName(name);
                detectedRoles.add(roleCode);
                subtaskToRole.put(name, roleCode);
            }

            IssueTypeMappingEntity mapping = new IssueTypeMappingEntity();
            mapping.setConfigId(configId);
            mapping.setJiraTypeName(name);
            mapping.setBoardCategory(category);
            mapping.setWorkflowRoleCode(roleCode);
            issueTypeRepo.save(mapping);
            typeCount++;
        }

        // If no role-specific subtasks found, create default SA/DEV/QA
        if (detectedRoles.isEmpty()) {
            detectedRoles.add("SA");
            detectedRoles.add("DEV");
            detectedRoles.add("QA");
            warnings.add("No role-specific subtask types found — using default SA/DEV/QA roles");
        }

        // 2. Create workflow roles
        int roleCount = 0;
        int sortOrder = 1;
        for (String roleCode : detectedRoles) {
            WorkflowRoleEntity role = new WorkflowRoleEntity();
            role.setConfigId(configId);
            role.setCode(roleCode);
            role.setDisplayName(getRoleDisplayName(roleCode));
            role.setColor(getRoleColor(roleCode));
            role.setSortOrder(sortOrder++);
            role.setDefault("DEV".equals(roleCode));
            roleRepo.save(role);
            roleCount++;
        }

        // If no DEV role was detected, mark the first role as default
        if (!detectedRoles.contains("DEV") && !detectedRoles.isEmpty()) {
            WorkflowRoleEntity firstRole = roleRepo.findByConfigIdOrderBySortOrderAsc(configId).get(0);
            firstRole.setDefault(true);
            roleRepo.save(firstRole);
        }

        // 3. Detect status mappings from Jira statusCategory
        int statusCount = 0;
        Set<String> processedStatuses = new HashSet<>();

        for (Map<String, Object> typeStatuses : statusesByType) {
            String issueTypeName = (String) typeStatuses.get("issueType");
            List<Map<String, Object>> statuses = (List<Map<String, Object>>) typeStatuses.get("statuses");
            if (statuses == null || issueTypeName == null) continue;

            // Determine board category for this issue type
            boolean isSubtask = subtaskToRole.containsKey(issueTypeName)
                    || issueTypes.stream().anyMatch(it ->
                    issueTypeName.equals(it.get("name")) && Boolean.TRUE.equals(it.get("subtask")));
            BoardCategory boardCat = detectBoardCategory(issueTypeName, isSubtask);
            if (boardCat == BoardCategory.IGNORE) continue;

            int statusSortOrder = 0;
            Map<String, StatusMappingEntity> savedMappings = new LinkedHashMap<>();
            for (Map<String, Object> status : statuses) {
                String statusName = (String) status.get("name");
                String statusCategoryKey = (String) status.get("statusCategory");
                if (statusName == null) continue;

                String dedupeKey = boardCat.name() + ":" + statusName;
                StatusCategory mappedCategory = mapJiraStatusCategory(statusCategoryKey, statusName, boardCat);

                // Handle duplicate status names (same name, different statusCategory)
                if (processedStatuses.contains(dedupeKey)) {
                    // Prefer "done" over "new" — Jira may return same-name statuses with different categories
                    StatusMappingEntity existing = savedMappings.get(dedupeKey);
                    if (existing != null && mappedCategory == StatusCategory.DONE
                            && existing.getStatusCategory() != StatusCategory.DONE) {
                        existing.setStatusCategory(mappedCategory);
                        existing.setScoreWeight(computeScoreWeight(mappedCategory, statusName));
                        statusMappingRepo.save(existing);
                    }
                    continue;
                }
                processedStatuses.add(dedupeKey);

                String roleCode = detectRoleFromStatusName(statusName, detectedRoles);
                int scoreWeight = computeScoreWeight(mappedCategory, statusName);

                StatusMappingEntity sm = new StatusMappingEntity();
                sm.setConfigId(configId);
                sm.setJiraStatusName(statusName);
                sm.setIssueCategory(boardCat);
                sm.setStatusCategory(mappedCategory);
                sm.setWorkflowRoleCode(roleCode);
                sm.setSortOrder(statusSortOrder);
                sm.setScoreWeight(scoreWeight);
                sm.setColor(computeDefaultColor(mappedCategory));
                statusMappingRepo.save(sm);
                savedMappings.put(dedupeKey, sm);
                statusCount++;
                statusSortOrder += 10;
            }
        }

        // 4. Detect link type mappings
        int linkCount = 0;
        for (Map<String, Object> lt : linkTypes) {
            String name = (String) lt.get("name");
            if (name == null) continue;

            LinkCategory linkCat = detectLinkCategory(name);

            LinkTypeMappingEntity lm = new LinkTypeMappingEntity();
            lm.setConfigId(configId);
            lm.setJiraLinkTypeName(name);
            lm.setLinkCategory(linkCat);
            linkTypeRepo.save(lm);
            linkCount++;
        }

        // Refresh caches
        workflowConfigService.clearCache();

        log.info("Auto-detection complete: {} issue types, {} roles, {} status mappings, {} link types. Warnings: {}",
                typeCount, roleCount, statusCount, linkCount, warnings.size());

        return new AutoDetectResult(typeCount, roleCount, statusCount, linkCount, warnings);
    }

    // ==================== Heuristics ====================

    BoardCategory detectBoardCategory(String typeName, boolean isSubtask) {
        if (isSubtask) return BoardCategory.SUBTASK;

        String lower = typeName.toLowerCase();
        if (lower.contains("epic") || lower.contains("эпик")) return BoardCategory.EPIC;
        if (lower.contains("story") || lower.contains("bug") || lower.contains("task")
                || lower.contains("история") || lower.contains("задача") || lower.contains("баг")
                || lower.contains("дефект") || lower.contains("улучшение") || lower.contains("improvement")) {
            return BoardCategory.STORY;
        }
        // Unknown non-subtask types → IGNORE
        return BoardCategory.IGNORE;
    }

    String detectRoleFromSubtaskName(String subtaskName) {
        String lower = subtaskName.toLowerCase();
        if (lower.contains("аналити") || lower.contains("analyt") || lower.contains("requirement")
                || lower.contains("требовани") || lower.contains("analysis")) {
            return "SA";
        }
        if (lower.contains("тест") || lower.contains("test") || lower.contains("qa")
                || lower.contains("верификац") || lower.contains("verif")) {
            return "QA";
        }
        // Default: DEV
        return "DEV";
    }

    StatusCategory mapJiraStatusCategory(String jiraStatusCategoryKey, String statusName, BoardCategory boardCat) {
        if (jiraStatusCategoryKey == null) return StatusCategory.NEW;

        return switch (jiraStatusCategoryKey) {
            case "new" -> StatusCategory.NEW;
            case "done" -> StatusCategory.DONE;
            case "indeterminate" -> mapIndeterminateStatus(statusName, boardCat);
            default -> StatusCategory.IN_PROGRESS;
        };
    }

    private StatusCategory mapIndeterminateStatus(String statusName, BoardCategory boardCat) {
        if (boardCat == BoardCategory.EPIC) {
            String lower = statusName.toLowerCase();
            if (lower.contains("requirement") || lower.contains("требовани") || lower.contains("оценк")
                    || lower.contains("estimat") || lower.contains("backlog") || lower.contains("бэклог")) {
                return StatusCategory.REQUIREMENTS;
            }
            if (lower.contains("plan") || lower.contains("заплан")) {
                return StatusCategory.PLANNED;
            }
        }
        return StatusCategory.IN_PROGRESS;
    }

    String detectRoleFromStatusName(String statusName, Set<String> availableRoles) {
        if (availableRoles.size() <= 1) return null;

        String lower = statusName.toLowerCase();
        if ((lower.contains("analysis") || lower.contains("анализ") || lower.contains("аналитик"))
                && availableRoles.contains("SA")) {
            return "SA";
        }
        if ((lower.contains("test") || lower.contains("тест") || lower.contains("qa"))
                && availableRoles.contains("QA")) {
            return "QA";
        }
        if ((lower.contains("develop") || lower.contains("разработ") || lower.contains("dev review")
                || lower.contains("ревью разработ"))
                && availableRoles.contains("DEV")) {
            return "DEV";
        }
        return null;
    }

    LinkCategory detectLinkCategory(String linkTypeName) {
        String lower = linkTypeName.toLowerCase();
        if (lower.contains("block")) return LinkCategory.BLOCKS;
        if (lower.contains("relat")) return LinkCategory.RELATED;
        return LinkCategory.IGNORE;
    }

    int computeScoreWeight(StatusCategory category, String statusName) {
        return switch (category) {
            case DONE -> 0;
            case NEW -> -5;
            case REQUIREMENTS -> {
                String lower = statusName.toLowerCase();
                if (lower.contains("estimat") || lower.contains("оценк")) yield 10;
                yield 5;
            }
            case PLANNED -> 15;
            case IN_PROGRESS -> {
                String lower = statusName.toLowerCase();
                if (lower.contains("accept") || lower.contains("приём") || lower.contains("приeм")
                        || lower.contains("e2e")) yield 30;
                if (lower.contains("develop") || lower.contains("разработ")) yield 25;
                yield 20;
            }
            default -> 0;
        };
    }

    int computeWeightFromLevel(int level, int maxLevel, String statusCategoryKey) {
        if (level == 0) return -5;
        if ("done".equals(statusCategoryKey) || level == maxLevel) return 0;
        if (maxLevel <= 1) return 20;

        // Linearly interpolate 5..30 based on position between level 1 and maxLevel-1
        double ratio = (double) (level - 1) / (maxLevel - 2);
        return 5 + (int) Math.round(ratio * 25);
    }

    String computeDefaultColor(StatusCategory cat) {
        return switch (cat) {
            case NEW, TODO -> "#DFE1E6";
            case REQUIREMENTS -> "#E6FCFF";
            case PLANNED -> "#EAE6FF";
            case IN_PROGRESS -> "#DEEBFF";
            case DONE -> "#E3FCEF";
        };
    }

    // ==================== Helpers ====================

    private String getRoleDisplayName(String roleCode) {
        return switch (roleCode) {
            case "SA" -> "System Analyst";
            case "DEV" -> "Developer";
            case "QA" -> "QA Engineer";
            default -> roleCode;
        };
    }

    private String getRoleColor(String roleCode) {
        return switch (roleCode) {
            case "SA" -> "#3b82f6";
            case "DEV" -> "#10b981";
            case "QA" -> "#f59e0b";
            default -> "#666666";
        };
    }

    private Long getDefaultConfigId() {
        String envProjectKey = jiraProperties.getProjectKey();
        if (envProjectKey != null && !envProjectKey.isBlank()) {
            var byKey = configRepo.findByProjectKey(envProjectKey);
            if (byKey.isPresent()) return byKey.get().getId();
        }
        return configRepo.findByIsDefaultTrue().map(ProjectConfigurationEntity::getId).orElse(null);
    }

    private Long getOrCreateDefaultConfigId() {
        String envProjectKey = jiraProperties.getProjectKey();

        // Try by project_key first
        if (envProjectKey != null && !envProjectKey.isBlank()) {
            var byKey = configRepo.findByProjectKey(envProjectKey);
            if (byKey.isPresent()) return byKey.get().getId();
        }

        // Fallback to default, or create new
        boolean isNew = false;
        ProjectConfigurationEntity config = configRepo.findByIsDefaultTrue().orElse(null);
        if (config == null) {
            config = new ProjectConfigurationEntity();
            config.setName("Default");
            config.setDefault(true);
            isNew = true;
        }

        // Auto-assign project_key if missing
        boolean needsSave = isNew;
        if (config.getProjectKey() == null && envProjectKey != null && !envProjectKey.isBlank()) {
            config.setProjectKey(envProjectKey);
            needsSave = true;
        }

        if (needsSave) {
            config = configRepo.save(config);
        }

        return config.getId();
    }

    // ==================== Result DTO ====================

    public record AutoDetectResult(
            int issueTypeCount,
            int roleCount,
            int statusMappingCount,
            int linkTypeCount,
            List<String> warnings
    ) {}
}

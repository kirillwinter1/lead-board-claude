package com.leadboard.config.service;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.status.StatusCategory;
import com.leadboard.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class WorkflowConfigService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConfigService.class);

    private final ProjectConfigurationRepository configRepo;
    private final WorkflowRoleRepository roleRepo;
    private final IssueTypeMappingRepository issueTypeRepo;
    private final StatusMappingRepository statusMappingRepo;
    private final LinkTypeMappingRepository linkTypeRepo;
    private final ObjectMapper objectMapper;
    private final JiraConfigResolver jiraConfigResolver;

    /**
     * Per-tenant cache of fully-loaded, immutable configuration snapshots.
     * Key = TenantContext.getCurrentTenantId(); {@link #NO_TENANT_KEY} (-1L) for the
     * single-tenant / .env context (no tenant).
     *
     * <p>Each snapshot is built once from the DB under the loading thread's tenant
     * context and never mutated afterwards. Readers resolve THEIR OWN tenant's
     * snapshot on every call, so a long-running task under tenant A can never observe
     * a reload triggered by an interleaving tenant B request (BUG-60).
     */
    private final ConcurrentHashMap<Long, ConfigSnapshot> snapshots = new ConcurrentHashMap<>();

    private static final long NO_TENANT_KEY = -1L;

    public WorkflowConfigService(
            ProjectConfigurationRepository configRepo,
            WorkflowRoleRepository roleRepo,
            IssueTypeMappingRepository issueTypeRepo,
            StatusMappingRepository statusMappingRepo,
            LinkTypeMappingRepository linkTypeRepo,
            ObjectMapper objectMapper,
            JiraConfigResolver jiraConfigResolver) {
        this.configRepo = configRepo;
        this.roleRepo = roleRepo;
        this.issueTypeRepo = issueTypeRepo;
        this.statusMappingRepo = statusMappingRepo;
        this.linkTypeRepo = linkTypeRepo;
        this.objectMapper = objectMapper;
        this.jiraConfigResolver = jiraConfigResolver;
    }

    @PostConstruct
    public void init() {
        // Warm the snapshot for the startup (default) context. DB errors while loading
        // must never crash the application — buildSnapshot() returns an empty snapshot
        // on failure, preserving the previous fail-soft semantics.
        snapshot();
    }

    /**
     * Invalidate the cached snapshot for the CURRENT tenant only (other tenants keep
     * their snapshots), then eagerly rebuild it. Call after a configuration change.
     */
    public synchronized void clearCache() {
        long key = currentKey();
        snapshots.remove(key);
        loadSnapshot(key);
    }

    /**
     * Ensure configuration is loaded for the current tenant. Kept for backward
     * compatibility — snapshot resolution now happens implicitly inside every reader,
     * so callers no longer strictly need this. Resolving the snapshot triggers a
     * lazy load if the current tenant has none yet.
     */
    public void ensureLoaded() {
        snapshot();
    }

    // ==================== Snapshot resolution & loading ====================

    private static long currentKey() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return tenantId != null ? tenantId : NO_TENANT_KEY;
    }

    /**
     * Resolve the immutable configuration snapshot for the current tenant, loading it
     * (double-checked, synchronized) on first access.
     */
    private ConfigSnapshot snapshot() {
        long key = currentKey();
        ConfigSnapshot snap = snapshots.get(key);
        if (snap != null) return snap;
        return loadSnapshot(key);
    }

    private synchronized ConfigSnapshot loadSnapshot(long key) {
        // Double-checked: another thread may have loaded it while we waited for the lock.
        ConfigSnapshot existing = snapshots.get(key);
        if (existing != null) return existing;
        ConfigSnapshot snap = buildSnapshot();
        snapshots.put(key, snap);
        return snap;
    }

    /**
     * Build an immutable snapshot from the DB using the CURRENT thread's tenant context.
     * On any failure returns an empty snapshot (fail-soft — never throws).
     */
    private ConfigSnapshot buildSnapshot() {
        try {
            // Find ALL project configurations for this tenant
            List<String> allKeys = jiraConfigResolver.getAllProjectKeys();
            List<ProjectConfigurationEntity> configs;

            if (!allKeys.isEmpty()) {
                configs = new ArrayList<>(configRepo.findAllByProjectKeyIn(allKeys));
                // Also include default config if it's not project-specific
                configRepo.findByIsDefaultTrue().ifPresent(def -> {
                    if (configs.stream().noneMatch(c -> c.getId().equals(def.getId()))) {
                        configs.add(0, def);
                    }
                });
            } else {
                // No project keys configured — load default only
                configs = new ArrayList<>();
                configRepo.findByIsDefaultTrue().ifPresent(configs::add);
            }

            if (configs.isEmpty()) {
                log.warn("No project configuration found in DB. Using empty config.");
                return ConfigSnapshot.empty();
            }

            // Sort: default first, then by creation time
            configs.sort((a, b) -> {
                if (a.isDefault() && !b.isDefault()) return -1;
                if (!a.isDefault() && b.isDefault()) return 1;
                return Long.compare(a.getId(), b.getId());
            });

            // Default config = first one (is_default=true or earliest)
            ProjectConfigurationEntity defaultConfig = configs.get(0);

            // Auto-assign project_key to default config if missing
            String firstKey = allKeys.isEmpty() ? null : allKeys.get(0);
            if (defaultConfig.getProjectKey() == null && firstKey != null) {
                defaultConfig.setProjectKey(firstKey);
                configRepo.save(defaultConfig);
                log.info("Auto-assigned project key '{}' to default configuration", firstKey);
            }

            Long defaultConfigId = defaultConfig.getId();
            List<Long> allConfigIds = configs.stream().map(ProjectConfigurationEntity::getId).toList();
            String projectKey = defaultConfig.getProjectKey();

            // ==== Merged loading: union all configs (global first-wins) + per-project maps ====

            Map<String, BoardCategory> typeToCategory = new HashMap<>();
            Map<String, String> typeToRoleCode = new HashMap<>();
            Map<String, StatusCategory> statusLookup = new HashMap<>();
            Map<String, String> statusToRoleCode = new HashMap<>();
            Map<String, Integer> statusSortOrder = new HashMap<>();
            Map<String, Integer> statusScoreWeight = new HashMap<>();
            Map<String, String> statusColor = new HashMap<>();
            Map<String, LinkCategory> linkTypeLookup = new HashMap<>();
            Map<String, BoardCategory> projectTypeToCategory = new HashMap<>();
            Map<String, String> projectTypeToRoleCode = new HashMap<>();
            Map<String, StatusCategory> projectStatusLookup = new HashMap<>();
            Map<String, String> projectStatusToRoleCode = new HashMap<>();
            Map<String, Integer> projectStatusSortOrder = new HashMap<>();
            Map<String, Integer> projectStatusScoreWeight = new HashMap<>();
            Map<String, String> projectStatusColor = new HashMap<>();

            Set<String> projectNames = new HashSet<>();
            Set<String> epicNames = new HashSet<>();
            Set<String> storyNames = new HashSet<>();
            Set<String> bugNames = new HashSet<>();
            Set<String> subtaskNames = new HashSet<>();
            List<WorkflowRoleEntity> mergedRoles = new ArrayList<>();
            Set<String> seenRoleCodes = new HashSet<>();
            int totalTypeMappings = 0;
            int totalStatusMappings = 0;
            int totalLinkMappings = 0;

            for (ProjectConfigurationEntity config : configs) {
                Long configId = config.getId();
                String cfgProjectKey = config.getProjectKey(); // may be null for default

                // Merge roles: union by code (first-wins)
                for (WorkflowRoleEntity role : roleRepo.findByConfigIdOrderBySortOrderAsc(configId)) {
                    if (seenRoleCodes.add(role.getCode())) {
                        mergedRoles.add(role);
                    }
                }

                // Merge issue type mappings: global (first-wins) + per-project
                for (IssueTypeMappingEntity m : issueTypeRepo.findByConfigId(configId)) {
                    if (m.getBoardCategory() == null) continue;
                    String typeKey = m.getJiraTypeName().toLowerCase();
                    // Global (first-wins)
                    typeToCategory.putIfAbsent(typeKey, m.getBoardCategory());
                    if (m.getWorkflowRoleCode() != null) {
                        typeToRoleCode.putIfAbsent(typeKey, m.getWorkflowRoleCode());
                    }
                    // Per-project (always set — last config for this project wins, but typically 1 config per project)
                    if (cfgProjectKey != null) {
                        String pKey = cfgProjectKey + ":" + typeKey;
                        projectTypeToCategory.put(pKey, m.getBoardCategory());
                        if (m.getWorkflowRoleCode() != null) {
                            projectTypeToRoleCode.put(pKey, m.getWorkflowRoleCode());
                        }
                    }
                    switch (m.getBoardCategory()) {
                        case PROJECT -> projectNames.add(m.getJiraTypeName());
                        case EPIC -> epicNames.add(m.getJiraTypeName());
                        case STORY -> storyNames.add(m.getJiraTypeName());
                        case BUG -> bugNames.add(m.getJiraTypeName());
                        case SUBTASK -> subtaskNames.add(m.getJiraTypeName());
                        default -> {}
                    }
                    totalTypeMappings++;
                }

                // Merge status mappings: global (first-wins) + per-project
                for (StatusMappingEntity sm : statusMappingRepo.findByConfigId(configId)) {
                    String key = buildStatusKey(sm.getIssueCategory().name(), sm.getJiraStatusName());
                    // Global (first-wins)
                    statusLookup.putIfAbsent(key, sm.getStatusCategory());
                    if (sm.getWorkflowRoleCode() != null) {
                        statusToRoleCode.putIfAbsent(key, sm.getWorkflowRoleCode());
                    }
                    statusSortOrder.putIfAbsent(key, sm.getSortOrder());
                    statusScoreWeight.putIfAbsent(key, sm.getScoreWeight());
                    if (sm.getColor() != null) {
                        statusColor.putIfAbsent(key, sm.getColor());
                    }
                    // Per-project
                    if (cfgProjectKey != null) {
                        String pKey = cfgProjectKey + ":" + key;
                        projectStatusLookup.put(pKey, sm.getStatusCategory());
                        if (sm.getWorkflowRoleCode() != null) {
                            projectStatusToRoleCode.put(pKey, sm.getWorkflowRoleCode());
                        }
                        projectStatusSortOrder.put(pKey, sm.getSortOrder());
                        projectStatusScoreWeight.put(pKey, sm.getScoreWeight());
                        if (sm.getColor() != null) {
                            projectStatusColor.put(pKey, sm.getColor());
                        }
                    }
                    totalStatusMappings++;
                }

                // Merge link types: union by name.toLowerCase() (first-wins)
                for (LinkTypeMappingEntity lm : linkTypeRepo.findByConfigId(configId)) {
                    linkTypeLookup.putIfAbsent(lm.getJiraLinkTypeName().toLowerCase(), lm.getLinkCategory());
                    totalLinkMappings++;
                }
            }

            // Load epic link config and score weights from default config
            String epicLinkType = defaultConfig.getEpicLinkType();
            String epicLinkName = defaultConfig.getEpicLinkName();

            Map<String, Integer> scoreWeightsMap = Map.of();
            if (defaultConfig.getStatusScoreWeights() != null && !defaultConfig.getStatusScoreWeights().isEmpty()) {
                try {
                    scoreWeightsMap = objectMapper.readValue(defaultConfig.getStatusScoreWeights(),
                            new TypeReference<Map<String, Integer>>() {});
                } catch (Exception e) {
                    log.error("Failed to parse status_score_weights JSONB", e);
                    scoreWeightsMap = Map.of();
                }
            }

            if (mergedRoles.isEmpty() && totalTypeMappings == 0) {
                log.warn("Workflow configuration is EMPTY — no roles or type mappings found. " +
                        "Auto-detect will populate config on first Jira sync, or configure manually via Settings > Workflow.");
            } else {
                log.info("Workflow configuration loaded (merged {} configs): {} roles, {} type mappings, {} status mappings, {} link mappings",
                        configs.size(), mergedRoles.size(), totalTypeMappings, totalStatusMappings, totalLinkMappings);
            }

            return new ConfigSnapshot(
                    defaultConfigId, allConfigIds,
                    typeToCategory, typeToRoleCode,
                    statusLookup, statusToRoleCode, statusSortOrder, statusScoreWeight, statusColor,
                    linkTypeLookup,
                    projectTypeToCategory, projectTypeToRoleCode,
                    projectStatusLookup, projectStatusToRoleCode, projectStatusSortOrder,
                    projectStatusScoreWeight, projectStatusColor,
                    mergedRoles, scoreWeightsMap,
                    Set.copyOf(projectNames), Set.copyOf(epicNames), Set.copyOf(storyNames),
                    Set.copyOf(bugNames), Set.copyOf(subtaskNames),
                    projectKey, epicLinkType, epicLinkName);
        } catch (Exception e) {
            log.error("Failed to load workflow configuration from DB", e);
            return ConfigSnapshot.empty();
        }
    }

    // ==================== Issue Type Categorization ====================

    /**
     * Categorize issue type using global merged config (backward compat).
     */
    public BoardCategory categorizeIssueType(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName, null);
    }

    /**
     * Categorize issue type with project-specific resolution.
     * Tries per-project config first, falls back to global merged config.
     */
    public BoardCategory categorizeIssueType(String jiraTypeName, String projectKey) {
        return categorizeIssueType(snapshot(), jiraTypeName, projectKey);
    }

    private BoardCategory categorizeIssueType(ConfigSnapshot s, String jiraTypeName, String projectKey) {
        if (jiraTypeName == null) return null;
        String typeKey = jiraTypeName.toLowerCase();
        // Per-project lookup first
        if (projectKey != null) {
            BoardCategory cat = s.projectTypeToCategory.get(projectKey + ":" + typeKey);
            if (cat != null) return cat;
        }
        return s.typeToCategory.get(typeKey);
    }

    public boolean isProject(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName) == BoardCategory.PROJECT;
    }

    public boolean isEpic(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName) == BoardCategory.EPIC;
    }

    public boolean isEpic(String jiraTypeName, String projectKey) {
        return categorizeIssueType(jiraTypeName, projectKey) == BoardCategory.EPIC;
    }

    public boolean isStory(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName) == BoardCategory.STORY;
    }

    public boolean isBug(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName) == BoardCategory.BUG;
    }

    public boolean isSubtask(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName) == BoardCategory.SUBTASK;
    }

    public boolean isStoryOrBug(String jiraTypeName) {
        return isStoryOrBug(jiraTypeName, null);
    }

    public boolean isStoryOrBug(String jiraTypeName, String projectKey) {
        BoardCategory cat = categorizeIssueType(jiraTypeName, projectKey);
        return cat == BoardCategory.STORY || cat == BoardCategory.BUG;
    }

    public List<String> getBugTypeNames() {
        return List.copyOf(snapshot().bugTypeNames);
    }

    public List<String> getProjectTypeNames() {
        return List.copyOf(snapshot().projectTypeNames);
    }

    public List<String> getEpicTypeNames() {
        return List.copyOf(snapshot().epicTypeNames);
    }

    public List<String> getStoryTypeNames() {
        return List.copyOf(snapshot().storyTypeNames);
    }

    public List<String> getSubtaskTypeNames() {
        return List.copyOf(snapshot().subtaskTypeNames);
    }

    // ==================== Roles ====================

    public String getSubtaskRole(String jiraTypeName) {
        return getSubtaskRole(jiraTypeName, null);
    }

    public String getSubtaskRole(String jiraTypeName, String projectKey) {
        ConfigSnapshot s = snapshot();
        if (jiraTypeName == null) return getDefaultRoleCode(s);
        String typeKey = jiraTypeName.toLowerCase();
        if (projectKey != null) {
            String role = s.projectTypeToRoleCode.get(projectKey + ":" + typeKey);
            if (role != null) return role;
        }
        String role = s.typeToRoleCode.get(typeKey);
        return role != null ? role : getDefaultRoleCode(s);
    }

    public String getDefaultRoleCode() {
        return getDefaultRoleCode(snapshot());
    }

    private String getDefaultRoleCode(ConfigSnapshot s) {
        return s.cachedRoles.stream()
                .filter(WorkflowRoleEntity::isDefault)
                .findFirst()
                .map(WorkflowRoleEntity::getCode)
                // No explicit default — use the first role by sort order
                .or(() -> s.cachedRoles.stream().findFirst().map(WorkflowRoleEntity::getCode))
                .orElse(null);
    }

    public List<WorkflowRoleEntity> getRolesInPipelineOrder() {
        return snapshot().cachedRoles;
    }

    public List<String> getRoleCodesInPipelineOrder() {
        return snapshot().cachedRoles.stream().map(WorkflowRoleEntity::getCode).toList();
    }

    // ==================== Status Categorization ====================

    public StatusCategory categorize(String status, String issueType) {
        return categorize(status, issueType, null);
    }

    public StatusCategory categorize(String status, String issueType, String projectKey) {
        ConfigSnapshot s = snapshot();
        if (status == null) return StatusCategory.NEW;

        BoardCategory cat = categorizeIssueType(s, issueType, projectKey);
        if (cat == null) return StatusCategory.NEW; // Unmapped type
        return categorizeByBoardCategory(s, status, cat, projectKey);
    }

    public StatusCategory categorizeEpic(String status) {
        return categorizeByBoardCategory(snapshot(), status, BoardCategory.EPIC, null);
    }

    public StatusCategory categorizeEpic(String status, String projectKey) {
        return categorizeByBoardCategory(snapshot(), status, BoardCategory.EPIC, projectKey);
    }

    public StatusCategory categorizeStory(String status) {
        return categorizeByBoardCategory(snapshot(), status, BoardCategory.STORY, null);
    }

    public StatusCategory categorizeStory(String status, String projectKey) {
        return categorizeByBoardCategory(snapshot(), status, BoardCategory.STORY, projectKey);
    }

    public StatusCategory categorizeSubtask(String status) {
        return categorizeByBoardCategory(snapshot(), status, BoardCategory.SUBTASK, null);
    }

    public StatusCategory categorizeSubtask(String status, String projectKey) {
        return categorizeByBoardCategory(snapshot(), status, BoardCategory.SUBTASK, projectKey);
    }

    private StatusCategory categorizeByBoardCategory(ConfigSnapshot s, String status, BoardCategory boardCat, String projectKey) {
        if (status == null) return StatusCategory.NEW;

        String key = buildStatusKey(boardCat.name(), status);

        // Per-project lookup first
        if (projectKey != null) {
            String pKey = projectKey + ":" + key;
            StatusCategory pCat = s.projectStatusLookup.get(pKey);
            if (pCat != null) return pCat;
        }

        // Global lookup
        StatusCategory cat = s.statusLookup.get(key);
        if (cat != null) return cat;

        // Try case-insensitive
        for (Map.Entry<String, StatusCategory> entry : s.statusLookup.entrySet()) {
            if (entry.getKey().startsWith(boardCat.name() + ":") &&
                entry.getKey().substring(boardCat.name().length() + 1).equalsIgnoreCase(status)) {
                return entry.getValue();
            }
        }

        // BUG fallback: try STORY mappings if no BUG-specific mapping exists
        if (boardCat == BoardCategory.BUG) {
            String storyKey = buildStatusKey("STORY", status);
            StatusCategory storyCat = s.statusLookup.get(storyKey);
            if (storyCat != null) return storyCat;
            for (Map.Entry<String, StatusCategory> entry : s.statusLookup.entrySet()) {
                if (entry.getKey().startsWith("STORY:") &&
                    entry.getKey().substring(6).equalsIgnoreCase(status)) {
                    return entry.getValue();
                }
            }
        }

        // Fallback: substring matching
        String sLower = status.toLowerCase();
        if (sLower.contains("done") || sLower.contains("closed") || sLower.contains("resolved") ||
            sLower.contains("завершен") || sLower.contains("готов") || sLower.contains("выполнен")) {
            return StatusCategory.DONE;
        }
        if (sLower.contains("progress") || sLower.contains("work") || sLower.contains("review") ||
            sLower.contains("test") || sLower.contains("develop") || sLower.contains("analysis") ||
            sLower.contains("accept") || sLower.contains("e2e") || sLower.contains("plan") ||
            sLower.contains("в работе") || sLower.contains("ревью") || sLower.contains("разработ") ||
            sLower.contains("анализ") || sLower.contains("тест") || sLower.contains("запланирован") ||
            sLower.contains("приёмк") || sLower.contains("приемк") || sLower.contains("проверк")) {
            return StatusCategory.IN_PROGRESS;
        }

        log.warn("Unknown status '{}' for category {}, defaulting to NEW", status, boardCat);
        return StatusCategory.NEW;
    }

    public boolean isDone(String status, String issueType) {
        return isDone(status, issueType, null);
    }

    public boolean isDone(String status, String issueType, String projectKey) {
        return categorize(status, issueType, projectKey) == StatusCategory.DONE;
    }

    public boolean isInProgress(String status, String issueType) {
        return isInProgress(status, issueType, null);
    }

    public boolean isInProgress(String status, String issueType, String projectKey) {
        StatusCategory cat = categorize(status, issueType, projectKey);
        return cat == StatusCategory.IN_PROGRESS || cat == StatusCategory.PLANNED;
    }

    // ==================== Phase (Role) Determination ====================

    public String determinePhase(String status, String issueType) {
        ConfigSnapshot s = snapshot();
        if (status == null && issueType == null) return getDefaultRoleCode(s);

        // First check by issue type (for subtasks)
        if (issueType != null) {
            String roleByType = s.typeToRoleCode.get(issueType.toLowerCase());
            if (roleByType != null) return roleByType;
        }

        // Then check by status in mapping
        if (status != null) {
            // Try STORY status mappings first (they have role codes)
            String storyKey = buildStatusKey("STORY", status);
            String role = s.statusToRoleCode.get(storyKey);
            if (role != null) return role;

            // Case-insensitive search
            for (Map.Entry<String, String> entry : s.statusToRoleCode.entrySet()) {
                String entryStatus = entry.getKey().substring(entry.getKey().indexOf(':') + 1);
                if (entryStatus.equalsIgnoreCase(status)) return entry.getValue();
            }

            // Fallback substring matching: check if the status name contains any configured role's displayName or code
            String lower = status.toLowerCase();
            for (WorkflowRoleEntity wfRole : s.cachedRoles) {
                if (wfRole.getDisplayName() != null && lower.contains(wfRole.getDisplayName().toLowerCase())) {
                    return wfRole.getCode();
                }
                if (wfRole.getCode() != null && lower.contains(wfRole.getCode().toLowerCase())) {
                    return wfRole.getCode();
                }
            }
        }

        // Fallback by issue type substring: check against configured roles
        if (issueType != null) {
            String typeLower = issueType.toLowerCase();
            for (WorkflowRoleEntity wfRole : s.cachedRoles) {
                if (wfRole.getDisplayName() != null && typeLower.contains(wfRole.getDisplayName().toLowerCase())) {
                    return wfRole.getCode();
                }
                if (wfRole.getCode() != null && typeLower.contains(wfRole.getCode().toLowerCase())) {
                    return wfRole.getCode();
                }
            }
            // Bugs default to last role in pipeline (typically QA)
            if (categorizeIssueType(s, issueType, null) == BoardCategory.BUG && !s.cachedRoles.isEmpty()) {
                return s.cachedRoles.get(s.cachedRoles.size() - 1).getCode();
            }
        }

        return getDefaultRoleCode(s);
    }

    // ==================== Planning-specific ====================

    public boolean isAllowedForRoughEstimate(String epicStatus) {
        if (epicStatus == null) return false;
        StatusCategory cat = categorizeEpic(epicStatus);
        return cat == StatusCategory.NEW || cat == StatusCategory.REQUIREMENTS || cat == StatusCategory.TODO;
    }

    public boolean isPlanningAllowed(String epicStatus) {
        if (epicStatus == null) return false;
        StatusCategory cat = categorizeEpic(epicStatus);
        return cat == StatusCategory.PLANNED || cat == StatusCategory.IN_PROGRESS;
    }

    public boolean isTimeLoggingAllowed(String epicStatus) {
        if (epicStatus == null) return false;
        StatusCategory cat = categorizeEpic(epicStatus);
        return cat == StatusCategory.IN_PROGRESS;
    }

    public boolean isEpicInProgress(String epicStatus) {
        if (epicStatus == null) return false;
        StatusCategory cat = categorizeEpic(epicStatus);
        return cat == StatusCategory.IN_PROGRESS;
    }

    // ==================== AutoScore ====================

    public int getStatusScoreWeight(String status) {
        if (status == null) return 0;
        ConfigSnapshot s = snapshot();
        // First try JSONB score weights map (exact match)
        Integer weight = s.scoreWeightsMap.get(status);
        if (weight != null) return weight;

        // Try case-insensitive
        for (Map.Entry<String, Integer> entry : s.scoreWeightsMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(status)) return entry.getValue();
        }

        // Try DB status_mappings score_weight for EPIC
        String epicKey = buildStatusKey("EPIC", status);
        Integer dbWeight = s.statusScoreWeight.get(epicKey);
        if (dbWeight != null) return dbWeight;

        // Case-insensitive DB lookup
        for (Map.Entry<String, Integer> entry : s.statusScoreWeight.entrySet()) {
            if (entry.getKey().startsWith("EPIC:") &&
                entry.getKey().substring(5).equalsIgnoreCase(status)) {
                return entry.getValue();
            }
        }

        return 0;
    }

    public int getStoryStatusSortOrder(String storyStatus) {
        if (storyStatus == null) return 0;
        ConfigSnapshot s = snapshot();
        String key = buildStatusKey("STORY", storyStatus);
        Integer order = s.statusSortOrder.get(key);
        if (order != null) return order;

        // Case-insensitive
        for (Map.Entry<String, Integer> entry : s.statusSortOrder.entrySet()) {
            if (entry.getKey().startsWith("STORY:") &&
                entry.getKey().substring(6).equalsIgnoreCase(storyStatus)) {
                return entry.getValue();
            }
        }

        return 0;
    }

    public String getStoryStatusColor(String storyStatus) {
        if (storyStatus == null) return null;
        ConfigSnapshot s = snapshot();
        String key = buildStatusKey("STORY", storyStatus);
        String color = s.statusColor.get(key);
        if (color != null) return color;

        // Case-insensitive
        for (Map.Entry<String, String> entry : s.statusColor.entrySet()) {
            if (entry.getKey().startsWith("STORY:") &&
                entry.getKey().substring(6).equalsIgnoreCase(storyStatus)) {
                return entry.getValue();
            }
        }

        return null;
    }

    public int getStoryStatusScoreWeight(String storyStatus) {
        if (storyStatus == null) return 0;
        ConfigSnapshot s = snapshot();
        String key = buildStatusKey("STORY", storyStatus);
        Integer weight = s.statusScoreWeight.get(key);
        if (weight != null) return weight;

        // Case-insensitive
        for (Map.Entry<String, Integer> entry : s.statusScoreWeight.entrySet()) {
            if (entry.getKey().startsWith("STORY:") &&
                entry.getKey().substring(6).equalsIgnoreCase(storyStatus)) {
                return entry.getValue();
            }
        }

        return 0;
    }

    // ==================== Score Weight with Category Fallback ====================

    /**
     * Returns a default score weight for a StatusCategory + BoardCategory pair.
     * Used as a fallback when no explicit score_weight is configured in DB.
     *
     * EPIC scale: NEW→-5, REQUIREMENTS→8, PLANNED→15, IN_PROGRESS→25, DONE→0
     * STORY scale: NEW→0, REQUIREMENTS→20, PLANNED→30, IN_PROGRESS→50, DONE→0
     */
    public int getDefaultScoreWeightForCategory(StatusCategory statusCategory, BoardCategory boardCategory) {
        if (statusCategory == null || boardCategory == null) return 0;
        StatusCategory normalized = statusCategory.normalized();

        if (boardCategory == BoardCategory.EPIC) {
            return switch (normalized) {
                case NEW -> -5;
                case REQUIREMENTS -> 8;
                case PLANNED -> 15;
                case IN_PROGRESS -> 25;
                case DEV_DONE -> 5;
                case DONE -> 0;
                default -> 0;
            };
        }

        // STORY / BUG / SUBTASK scale
        return switch (normalized) {
            case NEW -> 0;
            case REQUIREMENTS -> 20;
            case PLANNED -> 30;
            case IN_PROGRESS -> 50;
            case DEV_DONE -> 10;
            case DONE -> 0;
            default -> 0;
        };
    }

    /**
     * Returns score weight for a status, combining DB lookup + category-based fallback.
     * Single method that replaces ALL hardcoded substring-matching fallbacks.
     *
     * Lookup chain:
     * 1. DB-driven score_weight (exact match via existing getStatusScoreWeight / getStoryStatusScoreWeight)
     * 2. Category-based fallback: categorize the status, then use default weight for that category
     */
    public int getStatusScoreWeightWithFallback(String status, BoardCategory boardCat) {
        if (status == null || boardCat == null) return 0;
        ConfigSnapshot s = snapshot();

        // 1. Try existing DB-driven lookup
        int dbWeight;
        if (boardCat == BoardCategory.EPIC) {
            dbWeight = getStatusScoreWeight(status);
        } else {
            dbWeight = getStoryStatusScoreWeight(status);
        }
        if (dbWeight != 0) return dbWeight;

        // 2. Category-based fallback: categorize the status, then get default weight
        StatusCategory statusCategory = categorizeByBoardCategory(s, status, boardCat, null);
        return getDefaultScoreWeightForCategory(statusCategory, boardCat);
    }

    /**
     * Returns the first status name mapped to a given StatusCategory for a BoardCategory.
     * Used by SimulationPlanner to get real status names like "Done" or "In Progress" from config.
     */
    public String getFirstStatusNameForCategory(StatusCategory target, BoardCategory boardCat) {
        if (target == null || boardCat == null) return null;
        ConfigSnapshot s = snapshot();

        // Pick the pipeline-first status: lowest statusSortOrder among all matching entries.
        // statusLookup iteration order is arbitrary — relying on the first hash-order match
        // is non-deterministic and produced user-visible wrong picks (e.g. "Test Review"
        // instead of the first in-progress status "Аналитика").
        String pipelineFirst = lowestSortOrderStatusName(s, target, boardCat.name() + ":");
        if (pipelineFirst != null) return pipelineFirst;

        // BUG fallback: try STORY mappings
        if (boardCat == BoardCategory.BUG) {
            String storyFallback = lowestSortOrderStatusName(s, target, "STORY:");
            if (storyFallback != null) return storyFallback;
        }

        // Hardcoded last resort for common status names
        return switch (target) {
            case DONE -> "Done";
            case DEV_DONE -> "Dev Done";
            case IN_PROGRESS -> "In Progress";
            case NEW -> "New";
            default -> null;
        };
    }

    /**
     * Among all {@code statusLookup} entries under {@code prefix} that map to {@code target},
     * returns the status name with the LOWEST {@code statusSortOrder} (missing order →
     * {@link Integer#MAX_VALUE}, tie-break by name for full determinism), or null if none match.
     */
    private String lowestSortOrderStatusName(ConfigSnapshot s, StatusCategory target, String prefix) {
        String best = null;
        int bestOrder = Integer.MAX_VALUE;
        for (Map.Entry<String, StatusCategory> entry : s.statusLookup.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix) || entry.getValue() != target) continue;
            String name = key.substring(prefix.length());
            int order = s.statusSortOrder.getOrDefault(key, Integer.MAX_VALUE);
            if (best == null || order < bestOrder || (order == bestOrder && name.compareTo(best) < 0)) {
                best = name;
                bestOrder = order;
            }
        }
        return best;
    }

    /**
     * Returns all distinct status names mapped to a given StatusCategory across all board categories.
     */
    public List<String> getStatusNamesByCategory(StatusCategory target) {
        if (target == null) return List.of();
        ConfigSnapshot s = snapshot();

        Set<String> names = new HashSet<>();
        for (Map.Entry<String, StatusCategory> entry : s.statusLookup.entrySet()) {
            if (entry.getValue() == target) {
                int colonIdx = entry.getKey().indexOf(':');
                if (colonIdx >= 0) {
                    names.add(entry.getKey().substring(colonIdx + 1));
                }
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * All status names configured for a {@code boardCat} that map to {@code target}, ordered by
     * pipeline sort order (lowest first; ties broken by name). Unlike
     * {@link #getFirstStatusNameForCategory} (single default), this returns the full option list
     * a user can pick from — used by Data Quality transition fixes (F84) as the offline/fallback
     * source when live Jira transitions are unavailable.
     */
    public List<String> getStatusNamesForCategory(StatusCategory target, BoardCategory boardCat) {
        if (target == null || boardCat == null) return List.of();
        ConfigSnapshot s = snapshot();
        String prefix = boardCat.name() + ":";
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, StatusCategory> entry : s.statusLookup.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix) && entry.getValue() == target) {
                names.add(key.substring(prefix.length()));
            }
        }
        return orderStatusNames(boardCat, names);
    }

    /**
     * Orders arbitrary status names by the configured pipeline sort order of {@code boardCat}
     * (lowest {@code statusSortOrder} first; unknown → last; ties broken by name for determinism).
     * Used to order Jira-derived transition targets so the first option is the pipeline-first
     * status (F84).
     */
    public List<String> orderStatusNames(BoardCategory boardCat, List<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        ConfigSnapshot s = snapshot();
        String prefix = boardCat == null ? null : boardCat.name() + ":";
        return names.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator
                        .comparingInt((String n) -> prefix == null
                                ? Integer.MAX_VALUE
                                : s.statusSortOrder.getOrDefault(prefix + n, Integer.MAX_VALUE))
                        .thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.toList());
    }

    // ==================== Link Types ====================

    public LinkCategory categorizeLinkType(String linkTypeName) {
        if (linkTypeName == null) return LinkCategory.IGNORE;
        LinkCategory cat = snapshot().linkTypeLookup.get(linkTypeName.toLowerCase());
        if (cat != null) return cat;

        // Fallback: substring matching
        String lower = linkTypeName.toLowerCase();
        if (lower.contains("block")) return LinkCategory.BLOCKS;
        if (lower.contains("relat") || lower.contains("duplicate") || lower.contains("clone")) return LinkCategory.RELATED;

        return LinkCategory.IGNORE;
    }

    // ==================== Sync helpers ====================

    public String computeBoardCategory(String issueType, boolean isSubtask) {
        return computeBoardCategory(issueType, isSubtask, null);
    }

    public String computeBoardCategory(String issueType, boolean isSubtask, String projectKey) {
        BoardCategory cat = categorizeIssueType(issueType, projectKey);
        if (cat == null) return null; // Unmapped type
        if (isSubtask && cat != BoardCategory.SUBTASK) {
            return BoardCategory.SUBTASK.name();
        }
        return cat.name();
    }

    public String computeWorkflowRole(String issueType) {
        return computeWorkflowRole(issueType, null);
    }

    public String computeWorkflowRole(String issueType, String projectKey) {
        BoardCategory cat = categorizeIssueType(issueType, projectKey);
        if (cat == BoardCategory.SUBTASK) {
            return getSubtaskRole(issueType, projectKey);
        }
        return null;
    }

    // ==================== Poker / Story creation ====================

    public String getSubtaskTypeName(String roleCode) {
        if (roleCode == null) return null;
        ConfigSnapshot s = snapshot();
        // Find issue type mapped to this role code
        for (Map.Entry<String, String> entry : s.typeToRoleCode.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(roleCode)) {
                // Return the original name (not lowercased key)
                for (IssueTypeMappingEntity m : issueTypeRepo.findByConfigId(s.defaultConfigId)) {
                    if (m.getJiraTypeName().toLowerCase().equals(entry.getKey()) &&
                        roleCode.equalsIgnoreCase(m.getWorkflowRoleCode())) {
                        return m.getJiraTypeName();
                    }
                }
            }
        }
        return null;
    }

    public String getStoryTypeName() {
        // Return first STORY type name
        return snapshot().storyTypeNames.stream().findFirst().orElse("Story");
    }

    // ==================== Helpers ====================

    public Long getDefaultConfigId() {
        return snapshot().defaultConfigId;
    }

    /**
     * Returns all config IDs loaded for this tenant (merged view).
     */
    public List<Long> getAllConfigIds() {
        return snapshot().allConfigIds;
    }

    /**
     * Returns config ID for a specific project key, or null if not found.
     */
    public Long getConfigIdForProject(String projectKey) {
        if (projectKey == null) return getDefaultConfigId();
        return configRepo.findByProjectKey(projectKey)
                .map(ProjectConfigurationEntity::getId)
                .orElse(null);
    }

    public String getProjectKey() {
        return snapshot().projectKey;
    }

    public String getEpicLinkType() {
        String epicLinkType = snapshot().epicLinkType;
        return epicLinkType != null ? epicLinkType : "parent";
    }

    public String getEpicLinkName() {
        return snapshot().epicLinkName;
    }

    /**
     * Returns all status names mapped for a given board category and status category.
     * Used for backwards compatibility with methods that need lists of status names.
     */
    public List<String> getStatusNames(BoardCategory boardCategory, StatusCategory statusCategory) {
        ConfigSnapshot s = snapshot();
        List<String> result = new ArrayList<>();
        String prefix = boardCategory.name() + ":";
        for (Map.Entry<String, StatusCategory> entry : s.statusLookup.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue() == statusCategory) {
                result.add(entry.getKey().substring(prefix.length()));
            }
        }
        return result;
    }

    /**
     * Returns STORY pipeline statuses (excluding NEW/DONE) sorted by sort_order.
     * Each entry: [statusName, sortOrder, color].
     */
    public List<StoryPipelineStatus> getStoryPipelineStatuses() {
        ConfigSnapshot s = snapshot();
        List<StoryPipelineStatus> result = new ArrayList<>();
        String prefix = "STORY:";
        for (Map.Entry<String, StatusCategory> entry : s.statusLookup.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            StatusCategory cat = entry.getValue();
            if (cat == StatusCategory.NEW || cat == StatusCategory.DONE || cat == StatusCategory.TODO) continue;

            String statusName = entry.getKey().substring(prefix.length());
            int sortOrder = s.statusSortOrder.getOrDefault(entry.getKey(), 0);
            String color = s.statusColor.getOrDefault(entry.getKey(), null);
            result.add(new StoryPipelineStatus(statusName, sortOrder, color));
        }
        result.sort(Comparator.comparingInt(StoryPipelineStatus::sortOrder));
        return result;
    }

    public record StoryPipelineStatus(String statusName, int sortOrder, String color) {}

    private static String buildStatusKey(String issueCategory, String statusName) {
        return issueCategory + ":" + statusName;
    }

    // ==================== Immutable per-tenant snapshot ====================

    /**
     * Immutable snapshot of a single tenant's fully-loaded workflow configuration.
     * Built once by {@link #buildSnapshot()} and never mutated afterwards, so it can be
     * shared and read concurrently by any number of threads without interference.
     */
    private static final class ConfigSnapshot {
        final Long defaultConfigId;
        final List<Long> allConfigIds;
        // Global (merged) lookups — used when projectKey is unknown
        final Map<String, BoardCategory> typeToCategory;
        final Map<String, String> typeToRoleCode;
        final Map<String, StatusCategory> statusLookup;
        final Map<String, String> statusToRoleCode;
        final Map<String, Integer> statusSortOrder;
        final Map<String, Integer> statusScoreWeight;
        final Map<String, String> statusColor;
        final Map<String, LinkCategory> linkTypeLookup;
        // Per-project lookups — used when projectKey is known (key = "PROJECT_KEY:value")
        final Map<String, BoardCategory> projectTypeToCategory;
        final Map<String, String> projectTypeToRoleCode;
        final Map<String, StatusCategory> projectStatusLookup;
        final Map<String, String> projectStatusToRoleCode;
        final Map<String, Integer> projectStatusSortOrder;
        final Map<String, Integer> projectStatusScoreWeight;
        final Map<String, String> projectStatusColor;
        final List<WorkflowRoleEntity> cachedRoles;
        final Map<String, Integer> scoreWeightsMap;
        // Sets for quick lookups
        final Set<String> projectTypeNames;
        final Set<String> epicTypeNames;
        final Set<String> storyTypeNames;
        final Set<String> bugTypeNames;
        final Set<String> subtaskTypeNames;
        final String projectKey;
        final String epicLinkType;
        final String epicLinkName;

        ConfigSnapshot(
                Long defaultConfigId, List<Long> allConfigIds,
                Map<String, BoardCategory> typeToCategory, Map<String, String> typeToRoleCode,
                Map<String, StatusCategory> statusLookup, Map<String, String> statusToRoleCode,
                Map<String, Integer> statusSortOrder, Map<String, Integer> statusScoreWeight,
                Map<String, String> statusColor, Map<String, LinkCategory> linkTypeLookup,
                Map<String, BoardCategory> projectTypeToCategory, Map<String, String> projectTypeToRoleCode,
                Map<String, StatusCategory> projectStatusLookup, Map<String, String> projectStatusToRoleCode,
                Map<String, Integer> projectStatusSortOrder, Map<String, Integer> projectStatusScoreWeight,
                Map<String, String> projectStatusColor,
                List<WorkflowRoleEntity> cachedRoles, Map<String, Integer> scoreWeightsMap,
                Set<String> projectTypeNames, Set<String> epicTypeNames, Set<String> storyTypeNames,
                Set<String> bugTypeNames, Set<String> subtaskTypeNames,
                String projectKey, String epicLinkType, String epicLinkName) {
            this.defaultConfigId = defaultConfigId;
            this.allConfigIds = allConfigIds;
            this.typeToCategory = typeToCategory;
            this.typeToRoleCode = typeToRoleCode;
            this.statusLookup = statusLookup;
            this.statusToRoleCode = statusToRoleCode;
            this.statusSortOrder = statusSortOrder;
            this.statusScoreWeight = statusScoreWeight;
            this.statusColor = statusColor;
            this.linkTypeLookup = linkTypeLookup;
            this.projectTypeToCategory = projectTypeToCategory;
            this.projectTypeToRoleCode = projectTypeToRoleCode;
            this.projectStatusLookup = projectStatusLookup;
            this.projectStatusToRoleCode = projectStatusToRoleCode;
            this.projectStatusSortOrder = projectStatusSortOrder;
            this.projectStatusScoreWeight = projectStatusScoreWeight;
            this.projectStatusColor = projectStatusColor;
            this.cachedRoles = cachedRoles;
            this.scoreWeightsMap = scoreWeightsMap;
            this.projectTypeNames = projectTypeNames;
            this.epicTypeNames = epicTypeNames;
            this.storyTypeNames = storyTypeNames;
            this.bugTypeNames = bugTypeNames;
            this.subtaskTypeNames = subtaskTypeNames;
            this.projectKey = projectKey;
            this.epicLinkType = epicLinkType;
            this.epicLinkName = epicLinkName;
        }

        static ConfigSnapshot empty() {
            return new ConfigSnapshot(
                    null, List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    List.of(), Map.of(),
                    Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                    null, null, null);
        }
    }
}

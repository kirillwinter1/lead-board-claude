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

    // Per-tenant cache: tenantId → loaded flag. -1L = no tenant (public/legacy).
    private final ConcurrentHashMap<Long, Boolean> loadedTenants = new ConcurrentHashMap<>();

    // Cached lookups (for current tenant — reloaded when tenant changes)
    private volatile Long defaultConfigId;
    private final ConcurrentHashMap<String, BoardCategory> typeToCategory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> typeToRoleCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StatusCategory> statusLookup = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> statusToRoleCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> statusSortOrder = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> statusScoreWeight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkCategory> linkTypeLookup = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> statusColor = new ConcurrentHashMap<>();
    private volatile List<WorkflowRoleEntity> cachedRoles = List.of();
    private volatile Map<String, Integer> scoreWeightsMap = Map.of();

    // Sets for quick lookups
    private volatile Set<String> projectTypeNames = Set.of();
    private volatile Set<String> epicTypeNames = Set.of();
    private volatile Set<String> storyTypeNames = Set.of();
    private volatile Set<String> bugTypeNames = Set.of();
    private volatile Set<String> subtaskTypeNames = Set.of();
    private volatile String projectKey;
    private volatile String epicLinkType;
    private volatile String epicLinkName;

    // Track which tenant is currently loaded
    private volatile Long currentlyLoadedTenantId = null;

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
        loadConfiguration();
    }

    public synchronized void clearCache() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            loadedTenants.remove(tenantId);
        }
        loadConfiguration();
    }

    /**
     * Ensure configuration is loaded for the current tenant.
     * Call this before any cache read in a multi-tenant context.
     * Synchronized to prevent race conditions when multiple threads
     * switch between tenants concurrently (BUG-60).
     */
    public synchronized void ensureLoaded() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            // No tenant context — use default (already loaded at startup)
            return;
        }
        if (!tenantId.equals(currentlyLoadedTenantId)) {
            // Different tenant — reload
            loadConfiguration();
            currentlyLoadedTenantId = tenantId;
            loadedTenants.put(tenantId, true);
        }
    }

    private void loadConfiguration() {
        try {
            String envProjectKey = jiraConfigResolver.getProjectKey();
            ProjectConfigurationEntity config = null;

            // 1. Try to find by project_key from env
            if (envProjectKey != null && !envProjectKey.isBlank()) {
                config = configRepo.findByProjectKey(envProjectKey).orElse(null);
            }

            // 2. Fallback: find default config
            if (config == null) {
                config = configRepo.findByIsDefaultTrue().orElse(null);
            }

            if (config == null) {
                log.warn("No default project configuration found in DB. Using empty config.");
                defaultConfigId = null;
                projectKey = null;
                return;
            }

            // 3. Auto-assign project_key if config has none and env has one
            if (config.getProjectKey() == null && envProjectKey != null && !envProjectKey.isBlank()) {
                config.setProjectKey(envProjectKey);
                configRepo.save(config);
                log.info("Auto-assigned project key '{}' to default configuration", envProjectKey);
            }

            defaultConfigId = config.getId();
            projectKey = config.getProjectKey();

            // Load roles
            cachedRoles = roleRepo.findByConfigIdOrderBySortOrderAsc(defaultConfigId);

            // Load issue type mappings
            typeToCategory.clear();
            typeToRoleCode.clear();
            List<IssueTypeMappingEntity> typeMappings = issueTypeRepo.findByConfigId(defaultConfigId);
            Set<String> projectNames = new HashSet<>();
            Set<String> epicNames = new HashSet<>();
            Set<String> storyNames = new HashSet<>();
            Set<String> bugNames = new HashSet<>();
            Set<String> subtaskNames = new HashSet<>();
            for (IssueTypeMappingEntity m : typeMappings) {
                if (m.getBoardCategory() == null) continue; // Skip unmapped types
                typeToCategory.put(m.getJiraTypeName().toLowerCase(), m.getBoardCategory());
                if (m.getWorkflowRoleCode() != null) {
                    typeToRoleCode.put(m.getJiraTypeName().toLowerCase(), m.getWorkflowRoleCode());
                }
                switch (m.getBoardCategory()) {
                    case PROJECT -> projectNames.add(m.getJiraTypeName());
                    case EPIC -> epicNames.add(m.getJiraTypeName());
                    case STORY -> storyNames.add(m.getJiraTypeName());
                    case BUG -> bugNames.add(m.getJiraTypeName());
                    case SUBTASK -> subtaskNames.add(m.getJiraTypeName());
                    default -> {}
                }
            }
            projectTypeNames = Set.copyOf(projectNames);
            epicTypeNames = Set.copyOf(epicNames);
            storyTypeNames = Set.copyOf(storyNames);
            bugTypeNames = Set.copyOf(bugNames);
            subtaskTypeNames = Set.copyOf(subtaskNames);

            // Load epic link config
            epicLinkType = config.getEpicLinkType();
            epicLinkName = config.getEpicLinkName();

            // Load status mappings
            statusLookup.clear();
            statusToRoleCode.clear();
            statusSortOrder.clear();
            statusScoreWeight.clear();
            statusColor.clear();
            List<StatusMappingEntity> statusMappings = statusMappingRepo.findByConfigId(defaultConfigId);
            for (StatusMappingEntity sm : statusMappings) {
                String key = buildStatusKey(sm.getIssueCategory().name(), sm.getJiraStatusName());
                statusLookup.put(key, sm.getStatusCategory());
                if (sm.getWorkflowRoleCode() != null) {
                    statusToRoleCode.put(key, sm.getWorkflowRoleCode());
                }
                statusSortOrder.put(key, sm.getSortOrder());
                statusScoreWeight.put(key, sm.getScoreWeight());
                if (sm.getColor() != null) {
                    statusColor.put(key, sm.getColor());
                }
            }

            // Load score weights from JSONB
            if (config.getStatusScoreWeights() != null && !config.getStatusScoreWeights().isEmpty()) {
                try {
                    scoreWeightsMap = objectMapper.readValue(config.getStatusScoreWeights(),
                            new TypeReference<Map<String, Integer>>() {});
                } catch (Exception e) {
                    log.error("Failed to parse status_score_weights JSONB", e);
                    scoreWeightsMap = Map.of();
                }
            }

            // Load link type mappings
            linkTypeLookup.clear();
            List<LinkTypeMappingEntity> linkMappings = linkTypeRepo.findByConfigId(defaultConfigId);
            for (LinkTypeMappingEntity lm : linkMappings) {
                linkTypeLookup.put(lm.getJiraLinkTypeName().toLowerCase(), lm.getLinkCategory());
            }

            if (cachedRoles.isEmpty() && typeMappings.isEmpty()) {
                log.warn("Workflow configuration is EMPTY — no roles or type mappings found. " +
                        "Auto-detect will populate config on first Jira sync, or configure manually via Settings > Workflow.");
            } else {
                log.info("Workflow configuration loaded: {} roles, {} type mappings, {} status mappings, {} link mappings",
                        cachedRoles.size(), typeMappings.size(), statusMappings.size(), linkMappings.size());
            }
        } catch (Exception e) {
            log.error("Failed to load workflow configuration from DB", e);
        }
    }

    // ==================== Issue Type Categorization ====================

    public BoardCategory categorizeIssueType(String jiraTypeName) {
        ensureLoaded();
        if (jiraTypeName == null) return null;
        return typeToCategory.get(jiraTypeName.toLowerCase());
    }

    public boolean isProject(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName) == BoardCategory.PROJECT;
    }

    public boolean isEpic(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName) == BoardCategory.EPIC;
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
        BoardCategory cat = categorizeIssueType(jiraTypeName);
        return cat == BoardCategory.STORY || cat == BoardCategory.BUG;
    }

    public List<String> getBugTypeNames() {
        return List.copyOf(bugTypeNames);
    }

    public List<String> getProjectTypeNames() {
        return List.copyOf(projectTypeNames);
    }

    public List<String> getEpicTypeNames() {
        return List.copyOf(epicTypeNames);
    }

    public List<String> getStoryTypeNames() {
        return List.copyOf(storyTypeNames);
    }

    public List<String> getSubtaskTypeNames() {
        return List.copyOf(subtaskTypeNames);
    }

    // ==================== Roles ====================

    public String getSubtaskRole(String jiraTypeName) {
        if (jiraTypeName == null) return getDefaultRoleCode();
        String role = typeToRoleCode.get(jiraTypeName.toLowerCase());
        return role != null ? role : getDefaultRoleCode();
    }

    public String getDefaultRoleCode() {
        return cachedRoles.stream()
                .filter(WorkflowRoleEntity::isDefault)
                .findFirst()
                .map(WorkflowRoleEntity::getCode)
                .orElse("DEV");
    }

    public List<WorkflowRoleEntity> getRolesInPipelineOrder() {
        ensureLoaded();
        return cachedRoles;
    }

    public List<String> getRoleCodesInPipelineOrder() {
        return cachedRoles.stream().map(WorkflowRoleEntity::getCode).toList();
    }

    // ==================== Status Categorization ====================

    public StatusCategory categorize(String status, String issueType) {
        ensureLoaded();
        if (status == null) return StatusCategory.NEW;

        BoardCategory cat = categorizeIssueType(issueType);
        if (cat == null) return StatusCategory.NEW; // Unmapped type
        return categorizeByBoardCategory(status, cat);
    }

    public StatusCategory categorizeEpic(String status) {
        return categorizeByBoardCategory(status, BoardCategory.EPIC);
    }

    public StatusCategory categorizeStory(String status) {
        return categorizeByBoardCategory(status, BoardCategory.STORY);
    }

    public StatusCategory categorizeSubtask(String status) {
        return categorizeByBoardCategory(status, BoardCategory.SUBTASK);
    }

    private StatusCategory categorizeByBoardCategory(String status, BoardCategory boardCat) {
        if (status == null) return StatusCategory.NEW;

        // Try exact match in DB mapping
        String key = buildStatusKey(boardCat.name(), status);
        StatusCategory cat = statusLookup.get(key);
        if (cat != null) return cat;

        // Try case-insensitive
        for (Map.Entry<String, StatusCategory> entry : statusLookup.entrySet()) {
            if (entry.getKey().startsWith(boardCat.name() + ":") &&
                entry.getKey().substring(boardCat.name().length() + 1).equalsIgnoreCase(status)) {
                return entry.getValue();
            }
        }

        // BUG fallback: try STORY mappings if no BUG-specific mapping exists
        if (boardCat == BoardCategory.BUG) {
            String storyKey = buildStatusKey("STORY", status);
            StatusCategory storyCat = statusLookup.get(storyKey);
            if (storyCat != null) return storyCat;
            for (Map.Entry<String, StatusCategory> entry : statusLookup.entrySet()) {
                if (entry.getKey().startsWith("STORY:") &&
                    entry.getKey().substring(6).equalsIgnoreCase(status)) {
                    return entry.getValue();
                }
            }
        }

        // Fallback: substring matching
        String s = status.toLowerCase();
        if (s.contains("done") || s.contains("closed") || s.contains("resolved") ||
            s.contains("завершен") || s.contains("готов") || s.contains("выполнен")) {
            return StatusCategory.DONE;
        }
        if (s.contains("progress") || s.contains("work") || s.contains("review") ||
            s.contains("test") || s.contains("develop") || s.contains("analysis") ||
            s.contains("accept") || s.contains("e2e") || s.contains("plan") ||
            s.contains("в работе") || s.contains("ревью") || s.contains("разработ") ||
            s.contains("анализ") || s.contains("тест") || s.contains("запланирован") ||
            s.contains("приёмк") || s.contains("приемк") || s.contains("проверк")) {
            return StatusCategory.IN_PROGRESS;
        }

        log.warn("Unknown status '{}' for category {}, defaulting to NEW", status, boardCat);
        return StatusCategory.NEW;
    }

    public boolean isDone(String status, String issueType) {
        return categorize(status, issueType) == StatusCategory.DONE;
    }

    public boolean isInProgress(String status, String issueType) {
        StatusCategory cat = categorize(status, issueType);
        return cat == StatusCategory.IN_PROGRESS || cat == StatusCategory.PLANNED;
    }

    // ==================== Phase (Role) Determination ====================

    public String determinePhase(String status, String issueType) {
        if (status == null && issueType == null) return getDefaultRoleCode();

        // First check by issue type (for subtasks)
        if (issueType != null) {
            String roleByType = typeToRoleCode.get(issueType.toLowerCase());
            if (roleByType != null) return roleByType;
        }

        // Then check by status in mapping
        if (status != null) {
            // Try STORY status mappings first (they have role codes)
            String storyKey = buildStatusKey("STORY", status);
            String role = statusToRoleCode.get(storyKey);
            if (role != null) return role;

            // Case-insensitive search
            for (Map.Entry<String, String> entry : statusToRoleCode.entrySet()) {
                String entryStatus = entry.getKey().substring(entry.getKey().indexOf(':') + 1);
                if (entryStatus.equalsIgnoreCase(status)) return entry.getValue();
            }

            // Fallback substring matching
            String lower = status.toLowerCase();
            if (lower.contains("analysis") || lower.contains("анализ") || lower.contains("аналитик")) return "SA";
            if (lower.contains("test") || lower.contains("qa") || lower.contains("тест")) return "QA";
        }

        // Fallback by issue type substring
        if (issueType != null) {
            String typeLower = issueType.toLowerCase();
            if (typeLower.contains("аналитик") || typeLower.contains("analyt")) return "SA";
            if (typeLower.contains("тестир") || typeLower.contains("test") || typeLower.contains("qa")) return "QA";
            // Bugs default to QA phase
            if (isBug(issueType)) return "QA";
        }

        return getDefaultRoleCode();
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
        // First try JSONB score weights map (exact match)
        Integer weight = scoreWeightsMap.get(status);
        if (weight != null) return weight;

        // Try case-insensitive
        for (Map.Entry<String, Integer> entry : scoreWeightsMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(status)) return entry.getValue();
        }

        // Try DB status_mappings score_weight for EPIC
        String epicKey = buildStatusKey("EPIC", status);
        Integer dbWeight = statusScoreWeight.get(epicKey);
        if (dbWeight != null) return dbWeight;

        // Case-insensitive DB lookup
        for (Map.Entry<String, Integer> entry : statusScoreWeight.entrySet()) {
            if (entry.getKey().startsWith("EPIC:") &&
                entry.getKey().substring(5).equalsIgnoreCase(status)) {
                return entry.getValue();
            }
        }

        return 0;
    }

    public int getStoryStatusSortOrder(String storyStatus) {
        if (storyStatus == null) return 0;
        String key = buildStatusKey("STORY", storyStatus);
        Integer order = statusSortOrder.get(key);
        if (order != null) return order;

        // Case-insensitive
        for (Map.Entry<String, Integer> entry : statusSortOrder.entrySet()) {
            if (entry.getKey().startsWith("STORY:") &&
                entry.getKey().substring(6).equalsIgnoreCase(storyStatus)) {
                return entry.getValue();
            }
        }

        return 0;
    }

    public String getStoryStatusColor(String storyStatus) {
        if (storyStatus == null) return null;
        String key = buildStatusKey("STORY", storyStatus);
        String color = statusColor.get(key);
        if (color != null) return color;

        // Case-insensitive
        for (Map.Entry<String, String> entry : statusColor.entrySet()) {
            if (entry.getKey().startsWith("STORY:") &&
                entry.getKey().substring(6).equalsIgnoreCase(storyStatus)) {
                return entry.getValue();
            }
        }

        return null;
    }

    public int getStoryStatusScoreWeight(String storyStatus) {
        if (storyStatus == null) return 0;
        String key = buildStatusKey("STORY", storyStatus);
        Integer weight = statusScoreWeight.get(key);
        if (weight != null) return weight;

        // Case-insensitive
        for (Map.Entry<String, Integer> entry : statusScoreWeight.entrySet()) {
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
        ensureLoaded();

        // 1. Try existing DB-driven lookup
        int dbWeight;
        if (boardCat == BoardCategory.EPIC) {
            dbWeight = getStatusScoreWeight(status);
        } else {
            dbWeight = getStoryStatusScoreWeight(status);
        }
        if (dbWeight != 0) return dbWeight;

        // 2. Category-based fallback: categorize the status, then get default weight
        StatusCategory statusCategory = categorizeByBoardCategory(status, boardCat);
        return getDefaultScoreWeightForCategory(statusCategory, boardCat);
    }

    /**
     * Returns the first status name mapped to a given StatusCategory for a BoardCategory.
     * Used by SimulationPlanner to get real status names like "Done" or "In Progress" from config.
     */
    public String getFirstStatusNameForCategory(StatusCategory target, BoardCategory boardCat) {
        if (target == null || boardCat == null) return null;
        ensureLoaded();

        String prefix = boardCat.name() + ":";
        for (Map.Entry<String, StatusCategory> entry : statusLookup.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue() == target) {
                return entry.getKey().substring(prefix.length());
            }
        }

        // BUG fallback: try STORY mappings
        if (boardCat == BoardCategory.BUG) {
            String storyPrefix = "STORY:";
            for (Map.Entry<String, StatusCategory> entry : statusLookup.entrySet()) {
                if (entry.getKey().startsWith(storyPrefix) && entry.getValue() == target) {
                    return entry.getKey().substring(storyPrefix.length());
                }
            }
        }

        // Hardcoded last resort for common status names
        return switch (target) {
            case DONE -> "Done";
            case IN_PROGRESS -> "In Progress";
            case NEW -> "New";
            default -> null;
        };
    }

    /**
     * Returns all distinct status names mapped to a given StatusCategory across all board categories.
     */
    public List<String> getStatusNamesByCategory(StatusCategory target) {
        if (target == null) return List.of();
        ensureLoaded();

        Set<String> names = new HashSet<>();
        for (Map.Entry<String, StatusCategory> entry : statusLookup.entrySet()) {
            if (entry.getValue() == target) {
                int colonIdx = entry.getKey().indexOf(':');
                if (colonIdx >= 0) {
                    names.add(entry.getKey().substring(colonIdx + 1));
                }
            }
        }
        return new ArrayList<>(names);
    }

    // ==================== Link Types ====================

    public LinkCategory categorizeLinkType(String linkTypeName) {
        if (linkTypeName == null) return LinkCategory.IGNORE;
        LinkCategory cat = linkTypeLookup.get(linkTypeName.toLowerCase());
        if (cat != null) return cat;

        // Fallback: substring matching
        String lower = linkTypeName.toLowerCase();
        if (lower.contains("block")) return LinkCategory.BLOCKS;
        if (lower.contains("relat") || lower.contains("duplicate") || lower.contains("clone")) return LinkCategory.RELATED;

        return LinkCategory.IGNORE;
    }

    // ==================== Sync helpers ====================

    public String computeBoardCategory(String issueType, boolean isSubtask) {
        BoardCategory cat = categorizeIssueType(issueType);
        if (cat == null) return null; // Unmapped type
        if (isSubtask && cat != BoardCategory.SUBTASK) {
            return BoardCategory.SUBTASK.name();
        }
        return cat.name();
    }

    public String computeWorkflowRole(String issueType) {
        BoardCategory cat = categorizeIssueType(issueType);
        if (cat == BoardCategory.SUBTASK) {
            return getSubtaskRole(issueType);
        }
        return null;
    }

    // ==================== Poker / Story creation ====================

    public String getSubtaskTypeName(String roleCode) {
        if (roleCode == null) return null;
        // Find issue type mapped to this role code
        for (Map.Entry<String, String> entry : typeToRoleCode.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(roleCode)) {
                // Return the original name (not lowercased key)
                for (IssueTypeMappingEntity m : issueTypeRepo.findByConfigId(defaultConfigId)) {
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
        return storyTypeNames.stream().findFirst().orElse("Story");
    }

    // ==================== Helpers ====================

    public Long getDefaultConfigId() {
        ensureLoaded();
        return defaultConfigId;
    }

    public String getProjectKey() {
        ensureLoaded();
        return projectKey;
    }

    public String getEpicLinkType() {
        return epicLinkType != null ? epicLinkType : "parent";
    }

    public String getEpicLinkName() {
        return epicLinkName;
    }

    /**
     * Returns all status names mapped for a given board category and status category.
     * Used for backwards compatibility with methods that need lists of status names.
     */
    public List<String> getStatusNames(BoardCategory boardCategory, StatusCategory statusCategory) {
        List<String> result = new ArrayList<>();
        String prefix = boardCategory.name() + ":";
        for (Map.Entry<String, StatusCategory> entry : statusLookup.entrySet()) {
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
        List<StoryPipelineStatus> result = new ArrayList<>();
        String prefix = "STORY:";
        for (Map.Entry<String, StatusCategory> entry : statusLookup.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            StatusCategory cat = entry.getValue();
            if (cat == StatusCategory.NEW || cat == StatusCategory.DONE || cat == StatusCategory.TODO) continue;

            String statusName = entry.getKey().substring(prefix.length());
            int sortOrder = statusSortOrder.getOrDefault(entry.getKey(), 0);
            String color = statusColor.getOrDefault(entry.getKey(), null);
            result.add(new StoryPipelineStatus(statusName, sortOrder, color));
        }
        result.sort(Comparator.comparingInt(StoryPipelineStatus::sortOrder));
        return result;
    }

    public record StoryPipelineStatus(String statusName, int sortOrder, String color) {}

    private String buildStatusKey(String issueCategory, String statusName) {
        return issueCategory + ":" + statusName;
    }
}

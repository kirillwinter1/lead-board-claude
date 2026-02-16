package com.leadboard.config.service;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.status.StatusCategory;
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
    private final JiraProperties jiraProperties;

    // Cached lookups
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
    private volatile Set<String> epicTypeNames = Set.of();
    private volatile Set<String> storyTypeNames = Set.of();
    private volatile Set<String> subtaskTypeNames = Set.of();
    private volatile String projectKey;

    public WorkflowConfigService(
            ProjectConfigurationRepository configRepo,
            WorkflowRoleRepository roleRepo,
            IssueTypeMappingRepository issueTypeRepo,
            StatusMappingRepository statusMappingRepo,
            LinkTypeMappingRepository linkTypeRepo,
            ObjectMapper objectMapper,
            JiraProperties jiraProperties) {
        this.configRepo = configRepo;
        this.roleRepo = roleRepo;
        this.issueTypeRepo = issueTypeRepo;
        this.statusMappingRepo = statusMappingRepo;
        this.linkTypeRepo = linkTypeRepo;
        this.objectMapper = objectMapper;
        this.jiraProperties = jiraProperties;
    }

    @PostConstruct
    public void init() {
        loadConfiguration();
    }

    public void clearCache() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        try {
            String envProjectKey = jiraProperties.getProjectKey();
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
            Set<String> epicNames = new HashSet<>();
            Set<String> storyNames = new HashSet<>();
            Set<String> subtaskNames = new HashSet<>();
            for (IssueTypeMappingEntity m : typeMappings) {
                typeToCategory.put(m.getJiraTypeName().toLowerCase(), m.getBoardCategory());
                if (m.getWorkflowRoleCode() != null) {
                    typeToRoleCode.put(m.getJiraTypeName().toLowerCase(), m.getWorkflowRoleCode());
                }
                switch (m.getBoardCategory()) {
                    case EPIC -> epicNames.add(m.getJiraTypeName());
                    case STORY -> storyNames.add(m.getJiraTypeName());
                    case SUBTASK -> subtaskNames.add(m.getJiraTypeName());
                    default -> {}
                }
            }
            epicTypeNames = Set.copyOf(epicNames);
            storyTypeNames = Set.copyOf(storyNames);
            subtaskTypeNames = Set.copyOf(subtaskNames);

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
        if (jiraTypeName == null) return BoardCategory.IGNORE;
        BoardCategory cat = typeToCategory.get(jiraTypeName.toLowerCase());
        if (cat != null) return cat;

        // Fallback: substring matching
        String lower = jiraTypeName.toLowerCase();
        if (lower.contains("epic") || lower.contains("эпик")) return BoardCategory.EPIC;
        if (lower.contains("sub-task") || lower.contains("подзадача") ||
            lower.contains("аналитик") || lower.contains("разработк") || lower.contains("тестирован")) {
            return BoardCategory.SUBTASK;
        }
        if (lower.contains("story") || lower.contains("bug") || lower.contains("task") ||
            lower.contains("история") || lower.contains("баг") || lower.contains("задача")) {
            return BoardCategory.STORY;
        }

        log.warn("Unknown issue type '{}', defaulting to IGNORE", jiraTypeName);
        return BoardCategory.IGNORE;
    }

    public boolean isEpic(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName) == BoardCategory.EPIC;
    }

    public boolean isStory(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName) == BoardCategory.STORY;
    }

    public boolean isSubtask(String jiraTypeName) {
        return categorizeIssueType(jiraTypeName) == BoardCategory.SUBTASK;
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
        if (role != null) return role;

        // Fallback: substring matching
        String lower = jiraTypeName.toLowerCase();
        if (lower.contains("аналитик") || lower.contains("analyt") || lower.contains("analysis")) return "SA";
        if (lower.contains("тестир") || lower.contains("test") || lower.contains("qa")) return "QA";

        return getDefaultRoleCode();
    }

    public String getDefaultRoleCode() {
        return cachedRoles.stream()
                .filter(WorkflowRoleEntity::isDefault)
                .findFirst()
                .map(WorkflowRoleEntity::getCode)
                .orElse("DEV");
    }

    public List<WorkflowRoleEntity> getRolesInPipelineOrder() {
        return cachedRoles;
    }

    public List<String> getRoleCodesInPipelineOrder() {
        return cachedRoles.stream().map(WorkflowRoleEntity::getCode).toList();
    }

    // ==================== Status Categorization ====================

    public StatusCategory categorize(String status, String issueType) {
        if (status == null) return StatusCategory.NEW;

        BoardCategory cat = categorizeIssueType(issueType);
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
            if (typeLower.contains("тестир") || typeLower.contains("test") || typeLower.contains("qa") ||
                typeLower.contains("bug") || typeLower.contains("баг") || typeLower.contains("дефект")) return "QA";
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
        if (isSubtask) {
            BoardCategory cat = categorizeIssueType(issueType);
            return (cat == BoardCategory.SUBTASK) ? "SUBTASK" : categorizeIssueType(issueType).name();
        }
        return categorizeIssueType(issueType).name();
    }

    public String computeWorkflowRole(String issueType) {
        if (isSubtask(issueType)) {
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
        return defaultConfigId;
    }

    public String getProjectKey() {
        return projectKey;
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

package com.leadboard.config.service;

import com.leadboard.config.entity.*;
import com.leadboard.config.repository.*;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraTransition;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
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
    private final JiraClient jiraClient;
    private final JiraIssueRepository jiraIssueRepository;

    public MappingAutoDetectService(
            JiraMetadataService jiraMetadataService,
            ProjectConfigurationRepository configRepo,
            WorkflowRoleRepository roleRepo,
            IssueTypeMappingRepository issueTypeRepo,
            StatusMappingRepository statusMappingRepo,
            LinkTypeMappingRepository linkTypeRepo,
            WorkflowConfigService workflowConfigService,
            JiraClient jiraClient,
            JiraIssueRepository jiraIssueRepository
    ) {
        this.jiraMetadataService = jiraMetadataService;
        this.configRepo = configRepo;
        this.roleRepo = roleRepo;
        this.issueTypeRepo = issueTypeRepo;
        this.statusMappingRepo = statusMappingRepo;
        this.linkTypeRepo = linkTypeRepo;
        this.workflowConfigService = workflowConfigService;
        this.jiraClient = jiraClient;
        this.jiraIssueRepository = jiraIssueRepository;
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

        // Enhance with real workflow graph from Jira Transitions API
        enhanceWithTransitionsGraph(configId, warnings);

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

    // ==================== Workflow Graph Enhancement ====================

    /**
     * Enhances status mappings with real workflow order from Jira Transitions API.
     * For each board category, fetches transitions from sample issues,
     * builds a directed graph, and uses BFS from NEW statuses to compute accurate
     * sortOrder and scoreWeight based on actual workflow position.
     */
    void enhanceWithTransitionsGraph(Long configId, List<String> warnings) {
        for (BoardCategory category : List.of(BoardCategory.EPIC, BoardCategory.STORY, BoardCategory.SUBTASK)) {
            try {
                enhanceCategoryWithGraph(configId, category, warnings);
            } catch (Exception e) {
                log.warn("Failed to enhance {} workflow with transitions graph: {}",
                        category, e.getMessage());
                warnings.add("Could not build workflow graph for " + category + ": " + e.getMessage());
            }
        }
    }

    private void enhanceCategoryWithGraph(Long configId, BoardCategory category, List<String> warnings) {
        List<StatusMappingEntity> mappings = statusMappingRepo.findByConfigIdAndIssueCategory(configId, category);
        if (mappings.isEmpty()) return;

        // Collect unique status names
        Set<String> statusNames = new LinkedHashSet<>();
        for (StatusMappingEntity m : mappings) {
            statusNames.add(m.getJiraStatusName());
        }

        // Find a sample issue for each status
        Map<String, String> statusToIssueKey = new LinkedHashMap<>();
        for (String statusName : statusNames) {
            List<JiraIssueEntity> issues = jiraIssueRepository.findByStatusAndBoardCategory(
                    statusName, category.name());
            if (!issues.isEmpty()) {
                statusToIssueKey.put(statusName, issues.getFirst().getIssueKey());
            }
        }

        if (statusToIssueKey.isEmpty()) {
            log.debug("No sample issues found for {} — skipping graph enhancement", category);
            return;
        }

        // Build transition graph: currentStatus → Set<targetStatus>
        // Also track statusCategory from transition targets
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        Map<String, String> statusCategoryKeys = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : statusToIssueKey.entrySet()) {
            String currentStatus = entry.getKey();
            String issueKey = entry.getValue();

            try {
                List<JiraTransition> transitions = jiraClient.getTransitionsBasicAuth(issueKey);
                Set<String> targets = new LinkedHashSet<>();
                for (JiraTransition t : transitions) {
                    if (t.to() != null && t.to().name() != null) {
                        targets.add(t.to().name());
                        // Track statusCategory for targets
                        if (t.to().statusCategory() != null && t.to().statusCategory().key() != null) {
                            statusCategoryKeys.put(t.to().name(), t.to().statusCategory().key());
                        }
                    }
                }
                graph.put(currentStatus, targets);
            } catch (Exception e) {
                log.debug("Failed to get transitions for {} ({}): {}", issueKey, currentStatus, e.getMessage());
            }
        }

        if (graph.isEmpty()) {
            log.debug("No transitions retrieved for {} — skipping graph enhancement", category);
            return;
        }

        // Correct statusCategory from transitions API data — only for DONE (reliable).
        // Jira's "new" (To Do) category is too broad (includes intermediate waiting statuses),
        // so we keep our heuristic mapping for non-done statuses.
        for (StatusMappingEntity m : mappings) {
            String apiCatKey = statusCategoryKeys.get(m.getJiraStatusName());
            if ("done".equals(apiCatKey) && m.getStatusCategory() != StatusCategory.DONE) {
                log.info("Correcting statusCategory for '{}' in {}: {} → DONE",
                        m.getJiraStatusName(), category, m.getStatusCategory());
                m.setStatusCategory(StatusCategory.DONE);
            }
        }

        // Also populate statusCategoryKeys from existing mappings (for statuses not seen as targets)
        for (StatusMappingEntity m : mappings) {
            String catKey = switch (m.getStatusCategory()) {
                case NEW -> "new";
                case DONE -> "done";
                default -> "indeterminate";
            };
            statusCategoryKeys.putIfAbsent(m.getJiraStatusName(), catKey);
        }

        // Find the TRUE workflow start status.
        // Step 1: Collect all statuses with StatusCategory.NEW
        Set<String> newStatuses = new LinkedHashSet<>();
        for (StatusMappingEntity m : mappings) {
            if (m.getStatusCategory() == StatusCategory.NEW) {
                newStatuses.add(m.getJiraStatusName());
            }
        }

        // Fallback: if no NEW statuses found, look for statusCategory "new" from transitions
        if (newStatuses.isEmpty()) {
            for (Map.Entry<String, String> e : statusCategoryKeys.entrySet()) {
                if ("new".equals(e.getValue())) {
                    newStatuses.add(e.getKey());
                }
            }
        }

        // Step 2: If multiple NEW candidates, narrow down to find the TRUE start.
        // Strategy: prefer candidates that are SOURCES in the graph (have issues),
        // then pick the one with lowest in-degree among those.
        if (newStatuses.size() > 1 && !graph.isEmpty()) {
            // First, prefer candidates that are sources (have issues → have outgoing transitions)
            Set<String> sourceCandidates = new LinkedHashSet<>();
            for (String s : newStatuses) {
                if (graph.containsKey(s)) {
                    sourceCandidates.add(s);
                }
            }
            Set<String> pool = sourceCandidates.isEmpty() ? newStatuses : sourceCandidates;

            // Among the pool, find the one with lowest in-degree
            Map<String, Integer> inDegree = new LinkedHashMap<>();
            for (String s : pool) {
                inDegree.put(s, 0);
            }
            for (Map.Entry<String, Set<String>> gEntry : graph.entrySet()) {
                for (String target : gEntry.getValue()) {
                    if (inDegree.containsKey(target)) {
                        inDegree.merge(target, 1, Integer::sum);
                    }
                }
            }
            int minDeg = inDegree.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            Set<String> narrowed = new LinkedHashSet<>();
            for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
                if (e.getValue() == minDeg) {
                    narrowed.add(e.getKey());
                }
            }
            if (!narrowed.isEmpty()) {
                String start = narrowed.iterator().next();
                newStatuses = new LinkedHashSet<>();
                newStatuses.add(start);
                log.info("Narrowed workflow start for {} to '{}' (in-degree={}, was {} candidates)",
                        category, start, minDeg, pool.size());
            }
        }

        if (newStatuses.isEmpty()) {
            log.debug("No NEW statuses found for {} — skipping graph enhancement", category);
            return;
        }

        // Build enhanced graph: add REVERSE edges for statuses that have no issues
        // (non-source statuses). These statuses only appear as targets in the original graph.
        // By reversing their incoming edges, BFS can traverse through them and establish ordering.
        Map<String, Set<String>> enhancedGraph = new LinkedHashMap<>(graph);
        for (Map.Entry<String, Set<String>> gEntry : graph.entrySet()) {
            String source = gEntry.getKey();
            for (String target : gEntry.getValue()) {
                if (!graph.containsKey(target) && statusNames.contains(target)) {
                    // target is not a source (no issues) — add reverse edge
                    enhancedGraph.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(source);
                }
            }
        }

        if (enhancedGraph.size() != graph.size()) {
            log.info("Enhanced graph for {} with reverse edges: {} → {} nodes",
                    category, graph.size(), enhancedGraph.size());
        }

        // Longest forward path from start status (each status gets unique position)
        Map<String, Integer> levels = longestForwardPath(newStatuses, enhancedGraph);

        // Assign levels to orphan statuses (not reached by graph traversal).
        // Sort orphans by statusCategory so they're placed in workflow order:
        // IN_PROGRESS (active work) → NEW (Jira "To Do"/waiting) → DONE (terminal).
        // Within same category, names containing "review" go after others.
        int maxLevel = levels.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> orphans = new ArrayList<>();
        List<String> doneOrphans = new ArrayList<>();
        for (String statusName : statusNames) {
            if (!levels.containsKey(statusName)) {
                String catKey = statusCategoryKeys.getOrDefault(statusName, "indeterminate");
                if ("done".equals(catKey)) {
                    doneOrphans.add(statusName);
                } else {
                    orphans.add(statusName);
                }
            }
        }
        // Sort non-done orphans: IN_PROGRESS first, then NEW; "review" names last within group
        orphans.sort((a, b) -> {
            String catA = statusCategoryKeys.getOrDefault(a, "indeterminate");
            String catB = statusCategoryKeys.getOrDefault(b, "indeterminate");
            int catOrder = orphanCategoryOrder(catA) - orphanCategoryOrder(catB);
            if (catOrder != 0) return catOrder;
            boolean reviewA = a.toLowerCase().contains("review");
            boolean reviewB = b.toLowerCase().contains("review");
            if (reviewA != reviewB) return reviewA ? 1 : -1;
            return a.compareToIgnoreCase(b);
        });
        int nextLevel = maxLevel + 1;
        for (String orphan : orphans) {
            levels.put(orphan, nextLevel++);
        }
        for (String done : doneOrphans) {
            levels.put(done, nextLevel);
        }

        // Recalculate maxLevel after assigning orphans
        maxLevel = levels.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        // Update sortOrder and scoreWeight
        int edgeCount = graph.values().stream().mapToInt(Set::size).sum();
        log.info("Built workflow graph for {}: {} statuses, {} edges, max depth {}",
                category, levels.size(), edgeCount, maxLevel);

        for (StatusMappingEntity m : mappings) {
            Integer level = levels.get(m.getJiraStatusName());
            if (level == null) continue;

            m.setSortOrder(level * 10);
            m.setScoreWeight(computeWeightFromLevel(level, maxLevel,
                    statusCategoryKeys.getOrDefault(m.getJiraStatusName(), "indeterminate")));
            statusMappingRepo.save(m);
        }
    }

    Map<String, Integer> bfsFromStatuses(Set<String> startStatuses, Map<String, Set<String>> graph) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        Queue<String> queue = new LinkedList<>();

        for (String start : startStatuses) {
            levels.put(start, 0);
            queue.add(start);
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentLevel = levels.get(current);
            Set<String> neighbors = graph.getOrDefault(current, Set.of());

            for (String neighbor : neighbors) {
                if (!levels.containsKey(neighbor)) {
                    levels.put(neighbor, currentLevel + 1);
                    queue.add(neighbor);
                }
            }
        }

        return levels;
    }

    /**
     * Computes the longest forward path from start statuses.
     * Uses BFS to establish edge direction, then relaxes forward-only edges
     * to find the longest path (unique position for each status in the pipeline).
     */
    Map<String, Integer> longestForwardPath(Set<String> startStatuses, Map<String, Set<String>> graph) {
        // Step 1: BFS for shortest-path levels (to detect forward vs back edges)
        Map<String, Integer> bfsLevels = bfsFromStatuses(startStatuses, graph);

        // Step 2: Build forward-only graph (keep only edges where target BFS level > source)
        Map<String, Set<String>> forwardGraph = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            String current = entry.getKey();
            Integer currentBfs = bfsLevels.get(current);
            if (currentBfs == null) continue;

            Set<String> forwardNeighbors = new LinkedHashSet<>();
            for (String neighbor : entry.getValue()) {
                Integer neighborBfs = bfsLevels.get(neighbor);
                if (neighborBfs != null && neighborBfs > currentBfs) {
                    forwardNeighbors.add(neighbor);
                }
            }
            if (!forwardNeighbors.isEmpty()) {
                forwardGraph.put(current, forwardNeighbors);
            }
        }

        // Step 3: Longest path on DAG via topological-order relaxation
        List<String> topoOrder = bfsLevels.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();

        Map<String, Integer> longest = new LinkedHashMap<>();
        for (String start : startStatuses) {
            longest.put(start, 0);
        }

        for (String current : topoOrder) {
            Integer currentLevel = longest.get(current);
            if (currentLevel == null) continue;

            for (String neighbor : forwardGraph.getOrDefault(current, Set.of())) {
                int newLevel = currentLevel + 1;
                Integer existing = longest.get(neighbor);
                if (existing == null || newLevel > existing) {
                    longest.put(neighbor, newLevel);
                }
            }
        }

        return longest;
    }

    private int orphanCategoryOrder(String catKey) {
        return switch (catKey) {
            case "indeterminate" -> 0; // IN_PROGRESS — active work first
            case "new" -> 1;           // NEW/To Do — waiting states after
            default -> 2;
        };
    }

    int computeWeightFromLevel(int level, int maxLevel, String statusCategoryKey) {
        // Only the first status (level 0) gets -5; don't use statusCategory "new"
        // because Jira marks intermediate statuses (Ready to Release) as "To Do"
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
        return configRepo.findByIsDefaultTrue().map(ProjectConfigurationEntity::getId).orElse(null);
    }

    private Long getOrCreateDefaultConfigId() {
        return configRepo.findByIsDefaultTrue()
                .orElseGet(() -> {
                    ProjectConfigurationEntity config = new ProjectConfigurationEntity();
                    config.setName("Default");
                    config.setDefault(true);
                    return configRepo.save(config);
                })
                .getId();
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

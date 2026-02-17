package com.leadboard.board;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.RoughEstimateProperties;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.UnifiedPlanningService;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.PlannedEpic;
import com.leadboard.planning.dto.UnifiedPlanningResult.PlannedStory;
import com.leadboard.quality.DataQualityService;
import com.leadboard.quality.DataQualityViolation;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);
    private static final long SECONDS_PER_DAY = 8 * 3600; // 8 hours per day

    private final JiraIssueRepository issueRepository;
    private final JiraProperties jiraProperties;
    private final TeamRepository teamRepository;
    private final RoughEstimateProperties roughEstimateProperties;
    private final DataQualityService dataQualityService;
    private final UnifiedPlanningService unifiedPlanningService;
    private final WorkflowConfigService workflowConfigService;

    public BoardService(JiraIssueRepository issueRepository, JiraProperties jiraProperties,
                        TeamRepository teamRepository, RoughEstimateProperties roughEstimateProperties,
                        DataQualityService dataQualityService,
                        UnifiedPlanningService unifiedPlanningService,
                        WorkflowConfigService workflowConfigService) {
        this.issueRepository = issueRepository;
        this.jiraProperties = jiraProperties;
        this.teamRepository = teamRepository;
        this.roughEstimateProperties = roughEstimateProperties;
        this.dataQualityService = dataQualityService;
        this.unifiedPlanningService = unifiedPlanningService;
        this.workflowConfigService = workflowConfigService;
    }

    public BoardResponse getBoard(String query, List<String> statuses, List<Long> teamIds, int page, int size) {
        String projectKey = jiraProperties.getProjectKey();
        String baseUrl = jiraProperties.getBaseUrl();

        if (projectKey == null || projectKey.isEmpty() || baseUrl == null || baseUrl.isEmpty()) {
            return new BoardResponse(Collections.emptyList(), 0);
        }

        try {
            List<JiraIssueEntity> allIssues = issueRepository.findByProjectKey(projectKey);

            if (allIssues.isEmpty()) {
                log.warn("No cached issues found for project: {}. Run sync first.", projectKey);
                return new BoardResponse(Collections.emptyList(), 0);
            }

            Map<Long, String> teamNames = new HashMap<>();
            Map<Long, String> teamColors = new HashMap<>();
            teamRepository.findByActiveTrue().forEach(team -> {
                teamNames.put(team.getId(), team.getName());
                teamColors.put(team.getId(), team.getColor());
            });

            Map<String, JiraIssueEntity> issueMap = allIssues.stream()
                    .collect(Collectors.toMap(JiraIssueEntity::getIssueKey, e -> e));

            List<JiraIssueEntity> epics = allIssues.stream()
                    .filter(e -> workflowConfigService.isEpic(e.getIssueType()))
                    .collect(Collectors.toList());

            List<JiraIssueEntity> stories = allIssues.stream()
                    .filter(e -> workflowConfigService.isStory(e.getIssueType()))
                    .collect(Collectors.toList());

            List<JiraIssueEntity> subtasks = allIssues.stream()
                    .filter(JiraIssueEntity::isSubtask)
                    .collect(Collectors.toList());

            // Apply filters to epics
            List<JiraIssueEntity> filteredEpics = epics.stream()
                    .filter(epic -> {
                        if (query != null && !query.isEmpty()) {
                            String q = query.toLowerCase();
                            if (!epic.getIssueKey().toLowerCase().contains(q) &&
                                !epic.getSummary().toLowerCase().contains(q)) {
                                return false;
                            }
                        }
                        if (statuses != null && !statuses.isEmpty()) {
                            if (!statuses.contains(epic.getStatus())) {
                                return false;
                            }
                        }
                        if (teamIds != null && !teamIds.isEmpty()) {
                            if (epic.getTeamId() == null || !teamIds.contains(epic.getTeamId())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            // Build hierarchy
            Map<String, BoardNode> epicMap = new LinkedHashMap<>();
            Map<String, BoardNode> storyMap = new LinkedHashMap<>();

            for (JiraIssueEntity epic : filteredEpics) {
                BoardNode node = mapToNode(epic, baseUrl, teamNames, teamColors);
                epicMap.put(epic.getIssueKey(), node);
            }

            // Build reverse lookup: epicKey → parentProjectKey
            List<JiraIssueEntity> projectIssues = allIssues.stream()
                    .filter(e -> "PROJECT".equals(e.getBoardCategory()))
                    .collect(Collectors.toList());
            Map<String, String> epicToProjectKey = new HashMap<>();
            for (JiraIssueEntity proj : projectIssues) {
                // Parent mode: epics whose parentKey = project key
                for (JiraIssueEntity issue : allIssues) {
                    if (proj.getIssueKey().equals(issue.getParentKey()) && "EPIC".equals(issue.getBoardCategory())) {
                        epicToProjectKey.putIfAbsent(issue.getIssueKey(), proj.getIssueKey());
                    }
                }
                // Link mode: childEpicKeys
                String[] linkedKeys = proj.getChildEpicKeys();
                if (linkedKeys != null) {
                    for (String lk : linkedKeys) {
                        JiraIssueEntity linked = issueMap.get(lk);
                        if (linked != null && "EPIC".equals(linked.getBoardCategory())) {
                            epicToProjectKey.putIfAbsent(lk, proj.getIssueKey());
                        }
                    }
                }
            }

            // Set parentProjectKey on epic nodes
            for (Map.Entry<String, BoardNode> entry : epicMap.entrySet()) {
                String projKey = epicToProjectKey.get(entry.getKey());
                if (projKey != null) {
                    entry.getValue().setParentProjectKey(projKey);
                }
            }

            for (JiraIssueEntity story : stories) {
                BoardNode storyNode = mapToNode(story, baseUrl, teamNames, teamColors);
                storyMap.put(story.getIssueKey(), storyNode);

                String parentKey = story.getParentKey();
                if (parentKey != null && epicMap.containsKey(parentKey)) {
                    epicMap.get(parentKey).addChild(storyNode);
                }
            }

            for (JiraIssueEntity subtask : subtasks) {
                BoardNode subtaskNode = mapToNode(subtask, baseUrl, teamNames, teamColors);

                // Use WorkflowConfigService for role detection
                String role = workflowConfigService.getSubtaskRole(subtask.getIssueType());
                subtaskNode.setRole(role);

                String parentKey = subtask.getParentKey();
                if (parentKey != null && storyMap.containsKey(parentKey)) {
                    storyMap.get(parentKey).addChild(subtaskNode);
                }
            }

            enrichStoriesWithForecast(epicMap);

            for (BoardNode story : storyMap.values()) {
                aggregateProgress(story);
            }

            for (BoardNode epic : epicMap.values()) {
                aggregateProgress(epic);
            }

            addDataQualityAlerts(filteredEpics, stories, subtasks, issueMap, epicMap, storyMap);

            // Sort children within each epic by manualOrder
            for (BoardNode epic : epicMap.values()) {
                if (!epic.getChildren().isEmpty()) {
                    epic.getChildren().sort((a, b) -> {
                        Integer orderA = a.getManualOrder();
                        Integer orderB = b.getManualOrder();
                        if (orderA != null && orderB != null) return orderA.compareTo(orderB);
                        if (orderA != null) return -1;
                        if (orderB != null) return 1;
                        BigDecimal scoreA = a.getAutoScore();
                        BigDecimal scoreB = b.getAutoScore();
                        if (scoreA != null && scoreB != null) return scoreB.compareTo(scoreA);
                        if (scoreA != null) return -1;
                        if (scoreB != null) return 1;
                        return 0;
                    });
                }
            }

            // Sort epics by manualOrder
            List<BoardNode> items = new ArrayList<>(epicMap.values());
            items.sort((a, b) -> {
                Integer orderA = a.getManualOrder();
                Integer orderB = b.getManualOrder();
                if (orderA != null && orderB != null) return orderA.compareTo(orderB);
                if (orderA != null) return -1;
                if (orderB != null) return 1;
                BigDecimal scoreA = a.getAutoScore();
                BigDecimal scoreB = b.getAutoScore();
                if (scoreA != null && scoreB != null) return scoreB.compareTo(scoreA);
                if (scoreA != null) return -1;
                if (scoreB != null) return 1;
                return 0;
            });

            int total = items.size();
            int fromIndex = Math.min(page * size, total);
            int toIndex = Math.min(fromIndex + size, total);
            List<BoardNode> pagedItems = items.subList(fromIndex, toIndex);

            return new BoardResponse(pagedItems, total);
        } catch (Exception e) {
            log.error("Failed to build board from cache: {}", e.getMessage(), e);
            return new BoardResponse(Collections.emptyList(), 0);
        }
    }

    public BoardResponse getBoard() {
        return getBoard(null, null, null, 0, 50);
    }

    private BoardNode mapToNode(JiraIssueEntity entity, String baseUrl, Map<Long, String> teamNames, Map<Long, String> teamColors) {
        String jiraUrl = baseUrl + "/browse/" + entity.getIssueKey();
        BoardNode node = new BoardNode(
                entity.getIssueKey(),
                entity.getSummary(),
                entity.getStatus(),
                entity.getIssueType(),
                jiraUrl
        );

        if (entity.isSubtask()) {
            node.setEstimateSeconds(entity.getEffectiveEstimateSeconds());
            node.setLoggedSeconds(entity.getTimeSpentSeconds());
        }

        if (entity.getTeamId() != null) {
            node.setTeamId(entity.getTeamId());
            node.setTeamName(teamNames.get(entity.getTeamId()));
            node.setTeamColor(teamColors.get(entity.getTeamId()));
        } else if (entity.getTeamFieldValue() != null) {
            node.setTeamName(entity.getTeamFieldValue());
        }

        if (workflowConfigService.isEpic(entity.getIssueType())) {
            boolean epicInTodo = workflowConfigService.isAllowedForRoughEstimate(entity.getStatus());
            node.setEpicInTodo(epicInTodo);

            // Dynamic rough estimates
            node.setRoughEstimates(entity.getRoughEstimates());

            if (epicInTodo) {
                Map<String, BoardNode.RoleMetrics> roleProgressMap = buildRoleProgressFromRoughEstimates(entity);
                node.setRoleProgress(roleProgressMap);

                long roughEstimateSeconds = 0;
                if (entity.getRoughEstimates() != null) {
                    for (BigDecimal days : entity.getRoughEstimates().values()) {
                        if (days != null) {
                            roughEstimateSeconds += days.multiply(BigDecimal.valueOf(SECONDS_PER_DAY)).longValue();
                        }
                    }
                }
                if (roughEstimateSeconds > 0) {
                    node.setEstimateSeconds(roughEstimateSeconds);
                }
            }

            node.setFlagged(entity.getFlagged());
            node.setAutoScore(entity.getAutoScore());
            node.setManualOrder(entity.getManualOrder());
        } else if (workflowConfigService.isStory(entity.getIssueType())) {
            node.setAutoScore(entity.getAutoScore());
            node.setManualOrder(entity.getManualOrder());
            node.setFlagged(entity.getFlagged());
            node.setBlocks(entity.getBlocks());
            node.setBlockedBy(entity.getIsBlockedBy());
            node.setAssigneeAccountId(entity.getAssigneeAccountId());
            node.setAssigneeDisplayName(entity.getAssigneeDisplayName());

            List<JiraIssueEntity> childSubtasks = issueRepository.findByParentKey(entity.getIssueKey());
            long subtaskEstimate = 0;
            long subtaskSpent = 0;
            for (JiraIssueEntity st : childSubtasks) {
                subtaskEstimate += st.getEffectiveEstimateSeconds();
                subtaskSpent += st.getTimeSpentSeconds() != null ? st.getTimeSpentSeconds() : 0;
            }
            if (subtaskEstimate > 0) {
                long remainingSeconds = subtaskEstimate - subtaskSpent;
                if (remainingSeconds > 0) {
                    int workDays = (int) Math.ceil(remainingSeconds / 3600.0 / 8.0);
                    node.setExpectedDone(LocalDate.now().plusDays(workDays));
                } else {
                    node.setExpectedDone(LocalDate.now());
                }
            }
        }

        return node;
    }

    private void aggregateProgress(BoardNode node) {
        boolean isEpic = workflowConfigService.isEpic(node.getIssueType());

        if (node.getChildren().isEmpty()) {
            if (isEpic) {
                node.setEstimateSeconds(0L);
                node.setLoggedSeconds(0L);
                node.setProgress(0);
                return;
            }
            Long estimate = node.getEstimateSeconds();
            Long logged = node.getLoggedSeconds();
            if (estimate != null && estimate > 0) {
                int progress = (int) Math.min(100, ((logged != null ? logged : 0) * 100) / estimate);
                node.setProgress(progress);
            } else {
                node.setProgress(0);
            }
            return;
        }

        // Aggregate from children by role using dynamic role codes
        List<String> roleCodes = workflowConfigService.getRoleCodesInPipelineOrder();
        Map<String, long[]> roleMetrics = new LinkedHashMap<>(); // roleCode -> [estimate, logged]
        for (String code : roleCodes) {
            roleMetrics.put(code, new long[]{0, 0});
        }

        long totalLogged = 0;

        for (BoardNode child : node.getChildren()) {
            aggregateProgress(child);

            if (child.getRoleProgress() != null && !child.getRoleProgress().isEmpty()
                    && !workflowConfigService.isEpic(child.getIssueType())) {
                // Aggregate from child's dynamic roleProgressMap
                for (Map.Entry<String, BoardNode.RoleMetrics> entry : child.getRoleProgress().entrySet()) {
                    addToRoleMetrics(roleMetrics, entry.getKey(), entry.getValue());
                }
            } else if (child.getRole() != null) {
                // Direct subtask: role is already a code from workflowConfigService.getSubtaskRole()
                String roleCode = child.getRole();
                long est = child.getEstimateSeconds() != null ? child.getEstimateSeconds() : 0;
                long lg = child.getLoggedSeconds() != null ? child.getLoggedSeconds() : 0;
                long[] metrics = roleMetrics.computeIfAbsent(roleCode, k -> new long[]{0, 0});
                metrics[0] += est;
                metrics[1] += lg;
            }

            if (child.getLoggedSeconds() != null) {
                totalLogged += child.getLoggedSeconds();
            }
        }

        long totalEstimate = roleMetrics.values().stream().mapToLong(m -> m[0]).sum();

        if (isEpic && node.isEpicInTodo()) {
            if (totalEstimate > 0) {
                node.setEstimateSeconds(totalEstimate);
                node.setLoggedSeconds(totalLogged);
                node.setProgress((int) Math.min(100, (totalLogged * 100) / totalEstimate));

                Map<String, BoardNode.RoleMetrics> roleProgressMap = buildDynamicRoleProgressMap(roleMetrics, null);
                node.setRoleProgress(roleProgressMap);
            } else if (node.getRoleProgress() != null && !node.getRoleProgress().isEmpty()) {
                // Rough estimates already set on the node, just update logged
                node.setLoggedSeconds(totalLogged);
                Long estimate = node.getEstimateSeconds();
                if (estimate != null && estimate > 0) {
                    node.setProgress((int) Math.min(100, (totalLogged * 100) / estimate));
                } else {
                    node.setProgress(0);
                }
            }
        } else {
            node.setEstimateSeconds(totalEstimate);
            node.setLoggedSeconds(totalLogged);
            node.setProgress(totalEstimate > 0 ? (int) Math.min(100, (totalLogged * 100) / totalEstimate) : 0);

            Map<String, BigDecimal> roughEstimates = isEpic ? node.getRoughEstimates() : null;
            Map<String, BoardNode.RoleMetrics> roleProgressMap = buildDynamicRoleProgressMap(roleMetrics, roughEstimates);
            node.setRoleProgress(roleProgressMap);
        }
    }

    private Map<String, BoardNode.RoleMetrics> buildRoleProgressFromRoughEstimates(JiraIssueEntity entity) {
        Map<String, BigDecimal> roughEst = entity.getRoughEstimates();
        Map<String, BoardNode.RoleMetrics> roleProgressMap = new LinkedHashMap<>();
        for (var role : workflowConfigService.getRolesInPipelineOrder()) {
            BigDecimal days = roughEst != null ? roughEst.get(role.getCode()) : null;
            roleProgressMap.put(role.getCode(),
                    new BoardNode.RoleMetrics(0, 0, days, role.getDisplayName(), role.getColor()));
        }
        return roleProgressMap;
    }

    private Map<String, BoardNode.RoleMetrics> buildDynamicRoleProgressMap(
            Map<String, long[]> roleMetrics, Map<String, BigDecimal> roughEstimates) {
        Map<String, BoardNode.RoleMetrics> result = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> entry : roleMetrics.entrySet()) {
            String code = entry.getKey();
            long[] m = entry.getValue();
            BigDecimal roughDays = roughEstimates != null ? roughEstimates.get(code) : null;
            if (roughDays != null) {
                result.put(code, new BoardNode.RoleMetrics(m[0], m[1], roughDays));
            } else {
                result.put(code, new BoardNode.RoleMetrics(m[0], m[1]));
            }
        }
        return result;
    }

    private void addToRoleMetrics(Map<String, long[]> roleMetrics, String roleCode, BoardNode.RoleMetrics rm) {
        if (rm == null) return;
        long[] metrics = roleMetrics.computeIfAbsent(roleCode, k -> new long[]{0, 0});
        metrics[0] += rm.getEstimateSeconds();
        metrics[1] += rm.getLoggedSeconds();
    }

    private void enrichStoriesWithForecast(Map<String, BoardNode> epicMap) {
        Set<Long> teamIds = epicMap.values().stream()
                .map(BoardNode::getTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (teamIds.isEmpty()) return;

        Map<String, PlannedStory> allPlannedStories = new HashMap<>();

        for (Long teamId : teamIds) {
            try {
                UnifiedPlanningResult plan = unifiedPlanningService.calculatePlan(teamId);
                for (PlannedEpic plannedEpic : plan.epics()) {
                    for (PlannedStory plannedStory : plannedEpic.stories()) {
                        allPlannedStories.put(plannedStory.storyKey(), plannedStory);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to calculate unified plan for team {}: {}", teamId, e.getMessage());
            }
        }

        for (BoardNode epic : epicMap.values()) {
            if (epic.getTeamId() == null || epic.getChildren().isEmpty()) continue;

            for (BoardNode child : epic.getChildren()) {
                PlannedStory planned = allPlannedStories.get(child.getIssueKey());
                if (planned != null) {
                    child.setExpectedDone(planned.endDate());

                    if (child.getAssigneeDisplayName() == null && planned.phases() != null) {
                        for (var phase : planned.phases().values()) {
                            if (phase != null && phase.assigneeDisplayName() != null) {
                                child.setAssigneeAccountId(phase.assigneeAccountId());
                                child.setAssigneeDisplayName(phase.assigneeDisplayName());
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void addDataQualityAlerts(
            List<JiraIssueEntity> epics,
            List<JiraIssueEntity> stories,
            List<JiraIssueEntity> subtasks,
            Map<String, JiraIssueEntity> issueMap,
            Map<String, BoardNode> epicMap,
            Map<String, BoardNode> storyMap
    ) {
        Map<String, List<JiraIssueEntity>> childrenByParent = new HashMap<>();
        Map<String, List<JiraIssueEntity>> subtasksByParent = new HashMap<>();

        for (JiraIssueEntity story : stories) {
            if (story.getParentKey() != null) {
                childrenByParent.computeIfAbsent(story.getParentKey(), k -> new ArrayList<>()).add(story);
            }
        }
        for (JiraIssueEntity subtask : subtasks) {
            if (subtask.getParentKey() != null) {
                subtasksByParent.computeIfAbsent(subtask.getParentKey(), k -> new ArrayList<>()).add(subtask);
            }
        }

        for (JiraIssueEntity epic : epics) {
            BoardNode epicNode = epicMap.get(epic.getIssueKey());
            if (epicNode == null) continue;

            List<JiraIssueEntity> children = childrenByParent.getOrDefault(epic.getIssueKey(), List.of());
            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, children);
            epicNode.addAlerts(violations);
        }

        for (JiraIssueEntity story : stories) {
            BoardNode storyNode = storyMap.get(story.getIssueKey());
            if (storyNode == null) continue;

            JiraIssueEntity epic = story.getParentKey() != null ? issueMap.get(story.getParentKey()) : null;
            List<JiraIssueEntity> storySubtasks = subtasksByParent.getOrDefault(story.getIssueKey(), List.of());
            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, storySubtasks);
            storyNode.addAlerts(violations);

            for (JiraIssueEntity subtask : storySubtasks) {
                List<DataQualityViolation> subtaskViolations = dataQualityService.checkSubtask(
                        subtask, story, epic);
                for (BoardNode child : storyNode.getChildren()) {
                    if (child.getIssueKey().equals(subtask.getIssueKey())) {
                        child.addAlerts(subtaskViolations);
                        break;
                    }
                }
            }
        }
    }
}

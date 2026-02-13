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
import com.leadboard.status.StatusMappingConfig;
import com.leadboard.status.StatusMappingService;
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
    private final StatusMappingService statusMappingService;
    private final UnifiedPlanningService unifiedPlanningService;
    private final WorkflowConfigService workflowConfigService;

    public BoardService(JiraIssueRepository issueRepository, JiraProperties jiraProperties,
                        TeamRepository teamRepository, RoughEstimateProperties roughEstimateProperties,
                        DataQualityService dataQualityService, StatusMappingService statusMappingService,
                        UnifiedPlanningService unifiedPlanningService,
                        WorkflowConfigService workflowConfigService) {
        this.issueRepository = issueRepository;
        this.jiraProperties = jiraProperties;
        this.teamRepository = teamRepository;
        this.roughEstimateProperties = roughEstimateProperties;
        this.dataQualityService = dataQualityService;
        this.statusMappingService = statusMappingService;
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
            teamRepository.findByActiveTrue().forEach(team ->
                teamNames.put(team.getId(), team.getName())
            );

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
                BoardNode node = mapToNode(epic, baseUrl, teamNames);
                epicMap.put(epic.getIssueKey(), node);
            }

            for (JiraIssueEntity story : stories) {
                BoardNode storyNode = mapToNode(story, baseUrl, teamNames);
                storyMap.put(story.getIssueKey(), storyNode);

                String parentKey = story.getParentKey();
                if (parentKey != null && epicMap.containsKey(parentKey)) {
                    epicMap.get(parentKey).addChild(storyNode);
                }
            }

            for (JiraIssueEntity subtask : subtasks) {
                BoardNode subtaskNode = mapToNode(subtask, baseUrl, teamNames);

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

            StatusMappingConfig statusMapping = statusMappingService.getDefaultConfig();
            addDataQualityAlerts(filteredEpics, stories, subtasks, issueMap, epicMap, storyMap, statusMapping);

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

    private BoardNode mapToNode(JiraIssueEntity entity, String baseUrl, Map<Long, String> teamNames) {
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
        } else if (entity.getTeamFieldValue() != null) {
            node.setTeamName(entity.getTeamFieldValue());
        }

        if (workflowConfigService.isEpic(entity.getIssueType())) {
            boolean epicInTodo = workflowConfigService.isAllowedForRoughEstimate(entity.getStatus());
            node.setEpicInTodo(epicInTodo);

            // Legacy fields (for frontend backward compat)
            node.setRoughEstimateSaDays(entity.getRoughEstimateSaDays());
            node.setRoughEstimateDevDays(entity.getRoughEstimateDevDays());
            node.setRoughEstimateQaDays(entity.getRoughEstimateQaDays());

            // New dynamic rough estimates
            node.setRoughEstimates(entity.getRoughEstimates());

            if (epicInTodo) {
                BoardNode.RoleProgress roleProgress = new BoardNode.RoleProgress();
                roleProgress.setAnalytics(new BoardNode.RoleMetrics(0, 0, entity.getRoughEstimateSaDays()));
                roleProgress.setDevelopment(new BoardNode.RoleMetrics(0, 0, entity.getRoughEstimateDevDays()));
                roleProgress.setTesting(new BoardNode.RoleMetrics(0, 0, entity.getRoughEstimateQaDays()));
                node.setRoleProgress(roleProgress);

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

        // Aggregate from children by role
        // Use dynamic role codes from WorkflowConfigService
        List<String> roleCodes = workflowConfigService.getRoleCodesInPipelineOrder();
        Map<String, long[]> roleMetrics = new LinkedHashMap<>(); // roleCode -> [estimate, logged]
        for (String code : roleCodes) {
            roleMetrics.put(code, new long[]{0, 0});
        }

        long totalLogged = 0;

        for (BoardNode child : node.getChildren()) {
            aggregateProgress(child);

            if (child.getRoleProgress() != null && !workflowConfigService.isEpic(child.getIssueType())) {
                // Legacy aggregation from RoleProgress
                BoardNode.RoleProgress rp = child.getRoleProgress();
                addToRoleMetrics(roleMetrics, "SA", rp.getAnalytics());
                addToRoleMetrics(roleMetrics, "DEV", rp.getDevelopment());
                addToRoleMetrics(roleMetrics, "QA", rp.getTesting());
            } else if (child.getRole() != null) {
                // Map subtask role to our role codes
                String roleCode = mapSubtaskRoleToCode(child.getRole());
                long est = child.getEstimateSeconds() != null ? child.getEstimateSeconds() : 0;
                long lg = child.getLoggedSeconds() != null ? child.getLoggedSeconds() : 0;
                long[] metrics = roleMetrics.get(roleCode);
                if (metrics != null) {
                    metrics[0] += est;
                    metrics[1] += lg;
                }
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
                node.setProgress(totalEstimate > 0 ? (int) Math.min(100, (totalLogged * 100) / totalEstimate) : 0);

                BoardNode.RoleProgress roleProgress = new BoardNode.RoleProgress();
                long[] sa = roleMetrics.getOrDefault("SA", new long[]{0, 0});
                long[] dev = roleMetrics.getOrDefault("DEV", new long[]{0, 0});
                long[] qa = roleMetrics.getOrDefault("QA", new long[]{0, 0});
                roleProgress.setAnalytics(new BoardNode.RoleMetrics(sa[0], sa[1], null));
                roleProgress.setDevelopment(new BoardNode.RoleMetrics(dev[0], dev[1], null));
                roleProgress.setTesting(new BoardNode.RoleMetrics(qa[0], qa[1], null));
                node.setRoleProgress(roleProgress);
            } else if (node.getRoleProgress() != null) {
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

            BoardNode.RoleProgress roleProgress = new BoardNode.RoleProgress();
            long[] sa = roleMetrics.getOrDefault("SA", new long[]{0, 0});
            long[] dev = roleMetrics.getOrDefault("DEV", new long[]{0, 0});
            long[] qa = roleMetrics.getOrDefault("QA", new long[]{0, 0});

            if (isEpic) {
                roleProgress.setAnalytics(new BoardNode.RoleMetrics(sa[0], sa[1], node.getRoughEstimateSaDays()));
                roleProgress.setDevelopment(new BoardNode.RoleMetrics(dev[0], dev[1], node.getRoughEstimateDevDays()));
                roleProgress.setTesting(new BoardNode.RoleMetrics(qa[0], qa[1], node.getRoughEstimateQaDays()));
            } else {
                roleProgress.setAnalytics(new BoardNode.RoleMetrics(sa[0], sa[1]));
                roleProgress.setDevelopment(new BoardNode.RoleMetrics(dev[0], dev[1]));
                roleProgress.setTesting(new BoardNode.RoleMetrics(qa[0], qa[1]));
            }
            node.setRoleProgress(roleProgress);
        }
    }

    private void addToRoleMetrics(Map<String, long[]> roleMetrics, String roleCode, BoardNode.RoleMetrics rm) {
        if (rm == null) return;
        long[] metrics = roleMetrics.get(roleCode);
        if (metrics != null) {
            metrics[0] += rm.getEstimateSeconds();
            metrics[1] += rm.getLoggedSeconds();
        }
    }

    private String mapSubtaskRoleToCode(String subtaskRole) {
        if (subtaskRole == null) return "DEV";
        // Map legacy role names to codes
        return switch (subtaskRole.toUpperCase()) {
            case "ANALYTICS", "SA" -> "SA";
            case "TESTING", "QA" -> "QA";
            default -> "DEV";
        };
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
                        var phases = planned.phases();
                        if (phases.sa() != null && phases.sa().assigneeDisplayName() != null) {
                            child.setAssigneeAccountId(phases.sa().assigneeAccountId());
                            child.setAssigneeDisplayName(phases.sa().assigneeDisplayName());
                        } else if (phases.dev() != null && phases.dev().assigneeDisplayName() != null) {
                            child.setAssigneeAccountId(phases.dev().assigneeAccountId());
                            child.setAssigneeDisplayName(phases.dev().assigneeDisplayName());
                        } else if (phases.qa() != null && phases.qa().assigneeDisplayName() != null) {
                            child.setAssigneeAccountId(phases.qa().assigneeAccountId());
                            child.setAssigneeDisplayName(phases.qa().assigneeDisplayName());
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
            Map<String, BoardNode> storyMap,
            StatusMappingConfig statusMapping
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
            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, children, statusMapping);
            epicNode.addAlerts(violations);
        }

        for (JiraIssueEntity story : stories) {
            BoardNode storyNode = storyMap.get(story.getIssueKey());
            if (storyNode == null) continue;

            JiraIssueEntity epic = story.getParentKey() != null ? issueMap.get(story.getParentKey()) : null;
            List<JiraIssueEntity> storySubtasks = subtasksByParent.getOrDefault(story.getIssueKey(), List.of());
            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, storySubtasks, statusMapping);
            storyNode.addAlerts(violations);

            for (JiraIssueEntity subtask : storySubtasks) {
                List<DataQualityViolation> subtaskViolations = dataQualityService.checkSubtask(
                        subtask, story, epic, statusMapping);
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

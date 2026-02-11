package com.leadboard.board;

import com.leadboard.config.JiraProperties;
import com.leadboard.config.RoughEstimateProperties;
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

    public BoardService(JiraIssueRepository issueRepository, JiraProperties jiraProperties,
                        TeamRepository teamRepository, RoughEstimateProperties roughEstimateProperties,
                        DataQualityService dataQualityService, StatusMappingService statusMappingService,
                        UnifiedPlanningService unifiedPlanningService) {
        this.issueRepository = issueRepository;
        this.jiraProperties = jiraProperties;
        this.teamRepository = teamRepository;
        this.roughEstimateProperties = roughEstimateProperties;
        this.dataQualityService = dataQualityService;
        this.statusMappingService = statusMappingService;
        this.unifiedPlanningService = unifiedPlanningService;
    }

    public BoardResponse getBoard(String query, List<String> statuses, List<Long> teamIds, int page, int size) {
        String projectKey = jiraProperties.getProjectKey();
        String baseUrl = jiraProperties.getBaseUrl();

        if (projectKey == null || projectKey.isEmpty() || baseUrl == null || baseUrl.isEmpty()) {
            return new BoardResponse(Collections.emptyList(), 0);
        }

        try {
            // Get all issues from cache
            List<JiraIssueEntity> allIssues = issueRepository.findByProjectKey(projectKey);

            if (allIssues.isEmpty()) {
                log.warn("No cached issues found for project: {}. Run sync first.", projectKey);
                return new BoardResponse(Collections.emptyList(), 0);
            }

            // Load team names map
            Map<Long, String> teamNames = new HashMap<>();
            teamRepository.findByActiveTrue().forEach(team ->
                teamNames.put(team.getId(), team.getName())
            );

            // Separate by type
            Map<String, JiraIssueEntity> issueMap = allIssues.stream()
                    .collect(Collectors.toMap(JiraIssueEntity::getIssueKey, e -> e));

            List<JiraIssueEntity> epics = allIssues.stream()
                    .filter(e -> "Эпик".equals(e.getIssueType()) || "Epic".equals(e.getIssueType()))
                    .collect(Collectors.toList());

            List<JiraIssueEntity> stories = allIssues.stream()
                    .filter(e -> "История".equals(e.getIssueType()) || "Story".equals(e.getIssueType()))
                    .collect(Collectors.toList());

            List<JiraIssueEntity> bugs = allIssues.stream()
                    .filter(e -> "Баг".equals(e.getIssueType()) || "Bug".equals(e.getIssueType()))
                    .collect(Collectors.toList());

            List<JiraIssueEntity> subtasks = allIssues.stream()
                    .filter(JiraIssueEntity::isSubtask)
                    .collect(Collectors.toList());

            // Apply filters to epics
            List<JiraIssueEntity> filteredEpics = epics.stream()
                    .filter(epic -> {
                        // Filter by query
                        if (query != null && !query.isEmpty()) {
                            String q = query.toLowerCase();
                            if (!epic.getIssueKey().toLowerCase().contains(q) &&
                                !epic.getSummary().toLowerCase().contains(q)) {
                                return false;
                            }
                        }
                        // Filter by status
                        if (statuses != null && !statuses.isEmpty()) {
                            if (!statuses.contains(epic.getStatus())) {
                                return false;
                            }
                        }
                        // Filter by team IDs
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

            // Create Epic nodes
            for (JiraIssueEntity epic : filteredEpics) {
                BoardNode node = mapToNode(epic, baseUrl, teamNames);
                epicMap.put(epic.getIssueKey(), node);
            }

            // Create Story nodes and attach to Epics
            for (JiraIssueEntity story : stories) {
                BoardNode storyNode = mapToNode(story, baseUrl, teamNames);
                storyMap.put(story.getIssueKey(), storyNode);

                String parentKey = story.getParentKey();
                if (parentKey != null && epicMap.containsKey(parentKey)) {
                    epicMap.get(parentKey).addChild(storyNode);
                }
            }

            // Create Bug nodes and attach to Epics (same level as Stories)
            for (JiraIssueEntity bug : bugs) {
                BoardNode bugNode = mapToNode(bug, baseUrl, teamNames);
                storyMap.put(bug.getIssueKey(), bugNode);

                String parentKey = bug.getParentKey();
                if (parentKey != null && epicMap.containsKey(parentKey)) {
                    epicMap.get(parentKey).addChild(bugNode);
                }
            }

            // Create Sub-task nodes and attach to Stories/Bugs
            for (JiraIssueEntity subtask : subtasks) {
                BoardNode subtaskNode = mapToNode(subtask, baseUrl, teamNames);

                // Map subtask type to role
                String role = jiraProperties.getRoleForSubtaskType(subtask.getIssueType());
                subtaskNode.setRole(role);

                String parentKey = subtask.getParentKey();
                if (parentKey != null && storyMap.containsKey(parentKey)) {
                    storyMap.get(parentKey).addChild(subtaskNode);
                }
            }

            // Enrich stories with forecast data (assignee-based capacity planning)
            enrichStoriesWithForecast(epicMap);

            // Calculate progress for Stories (aggregate from sub-tasks)
            for (BoardNode story : storyMap.values()) {
                aggregateProgress(story);
            }

            // Calculate progress for Epics (aggregate from stories)
            for (BoardNode epic : epicMap.values()) {
                aggregateProgress(epic);
            }

            // Add data quality alerts
            StatusMappingConfig statusMapping = statusMappingService.getDefaultConfig();
            addDataQualityAlerts(filteredEpics, stories, bugs, subtasks, issueMap, epicMap, storyMap, statusMapping);

            // Sort children (stories/bugs) within each epic by manualOrder (ascending)
            for (BoardNode epic : epicMap.values()) {
                if (!epic.getChildren().isEmpty()) {
                    epic.getChildren().sort((a, b) -> {
                        Integer orderA = a.getManualOrder();
                        Integer orderB = b.getManualOrder();

                        // If both have order, compare (ascending)
                        if (orderA != null && orderB != null) {
                            return orderA.compareTo(orderB);
                        }

                        // Items with order come before those without
                        if (orderA != null) return -1;
                        if (orderB != null) return 1;

                        // Both null - fall back to autoScore (descending) for backward compatibility
                        BigDecimal scoreA = a.getAutoScore();
                        BigDecimal scoreB = b.getAutoScore();
                        if (scoreA != null && scoreB != null) {
                            return scoreB.compareTo(scoreA);
                        }
                        if (scoreA != null) return -1;
                        if (scoreB != null) return 1;

                        return 0;
                    });
                }
            }

            // Sort epics by manualOrder (ascending)
            List<BoardNode> items = new ArrayList<>(epicMap.values());
            items.sort((a, b) -> {
                Integer orderA = a.getManualOrder();
                Integer orderB = b.getManualOrder();

                if (orderA != null && orderB != null) {
                    return orderA.compareTo(orderB); // Ascending
                }
                if (orderA != null) return -1;
                if (orderB != null) return 1;

                // Fall back to autoScore (descending) for backward compatibility
                BigDecimal scoreA = a.getAutoScore();
                BigDecimal scoreB = b.getAutoScore();
                if (scoreA != null && scoreB != null) {
                    return scoreB.compareTo(scoreA);
                }
                if (scoreA != null) return -1;
                if (scoreB != null) return 1;
                return 0;
            });

            // Apply pagination
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

        node.setEstimateSeconds(entity.getEffectiveEstimateSeconds());
        node.setLoggedSeconds(entity.getTimeSpentSeconds());

        // Set team info
        if (entity.getTeamId() != null) {
            node.setTeamId(entity.getTeamId());
            node.setTeamName(teamNames.get(entity.getTeamId()));
        } else if (entity.getTeamFieldValue() != null) {
            // Show raw field value if no team mapping exists
            node.setTeamName(entity.getTeamFieldValue());
        }

        // Set Epic-specific fields
        if (isEpic(entity.getIssueType())) {
            boolean epicInTodo = roughEstimateProperties.isStatusAllowed(entity.getStatus());
            node.setEpicInTodo(epicInTodo);

            // Store rough estimates for frontend (used for editing in TODO status)
            node.setRoughEstimateSaDays(entity.getRoughEstimateSaDays());
            node.setRoughEstimateDevDays(entity.getRoughEstimateDevDays());
            node.setRoughEstimateQaDays(entity.getRoughEstimateQaDays());

            // For Epics in TODO: use rough estimates for display
            if (epicInTodo) {
                BoardNode.RoleProgress roleProgress = new BoardNode.RoleProgress();
                roleProgress.setAnalytics(new BoardNode.RoleMetrics(0, 0, entity.getRoughEstimateSaDays()));
                roleProgress.setDevelopment(new BoardNode.RoleMetrics(0, 0, entity.getRoughEstimateDevDays()));
                roleProgress.setTesting(new BoardNode.RoleMetrics(0, 0, entity.getRoughEstimateQaDays()));
                node.setRoleProgress(roleProgress);

                // Calculate estimate as sum of rough estimates (converted to seconds)
                long roughEstimateSeconds = 0;
                if (entity.getRoughEstimateSaDays() != null) {
                    roughEstimateSeconds += entity.getRoughEstimateSaDays().multiply(BigDecimal.valueOf(SECONDS_PER_DAY)).longValue();
                }
                if (entity.getRoughEstimateDevDays() != null) {
                    roughEstimateSeconds += entity.getRoughEstimateDevDays().multiply(BigDecimal.valueOf(SECONDS_PER_DAY)).longValue();
                }
                if (entity.getRoughEstimateQaDays() != null) {
                    roughEstimateSeconds += entity.getRoughEstimateQaDays().multiply(BigDecimal.valueOf(SECONDS_PER_DAY)).longValue();
                }
                if (roughEstimateSeconds > 0) {
                    node.setEstimateSeconds(roughEstimateSeconds);
                }
            }
            // For Epics in progress: RoleProgress will be set by aggregateProgress()
        }

        // Set AutoScore and ManualOrder for both Epics and Stories
        if ("Epic".equalsIgnoreCase(entity.getIssueType()) || "Эпик".equalsIgnoreCase(entity.getIssueType())) {
            // Epic AutoScore and ManualOrder
            node.setAutoScore(entity.getAutoScore());
            node.setManualOrder(entity.getManualOrder());
        } else if ("Story".equalsIgnoreCase(entity.getIssueType()) || "История".equalsIgnoreCase(entity.getIssueType()) ||
                   "Bug".equalsIgnoreCase(entity.getIssueType()) || "Баг".equalsIgnoreCase(entity.getIssueType())) {
            // Story/Bug AutoScore, ManualOrder + additional fields
            node.setAutoScore(entity.getAutoScore());
            node.setManualOrder(entity.getManualOrder());
            node.setFlagged(entity.getFlagged());
            node.setBlocks(entity.getBlocks());
            node.setBlockedBy(entity.getIsBlockedBy());

            // Set assignee info
            node.setAssigneeAccountId(entity.getAssigneeAccountId());
            node.setAssigneeDisplayName(entity.getAssigneeDisplayName());

            // Calculate expected done date based on remaining work
            Long remaining = entity.getRemainingEstimateSeconds();
            if (remaining != null && remaining > 0) {
                // Use explicit remaining estimate from Jira
                double remainingHours = remaining / 3600.0;
                int workDays = (int) Math.ceil(remainingHours / 8.0);
                node.setExpectedDone(LocalDate.now().plusDays(workDays));
            } else {
                // Fallback to original estimate - spent
                Long estimate = entity.getOriginalEstimateSeconds();
                Long spent = entity.getTimeSpentSeconds();
                if (estimate != null && estimate > 0) {
                    long remainingSeconds = estimate - (spent != null ? spent : 0);
                    if (remainingSeconds > 0) {
                        double remainingHours = remainingSeconds / 3600.0;
                        int workDays = (int) Math.ceil(remainingHours / 8.0);
                        node.setExpectedDone(LocalDate.now().plusDays(workDays));
                    } else {
                        // Already completed
                        node.setExpectedDone(LocalDate.now());
                    }
                }
            }
        }

        return node;
    }

    private boolean isEpic(String issueType) {
        return "Epic".equalsIgnoreCase(issueType) || "Эпик".equalsIgnoreCase(issueType);
    }

    private void aggregateProgress(BoardNode node) {
        if (node.getChildren().isEmpty()) {
            // Epic with no children: don't use epic's own estimate (estimates are only on subtasks)
            if (isEpic(node.getIssueType())) {
                node.setEstimateSeconds(0L);
                node.setLoggedSeconds(0L);
                node.setProgress(0);
                return;
            }
            // Leaf node - calculate own progress
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

        // Aggregate from children
        long totalLogged = 0;
        long analyticsEstimate = 0, analyticsLogged = 0;
        long developmentEstimate = 0, developmentLogged = 0;
        long testingEstimate = 0, testingLogged = 0;

        for (BoardNode child : node.getChildren()) {
            // Recursively aggregate children first
            aggregateProgress(child);

            // If child has role progress (Story/Bug), aggregate from it
            if (child.getRoleProgress() != null && !isEpic(child.getIssueType())) {
                BoardNode.RoleProgress rp = child.getRoleProgress();
                analyticsEstimate += rp.getAnalytics().getEstimateSeconds();
                analyticsLogged += rp.getAnalytics().getLoggedSeconds();
                developmentEstimate += rp.getDevelopment().getEstimateSeconds();
                developmentLogged += rp.getDevelopment().getLoggedSeconds();
                testingEstimate += rp.getTesting().getEstimateSeconds();
                testingLogged += rp.getTesting().getLoggedSeconds();
            } else if (child.getRole() != null) {
                // Sub-task with role
                long est = child.getEstimateSeconds() != null ? child.getEstimateSeconds() : 0;
                long log = child.getLoggedSeconds() != null ? child.getLoggedSeconds() : 0;

                switch (child.getRole()) {
                    case "ANALYTICS":
                        analyticsEstimate += est;
                        analyticsLogged += log;
                        break;
                    case "DEVELOPMENT":
                        developmentEstimate += est;
                        developmentLogged += log;
                        break;
                    case "TESTING":
                        testingEstimate += est;
                        testingLogged += log;
                        break;
                }
            }

            // Total logged
            if (child.getLoggedSeconds() != null) {
                totalLogged += child.getLoggedSeconds();
            }
        }

        // For Epic in TODO: check if children have estimates
        if (isEpic(node.getIssueType()) && node.isEpicInTodo()) {
            long totalEstimateFromChildren = analyticsEstimate + developmentEstimate + testingEstimate;

            if (totalEstimateFromChildren > 0) {
                // Children have estimates → use aggregated values (not rough estimates)
                node.setEstimateSeconds(totalEstimateFromChildren);
                node.setLoggedSeconds(totalLogged);
                node.setProgress(totalEstimateFromChildren > 0
                        ? (int) Math.min(100, (totalLogged * 100) / totalEstimateFromChildren) : 0);

                // Create new roleProgress with aggregated values, without rough estimates
                BoardNode.RoleProgress roleProgress = new BoardNode.RoleProgress();
                roleProgress.setAnalytics(new BoardNode.RoleMetrics(analyticsEstimate, analyticsLogged, null));
                roleProgress.setDevelopment(new BoardNode.RoleMetrics(developmentEstimate, developmentLogged, null));
                roleProgress.setTesting(new BoardNode.RoleMetrics(testingEstimate, testingLogged, null));
                node.setRoleProgress(roleProgress);
            } else if (node.getRoleProgress() != null) {
                // No children estimates → keep rough estimates (already set in mapToNode)
                node.setLoggedSeconds(totalLogged);

                // Calculate overall progress based on rough estimate
                Long estimate = node.getEstimateSeconds();
                if (estimate != null && estimate > 0) {
                    node.setProgress((int) Math.min(100, (totalLogged * 100) / estimate));
                } else {
                    node.setProgress(0);
                }
            }
        } else if (isEpic(node.getIssueType()) && !node.isEpicInTodo()) {
            // For Epic in progress: aggregate from children (same as Story/Bug)
            long totalEstimate = analyticsEstimate + developmentEstimate + testingEstimate;
            node.setEstimateSeconds(totalEstimate);
            node.setLoggedSeconds(totalLogged);
            node.setProgress(totalEstimate > 0 ? (int) Math.min(100, (totalLogged * 100) / totalEstimate) : 0);

            BoardNode.RoleProgress roleProgress = new BoardNode.RoleProgress();
            roleProgress.setAnalytics(new BoardNode.RoleMetrics(analyticsEstimate, analyticsLogged, node.getRoughEstimateSaDays()));
            roleProgress.setDevelopment(new BoardNode.RoleMetrics(developmentEstimate, developmentLogged, node.getRoughEstimateDevDays()));
            roleProgress.setTesting(new BoardNode.RoleMetrics(testingEstimate, testingLogged, node.getRoughEstimateQaDays()));
            node.setRoleProgress(roleProgress);
        } else {
            // For Story/Bug: create new role progress
            long totalEstimate = analyticsEstimate + developmentEstimate + testingEstimate;
            node.setEstimateSeconds(totalEstimate);
            node.setLoggedSeconds(totalLogged);
            node.setProgress(totalEstimate > 0 ? (int) Math.min(100, (totalLogged * 100) / totalEstimate) : 0);

            BoardNode.RoleProgress roleProgress = new BoardNode.RoleProgress();
            roleProgress.setAnalytics(new BoardNode.RoleMetrics(analyticsEstimate, analyticsLogged));
            roleProgress.setDevelopment(new BoardNode.RoleMetrics(developmentEstimate, developmentLogged));
            roleProgress.setTesting(new BoardNode.RoleMetrics(testingEstimate, testingLogged));
            node.setRoleProgress(roleProgress);
        }
    }

    /**
     * Enriches stories with forecast data using UnifiedPlanningService.
     * Updates expectedDone dates based on assignee capacity and dependencies.
     */
    private void enrichStoriesWithForecast(Map<String, BoardNode> epicMap) {
        // Collect unique team IDs from epics
        Set<Long> teamIds = epicMap.values().stream()
                .map(BoardNode::getTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (teamIds.isEmpty()) {
            return;
        }

        // Calculate unified plan for each team (one call per team instead of per epic)
        Map<String, PlannedStory> allPlannedStories = new HashMap<>();

        for (Long teamId : teamIds) {
            try {
                UnifiedPlanningResult plan = unifiedPlanningService.calculatePlan(teamId);

                // Collect all planned stories from all epics
                for (PlannedEpic plannedEpic : plan.epics()) {
                    for (PlannedStory plannedStory : plannedEpic.stories()) {
                        allPlannedStories.put(plannedStory.storyKey(), plannedStory);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to calculate unified plan for team {}: {}", teamId, e.getMessage());
            }
        }

        // Update stories in epicMap with planned data
        for (BoardNode epic : epicMap.values()) {
            if (epic.getTeamId() == null || epic.getChildren().isEmpty()) {
                continue;
            }

            for (BoardNode child : epic.getChildren()) {
                PlannedStory planned = allPlannedStories.get(child.getIssueKey());
                if (planned != null) {
                    child.setExpectedDone(planned.endDate());

                    // Update assignee from first phase that has one (SA -> DEV -> QA)
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

    /**
     * Adds data quality alerts to all BoardNodes in the hierarchy.
     */
    private void addDataQualityAlerts(
            List<JiraIssueEntity> epics,
            List<JiraIssueEntity> stories,
            List<JiraIssueEntity> bugs,
            List<JiraIssueEntity> subtasks,
            Map<String, JiraIssueEntity> issueMap,
            Map<String, BoardNode> epicMap,
            Map<String, BoardNode> storyMap,
            StatusMappingConfig statusMapping
    ) {
        // Build parent-children relationships for quick lookup
        Map<String, List<JiraIssueEntity>> childrenByParent = new HashMap<>();
        Map<String, List<JiraIssueEntity>> subtasksByParent = new HashMap<>();

        for (JiraIssueEntity story : stories) {
            if (story.getParentKey() != null) {
                childrenByParent.computeIfAbsent(story.getParentKey(), k -> new ArrayList<>()).add(story);
            }
        }
        for (JiraIssueEntity bug : bugs) {
            if (bug.getParentKey() != null) {
                childrenByParent.computeIfAbsent(bug.getParentKey(), k -> new ArrayList<>()).add(bug);
            }
        }
        for (JiraIssueEntity subtask : subtasks) {
            if (subtask.getParentKey() != null) {
                subtasksByParent.computeIfAbsent(subtask.getParentKey(), k -> new ArrayList<>()).add(subtask);
            }
        }

        // Check epics
        for (JiraIssueEntity epic : epics) {
            BoardNode epicNode = epicMap.get(epic.getIssueKey());
            if (epicNode == null) continue;

            List<JiraIssueEntity> children = childrenByParent.getOrDefault(epic.getIssueKey(), List.of());
            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, children, statusMapping);
            epicNode.addAlerts(violations);
        }

        // Check stories and bugs
        for (JiraIssueEntity story : stories) {
            BoardNode storyNode = storyMap.get(story.getIssueKey());
            if (storyNode == null) continue;

            JiraIssueEntity epic = story.getParentKey() != null ? issueMap.get(story.getParentKey()) : null;
            List<JiraIssueEntity> storySubtasks = subtasksByParent.getOrDefault(story.getIssueKey(), List.of());
            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, storySubtasks, statusMapping);
            storyNode.addAlerts(violations);

            // Check subtasks
            for (JiraIssueEntity subtask : storySubtasks) {
                List<DataQualityViolation> subtaskViolations = dataQualityService.checkSubtask(
                        subtask, story, epic, statusMapping);

                // Find the subtask node and add violations
                for (BoardNode child : storyNode.getChildren()) {
                    if (child.getIssueKey().equals(subtask.getIssueKey())) {
                        child.addAlerts(subtaskViolations);
                        break;
                    }
                }
            }
        }

        for (JiraIssueEntity bug : bugs) {
            BoardNode bugNode = storyMap.get(bug.getIssueKey());
            if (bugNode == null) continue;

            JiraIssueEntity epic = bug.getParentKey() != null ? issueMap.get(bug.getParentKey()) : null;
            List<JiraIssueEntity> bugSubtasks = subtasksByParent.getOrDefault(bug.getIssueKey(), List.of());
            List<DataQualityViolation> violations = dataQualityService.checkStory(bug, epic, bugSubtasks, statusMapping);
            bugNode.addAlerts(violations);

            // Check subtasks
            for (JiraIssueEntity subtask : bugSubtasks) {
                List<DataQualityViolation> subtaskViolations = dataQualityService.checkSubtask(
                        subtask, bug, epic, statusMapping);

                for (BoardNode child : bugNode.getChildren()) {
                    if (child.getIssueKey().equals(subtask.getIssueKey())) {
                        child.addAlerts(subtaskViolations);
                        break;
                    }
                }
            }
        }
    }
}

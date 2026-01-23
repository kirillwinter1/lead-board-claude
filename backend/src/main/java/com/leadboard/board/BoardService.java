package com.leadboard.board;

import com.leadboard.config.JiraProperties;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    private final JiraIssueRepository issueRepository;
    private final JiraProperties jiraProperties;

    public BoardService(JiraIssueRepository issueRepository, JiraProperties jiraProperties) {
        this.issueRepository = issueRepository;
        this.jiraProperties = jiraProperties;
    }

    public BoardResponse getBoard(String query, List<String> statuses, int page, int size) {
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

            // Separate by type
            Map<String, JiraIssueEntity> issueMap = allIssues.stream()
                    .collect(Collectors.toMap(JiraIssueEntity::getIssueKey, e -> e));

            List<JiraIssueEntity> epics = allIssues.stream()
                    .filter(e -> "Эпик".equals(e.getIssueType()) || "Epic".equals(e.getIssueType()))
                    .collect(Collectors.toList());

            List<JiraIssueEntity> stories = allIssues.stream()
                    .filter(e -> "История".equals(e.getIssueType()) || "Story".equals(e.getIssueType()))
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
                        return true;
                    })
                    .collect(Collectors.toList());

            // Build hierarchy
            Map<String, BoardNode> epicMap = new LinkedHashMap<>();
            Map<String, BoardNode> storyMap = new LinkedHashMap<>();

            // Create Epic nodes
            for (JiraIssueEntity epic : filteredEpics) {
                BoardNode node = mapToNode(epic, baseUrl);
                epicMap.put(epic.getIssueKey(), node);
            }

            // Create Story nodes and attach to Epics
            for (JiraIssueEntity story : stories) {
                BoardNode storyNode = mapToNode(story, baseUrl);
                storyMap.put(story.getIssueKey(), storyNode);

                String parentKey = story.getParentKey();
                if (parentKey != null && epicMap.containsKey(parentKey)) {
                    epicMap.get(parentKey).addChild(storyNode);
                }
            }

            // Create Sub-task nodes and attach to Stories
            for (JiraIssueEntity subtask : subtasks) {
                BoardNode subtaskNode = mapToNode(subtask, baseUrl);

                // Map subtask type to role
                String role = jiraProperties.getRoleForSubtaskType(subtask.getIssueType());
                subtaskNode.setRole(role);

                String parentKey = subtask.getParentKey();
                if (parentKey != null && storyMap.containsKey(parentKey)) {
                    storyMap.get(parentKey).addChild(subtaskNode);
                }
            }

            // Calculate progress for Stories (aggregate from sub-tasks)
            for (BoardNode story : storyMap.values()) {
                aggregateProgress(story);
            }

            // Calculate progress for Epics (aggregate from stories)
            for (BoardNode epic : epicMap.values()) {
                aggregateProgress(epic);
            }

            // Apply pagination
            List<BoardNode> items = new ArrayList<>(epicMap.values());
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
        return getBoard(null, null, 0, 50);
    }

    private BoardNode mapToNode(JiraIssueEntity entity, String baseUrl) {
        String jiraUrl = baseUrl + "/browse/" + entity.getIssueKey();
        BoardNode node = new BoardNode(
                entity.getIssueKey(),
                entity.getSummary(),
                entity.getStatus(),
                entity.getIssueType(),
                jiraUrl
        );

        node.setEstimateSeconds(entity.getOriginalEstimateSeconds());
        node.setLoggedSeconds(entity.getTimeSpentSeconds());

        return node;
    }

    private void aggregateProgress(BoardNode node) {
        if (node.getChildren().isEmpty()) {
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
        long totalEstimate = 0;
        long totalLogged = 0;
        long analyticsEstimate = 0, analyticsLogged = 0;
        long developmentEstimate = 0, developmentLogged = 0;
        long testingEstimate = 0, testingLogged = 0;

        for (BoardNode child : node.getChildren()) {
            // Recursively aggregate children first
            aggregateProgress(child);

            // If child has role progress, use it; otherwise use child's direct values
            if (child.getRoleProgress() != null) {
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

            // Total
            if (child.getEstimateSeconds() != null) {
                totalEstimate += child.getEstimateSeconds();
            }
            if (child.getLoggedSeconds() != null) {
                totalLogged += child.getLoggedSeconds();
            }
        }

        node.setEstimateSeconds(totalEstimate);
        node.setLoggedSeconds(totalLogged);
        node.setProgress(totalEstimate > 0 ? (int) Math.min(100, (totalLogged * 100) / totalEstimate) : 0);

        // Set role progress
        BoardNode.RoleProgress roleProgress = new BoardNode.RoleProgress();
        roleProgress.setAnalytics(new BoardNode.RoleMetrics(analyticsEstimate, analyticsLogged));
        roleProgress.setDevelopment(new BoardNode.RoleMetrics(developmentEstimate, developmentLogged));
        roleProgress.setTesting(new BoardNode.RoleMetrics(testingEstimate, testingLogged));
        node.setRoleProgress(roleProgress);
    }
}

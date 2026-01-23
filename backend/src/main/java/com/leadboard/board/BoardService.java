package com.leadboard.board;

import com.leadboard.config.JiraProperties;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraIssue;
import com.leadboard.jira.JiraSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    private final JiraClient jiraClient;
    private final JiraProperties jiraProperties;

    public BoardService(JiraClient jiraClient, JiraProperties jiraProperties) {
        this.jiraClient = jiraClient;
        this.jiraProperties = jiraProperties;
    }

    public BoardResponse getBoard(String query, List<String> statuses, int page, int size) {
        String projectKey = jiraProperties.getProjectKey();
        String baseUrl = jiraProperties.getBaseUrl();

        if (projectKey == null || projectKey.isEmpty() || baseUrl == null || baseUrl.isEmpty()) {
            return new BoardResponse(Collections.emptyList(), 0);
        }

        try {
            // Build JQL for Epics with filters
            StringBuilder epicJql = new StringBuilder();
            epicJql.append(String.format("project = %s AND issuetype = Epic", projectKey));

            if (query != null && !query.isEmpty()) {
                epicJql.append(String.format(" AND (key ~ \"%s\" OR summary ~ \"%s\")", query, query));
            }
            if (statuses != null && !statuses.isEmpty()) {
                epicJql.append(" AND status IN (\"").append(String.join("\",\"", statuses)).append("\")");
            }
            epicJql.append(" ORDER BY created DESC");

            JiraSearchResponse epicResponse = jiraClient.search(epicJql.toString(), page * size, size);

            // Fetch all Stories for this project
            String storyJql = String.format("project = %s AND issuetype = Story ORDER BY created DESC", projectKey);
            JiraSearchResponse storyResponse = jiraClient.search(storyJql, 0, 500);

            // Fetch all Sub-tasks for this project
            String subtaskJql = String.format("project = %s AND issuetype in subTaskIssueTypes() ORDER BY created DESC", projectKey);
            JiraSearchResponse subtaskResponse = jiraClient.search(subtaskJql, 0, 1000);

            // Build maps for quick lookup
            Map<String, BoardNode> epicMap = new LinkedHashMap<>();
            Map<String, BoardNode> storyMap = new LinkedHashMap<>();

            // Create Epic nodes
            for (JiraIssue epic : epicResponse.getIssues()) {
                BoardNode node = mapToNode(epic, baseUrl);
                epicMap.put(epic.getKey(), node);
            }

            // Create Story nodes and attach to Epics
            for (JiraIssue story : storyResponse.getIssues()) {
                BoardNode storyNode = mapToNode(story, baseUrl);
                storyMap.put(story.getKey(), storyNode);

                JiraIssue.JiraParent parent = story.getFields().getParent();
                if (parent != null && epicMap.containsKey(parent.getKey())) {
                    epicMap.get(parent.getKey()).addChild(storyNode);
                }
            }

            // Create Sub-task nodes and attach to Stories
            for (JiraIssue subtask : subtaskResponse.getIssues()) {
                BoardNode subtaskNode = mapToNode(subtask, baseUrl);

                // Map subtask type to role
                String subtaskType = subtask.getFields().getIssuetype().getName();
                String role = jiraProperties.getRoleForSubtaskType(subtaskType);
                subtaskNode.setRole(role);

                JiraIssue.JiraParent parent = subtask.getFields().getParent();
                if (parent != null && storyMap.containsKey(parent.getKey())) {
                    storyMap.get(parent.getKey()).addChild(subtaskNode);
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

            List<BoardNode> items = new ArrayList<>(epicMap.values());
            return new BoardResponse(items, items.size());
        } catch (Exception e) {
            log.error("Failed to fetch board from Jira: {}", e.getMessage());
            return new BoardResponse(Collections.emptyList(), 0);
        }
    }

    public BoardResponse getBoard() {
        return getBoard(null, null, 0, 50);
    }

    private BoardNode mapToNode(JiraIssue issue, String baseUrl) {
        String jiraUrl = baseUrl + "/browse/" + issue.getKey();
        BoardNode node = new BoardNode(
                issue.getKey(),
                issue.getFields().getSummary(),
                issue.getFields().getStatus().getName(),
                issue.getFields().getIssuetype().getName(),
                jiraUrl
        );

        // Set time tracking
        JiraIssue.JiraTimeTracking timetracking = issue.getFields().getTimetracking();
        if (timetracking != null) {
            node.setEstimateSeconds(timetracking.getOriginalEstimateSeconds());
            node.setLoggedSeconds(timetracking.getTimeSpentSeconds());
        }

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

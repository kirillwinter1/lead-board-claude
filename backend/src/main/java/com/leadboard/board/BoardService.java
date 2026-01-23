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

    public BoardResponse getBoard() {
        String projectKey = jiraProperties.getProjectKey();
        String baseUrl = jiraProperties.getBaseUrl();

        if (projectKey == null || projectKey.isEmpty() || baseUrl == null || baseUrl.isEmpty()) {
            return new BoardResponse(Collections.emptyList(), 0);
        }

        try {
            // Fetch Epics
            String epicJql = String.format("project = %s AND issuetype = Epic ORDER BY created DESC", projectKey);
            JiraSearchResponse epicResponse = jiraClient.search(epicJql, 0, 50);

            // Fetch Stories
            String storyJql = String.format("project = %s AND issuetype = Story ORDER BY created DESC", projectKey);
            JiraSearchResponse storyResponse = jiraClient.search(storyJql, 0, 200);

            // Build hierarchy
            Map<String, BoardNode> epicMap = new LinkedHashMap<>();

            // Create Epic nodes
            for (JiraIssue epic : epicResponse.getIssues()) {
                BoardNode node = mapToNode(epic, baseUrl);
                epicMap.put(epic.getKey(), node);
            }

            // Attach Stories to their parent Epics
            for (JiraIssue story : storyResponse.getIssues()) {
                BoardNode storyNode = mapToNode(story, baseUrl);
                JiraIssue.JiraParent parent = story.getFields().getParent();

                if (parent != null && epicMap.containsKey(parent.getKey())) {
                    epicMap.get(parent.getKey()).addChild(storyNode);
                }
            }

            List<BoardNode> items = new ArrayList<>(epicMap.values());
            return new BoardResponse(items, items.size());
        } catch (Exception e) {
            log.error("Failed to fetch board from Jira: {}", e.getMessage());
            return new BoardResponse(Collections.emptyList(), 0);
        }
    }

    private BoardNode mapToNode(JiraIssue issue, String baseUrl) {
        String jiraUrl = baseUrl + "/browse/" + issue.getKey();
        return new BoardNode(
                issue.getKey(),
                issue.getFields().getSummary(),
                issue.getFields().getStatus().getName(),
                issue.getFields().getIssuetype().getName(),
                jiraUrl
        );
    }
}

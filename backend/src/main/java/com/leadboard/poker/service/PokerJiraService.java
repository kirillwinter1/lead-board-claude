package com.leadboard.poker.service;

import com.leadboard.jira.JiraClient;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.repository.PokerStoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PokerJiraService {

    private static final Logger log = LoggerFactory.getLogger(PokerJiraService.class);

    private final JiraClient jiraClient;
    private final PokerStoryRepository storyRepository;

    @Value("${jira.project-key:}")
    private String projectKey;

    public PokerJiraService(JiraClient jiraClient, PokerStoryRepository storyRepository) {
        this.jiraClient = jiraClient;
        this.storyRepository = storyRepository;
    }

    /**
     * Create a Story in Jira with subtasks for required roles.
     * Returns the created Story key.
     */
    @Transactional
    public String createStoryWithSubtasks(PokerStoryEntity pokerStory, String epicKey) {
        try {
            // Create Story
            String storyKey = jiraClient.createIssue(
                    projectKey,
                    "Story",
                    pokerStory.getTitle(),
                    epicKey
            );

            log.info("Created Jira Story: {}", storyKey);

            // Create Subtasks
            if (pokerStory.isNeedsSa()) {
                String subtaskKey = jiraClient.createSubtask(storyKey, "Анализ", projectKey);
                log.info("Created SA subtask: {}", subtaskKey);
            }
            if (pokerStory.isNeedsDev()) {
                String subtaskKey = jiraClient.createSubtask(storyKey, "Разработка", projectKey);
                log.info("Created DEV subtask: {}", subtaskKey);
            }
            if (pokerStory.isNeedsQa()) {
                String subtaskKey = jiraClient.createSubtask(storyKey, "Тестирование", projectKey);
                log.info("Created QA subtask: {}", subtaskKey);
            }

            // Update poker story with Jira key
            pokerStory.setStoryKey(storyKey);
            storyRepository.save(pokerStory);

            return storyKey;
        } catch (Exception e) {
            log.error("Failed to create story in Jira: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create story in Jira: " + e.getMessage(), e);
        }
    }

    /**
     * Update estimates on subtasks after voting is complete.
     */
    public void updateSubtaskEstimates(String storyKey, Integer saHours, Integer devHours, Integer qaHours) {
        try {
            List<Map<String, Object>> subtasks = jiraClient.getSubtasks(storyKey);

            for (Map<String, Object> subtask : subtasks) {
                String subtaskKey = (String) subtask.get("key");
                Map<String, Object> fields = (Map<String, Object>) subtask.get("fields");
                String summary = (String) fields.get("summary");

                Integer hours = null;
                if (summary != null) {
                    if (summary.contains("Анализ") && saHours != null) {
                        hours = saHours;
                    } else if (summary.contains("Разработка") && devHours != null) {
                        hours = devHours;
                    } else if (summary.contains("Тестирование") && qaHours != null) {
                        hours = qaHours;
                    }
                }

                if (hours != null && hours > 0) {
                    jiraClient.updateEstimate(subtaskKey, hours * 3600); // Convert hours to seconds
                    log.info("Updated estimate for {}: {} hours", subtaskKey, hours);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update subtask estimates: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update subtask estimates: " + e.getMessage(), e);
        }
    }
}

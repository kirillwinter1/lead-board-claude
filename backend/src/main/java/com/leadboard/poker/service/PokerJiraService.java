package com.leadboard.poker.service;

import com.leadboard.jira.JiraClient;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.repository.PokerStoryRepository;
import com.leadboard.sync.JiraIssueRepository;
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
    private final JiraIssueRepository issueRepository;

    @Value("${jira.project-key:}")
    private String projectKey;

    public PokerJiraService(JiraClient jiraClient, PokerStoryRepository storyRepository, JiraIssueRepository issueRepository) {
        this.jiraClient = jiraClient;
        this.storyRepository = storyRepository;
        this.issueRepository = issueRepository;
    }

    /**
     * Create a Story in Jira with subtasks. Returns the Jira story key.
     * Does NOT save anything to local DB — Jira is the single source of truth.
     */
    public String createStoryInJira(String epicKey, String title,
                                     boolean needsSa, boolean needsDev, boolean needsQa) {
        String storyTypeName = detectStoryTypeName();
        log.info("Creating story in Jira: type='{}', epic={}, title='{}'", storyTypeName, epicKey, title);

        String storyKey = jiraClient.createIssue(projectKey, storyTypeName, title, epicKey);
        log.info("Created Jira Story: {}", storyKey);

        if (needsSa) {
            String subtaskKey = jiraClient.createSubtask(storyKey, "Анализ", projectKey);
            log.info("Created SA subtask: {}", subtaskKey);
        }
        if (needsDev) {
            String subtaskKey = jiraClient.createSubtask(storyKey, "Разработка", projectKey);
            log.info("Created DEV subtask: {}", subtaskKey);
        }
        if (needsQa) {
            String subtaskKey = jiraClient.createSubtask(storyKey, "Тестирование", projectKey);
            log.info("Created QA subtask: {}", subtaskKey);
        }

        return storyKey;
    }

    /**
     * @deprecated Use {@link #createStoryInJira} instead. Kept for backward compatibility.
     */
    @Deprecated
    @Transactional
    public String createStoryWithSubtasks(PokerStoryEntity pokerStory, String epicKey) {
        String storyKey = createStoryInJira(epicKey, pokerStory.getTitle(),
                pokerStory.isNeedsSa(), pokerStory.isNeedsDev(), pokerStory.isNeedsQa());
        pokerStory.setStoryKey(storyKey);
        storyRepository.save(pokerStory);
        return storyKey;
    }

    /**
     * Detect the correct Story issue type name from synced data.
     * Different Jira projects may use "Story", "История", "Стори" etc.
     */
    private String detectStoryTypeName() {
        // Look at existing stories in the project to determine the correct issue type name
        List<String> storyTypes = List.of("История", "Story", "Стори");
        for (String typeName : storyTypes) {
            if (!issueRepository.findByProjectKeyAndIssueType(projectKey, typeName).isEmpty()) {
                return typeName;
            }
        }
        // Default fallback
        return "История";
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

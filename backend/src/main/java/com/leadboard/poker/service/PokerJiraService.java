package com.leadboard.poker.service;

import com.leadboard.config.service.WorkflowConfigService;
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
    private final WorkflowConfigService workflowConfigService;

    @Value("${jira.project-key:}")
    private String projectKey;

    public PokerJiraService(JiraClient jiraClient, PokerStoryRepository storyRepository,
                            JiraIssueRepository issueRepository, WorkflowConfigService workflowConfigService) {
        this.jiraClient = jiraClient;
        this.storyRepository = storyRepository;
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
    }

    /**
     * Create a Story in Jira with subtasks. Returns the Jira story key.
     * Does NOT save anything to local DB — Jira is the single source of truth.
     */
    public String createStoryInJira(String epicKey, String title,
                                     boolean needsSa, boolean needsDev, boolean needsQa) {
        String storyTypeName = workflowConfigService.getStoryTypeName();
        log.info("Creating story in Jira: type='{}', epic={}, title='{}'", storyTypeName, epicKey, title);

        String storyKey = jiraClient.createIssue(projectKey, storyTypeName, title, epicKey);
        log.info("Created Jira Story: {}", storyKey);

        if (needsSa) {
            String saSubtaskName = workflowConfigService.getSubtaskTypeName("SA");
            String subtaskKey = jiraClient.createSubtask(storyKey, saSubtaskName != null ? saSubtaskName : "Анализ", projectKey);
            log.info("Created SA subtask: {}", subtaskKey);
        }
        if (needsDev) {
            String devSubtaskName = workflowConfigService.getSubtaskTypeName("DEV");
            String subtaskKey = jiraClient.createSubtask(storyKey, devSubtaskName != null ? devSubtaskName : "Разработка", projectKey);
            log.info("Created DEV subtask: {}", subtaskKey);
        }
        if (needsQa) {
            String qaSubtaskName = workflowConfigService.getSubtaskTypeName("QA");
            String subtaskKey = jiraClient.createSubtask(storyKey, qaSubtaskName != null ? qaSubtaskName : "Тестирование", projectKey);
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
     * Update estimates on subtasks after voting is complete.
     */
    @SuppressWarnings("unchecked")
    public void updateSubtaskEstimates(String storyKey, Integer saHours, Integer devHours, Integer qaHours) {
        try {
            List<Map<String, Object>> subtasks = jiraClient.getSubtasks(storyKey);

            for (Map<String, Object> subtask : subtasks) {
                String subtaskKey = (String) subtask.get("key");
                Map<String, Object> fields = (Map<String, Object>) subtask.get("fields");

                // Determine role from issue type (preferred) or summary (fallback)
                String roleCode = null;
                Map<String, Object> issueTypeMap = (Map<String, Object>) fields.get("issuetype");
                if (issueTypeMap != null && issueTypeMap.get("name") != null) {
                    roleCode = workflowConfigService.getSubtaskRole((String) issueTypeMap.get("name"));
                }

                // Fallback to summary-based detection if issuetype not available
                if (roleCode == null) {
                    String summary = (String) fields.get("summary");
                    if (summary != null) {
                        roleCode = workflowConfigService.getSubtaskRole(summary);
                    }
                }

                Integer hours = null;
                if (roleCode != null) {
                    hours = switch (roleCode) {
                        case "SA" -> saHours;
                        case "DEV" -> devHours;
                        case "QA" -> qaHours;
                        default -> null;
                    };
                }

                if (hours != null && hours > 0) {
                    jiraClient.updateEstimate(subtaskKey, hours * 3600); // Convert hours to seconds
                    log.info("Updated estimate for {} (role={}): {} hours", subtaskKey, roleCode, hours);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update subtask estimates: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update subtask estimates: " + e.getMessage(), e);
        }
    }
}

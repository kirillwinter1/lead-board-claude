package com.leadboard.poker.service;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraClientException;
import com.leadboard.poker.dto.PublishResultResponse;
import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.entity.PokerStoryEntity.StoryStatus;
import com.leadboard.poker.repository.PokerSessionRepository;
import com.leadboard.poker.repository.PokerStoryRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PokerJiraService {

    private static final Logger log = LoggerFactory.getLogger(PokerJiraService.class);

    private final JiraClient jiraClient;
    private final PokerStoryRepository storyRepository;
    private final PokerSessionRepository sessionRepository;
    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;
    private final JiraConfigResolver jiraConfigResolver;

    public PokerJiraService(JiraClient jiraClient, PokerStoryRepository storyRepository,
                            PokerSessionRepository sessionRepository,
                            JiraIssueRepository issueRepository, WorkflowConfigService workflowConfigService,
                            JiraConfigResolver jiraConfigResolver) {
        this.jiraClient = jiraClient;
        this.storyRepository = storyRepository;
        this.sessionRepository = sessionRepository;
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
        this.jiraConfigResolver = jiraConfigResolver;
    }

    /**
     * Create a Story in Jira with a description, component and one subtask per role.
     * Returns the Jira story key. Does NOT save anything to local DB -- Jira is the
     * single source of truth. Jira failures are surfaced as {@link IllegalArgumentException}
     * (client error -> 400) or {@link JiraClientException} (upstream error -> 502), never 500.
     */
    public String createStoryInJira(String epicKey, String title, String description,
                                    String component, List<String> needsRoles) {
        // Tenant-aware project key (JiraConfigResolver), NEVER raw jira.project-key (BUG-177)
        String projectKey = jiraConfigResolver.getProjectKey();
        String storyTypeName = workflowConfigService.getStoryTypeName();
        List<String> components = (component != null && !component.isBlank())
                ? List.of(component) : List.of();
        log.info("Creating story in Jira: project='{}', type='{}', epic={}, title='{}', component='{}'",
                projectKey, storyTypeName, epicKey, title, component);

        String storyKey = null;
        try {
            storyKey = jiraClient.createIssue(projectKey, storyTypeName, title, epicKey,
                    description, components);
            log.info("Created Jira Story: {}", storyKey);

            for (String roleCode : needsRoles) {
                String subtaskTypeName = workflowConfigService.getSubtaskTypeName(roleCode);
                String subtaskKey = jiraClient.createSubtask(storyKey,
                        subtaskTypeName != null ? subtaskTypeName : roleCode, projectKey, subtaskTypeName,
                        description, components);
                log.info("Created {} subtask: {}", roleCode, subtaskKey);
            }

            return storyKey;
        } catch (WebClientResponseException e) {
            // Roll back the already-created Story so a subtask failure doesn't leave an
            // orphaned Story in Jira (which the poker session would never reference and
            // which would fail publish with "Story is not in Jira yet"). Best-effort.
            rollbackOrphanStory(storyKey);
            throw mapJiraError(e, "create story in Jira");
        }
    }

    /** Best-effort delete of a Story created before a later subtask create failed. */
    private void rollbackOrphanStory(String storyKey) {
        if (storyKey == null) return;
        try {
            jiraClient.deleteIssue(storyKey);
            log.info("Rolled back orphaned Jira Story {} after subtask create failure", storyKey);
        } catch (Exception cleanup) {
            log.warn("Could not roll back orphaned Jira Story {}: {}", storyKey, cleanup.getMessage());
        }
    }

    /**
     * Sync an edited poker story's title/description to its existing Jira issue so the
     * local copy never contradicts Jira. Client/upstream failures are mapped to 400/502
     * (never a raw 500), matching {@link #createStoryInJira}.
     */
    public void updateStoryInJira(String storyKey, String title, String description) {
        try {
            jiraClient.updateStoryFields(storyKey, title, description);
            log.info("Updated Jira story {} (title/description)", storyKey);
        } catch (WebClientResponseException e) {
            throw mapJiraError(e, "update story " + storyKey + " in Jira");
        }
    }

    /**
     * Publish a completed session's final estimates to Jira (F23 rework). For every
     * COMPLETED story that carries final estimates, ensure a subtask exists for each
     * estimated role and write the role's estimate as the subtask's Original Estimate
     * ({@code timetracking.originalEstimate = "{hours}h"}).
     *
     * Idempotent: subtasks are created only when missing; writing the Original Estimate
     * simply overwrites the previous value. Per-story failures are captured in the result
     * (status "error") without aborting the rest of the publish.
     */
    @Transactional(readOnly = true)
    public PublishResultResponse publishSession(Long sessionId) {
        PokerSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        String projectKey = jiraConfigResolver.getProjectKey();
        List<PokerStoryEntity> stories = storyRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);

        List<PublishResultResponse.StoryResult> results = new java.util.ArrayList<>();

        for (PokerStoryEntity story : stories) {
            Map<String, Integer> finals = story.getFinalEstimates();
            if (story.getStatus() != StoryStatus.COMPLETED || finals == null || finals.isEmpty()) {
                continue; // only completed, estimated stories are published
            }

            if (story.getStoryKey() == null || story.getStoryKey().isBlank()) {
                results.add(new PublishResultResponse.StoryResult(
                        story.getId(), null, story.getTitle(), "error",
                        "Story is not in Jira yet — create it in Jira before publishing", Map.of()));
                continue;
            }

            try {
                Map<String, String> written = publishStory(story, projectKey, finals);
                results.add(new PublishResultResponse.StoryResult(
                        story.getId(), story.getStoryKey(), story.getTitle(), "ok",
                        "Published " + written.size() + " estimate(s)", written));
            } catch (WebClientResponseException e) {
                RuntimeException mapped = mapJiraError(e, "publish story " + story.getStoryKey());
                results.add(new PublishResultResponse.StoryResult(
                        story.getId(), story.getStoryKey(), story.getTitle(), "error",
                        mapped.getMessage(), Map.of()));
            } catch (Exception e) {
                log.warn("Failed to publish story {}: {}", story.getStoryKey(), e.getMessage());
                results.add(new PublishResultResponse.StoryResult(
                        story.getId(), story.getStoryKey(), story.getTitle(), "error",
                        e.getMessage(), Map.of()));
            }
        }

        return new PublishResultResponse(sessionId, results);
    }

    /**
     * Ensure a subtask exists per estimated role on {@code story} and write each role's
     * Original Estimate. Returns role code -> subtask key for the estimates written.
     */
    private Map<String, String> publishStory(PokerStoryEntity story, String projectKey,
                                             Map<String, Integer> finals) {
        // Map existing subtasks by role so we don't create duplicates (idempotency).
        // Prefer the locally-synced subtasks (reliable) and only fall back to a live
        // Jira search when the DB has none (e.g. a just-created story not yet synced).
        Map<String, String> roleToSubtaskKey = new LinkedHashMap<>();
        for (JiraIssueEntity subtask : issueRepository.findByParentKey(story.getStoryKey())) {
            String role = workflowConfigService.getSubtaskRole(subtask.getIssueType());
            if (role != null) {
                roleToSubtaskKey.putIfAbsent(role, subtask.getIssueKey());
            }
        }
        if (roleToSubtaskKey.isEmpty()) {
            for (Map<String, Object> subtask : jiraClient.getSubtasks(story.getStoryKey())) {
                String subtaskKey = (String) subtask.get("key");
                @SuppressWarnings("unchecked")
                Map<String, Object> fields = (Map<String, Object>) subtask.get("fields");
                String role = roleOfSubtask(fields);
                if (role != null) {
                    roleToSubtaskKey.putIfAbsent(role, subtaskKey);
                }
            }
        }

        Map<String, String> written = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : finals.entrySet()) {
            String roleCode = entry.getKey();
            Integer hours = entry.getValue();
            if (hours == null || hours <= 0) continue;

            String subtaskKey = roleToSubtaskKey.get(roleCode);
            if (subtaskKey == null) {
                // Missing subtask for this role — create it (idempotent: only when absent).
                String subtaskTypeName = workflowConfigService.getSubtaskTypeName(roleCode);
                subtaskKey = jiraClient.createSubtask(story.getStoryKey(),
                        subtaskTypeName != null ? subtaskTypeName : roleCode, projectKey, subtaskTypeName);
                roleToSubtaskKey.put(roleCode, subtaskKey);
                log.info("Publish: created missing {} subtask {} for story {}",
                        roleCode, subtaskKey, story.getStoryKey());
            }

            // Original Estimate = "{hours}h" (JiraClient.updateEstimate writes timetracking.originalEstimate)
            jiraClient.updateEstimate(subtaskKey, hours * 3600);
            written.put(roleCode, subtaskKey);
            log.info("Publish: wrote Original Estimate {}h to {} (role={})", hours, subtaskKey, roleCode);
        }
        return written;
    }

    /** Resolve the workflow role code for a subtask from its issue type (preferred) or summary. */
    private String roleOfSubtask(Map<String, Object> fields) {
        if (fields == null) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> issueType = (Map<String, Object>) fields.get("issuetype");
        if (issueType != null && issueType.get("name") != null) {
            String role = workflowConfigService.getSubtaskRole((String) issueType.get("name"));
            if (role != null) return role;
        }
        String summary = (String) fields.get("summary");
        return summary != null ? workflowConfigService.getSubtaskRole(summary) : null;
    }

    /**
     * Translate a Jira {@link WebClientResponseException} into a domain exception so the
     * controller returns 400 (client error) or 502 (upstream error) — never a raw 500 with
     * a stack trace. The message is truncated and carries no credentials.
     */
    private RuntimeException mapJiraError(WebClientResponseException e, String action) {
        String body = e.getResponseBodyAsString();
        String trimmed = body != null && body.length() > 300 ? body.substring(0, 300) + "…" : body;
        String msg = "Failed to " + action + " (Jira " + e.getStatusCode().value() + "): " + trimmed;
        log.warn("{}", msg);
        if (e.getStatusCode().is4xxClientError()) {
            // Bad request to Jira (e.g. missing required field) -> 400 for the caller.
            return new IllegalArgumentException(msg);
        }
        return new JiraClientException(msg);
    }

    /**
     * Update estimates on subtasks after voting is complete.
     */
    @SuppressWarnings("unchecked")
    public void updateSubtaskEstimates(String storyKey, Map<String, Integer> finalEstimates) {
        if (finalEstimates == null || finalEstimates.isEmpty()) return;

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

                if (roleCode != null) {
                    Integer hours = finalEstimates.get(roleCode);
                    if (hours != null && hours > 0) {
                        jiraClient.updateEstimate(subtaskKey, hours * 3600); // Convert hours to seconds
                        log.info("Updated estimate for {} (role={}): {} hours", subtaskKey, roleCode, hours);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to update subtask estimates: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update subtask estimates: " + e.getMessage(), e);
        }
    }
}

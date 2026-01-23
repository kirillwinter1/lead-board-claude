package com.leadboard.sync;

import com.leadboard.config.JiraProperties;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraIssue;
import com.leadboard.jira.JiraSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final JiraClient jiraClient;
    private final JiraProperties jiraProperties;
    private final JiraIssueRepository issueRepository;
    private final JiraSyncStateRepository syncStateRepository;

    public SyncService(JiraClient jiraClient,
                       JiraProperties jiraProperties,
                       JiraIssueRepository issueRepository,
                       JiraSyncStateRepository syncStateRepository) {
        this.jiraClient = jiraClient;
        this.jiraProperties = jiraProperties;
        this.issueRepository = issueRepository;
        this.syncStateRepository = syncStateRepository;
    }

    @Scheduled(fixedRateString = "${jira.sync-interval-seconds:300}000")
    public void scheduledSync() {
        String projectKey = jiraProperties.getProjectKey();
        if (projectKey == null || projectKey.isEmpty()) {
            return;
        }
        log.info("Starting scheduled sync for project: {}", projectKey);
        syncProject(projectKey);
    }

    @Transactional
    public SyncStatus triggerSync() {
        String projectKey = jiraProperties.getProjectKey();
        if (projectKey == null || projectKey.isEmpty()) {
            return new SyncStatus(false, null, null, 0, "Project key not configured");
        }

        JiraSyncStateEntity state = getOrCreateSyncState(projectKey);
        if (state.isSyncInProgress()) {
            return new SyncStatus(true, state.getLastSyncStartedAt(), state.getLastSyncCompletedAt(),
                    state.getLastSyncIssuesCount(), "Sync already in progress");
        }

        // Run sync asynchronously
        new Thread(() -> syncProject(projectKey)).start();

        return new SyncStatus(true, OffsetDateTime.now(), state.getLastSyncCompletedAt(),
                state.getLastSyncIssuesCount(), null);
    }

    public SyncStatus getSyncStatus() {
        String projectKey = jiraProperties.getProjectKey();
        if (projectKey == null || projectKey.isEmpty()) {
            return new SyncStatus(false, null, null, 0, "Project key not configured");
        }

        JiraSyncStateEntity state = syncStateRepository.findByProjectKey(projectKey).orElse(null);
        if (state == null) {
            return new SyncStatus(false, null, null, 0, null);
        }

        return new SyncStatus(
                state.isSyncInProgress(),
                state.getLastSyncStartedAt(),
                state.getLastSyncCompletedAt(),
                state.getLastSyncIssuesCount(),
                state.getLastError()
        );
    }

    @Transactional
    public void syncProject(String projectKey) {
        JiraSyncStateEntity state = getOrCreateSyncState(projectKey);

        if (state.isSyncInProgress()) {
            log.warn("Sync already in progress for project: {}", projectKey);
            return;
        }

        // Mark sync as started
        state.setSyncInProgress(true);
        state.setLastSyncStartedAt(OffsetDateTime.now());
        state.setLastError(null);
        syncStateRepository.save(state);

        try {
            int totalSynced = 0;

            // Fetch all issues for the project
            String jql = String.format("project = %s ORDER BY updated DESC", projectKey);
            int startAt = 0;
            int maxResults = 100;

            while (true) {
                JiraSearchResponse response = jiraClient.search(jql, startAt, maxResults);
                List<JiraIssue> issues = response.getIssues();

                if (issues.isEmpty()) {
                    break;
                }

                for (JiraIssue issue : issues) {
                    saveOrUpdateIssue(issue, projectKey);
                    totalSynced++;
                }

                startAt += issues.size();

                if (issues.size() < maxResults) {
                    break;
                }
            }

            // Mark sync as completed
            state.setSyncInProgress(false);
            state.setLastSyncCompletedAt(OffsetDateTime.now());
            state.setLastSyncIssuesCount(totalSynced);
            syncStateRepository.save(state);

            log.info("Sync completed for project: {}. Total issues synced: {}", projectKey, totalSynced);

        } catch (Exception e) {
            log.error("Sync failed for project: {}", projectKey, e);
            state.setSyncInProgress(false);
            state.setLastError(e.getMessage());
            syncStateRepository.save(state);
        }
    }

    private void saveOrUpdateIssue(JiraIssue jiraIssue, String projectKey) {
        JiraIssueEntity entity = issueRepository.findByIssueKey(jiraIssue.getKey())
                .orElse(new JiraIssueEntity());

        entity.setIssueKey(jiraIssue.getKey());
        entity.setIssueId(jiraIssue.getId());
        entity.setProjectKey(projectKey);
        entity.setSummary(jiraIssue.getFields().getSummary());
        entity.setStatus(jiraIssue.getFields().getStatus().getName());
        entity.setIssueType(jiraIssue.getFields().getIssuetype().getName());
        entity.setSubtask(jiraIssue.getFields().getIssuetype().isSubtask());

        if (jiraIssue.getFields().getParent() != null) {
            entity.setParentKey(jiraIssue.getFields().getParent().getKey());
        } else {
            entity.setParentKey(null);
        }

        JiraIssue.JiraTimeTracking timetracking = jiraIssue.getFields().getTimetracking();
        if (timetracking != null) {
            entity.setOriginalEstimateSeconds(timetracking.getOriginalEstimateSeconds());
            entity.setTimeSpentSeconds(timetracking.getTimeSpentSeconds());
        }

        issueRepository.save(entity);
    }

    private JiraSyncStateEntity getOrCreateSyncState(String projectKey) {
        return syncStateRepository.findByProjectKey(projectKey)
                .orElseGet(() -> {
                    JiraSyncStateEntity newState = new JiraSyncStateEntity();
                    newState.setProjectKey(projectKey);
                    return syncStateRepository.save(newState);
                });
    }

    public record SyncStatus(
            boolean syncInProgress,
            OffsetDateTime lastSyncStartedAt,
            OffsetDateTime lastSyncCompletedAt,
            int issuesCount,
            String error
    ) {}
}

package com.leadboard.sync;

import com.leadboard.config.JiraProperties;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraIssue;
import com.leadboard.jira.JiraSearchResponse;
import com.leadboard.planning.AutoScoreService;
import com.leadboard.planning.StoryAutoScoreService;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final JiraClient jiraClient;
    private final JiraProperties jiraProperties;
    private final JiraIssueRepository issueRepository;
    private final JiraSyncStateRepository syncStateRepository;
    private final TeamRepository teamRepository;
    private final AutoScoreService autoScoreService;
    private final StoryAutoScoreService storyAutoScoreService;

    public SyncService(JiraClient jiraClient,
                       JiraProperties jiraProperties,
                       JiraIssueRepository issueRepository,
                       JiraSyncStateRepository syncStateRepository,
                       TeamRepository teamRepository,
                       AutoScoreService autoScoreService,
                       StoryAutoScoreService storyAutoScoreService) {
        this.jiraClient = jiraClient;
        this.jiraProperties = jiraProperties;
        this.issueRepository = issueRepository;
        this.syncStateRepository = syncStateRepository;
        this.teamRepository = teamRepository;
        this.autoScoreService = autoScoreService;
        this.storyAutoScoreService = storyAutoScoreService;
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

            // Build JQL - incremental sync if we have a previous sync time
            String jql;
            OffsetDateTime lastSync = state.getLastSyncCompletedAt();
            if (lastSync != null) {
                // Incremental sync: only fetch issues updated since last sync
                // Subtract 1 minute to account for timing differences
                String lastSyncTime = lastSync.minusMinutes(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                jql = String.format("project = %s AND updated >= '%s' ORDER BY updated DESC", projectKey, lastSyncTime);
                log.info("Incremental sync for project: {} (changes since {})", projectKey, lastSyncTime);
            } else {
                // Full sync on first run
                jql = String.format("project = %s ORDER BY updated DESC", projectKey);
                log.info("Full sync for project: {} (first run)", projectKey);
            }

            int maxResults = 100;
            String nextPageToken = null;

            while (true) {
                JiraSearchResponse response = jiraClient.search(jql, maxResults, nextPageToken);
                List<JiraIssue> issues = response.getIssues();

                if (issues == null || issues.isEmpty()) {
                    break;
                }

                for (JiraIssue issue : issues) {
                    saveOrUpdateIssue(issue, projectKey);
                    totalSynced++;
                }

                // Use cursor-based pagination
                if (response.isLast() || response.getNextPageToken() == null) {
                    break;
                }
                nextPageToken = response.getNextPageToken();
            }

            // Mark sync as completed
            state.setSyncInProgress(false);
            state.setLastSyncCompletedAt(OffsetDateTime.now());
            state.setLastSyncIssuesCount(totalSynced);
            syncStateRepository.save(state);

            log.info("Sync completed for project: {}. Issues synced: {} ({})", projectKey, totalSynced,
                    lastSync != null ? "incremental" : "full");

            // Recalculate AutoScore for all epics and stories after sync
            try {
                int epicsUpdated = autoScoreService.recalculateAll();
                log.info("AutoScore recalculated for {} epics after sync", epicsUpdated);

                int storiesUpdated = storyAutoScoreService.recalculateAll();
                log.info("AutoScore recalculated for {} stories after sync", storiesUpdated);
            } catch (Exception e) {
                log.error("Failed to recalculate AutoScore after sync", e);
            }

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

        // Preserve local Lead Board data that survives sync
        BigDecimal savedRoughEstimateSaDays = entity.getRoughEstimateSaDays();
        BigDecimal savedRoughEstimateDevDays = entity.getRoughEstimateDevDays();
        BigDecimal savedRoughEstimateQaDays = entity.getRoughEstimateQaDays();
        OffsetDateTime savedRoughEstimateUpdatedAt = entity.getRoughEstimateUpdatedAt();
        String savedRoughEstimateUpdatedBy = entity.getRoughEstimateUpdatedBy();
        Integer savedManualPriorityBoost = entity.getManualPriorityBoost();
        BigDecimal savedAutoScore = entity.getAutoScore();
        OffsetDateTime savedAutoScoreCalculatedAt = entity.getAutoScoreCalculatedAt();

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
            entity.setRemainingEstimateSeconds(timetracking.getRemainingEstimateSeconds());
            entity.setTimeSpentSeconds(timetracking.getTimeSpentSeconds());
        }

        // Extract and map team field
        String teamFieldValue = extractTeamFieldValue(jiraIssue);
        entity.setTeamFieldValue(teamFieldValue);
        entity.setTeamId(findTeamIdByFieldValue(teamFieldValue));

        // Extract priority
        if (jiraIssue.getFields().getPriority() != null) {
            entity.setPriority(jiraIssue.getFields().getPriority().getName());
        }

        // Extract due date
        entity.setDueDate(parseLocalDate(jiraIssue.getFields().getDuedate()));

        // Extract created date
        entity.setJiraCreatedAt(parseOffsetDateTime(jiraIssue.getFields().getCreated()));

        // Extract assignee
        if (jiraIssue.getFields().getAssignee() != null) {
            String accountId = jiraIssue.getFields().getAssignee().getAccountId();
            String displayName = jiraIssue.getFields().getAssignee().getDisplayName();
            log.info("Issue {} has assignee: {} ({})", jiraIssue.getKey(), displayName, accountId);
            entity.setAssigneeAccountId(accountId);
            entity.setAssigneeDisplayName(displayName);
        } else {
            entity.setAssigneeAccountId(null);
            entity.setAssigneeDisplayName(null);
        }

        // Detect "In Progress" status to set started_at
        String status = jiraIssue.getFields().getStatus().getName();
        if (isInProgressStatus(status) && entity.getStartedAt() == null) {
            // Only set started_at if it's not already set (first time entering In Progress)
            entity.setStartedAt(OffsetDateTime.now());
        } else if (!isInProgressStatus(status) && entity.getStartedAt() != null) {
            // Clear started_at if status is no longer In Progress (moved back to To Do or completed)
            // Actually, let's keep it once set to track when work originally started
            // entity.setStartedAt(null);
        }

        // Extract flagged status (Impediment)
        List<Object> flaggedList = jiraIssue.getFields().getFlagged();
        entity.setFlagged(flaggedList != null && !flaggedList.isEmpty());

        // Extract issue links (blocks / is blocked by)
        List<JiraIssue.JiraIssueLink> issueLinks = jiraIssue.getFields().getIssuelinks();
        if (issueLinks != null && !issueLinks.isEmpty()) {
            List<String> blocks = new ArrayList<>();
            List<String> isBlockedBy = new ArrayList<>();

            for (JiraIssue.JiraIssueLink link : issueLinks) {
                if (link.getType() == null) continue;

                String linkType = link.getType().getName();
                String outward = link.getType().getOutward();
                String inward = link.getType().getInward();

                // Check if this is a "Blocks" type link
                boolean isBlocksLink = "Blocks".equalsIgnoreCase(linkType) ||
                        (outward != null && outward.toLowerCase().contains("block")) ||
                        (inward != null && inward.toLowerCase().contains("block"));

                if (!isBlocksLink) continue;

                // Outward link: this issue blocks another
                if (link.getOutwardIssue() != null) {
                    blocks.add(link.getOutwardIssue().getKey());
                }

                // Inward link: this issue is blocked by another
                if (link.getInwardIssue() != null) {
                    isBlockedBy.add(link.getInwardIssue().getKey());
                }
            }

            entity.setBlocks(blocks.isEmpty() ? null : blocks);
            entity.setIsBlockedBy(isBlockedBy.isEmpty() ? null : isBlockedBy);
        } else {
            entity.setBlocks(null);
            entity.setIsBlockedBy(null);
        }

        // Restore local Lead Board fields
        entity.setRoughEstimateSaDays(savedRoughEstimateSaDays);
        entity.setRoughEstimateDevDays(savedRoughEstimateDevDays);
        entity.setRoughEstimateQaDays(savedRoughEstimateQaDays);
        entity.setRoughEstimateUpdatedAt(savedRoughEstimateUpdatedAt);
        entity.setRoughEstimateUpdatedBy(savedRoughEstimateUpdatedBy);
        entity.setManualPriorityBoost(savedManualPriorityBoost != null ? savedManualPriorityBoost : 0);
        entity.setAutoScore(savedAutoScore);
        entity.setAutoScoreCalculatedAt(savedAutoScoreCalculatedAt);

        issueRepository.save(entity);
    }

    private LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private OffsetDateTime parseOffsetDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse datetime: {}", dateTimeStr);
            return null;
        }
    }

    private String extractTeamFieldValue(JiraIssue jiraIssue) {
        String teamFieldId = jiraProperties.getTeamFieldId();
        if (teamFieldId == null || teamFieldId.isEmpty()) {
            return null;
        }

        Object fieldValue = jiraIssue.getFields().getCustomField(teamFieldId);
        if (fieldValue == null) {
            return null;
        }

        // Handle different team field formats
        // Could be a simple string, or an object with "value" or "name" property
        if (fieldValue instanceof String) {
            return (String) fieldValue;
        } else if (fieldValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) fieldValue;
            // Try common Jira field patterns
            if (map.containsKey("value")) {
                return String.valueOf(map.get("value"));
            } else if (map.containsKey("name")) {
                return String.valueOf(map.get("name"));
            } else if (map.containsKey("displayName")) {
                return String.valueOf(map.get("displayName"));
            }
        }

        return String.valueOf(fieldValue);
    }

    private Long findTeamIdByFieldValue(String teamFieldValue) {
        if (teamFieldValue == null || teamFieldValue.isEmpty()) {
            return null;
        }

        Optional<TeamEntity> team = teamRepository.findByJiraTeamValue(teamFieldValue);
        return team.map(TeamEntity::getId).orElse(null);
    }

    private JiraSyncStateEntity getOrCreateSyncState(String projectKey) {
        return syncStateRepository.findByProjectKey(projectKey)
                .orElseGet(() -> {
                    JiraSyncStateEntity newState = new JiraSyncStateEntity();
                    newState.setProjectKey(projectKey);
                    return syncStateRepository.save(newState);
                });
    }

    private boolean isInProgressStatus(String status) {
        if (status == null) {
            return false;
        }
        String lowerStatus = status.toLowerCase();
        return lowerStatus.contains("in progress") ||
               lowerStatus.contains("in dev") ||
               lowerStatus.contains("in development") ||
               lowerStatus.contains("in review") ||
               lowerStatus.contains("in qa") ||
               lowerStatus.contains("in testing");
    }

    public record SyncStatus(
            boolean syncInProgress,
            OffsetDateTime lastSyncStartedAt,
            OffsetDateTime lastSyncCompletedAt,
            int issuesCount,
            String error
    ) {}
}

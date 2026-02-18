package com.leadboard.sync;

import com.leadboard.config.entity.LinkCategory;
import com.leadboard.config.service.MappingAutoDetectService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.config.JiraProperties;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraIssue;
import com.leadboard.jira.JiraSearchResponse;
import com.leadboard.metrics.service.FlagChangelogService;
import com.leadboard.metrics.service.StatusChangelogService;
import com.leadboard.planning.AutoScoreService;
import com.leadboard.planning.IssueOrderService;
import com.leadboard.planning.StoryAutoScoreService;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import com.leadboard.team.TeamSyncService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final int RECONCILE_EVERY_N_SYNCS = 12;

    private int scheduledSyncCount = 0;

    private final JiraClient jiraClient;
    private final JiraProperties jiraProperties;
    private final JiraIssueRepository issueRepository;
    private final JiraSyncStateRepository syncStateRepository;
    private final TeamRepository teamRepository;
    private final AutoScoreService autoScoreService;
    private final StoryAutoScoreService storyAutoScoreService;
    private final StatusChangelogService statusChangelogService;
    private final FlagChangelogService flagChangelogService;
    private final IssueOrderService issueOrderService;
    private final WorkflowConfigService workflowConfigService;
    private final MappingAutoDetectService autoDetectService;
    private final ChangelogImportService changelogImportService;
    private final TeamSyncService teamSyncService;

    public SyncService(JiraClient jiraClient,
                       JiraProperties jiraProperties,
                       JiraIssueRepository issueRepository,
                       JiraSyncStateRepository syncStateRepository,
                       TeamRepository teamRepository,
                       AutoScoreService autoScoreService,
                       StoryAutoScoreService storyAutoScoreService,
                       StatusChangelogService statusChangelogService,
                       FlagChangelogService flagChangelogService,
                       IssueOrderService issueOrderService,
                       WorkflowConfigService workflowConfigService,
                       MappingAutoDetectService autoDetectService,
                       ChangelogImportService changelogImportService,
                       TeamSyncService teamSyncService) {
        this.jiraClient = jiraClient;
        this.jiraProperties = jiraProperties;
        this.issueRepository = issueRepository;
        this.syncStateRepository = syncStateRepository;
        this.teamRepository = teamRepository;
        this.autoScoreService = autoScoreService;
        this.storyAutoScoreService = storyAutoScoreService;
        this.statusChangelogService = statusChangelogService;
        this.flagChangelogService = flagChangelogService;
        this.issueOrderService = issueOrderService;
        this.workflowConfigService = workflowConfigService;
        this.autoDetectService = autoDetectService;
        this.changelogImportService = changelogImportService;
        this.teamSyncService = teamSyncService;
    }

    @Scheduled(fixedRateString = "${jira.sync-interval-seconds:300}000")
    public void scheduledSync() {
        String projectKey = jiraProperties.getProjectKey();
        if (projectKey == null || projectKey.isEmpty()) {
            return;
        }

        // Skip scheduled sync if initial setup hasn't been done yet (wizard not completed)
        JiraSyncStateEntity existingState = syncStateRepository.findByProjectKey(projectKey).orElse(null);
        if (existingState == null || existingState.getLastSyncCompletedAt() == null) {
            log.debug("Skipping scheduled sync — initial setup not completed yet");
            return;
        }

        autoDetectIfNeeded(projectKey);
        log.info("Starting scheduled sync for project: {}", projectKey);
        syncProject(projectKey);

        scheduledSyncCount++;
        if (scheduledSyncCount % RECONCILE_EVERY_N_SYNCS == 0) {
            reconcileDeletedIssues(projectKey);
        }
    }

    @Transactional
    public SyncStatus triggerSync() {
        return triggerSync(null);
    }

    @Transactional
    public SyncStatus triggerSync(Integer months) {
        String projectKey = jiraProperties.getProjectKey();
        if (projectKey == null || projectKey.isEmpty()) {
            return new SyncStatus(false, null, null, 0, "Project key not configured");
        }

        JiraSyncStateEntity state = getOrCreateSyncState(projectKey);
        if (state.isSyncInProgress()) {
            return new SyncStatus(true, state.getLastSyncStartedAt(), state.getLastSyncCompletedAt(),
                    state.getLastSyncIssuesCount(), "Sync already in progress");
        }

        new Thread(() -> {
            autoDetectIfNeeded(projectKey);
            syncProject(projectKey, months);
            reconcileDeletedIssues(projectKey);
        }).start();

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
        syncProject(projectKey, null);
    }

    @Transactional
    public void syncProject(String projectKey, Integer months) {
        JiraSyncStateEntity state = getOrCreateSyncState(projectKey);

        if (state.isSyncInProgress()) {
            log.warn("Sync already in progress for project: {}", projectKey);
            return;
        }

        state.setSyncInProgress(true);
        state.setLastSyncStartedAt(OffsetDateTime.now());
        state.setLastError(null);
        syncStateRepository.save(state);

        List<String> statusChangedKeys = new ArrayList<>();

        try {
            int totalSynced = 0;

            OffsetDateTime lastSync = state.getLastSyncCompletedAt();
            String jql;
            if (lastSync != null) {
                String lastSyncTime = lastSync.minusMinutes(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                jql = String.format("project = %s AND updated >= '%s' ORDER BY updated DESC", projectKey, lastSyncTime);
                log.info("Incremental sync for project: {} (changes since {})", projectKey, lastSyncTime);
            } else if (months != null && months > 0) {
                int days = months * 30;
                jql = String.format("project = %s AND updated >= -%dd ORDER BY updated DESC", projectKey, days);
                log.info("First sync for project: {} (last {} months / ~{}d)", projectKey, months, days);
            } else {
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
                    boolean statusChanged = saveOrUpdateIssue(issue, projectKey);
                    if (statusChanged) {
                        statusChangedKeys.add(issue.getKey());
                    }
                    totalSynced++;
                }

                if (response.isLast() || response.getNextPageToken() == null) {
                    break;
                }
                nextPageToken = response.getNextPageToken();
            }

            state.setSyncInProgress(false);
            state.setLastSyncCompletedAt(OffsetDateTime.now());
            state.setLastSyncIssuesCount(totalSynced);
            syncStateRepository.save(state);

            log.info("Sync completed for project: {}. Issues synced: {} ({})", projectKey, totalSynced,
                    lastSync != null ? "incremental" : "full");

            // Normalize manual_order
            try {
                List<Long> allTeamIds = teamRepository.findByActiveTrue().stream()
                        .map(TeamEntity::getId)
                        .toList();
                for (Long teamId : allTeamIds) {
                    issueOrderService.normalizeTeamEpicOrders(teamId);
                }

                Set<String> allEpicKeys = issueRepository.findByProjectKey(projectKey).stream()
                        .filter(e -> e.getParentKey() != null)
                        .map(JiraIssueEntity::getParentKey)
                        .collect(Collectors.toSet());
                for (String epicKey : allEpicKeys) {
                    issueOrderService.normalizeStoryOrders(epicKey);
                }
                log.info("Normalized manual_order for {} teams and {} epics", allTeamIds.size(), allEpicKeys.size());
            } catch (Exception e) {
                log.error("Failed to normalize manual_order after sync", e);
            }

            // Recalculate AutoScore
            try {
                int epicsUpdated = autoScoreService.recalculateAll();
                log.info("AutoScore recalculated for {} epics after sync", epicsUpdated);

                int storiesUpdated = storyAutoScoreService.recalculateAll();
                log.info("AutoScore recalculated for {} stories after sync", storiesUpdated);
            } catch (Exception e) {
                log.error("Failed to recalculate AutoScore after sync", e);
            }

            // Re-link issues to teams (handles cases where teams were created after initial sync)
            try {
                int totalLinked = 0;
                for (var team : teamRepository.findByActiveTrue()) {
                    if (team.getJiraTeamValue() != null && !team.getJiraTeamValue().isEmpty()) {
                        totalLinked += issueRepository.linkIssuesToTeam(team.getId(), team.getJiraTeamValue());
                    }
                }
                if (totalLinked > 0) {
                    log.info("Linked {} issues to teams by team_field_value", totalLinked);
                    int inherited = issueRepository.inheritTeamFromParent();
                    if (inherited > 0) {
                        log.info("Inherited team for {} child issues", inherited);
                        issueRepository.inheritTeamFromParent();
                    }
                }
            } catch (Exception e) {
                log.error("Failed to re-link issues to teams after sync", e);
            }

            // Import real Jira changelogs async for issues that changed status
            if (!statusChangedKeys.isEmpty()) {
                log.info("Scheduling async changelog import for {} issues with status changes", statusChangedKeys.size());
                changelogImportService.importChangelogsForIssuesAsync(statusChangedKeys);
            }

            // Trigger team sync if organization ID is configured
            try {
                String orgId = jiraProperties.getOrganizationId();
                if (orgId != null && !orgId.isEmpty()) {
                    log.info("Triggering team sync after issue sync");
                    teamSyncService.syncTeams();
                }
            } catch (Exception e) {
                log.error("Team sync after issue sync failed", e);
            }

        } catch (Exception e) {
            log.error("Sync failed for project: {}", projectKey, e);
            state.setSyncInProgress(false);
            state.setLastError(e.getMessage());
            syncStateRepository.save(state);
        }
    }

    /**
     * Runs auto-detect OUTSIDE the sync transaction to avoid Hibernate session corruption.
     * Each call to autoDetectService.autoDetect() runs in its own REQUIRES_NEW transaction.
     */
    private void autoDetectIfNeeded(String projectKey) {
        try {
            JiraSyncStateEntity state = syncStateRepository.findByProjectKey(projectKey).orElse(null);
            boolean isFirstSync = (state == null || state.getLastSyncCompletedAt() == null);
            if (isFirstSync && autoDetectService.isConfigEmpty()) {
                log.info("First sync: auto-detecting workflow configuration from Jira...");
                var result = autoDetectService.autoDetect();
                log.info("Auto-detected: {} types, {} roles, {} statuses, {} links",
                        result.issueTypeCount(), result.roleCount(),
                        result.statusMappingCount(), result.linkTypeCount());
                if (!result.warnings().isEmpty()) {
                    result.warnings().forEach(w -> log.warn("Auto-detect warning: {}", w));
                }
            }
        } catch (Exception e) {
            log.error("Auto-detection failed, fallback substring matching will be used", e);
        }
    }

    /**
     * @return true if the issue's status changed during this sync
     */
    private boolean saveOrUpdateIssue(JiraIssue jiraIssue, String projectKey) {
        JiraIssueEntity existing = issueRepository.findByIssueKey(jiraIssue.getKey())
                .orElse(null);
        JiraIssueEntity entity = existing != null ? existing : new JiraIssueEntity();

        String previousStatus = existing != null ? existing.getStatus() : null;
        Boolean previousFlagged = existing != null ? existing.getFlagged() : null;

        // Preserve local Lead Board data
        Map<String, BigDecimal> savedRoughEstimates = entity.getRoughEstimates();
        OffsetDateTime savedRoughEstimateUpdatedAt = entity.getRoughEstimateUpdatedAt();
        String savedRoughEstimateUpdatedBy = entity.getRoughEstimateUpdatedBy();
        BigDecimal savedAutoScore = entity.getAutoScore();
        OffsetDateTime savedAutoScoreCalculatedAt = entity.getAutoScoreCalculatedAt();
        OffsetDateTime savedDoneAt = entity.getDoneAt();

        entity.setIssueKey(jiraIssue.getKey());
        entity.setIssueId(jiraIssue.getId());
        entity.setProjectKey(projectKey);
        entity.setSummary(jiraIssue.getFields().getSummary());
        entity.setStatus(jiraIssue.getFields().getStatus().getName());
        entity.setIssueType(jiraIssue.getFields().getIssuetype().getName());
        entity.setSubtask(jiraIssue.getFields().getIssuetype().isSubtask());

        // Compute board_category and workflow_role from workflow config
        String issueTypeName = jiraIssue.getFields().getIssuetype().getName();
        boolean isSubtaskFlag = jiraIssue.getFields().getIssuetype().isSubtask();
        entity.setBoardCategory(workflowConfigService.computeBoardCategory(issueTypeName, isSubtaskFlag));
        // Register unknown type if not yet mapped
        if (entity.getBoardCategory() == null) {
            try {
                autoDetectService.registerUnknownTypeIfNeeded(issueTypeName);
            } catch (Exception e) {
                log.warn("Failed to register unknown type '{}': {}", issueTypeName, e.getMessage());
            }
        }
        entity.setWorkflowRole(workflowConfigService.computeWorkflowRole(issueTypeName));

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

        String teamFieldValue = extractTeamFieldValue(jiraIssue);
        entity.setTeamFieldValue(teamFieldValue);
        entity.setTeamId(findTeamIdByFieldValue(teamFieldValue));

        if (jiraIssue.getFields().getPriority() != null) {
            entity.setPriority(jiraIssue.getFields().getPriority().getName());
        }

        entity.setDueDate(parseLocalDate(jiraIssue.getFields().getDuedate()));
        entity.setJiraCreatedAt(parseOffsetDateTime(jiraIssue.getFields().getCreated()));

        if (jiraIssue.getFields().getAssignee() != null) {
            entity.setAssigneeAccountId(jiraIssue.getFields().getAssignee().getAccountId());
            entity.setAssigneeDisplayName(jiraIssue.getFields().getAssignee().getDisplayName());
        } else {
            entity.setAssigneeAccountId(null);
            entity.setAssigneeDisplayName(null);
        }

        // Extract components
        List<JiraIssue.JiraComponent> jiraComponents = jiraIssue.getFields().getComponents();
        if (jiraComponents != null && !jiraComponents.isEmpty()) {
            entity.setComponents(jiraComponents.stream()
                    .map(JiraIssue.JiraComponent::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .toArray(String[]::new));
        } else {
            entity.setComponents(null);
        }

        // Detect "In Progress" using WorkflowConfigService
        // started_at will be corrected to real Jira timestamp by changelog import
        String status = jiraIssue.getFields().getStatus().getName();
        if (workflowConfigService.isInProgress(status, issueTypeName) && entity.getStartedAt() == null) {
            entity.setStartedAt(OffsetDateTime.now()); // Temporary fallback, will be fixed by changelog import
        }

        List<Object> flaggedList = jiraIssue.getFields().getFlagged();
        boolean isFlagged = flaggedList != null && !flaggedList.isEmpty();
        // Fallback: Jira Cloud may return flagged as customfield_10021 instead of "flagged"
        if (!isFlagged) {
            Object cf10021 = jiraIssue.getFields().getCustomFields().get("customfield_10021");
            if (cf10021 instanceof Map) {
                Object value = ((Map<?, ?>) cf10021).get("value");
                if (value instanceof List && !((List<?>) value).isEmpty()) {
                    isFlagged = true;
                }
            } else if (cf10021 instanceof List && !((List<?>) cf10021).isEmpty()) {
                isFlagged = true;
            }
        }
        entity.setFlagged(isFlagged);

        // Extract issue links using WorkflowConfigService for link categorization
        List<JiraIssue.JiraIssueLink> issueLinks = jiraIssue.getFields().getIssuelinks();
        if (issueLinks != null && !issueLinks.isEmpty()) {
            List<String> blocks = new ArrayList<>();
            List<String> isBlockedBy = new ArrayList<>();
            List<String> childEpicKeys = new ArrayList<>();
            boolean isProjectIssue = "PROJECT".equals(entity.getBoardCategory());

            for (JiraIssue.JiraIssueLink link : issueLinks) {
                if (link.getType() == null) continue;

                String linkTypeName = link.getType().getName();

                // For PROJECT issues, collect ALL linked issue keys (auto-detect mode)
                if (isProjectIssue) {
                    if (link.getInwardIssue() != null) {
                        childEpicKeys.add(link.getInwardIssue().getKey());
                    }
                    if (link.getOutwardIssue() != null) {
                        childEpicKeys.add(link.getOutwardIssue().getKey());
                    }
                }

                LinkCategory linkCategory = workflowConfigService.categorizeLinkType(linkTypeName);

                if (linkCategory != LinkCategory.BLOCKS) continue;

                if (link.getOutwardIssue() != null) {
                    blocks.add(link.getOutwardIssue().getKey());
                }
                if (link.getInwardIssue() != null) {
                    isBlockedBy.add(link.getInwardIssue().getKey());
                }
            }

            entity.setBlocks(blocks.isEmpty() ? null : blocks);
            entity.setIsBlockedBy(isBlockedBy.isEmpty() ? null : isBlockedBy);
            if (isProjectIssue) {
                entity.setChildEpicKeys(childEpicKeys.isEmpty() ? null : childEpicKeys.toArray(new String[0]));
            }
        } else {
            entity.setBlocks(null);
            entity.setIsBlockedBy(null);
        }

        // Restore local fields
        entity.setRoughEstimates(savedRoughEstimates);
        entity.setRoughEstimateUpdatedAt(savedRoughEstimateUpdatedAt);
        entity.setRoughEstimateUpdatedBy(savedRoughEstimateUpdatedBy);
        entity.setAutoScore(savedAutoScore);
        entity.setAutoScoreCalculatedAt(savedAutoScoreCalculatedAt);
        entity.setDoneAt(savedDoneAt);

        statusChangelogService.updateDoneAtIfNeeded(entity);

        issueRepository.save(entity);

        issueOrderService.assignOrderIfMissing(entity);

        boolean statusChanged = !java.util.Objects.equals(previousStatus, entity.getStatus());

        if (statusChanged) {
            // Record synthetic changelog immediately (fast, no extra API calls).
            // Real Jira changelog will be imported async after sync completes.
            JiraIssueEntity previousEntity = existing != null ? new JiraIssueEntity() : null;
            if (previousEntity != null) {
                previousEntity.setStatus(previousStatus);
                previousEntity.setUpdatedAt(existing.getUpdatedAt());
            }
            statusChangelogService.detectAndRecordStatusChange(previousEntity, entity);
        }

        // Detect flag change
        boolean wasFlagged = Boolean.TRUE.equals(previousFlagged);
        boolean nowFlagged = Boolean.TRUE.equals(entity.getFlagged());
        if (wasFlagged != nowFlagged) {
            JiraIssueEntity prevEntity = existing != null ? new JiraIssueEntity() : null;
            if (prevEntity != null) {
                prevEntity.setFlagged(previousFlagged);
            }
            flagChangelogService.detectAndRecordFlagChange(prevEntity, entity);
        }

        return statusChanged;
    }

    private LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private OffsetDateTime parseOffsetDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse datetime: {}", dateTimeStr);
            return null;
        }
    }

    private String extractTeamFieldValue(JiraIssue jiraIssue) {
        String teamFieldId = jiraProperties.getTeamFieldId();
        if (teamFieldId == null || teamFieldId.isEmpty()) return null;

        Object fieldValue = jiraIssue.getFields().getCustomField(teamFieldId);
        if (fieldValue == null) return null;

        if (fieldValue instanceof String) {
            return (String) fieldValue;
        } else if (fieldValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) fieldValue;
            if (map.containsKey("value")) return String.valueOf(map.get("value"));
            else if (map.containsKey("name")) return String.valueOf(map.get("name"));
            else if (map.containsKey("displayName")) return String.valueOf(map.get("displayName"));
        }

        return String.valueOf(fieldValue);
    }

    private Long findTeamIdByFieldValue(String teamFieldValue) {
        if (teamFieldValue == null || teamFieldValue.isEmpty()) return null;
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

    @Transactional
    public void reconcileDeletedIssues(String projectKey) {
        try {
            log.info("Starting reconciliation of deleted issues for project: {}", projectKey);

            Set<String> jiraKeys = new HashSet<>();
            String jql = String.format("project = %s ORDER BY key ASC", projectKey);
            String nextPageToken = null;

            while (true) {
                JiraSearchResponse response = jiraClient.searchKeysOnly(jql, 100, nextPageToken);
                List<JiraIssue> issues = response.getIssues();

                if (issues == null || issues.isEmpty()) break;

                for (JiraIssue issue : issues) {
                    jiraKeys.add(issue.getKey());
                }

                if (response.isLast() || response.getNextPageToken() == null) break;
                nextPageToken = response.getNextPageToken();
            }

            List<String> dbKeys = issueRepository.findAllIssueKeysByProjectKey(projectKey);

            List<String> orphanedKeys = dbKeys.stream()
                    .filter(key -> !jiraKeys.contains(key))
                    .toList();

            if (!orphanedKeys.isEmpty()) {
                log.info("Found {} deleted issues to remove: {}", orphanedKeys.size(), orphanedKeys);
                issueRepository.deleteByIssueKeyIn(orphanedKeys);
                log.info("Removed {} orphaned issues from database", orphanedKeys.size());
            } else {
                log.info("No deleted issues found during reconciliation");
            }

        } catch (Exception e) {
            log.error("Failed to reconcile deleted issues for project: {}", projectKey, e);
        }
    }

    public Map<String, Object> countIssuesInJira(Integer months) {
        String projectKey = jiraProperties.getProjectKey();
        if (projectKey == null || projectKey.isEmpty()) {
            return Map.of("total", 0, "months", 0, "error", "Project key not configured");
        }

        String jql;
        if (months != null && months > 0) {
            int days = months * 30;
            jql = String.format("project = %s AND updated >= -%dd", projectKey, days);
        } else {
            jql = String.format("project = %s", projectKey);
        }

        int total = jiraClient.countByJql(jql);
        return Map.of(
                "total", total,
                "months", months != null ? months : 0
        );
    }

    public record SyncStatus(
            boolean syncInProgress,
            OffsetDateTime lastSyncStartedAt,
            OffsetDateTime lastSyncCompletedAt,
            int issuesCount,
            String error
    ) {}
}

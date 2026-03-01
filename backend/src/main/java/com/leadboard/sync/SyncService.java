package com.leadboard.sync;

import com.leadboard.board.BoardService;
import com.leadboard.config.ObservabilityMetrics;
import com.leadboard.config.entity.LinkCategory;
import com.leadboard.config.service.MappingAutoDetectService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.config.JiraConfigResolver;
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
import com.leadboard.tenant.TenantJiraConfigRepository;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final JiraConfigResolver jiraConfigResolver;
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
    private final TenantJiraConfigRepository tenantJiraConfigRepository;
    private final ObservabilityMetrics observabilityMetrics;
    private final com.leadboard.planning.UnifiedPlanningService unifiedPlanningService;
    private final BoardService boardService;
    private final SyncService self;

    public SyncService(JiraClient jiraClient,
                       JiraConfigResolver jiraConfigResolver,
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
                       TeamSyncService teamSyncService,
                       TenantJiraConfigRepository tenantJiraConfigRepository,
                       ObservabilityMetrics observabilityMetrics,
                       com.leadboard.planning.UnifiedPlanningService unifiedPlanningService,
                       BoardService boardService,
                       @Lazy SyncService self) {
        this.jiraClient = jiraClient;
        this.jiraConfigResolver = jiraConfigResolver;
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
        this.tenantJiraConfigRepository = tenantJiraConfigRepository;
        this.observabilityMetrics = observabilityMetrics;
        this.unifiedPlanningService = unifiedPlanningService;
        this.boardService = boardService;
        this.self = self;
    }

    /**
     * BUG-39: Auto-recover stuck sync_in_progress flag on startup.
     * If sync has been running for more than 30 minutes, reset it.
     */
    @PostConstruct
    public void recoverStuckSync() {
        try {
            syncStateRepository.findAll().forEach(state -> {
                if (state.isSyncInProgress() && state.getLastSyncStartedAt() != null) {
                    Duration stuck = Duration.between(state.getLastSyncStartedAt(), OffsetDateTime.now());
                    if (stuck.toMinutes() > 30) {
                        log.warn("Recovering stuck sync for project {} (stuck for {})",
                                state.getProjectKey(), stuck);
                        state.setSyncInProgress(false);
                        state.setLastError("Sync was stuck, auto-recovered on startup");
                        syncStateRepository.save(state);
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Could not recover stuck syncs on startup (table may not exist in current schema): {}",
                    e.getMessage());
        }
    }

    @Scheduled(fixedRateString = "${jira.sync-interval-seconds:300}000")
    public void scheduledSync() {
        String projectKey = jiraConfigResolver.getProjectKey();
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
        self.syncProject(projectKey);

        scheduledSyncCount++;
        if (scheduledSyncCount % RECONCILE_EVERY_N_SYNCS == 0) {
            self.reconcileDeletedIssues(projectKey);
        }
    }

    public SyncStatus triggerSync() {
        return triggerSync(null);
    }

    public SyncStatus triggerSync(Integer months) {
        String projectKey = jiraConfigResolver.getProjectKey();
        if (projectKey == null || projectKey.isEmpty()) {
            return new SyncStatus(false, null, null, 0, "Project key not configured");
        }

        JiraSyncStateEntity state = getOrCreateSyncState(projectKey);
        if (state.isSyncInProgress()) {
            return new SyncStatus(true, state.getLastSyncStartedAt(), state.getLastSyncCompletedAt(),
                    state.getLastSyncIssuesCount(), "Sync already in progress");
        }

        self.runSyncAsync(projectKey, months);

        return new SyncStatus(true, OffsetDateTime.now(), state.getLastSyncCompletedAt(),
                state.getLastSyncIssuesCount(), null);
    }

    /**
     * BUG-41: Run sync asynchronously via @Async instead of raw Thread.
     */
    @Async
    public void runSyncAsync(String projectKey, Integer months) {
        try {
            autoDetectIfNeeded(projectKey);
            syncProject(projectKey, months);
            reconcileDeletedIssues(projectKey);
        } catch (Exception e) {
            log.error("Async sync failed for project: {}", projectKey, e);
        }
    }

    public SyncStatus getSyncStatus() {
        String projectKey = jiraConfigResolver.getProjectKey();
        if (projectKey == null || projectKey.isEmpty()) {
            return new SyncStatus(false, null, null, 0, "Project key not configured");
        }

        JiraSyncStateEntity state = syncStateRepository.findByProjectKey(projectKey).orElse(null);

        boolean setupCompleted = false;
        try {
            setupCompleted = tenantJiraConfigRepository.findActive()
                    .map(c -> c.isSetupCompleted())
                    .orElse(false);
        } catch (Exception e) {
            // tenant_jira_config may not exist in public schema
        }

        if (state == null) {
            return new SyncStatus(false, null, null, 0, null, setupCompleted);
        }

        return new SyncStatus(
                state.isSyncInProgress(),
                state.getLastSyncStartedAt(),
                state.getLastSyncCompletedAt(),
                state.getLastSyncIssuesCount(),
                state.getLastError(),
                setupCompleted
        );
    }

    /**
     * Called by TenantSyncScheduler in tenant context.
     * TenantContext is already set by the caller.
     */
    public void syncProjectForTenant(String projectKey) {
        autoDetectIfNeeded(projectKey);
        syncProject(projectKey, null);
    }

    public void syncProject(String projectKey) {
        syncProject(projectKey, null);
    }

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
        Timer.Sample syncTimer = observabilityMetrics.startSyncTimer();

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

            observabilityMetrics.stopSyncTimer(syncTimer);
            observabilityMetrics.recordIssuesSynced(totalSynced);

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

            // Invalidate planning and board caches after sync
            unifiedPlanningService.invalidateAllPlanCaches();
            boardService.invalidateBoardCache();

            // Trigger team sync if organization ID is configured
            try {
                String orgId = jiraConfigResolver.getOrganizationId();
                if (orgId != null && !orgId.isEmpty()) {
                    log.info("Triggering team sync after issue sync");
                    teamSyncService.syncTeams();
                }
            } catch (Exception e) {
                log.error("Team sync after issue sync failed", e);
            }

        } catch (Exception e) {
            observabilityMetrics.stopSyncTimer(syncTimer);
            observabilityMetrics.recordSyncError();
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
            if (isFirstSync && autoDetectService.isConfigEmptyForProject(projectKey)) {
                log.info("First sync for project {}: auto-detecting workflow configuration from Jira...", projectKey);
                var result = autoDetectService.autoDetectForProject(projectKey);
                log.info("Auto-detected for {}: {} types, {} roles, {} statuses, {} links",
                        projectKey, result.issueTypeCount(), result.roleCount(),
                        result.statusMappingCount(), result.linkTypeCount());
                if (!result.warnings().isEmpty()) {
                    result.warnings().forEach(w -> log.warn("Auto-detect warning: {}", w));
                }
            }
        } catch (Exception e) {
            log.error("Auto-detection failed for project {}, fallback substring matching will be used", projectKey, e);
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
        entity.setDescription(extractDescriptionText(jiraIssue.getFields().getDescription()));
        entity.setStatus(jiraIssue.getFields().getStatus().getName());
        entity.setIssueType(jiraIssue.getFields().getIssuetype().getName());
        entity.setSubtask(jiraIssue.getFields().getIssuetype().isSubtask());

        // Compute board_category and workflow_role from workflow config
        String issueTypeName = jiraIssue.getFields().getIssuetype().getName();
        boolean isSubtaskFlag = jiraIssue.getFields().getIssuetype().isSubtask();
        entity.setBoardCategory(workflowConfigService.computeBoardCategory(issueTypeName, isSubtaskFlag));
        // Register unknown type if not yet mapped (per-project)
        if (entity.getBoardCategory() == null) {
            try {
                autoDetectService.registerUnknownTypeIfNeeded(issueTypeName, projectKey);
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
        entity.setJiraUpdatedAt(parseOffsetDateTime(jiraIssue.getFields().getUpdated()));

        if (jiraIssue.getFields().getAssignee() != null) {
            entity.setAssigneeAccountId(jiraIssue.getFields().getAssignee().getAccountId());
            entity.setAssigneeDisplayName(jiraIssue.getFields().getAssignee().getDisplayName());
            entity.setAssigneeAvatarUrl(jiraIssue.getFields().getAssignee().getAvatarUrl48());
        } else {
            entity.setAssigneeAccountId(null);
            entity.setAssigneeDisplayName(null);
            entity.setAssigneeAvatarUrl(null);
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

    /**
     * Extract plain text from Jira API v3 ADF (Atlassian Document Format) description.
     * ADF is a JSON object with nested content nodes. This recursively extracts text.
     */
    @SuppressWarnings("unchecked")
    private String extractDescriptionText(Object description) {
        if (description == null) return null;
        if (description instanceof String s) return s.isBlank() ? null : s;
        if (description instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            Object content = map.get("content");
            if (content instanceof List<?> list) {
                for (Object item : list) {
                    String text = extractDescriptionText(item);
                    if (text != null && !text.isEmpty()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(text);
                    }
                }
            }
            Object textVal = map.get("text");
            if (textVal instanceof String s && !s.isEmpty()) {
                sb.append(s);
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        return null;
    }

    private String extractTeamFieldValue(JiraIssue jiraIssue) {
        String teamFieldId = jiraConfigResolver.getTeamFieldId();
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

            // BUG-40: Sanity check — abort if Jira returned 0 keys (possible API error)
            if (jiraKeys.isEmpty()) {
                log.warn("Reconciliation aborted: Jira returned 0 keys (possible API error)");
                return;
            }

            List<String> dbKeys = issueRepository.findAllIssueKeysByProjectKey(projectKey);

            List<String> orphanedKeys = dbKeys.stream()
                    .filter(key -> !jiraKeys.contains(key))
                    .toList();

            // BUG-40: Sanity check — abort if too many orphaned keys (>50% of DB)
            int dbCount = dbKeys.size();
            if (dbCount > 0 && orphanedKeys.size() > dbCount / 2) {
                log.warn("Reconciliation aborted: too many orphaned keys ({} of {}), possible API issue",
                        orphanedKeys.size(), dbCount);
                return;
            }

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
        String projectKey = jiraConfigResolver.getProjectKey();
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

        // BUG-46: Wrap in try-catch to avoid 500 on Jira API errors
        try {
            int total = jiraClient.countByJql(jql);
            return Map.of(
                    "total", total,
                    "months", months != null ? months : 0
            );
        } catch (Exception e) {
            log.error("Failed to count issues in Jira: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("total", 0);
            result.put("months", months != null ? months : 0);
            result.put("error", e.getMessage());
            return result;
        }
    }

    public record SyncStatus(
            boolean syncInProgress,
            OffsetDateTime lastSyncStartedAt,
            OffsetDateTime lastSyncCompletedAt,
            int issuesCount,
            String error,
            boolean setupCompleted
    ) {
        /** Backwards-compatible constructor (setupCompleted defaults to false). */
        public SyncStatus(boolean syncInProgress, OffsetDateTime lastSyncStartedAt,
                          OffsetDateTime lastSyncCompletedAt, int issuesCount, String error) {
            this(syncInProgress, lastSyncStartedAt, lastSyncCompletedAt, issuesCount, error, false);
        }
    }
}

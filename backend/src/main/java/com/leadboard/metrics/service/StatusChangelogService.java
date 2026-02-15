package com.leadboard.metrics.service;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraChangelogResponse;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.sync.JiraIssueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class StatusChangelogService {

    private static final Logger log = LoggerFactory.getLogger(StatusChangelogService.class);

    private final StatusChangelogRepository repository;
    private final WorkflowConfigService workflowConfigService;

    public StatusChangelogService(StatusChangelogRepository repository,
                                  WorkflowConfigService workflowConfigService) {
        this.repository = repository;
        this.workflowConfigService = workflowConfigService;
    }

    /**
     * Detects status change and records to changelog.
     * Called from SyncService during issue synchronization.
     *
     * @param existing the existing entity (null if new issue)
     * @param updated the updated entity with new status
     */
    @Transactional
    public void detectAndRecordStatusChange(JiraIssueEntity existing, JiraIssueEntity updated) {
        String oldStatus = existing != null ? existing.getStatus() : null;
        String newStatus = updated.getStatus();

        if (Objects.equals(oldStatus, newStatus)) {
            return;
        }

        // Calculate time in previous status
        Long timeInPrevStatus = null;
        if (existing != null && existing.getUpdatedAt() != null) {
            timeInPrevStatus = Duration.between(existing.getUpdatedAt(), OffsetDateTime.now()).toSeconds();
        }

        OffsetDateTime transitionedAt = OffsetDateTime.now();

        // Check if this exact transition already exists (avoid duplicates)
        if (repository.findByIssueKeyAndToStatusAndTransitionedAt(
                updated.getIssueKey(), newStatus, transitionedAt).isPresent()) {
            log.debug("Status transition already recorded for {} -> {}", updated.getIssueKey(), newStatus);
            return;
        }

        StatusChangelogEntity entry = new StatusChangelogEntity();
        entry.setIssueKey(updated.getIssueKey());
        entry.setIssueId(updated.getIssueId());
        entry.setFromStatus(oldStatus);
        entry.setToStatus(newStatus);
        entry.setTransitionedAt(transitionedAt);
        entry.setTimeInPreviousStatusSeconds(timeInPrevStatus);
        entry.setCreatedAt(OffsetDateTime.now());

        repository.save(entry);
        log.debug("Recorded status change for {}: {} -> {}", updated.getIssueKey(), oldStatus, newStatus);
    }

    /**
     * Updates done_at field when issue transitions to Done status.
     *
     * @param entity the issue entity to update
     */
    public void updateDoneAtIfNeeded(JiraIssueEntity entity) {
        boolean isDone = workflowConfigService.isDone(entity.getStatus(), entity.getIssueType());

        if (isDone && entity.getDoneAt() == null) {
            entity.setDoneAt(OffsetDateTime.now());
            log.debug("Set done_at for {} (status: {})", entity.getIssueKey(), entity.getStatus());
        } else if (!isDone && entity.getDoneAt() != null) {
            // Issue was reopened - clear done_at
            entity.setDoneAt(null);
            log.debug("Cleared done_at for {} (status: {})", entity.getIssueKey(), entity.getStatus());
        }
    }

    /**
     * Imports changelog histories from Jira, replacing synthetic SYNC entries.
     * Only status-change items are imported.
     *
     * @return number of changelog entries imported
     */
    @Transactional
    public int importJiraChangelog(String issueKey, String issueId,
                                   List<JiraChangelogResponse.ChangelogHistory> histories) {
        // Delete ALL existing entries for this issue (both SYNC and JIRA) to ensure idempotency
        repository.deleteByIssueKey(issueKey);

        // Filter and sort status changes
        List<StatusTransition> transitions = histories.stream()
                .flatMap(h -> h.getItems().stream()
                        .filter(item -> "status".equalsIgnoreCase(item.getField()))
                        .map(item -> new StatusTransition(
                                item.getFromString(),
                                item.getToString(),
                                parseJiraTimestamp(h.getCreated())
                        )))
                .filter(t -> t.transitionedAt != null)
                .sorted(Comparator.comparing(t -> t.transitionedAt))
                .toList();

        if (transitions.isEmpty()) {
            return 0;
        }

        OffsetDateTime previousTransitionAt = null;
        int count = 0;

        for (StatusTransition transition : transitions) {
            Long timeInPrevStatus = null;
            if (previousTransitionAt != null) {
                timeInPrevStatus = Duration.between(previousTransitionAt, transition.transitionedAt).toSeconds();
                if (timeInPrevStatus < 0) timeInPrevStatus = null;
            }

            StatusChangelogEntity entry = new StatusChangelogEntity();
            entry.setIssueKey(issueKey);
            entry.setIssueId(issueId);
            entry.setFromStatus(transition.fromStatus);
            entry.setToStatus(transition.toStatus);
            entry.setTransitionedAt(transition.transitionedAt);
            entry.setTimeInPreviousStatusSeconds(timeInPrevStatus);
            entry.setSource("JIRA");
            entry.setCreatedAt(OffsetDateTime.now());

            repository.save(entry);
            previousTransitionAt = transition.transitionedAt;
            count++;
        }

        log.debug("Imported {} changelog entries for {}", count, issueKey);
        return count;
    }

    /**
     * Finds the first in-progress transition timestamp for an issue from the changelog.
     */
    public Optional<OffsetDateTime> findFirstInProgressTransition(String issueKey, String issueType) {
        List<StatusChangelogEntity> entries = repository.findByIssueKeyOrderByTransitionedAtAsc(issueKey);
        return entries.stream()
                .filter(e -> workflowConfigService.isInProgress(e.getToStatus(), issueType))
                .map(StatusChangelogEntity::getTransitionedAt)
                .findFirst();
    }

    /**
     * Finds the last done transition timestamp for an issue from the changelog.
     */
    public Optional<OffsetDateTime> findLastDoneTransition(String issueKey, String issueType) {
        List<StatusChangelogEntity> entries = repository.findByIssueKeyOrderByTransitionedAtDesc(issueKey);
        return entries.stream()
                .filter(e -> workflowConfigService.isDone(e.getToStatus(), issueType))
                .map(StatusChangelogEntity::getTransitionedAt)
                .findFirst();
    }

    /**
     * Returns true if this issue already has JIRA-source changelog entries.
     */
    public boolean hasJiraChangelog(String issueKey) {
        return repository.existsByIssueKeyAndSource(issueKey, "JIRA");
    }

    private static final DateTimeFormatter JIRA_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private OffsetDateTime parseJiraTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e1) {
            // Jira uses +0000 format (without colon), try that
            try {
                return OffsetDateTime.parse(timestamp, JIRA_TIMESTAMP_FORMATTER);
            } catch (DateTimeParseException e2) {
                log.warn("Failed to parse Jira timestamp: {}", timestamp);
                return null;
            }
        }
    }

    private record StatusTransition(String fromStatus, String toStatus, OffsetDateTime transitionedAt) {}
}

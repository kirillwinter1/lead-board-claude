package com.leadboard.metrics.service;

import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.status.StatusMappingService;
import com.leadboard.sync.JiraIssueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
public class StatusChangelogService {

    private static final Logger log = LoggerFactory.getLogger(StatusChangelogService.class);

    private final StatusChangelogRepository repository;
    private final StatusMappingService statusMappingService;

    public StatusChangelogService(StatusChangelogRepository repository,
                                  StatusMappingService statusMappingService) {
        this.repository = repository;
        this.statusMappingService = statusMappingService;
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
        boolean isDone = statusMappingService.isDone(entity.getStatus(), null);

        if (isDone && entity.getDoneAt() == null) {
            entity.setDoneAt(OffsetDateTime.now());
            log.debug("Set done_at for {} (status: {})", entity.getIssueKey(), entity.getStatus());
        } else if (!isDone && entity.getDoneAt() != null) {
            // Issue was reopened - clear done_at
            entity.setDoneAt(null);
            log.debug("Cleared done_at for {} (status: {})", entity.getIssueKey(), entity.getStatus());
        }
    }
}

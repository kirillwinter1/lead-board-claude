package com.leadboard.quality;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class BugSlaService {

    private static final int STALE_THRESHOLD_DAYS = 14;

    private final BugSlaConfigRepository slaConfigRepository;
    private final WorkflowConfigService workflowConfigService;

    public BugSlaService(BugSlaConfigRepository slaConfigRepository,
                         WorkflowConfigService workflowConfigService) {
        this.slaConfigRepository = slaConfigRepository;
        this.workflowConfigService = workflowConfigService;
    }

    public List<BugSlaConfigEntity> getAllSlaConfigs() {
        return slaConfigRepository.findAll();
    }

    public Optional<Integer> getSlaForPriority(String priority) {
        if (priority == null) return Optional.empty();
        return slaConfigRepository.findByPriority(priority)
                .map(BugSlaConfigEntity::getMaxResolutionHours);
    }

    public BugSlaConfigEntity createSla(String priority, int hours) {
        if (slaConfigRepository.findByPriority(priority).isPresent()) {
            throw new IllegalArgumentException("Priority already exists: " + priority);
        }
        BugSlaConfigEntity config = new BugSlaConfigEntity();
        config.setPriority(priority);
        config.setMaxResolutionHours(hours);
        return slaConfigRepository.save(config);
    }

    public BugSlaConfigEntity updateSla(String priority, int hours) {
        BugSlaConfigEntity config = slaConfigRepository.findByPriority(priority)
                .orElseThrow(() -> new IllegalArgumentException("Unknown priority: " + priority));
        config.setMaxResolutionHours(hours);
        return slaConfigRepository.save(config);
    }

    public void deleteSla(String priority) {
        BugSlaConfigEntity config = slaConfigRepository.findByPriority(priority)
                .orElseThrow(() -> new IllegalArgumentException("Unknown priority: " + priority));
        slaConfigRepository.delete(config);
    }

    /**
     * Checks if a bug has breached its SLA.
     * SLA is measured from jiraCreatedAt to now (if open) or doneAt (if done).
     */
    public boolean checkSlaBreach(JiraIssueEntity bug) {
        if (bug.getPriority() == null || bug.getJiraCreatedAt() == null) return false;

        Optional<Integer> slaHours = getSlaForPriority(bug.getPriority());
        if (slaHours.isEmpty()) return false;

        long resolutionHours = getResolutionTimeHours(bug);
        return resolutionHours > slaHours.get();
    }

    /**
     * Returns the resolution time in hours for a bug.
     * If done, uses doneAt. If open, uses current time.
     */
    public long getResolutionTimeHours(JiraIssueEntity bug) {
        if (bug.getJiraCreatedAt() == null) return 0;

        OffsetDateTime end;
        if (workflowConfigService.isDone(bug.getStatus(), bug.getIssueType()) && bug.getDoneAt() != null) {
            end = bug.getDoneAt();
        } else {
            end = OffsetDateTime.now();
        }

        return ChronoUnit.HOURS.between(bug.getJiraCreatedAt(), end);
    }

    /**
     * Checks if a bug is stale (no updates for more than 14 days).
     * Only applies to non-done bugs.
     */
    public boolean checkStale(JiraIssueEntity bug) {
        if (workflowConfigService.isDone(bug.getStatus(), bug.getIssueType())) return false;
        if (bug.getJiraUpdatedAt() == null) return false;

        long daysSinceUpdate = ChronoUnit.DAYS.between(bug.getJiraUpdatedAt(), OffsetDateTime.now());
        return daysSinceUpdate > STALE_THRESHOLD_DAYS;
    }

    /**
     * Returns the number of days since the bug was last updated.
     */
    public long getDaysSinceUpdate(JiraIssueEntity bug) {
        if (bug.getJiraUpdatedAt() == null) return 0;
        return ChronoUnit.DAYS.between(bug.getJiraUpdatedAt(), OffsetDateTime.now());
    }
}

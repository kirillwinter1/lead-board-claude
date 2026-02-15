package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.metrics.entity.FlagChangelogEntity;
import com.leadboard.metrics.repository.FlagChangelogRepository;
import com.leadboard.sync.JiraIssueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FlagChangelogService {

    private static final Logger log = LoggerFactory.getLogger(FlagChangelogService.class);

    private final FlagChangelogRepository repository;
    private final WorkCalendarService workCalendarService;

    public FlagChangelogService(FlagChangelogRepository repository,
                                WorkCalendarService workCalendarService) {
        this.repository = repository;
        this.workCalendarService = workCalendarService;
    }

    /**
     * Detects flag change and records to changelog.
     * Called from SyncService during issue synchronization.
     */
    @Transactional
    public void detectAndRecordFlagChange(JiraIssueEntity existing, JiraIssueEntity updated) {
        boolean wasFlagged = existing != null && Boolean.TRUE.equals(existing.getFlagged());
        boolean nowFlagged = Boolean.TRUE.equals(updated.getFlagged());

        if (wasFlagged == nowFlagged) {
            return;
        }

        if (!wasFlagged && nowFlagged) {
            // Issue was flagged — create new open entry
            FlagChangelogEntity entry = new FlagChangelogEntity();
            entry.setIssueKey(updated.getIssueKey());
            entry.setFlaggedAt(OffsetDateTime.now());
            repository.save(entry);
            log.debug("Recorded flag ON for {}", updated.getIssueKey());
        } else {
            // Issue was unflagged — close open entry
            Optional<FlagChangelogEntity> openEntry = repository.findOpenByIssueKey(updated.getIssueKey());
            if (openEntry.isPresent()) {
                FlagChangelogEntity entry = openEntry.get();
                entry.setUnflaggedAt(OffsetDateTime.now());
                repository.save(entry);
                log.debug("Recorded flag OFF for {}", updated.getIssueKey());
            } else {
                log.warn("No open flag entry found for {} when unflagging", updated.getIssueKey());
            }
        }
    }

    /**
     * Calculates the number of working days an issue was flagged within [from, to].
     */
    public int calculateFlaggedWorkdays(String issueKey, LocalDate from, LocalDate to) {
        List<FlagChangelogEntity> entries = repository.findByIssueKey(issueKey);
        if (entries.isEmpty()) {
            return 0;
        }

        int totalFlaggedWorkdays = 0;

        for (FlagChangelogEntity entry : entries) {
            LocalDate flagStart = entry.getFlaggedAt().toLocalDate();
            LocalDate flagEnd = entry.getUnflaggedAt() != null
                    ? entry.getUnflaggedAt().toLocalDate()
                    : LocalDate.now();

            // Intersect [flagStart, flagEnd] with [from, to]
            LocalDate overlapStart = flagStart.isBefore(from) ? from : flagStart;
            LocalDate overlapEnd = flagEnd.isAfter(to) ? to : flagEnd;

            if (!overlapStart.isAfter(overlapEnd)) {
                totalFlaggedWorkdays += workCalendarService.countWorkdays(overlapStart, overlapEnd);
            }
        }

        return totalFlaggedWorkdays;
    }
}

package com.leadboard.status;

import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * F81 — builds an issue's chronological status journey (status + time spent in each)
 * from {@code status_changelog}, for the tooltip on the "days in status" badge.
 */
@Service
public class StatusHistoryService {

    private final JiraIssueRepository issueRepository;
    private final StatusChangelogRepository changelogRepository;

    public StatusHistoryService(JiraIssueRepository issueRepository,
                                StatusChangelogRepository changelogRepository) {
        this.issueRepository = issueRepository;
        this.changelogRepository = changelogRepository;
    }

    /** Returns the status journey for an issue, or empty if the issue is unknown. */
    @Transactional(readOnly = true)
    public Optional<StatusHistoryResponse> getHistory(String issueKey) {
        return issueRepository.findByIssueKey(issueKey).map(issue -> {
            List<StatusChangelogEntity> transitions =
                    changelogRepository.findByIssueKeyOrderByTransitionedAtAsc(issueKey);
            return buildHistory(issue, transitions, OffsetDateTime.now());
        });
    }

    /**
     * Pure builder (package-visible for tests): reconstruct the chronological path.
     * <ul>
     *   <li>Start segment = {@code fromStatus} of the first transition, spanning
     *       {@code jira_created_at → first transition}.</li>
     *   <li>Each transition enters its {@code toStatus}; the segment runs until the next
     *       transition (or {@code now} for the current status).</li>
     *   <li>No transitions → a single current segment from creation to now.</li>
     * </ul>
     * Note: if the first transition has a null {@code fromStatus} the start segment is
     * skipped, so {@code totalSeconds} may be less than {@code now - jira_created_at}.
     */
    StatusHistoryResponse buildHistory(JiraIssueEntity issue,
                                       List<StatusChangelogEntity> transitions,
                                       OffsetDateTime now) {
        String currentStatus = issue.getStatus();
        OffsetDateTime created = issue.getJiraCreatedAt();
        List<StatusHistoryResponse.Segment> segments = new ArrayList<>();

        if (transitions == null || transitions.isEmpty()) {
            long dur = created != null ? secondsBetween(created, now) : 0;
            segments.add(new StatusHistoryResponse.Segment(currentStatus, dur, true));
            return new StatusHistoryResponse(issue.getIssueKey(), currentStatus, dur, segments);
        }

        // Start segment: the status the issue was created in (fromStatus of first transition).
        StatusChangelogEntity first = transitions.get(0);
        if (first.getFromStatus() != null) {
            OffsetDateTime startAnchor = created != null ? created : first.getTransitionedAt();
            segments.add(new StatusHistoryResponse.Segment(
                    first.getFromStatus(),
                    secondsBetween(startAnchor, first.getTransitionedAt()),
                    false));
        }

        // Each transition enters toStatus until the next transition (or now for the last).
        for (int i = 0; i < transitions.size(); i++) {
            StatusChangelogEntity t = transitions.get(i);
            boolean isLast = i == transitions.size() - 1;
            OffsetDateTime end = isLast ? now : transitions.get(i + 1).getTransitionedAt();
            // The current segment uses the authoritative status name (jira_issues.status) so it
            // matches the badge even under localization mismatch ("Planned" vs "Запланировано").
            String status = isLast ? currentStatus : t.getToStatus();
            segments.add(new StatusHistoryResponse.Segment(
                    status,
                    secondsBetween(t.getTransitionedAt(), end),
                    isLast));
        }

        long total = segments.stream()
                .mapToLong(StatusHistoryResponse.Segment::durationSeconds)
                .sum();
        return new StatusHistoryResponse(issue.getIssueKey(), currentStatus, total, segments);
    }

    /** Non-negative whole seconds between two instants (clamps clock/data skew to 0). */
    private long secondsBetween(OffsetDateTime from, OffsetDateTime to) {
        return Math.max(0, ChronoUnit.SECONDS.between(from, to));
    }
}

package com.leadboard.status;

import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StatusHistoryServiceTest {

    private final JiraIssueRepository issueRepository = mock(JiraIssueRepository.class);
    private final StatusChangelogRepository changelogRepository = mock(StatusChangelogRepository.class);
    private final StatusHistoryService service = new StatusHistoryService(issueRepository, changelogRepository);

    private static final OffsetDateTime BASE = OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final long DAY = 86400;

    private JiraIssueEntity issue(String key, String status, OffsetDateTime created) {
        JiraIssueEntity e = new JiraIssueEntity();
        e.setIssueKey(key);
        e.setStatus(status);
        e.setJiraCreatedAt(created);
        return e;
    }

    private StatusChangelogEntity transition(String from, String to, OffsetDateTime at) {
        StatusChangelogEntity e = new StatusChangelogEntity();
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setTransitionedAt(at);
        return e;
    }

    @Test
    void buildsLinearJourneyWithStartSegmentAndCurrent() {
        JiraIssueEntity issue = issue("LB-1", "Development", BASE);
        List<StatusChangelogEntity> transitions = List.of(
                transition("New", "Analysis", BASE.plusDays(1)),        // 1 day in New
                transition("Analysis", "Development", BASE.plusDays(3)) // 2 days in Analysis
        );
        OffsetDateTime now = BASE.plusDays(8); // 5 days in Development (current)

        StatusHistoryResponse r = service.buildHistory(issue, transitions, now);

        assertEquals("LB-1", r.issueKey());
        assertEquals("Development", r.currentStatus());
        assertEquals(3, r.segments().size());

        assertEquals("New", r.segments().get(0).status());
        assertEquals(1 * DAY, r.segments().get(0).durationSeconds());
        assertFalse(r.segments().get(0).current());

        assertEquals("Analysis", r.segments().get(1).status());
        assertEquals(2 * DAY, r.segments().get(1).durationSeconds());

        assertEquals("Development", r.segments().get(2).status());
        assertEquals(5 * DAY, r.segments().get(2).durationSeconds());
        assertTrue(r.segments().get(2).current(), "last segment is the current status");

        assertEquals(8 * DAY, r.totalSeconds());
    }

    @Test
    void noTransitionsYieldsSingleCurrentSegmentFromCreation() {
        JiraIssueEntity issue = issue("LB-2", "New", BASE);
        OffsetDateTime now = BASE.plusDays(4);

        StatusHistoryResponse r = service.buildHistory(issue, List.of(), now);

        assertEquals(1, r.segments().size());
        assertEquals("New", r.segments().get(0).status());
        assertEquals(4 * DAY, r.segments().get(0).durationSeconds());
        assertTrue(r.segments().get(0).current());
        assertEquals(4 * DAY, r.totalSeconds());
    }

    @Test
    void reopenShowsRepeatedStatusChronologically() {
        JiraIssueEntity issue = issue("LB-3", "Done", BASE);
        List<StatusChangelogEntity> transitions = List.of(
                transition("New", "Development", BASE.plusDays(1)),
                transition("Development", "Done", BASE.plusDays(3)),
                transition("Done", "Development", BASE.plusDays(4)), // reopened
                transition("Development", "Done", BASE.plusDays(6))
        );
        OffsetDateTime now = BASE.plusDays(9);

        StatusHistoryResponse r = service.buildHistory(issue, transitions, now);

        // New + 4 transition targets = 5 segments, with Development and Done appearing twice
        assertEquals(5, r.segments().size());
        assertEquals("New", r.segments().get(0).status());
        assertEquals("Development", r.segments().get(1).status());
        assertEquals("Done", r.segments().get(2).status());
        assertEquals("Development", r.segments().get(3).status());
        assertEquals("Done", r.segments().get(4).status());
        assertTrue(r.segments().get(4).current());
    }

    @Test
    void nullFromStatusOnFirstTransitionSkipsStartSegment() {
        JiraIssueEntity issue = issue("LB-4", "Analysis", BASE);
        List<StatusChangelogEntity> transitions = List.of(
                transition(null, "Analysis", BASE.plusDays(1))
        );
        OffsetDateTime now = BASE.plusDays(3);

        StatusHistoryResponse r = service.buildHistory(issue, transitions, now);

        assertEquals(1, r.segments().size());
        assertEquals("Analysis", r.segments().get(0).status());
        assertTrue(r.segments().get(0).current());
    }

    @Test
    void clampsNegativeDurationsToZero() {
        JiraIssueEntity issue = issue("LB-5", "Development", BASE.plusDays(5));
        // transition timestamp BEFORE created (data skew)
        List<StatusChangelogEntity> transitions = List.of(
                transition("New", "Development", BASE.plusDays(2))
        );
        OffsetDateTime now = BASE.plusDays(1); // now before the transition

        StatusHistoryResponse r = service.buildHistory(issue, transitions, now);

        assertTrue(r.segments().stream().allMatch(s -> s.durationSeconds() >= 0));
        assertEquals(0, r.totalSeconds());
    }

    @Test
    void getHistoryReturnsEmptyForUnknownIssue() {
        when(issueRepository.findByIssueKey("NOPE")).thenReturn(Optional.empty());
        assertTrue(service.getHistory("NOPE").isEmpty());
    }

    @Test
    void getHistoryLoadsTransitionsForKnownIssue() {
        JiraIssueEntity issue = issue("LB-6", "New", BASE);
        when(issueRepository.findByIssueKey("LB-6")).thenReturn(Optional.of(issue));
        when(changelogRepository.findByIssueKeyOrderByTransitionedAtAsc("LB-6")).thenReturn(List.of());

        Optional<StatusHistoryResponse> r = service.getHistory("LB-6");

        assertTrue(r.isPresent());
        assertEquals("LB-6", r.get().issueKey());
        verify(changelogRepository).findByIssueKeyOrderByTransitionedAtAsc("LB-6");
    }
}

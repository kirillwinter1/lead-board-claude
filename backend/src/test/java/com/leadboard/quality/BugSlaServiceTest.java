package com.leadboard.quality;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BugSlaServiceTest {

    @Mock
    private BugSlaConfigRepository slaConfigRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    private BugSlaService bugSlaService;

    @BeforeEach
    void setUp() {
        lenient().when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        lenient().when(workflowConfigService.isDone(eq("Done"), anyString())).thenReturn(true);

        bugSlaService = new BugSlaService(slaConfigRepository, workflowConfigService);
    }

    private JiraIssueEntity createBug(String key, String status, String priority, OffsetDateTime createdAt) {
        JiraIssueEntity bug = new JiraIssueEntity();
        bug.setIssueKey(key);
        bug.setIssueType("Bug");
        bug.setStatus(status);
        bug.setPriority(priority);
        bug.setJiraCreatedAt(createdAt);
        bug.setJiraUpdatedAt(createdAt);
        bug.setSummary("Test bug");
        return bug;
    }

    @Nested
    class CheckSlaBreach {

        @Test
        void shouldDetectBreach_whenExceedsSla() {
            JiraIssueEntity bug = createBug("BUG-1", "Open", "High",
                    OffsetDateTime.now().minusHours(100));

            BugSlaConfigEntity sla = new BugSlaConfigEntity();
            sla.setPriority("High");
            sla.setMaxResolutionHours(72);
            when(slaConfigRepository.findByPriority("High")).thenReturn(Optional.of(sla));

            assertTrue(bugSlaService.checkSlaBreach(bug));
        }

        @Test
        void shouldNotDetectBreach_whenWithinSla() {
            JiraIssueEntity bug = createBug("BUG-2", "Open", "High",
                    OffsetDateTime.now().minusHours(50));

            BugSlaConfigEntity sla = new BugSlaConfigEntity();
            sla.setPriority("High");
            sla.setMaxResolutionHours(72);
            when(slaConfigRepository.findByPriority("High")).thenReturn(Optional.of(sla));

            assertFalse(bugSlaService.checkSlaBreach(bug));
        }

        @Test
        void shouldNotDetectBreach_whenNoPriorityConfigured() {
            JiraIssueEntity bug = createBug("BUG-3", "Open", "Unknown",
                    OffsetDateTime.now().minusHours(1000));

            when(slaConfigRepository.findByPriority("Unknown")).thenReturn(Optional.empty());

            assertFalse(bugSlaService.checkSlaBreach(bug));
        }

        @Test
        void shouldNotDetectBreach_whenNoPriority() {
            JiraIssueEntity bug = createBug("BUG-4", "Open", null,
                    OffsetDateTime.now().minusHours(1000));

            assertFalse(bugSlaService.checkSlaBreach(bug));
        }
    }

    @Nested
    class CheckStale {

        @Test
        void shouldDetectStale_whenNoUpdatesForMoreThan14Days() {
            JiraIssueEntity bug = createBug("BUG-5", "Open", "Medium",
                    OffsetDateTime.now().minusDays(30));
            bug.setJiraUpdatedAt(OffsetDateTime.now().minusDays(20));

            assertTrue(bugSlaService.checkStale(bug));
        }

        @Test
        void shouldNotDetectStale_whenRecentlyUpdated() {
            JiraIssueEntity bug = createBug("BUG-6", "Open", "Medium",
                    OffsetDateTime.now().minusDays(30));
            bug.setJiraUpdatedAt(OffsetDateTime.now().minusDays(5));

            assertFalse(bugSlaService.checkStale(bug));
        }

        @Test
        void shouldNotDetectStale_whenBugIsDone() {
            JiraIssueEntity bug = createBug("BUG-7", "Done", "Medium",
                    OffsetDateTime.now().minusDays(30));
            bug.setJiraUpdatedAt(OffsetDateTime.now().minusDays(20));

            assertFalse(bugSlaService.checkStale(bug));
        }
    }

    @Nested
    class GetResolutionTimeHours {

        @Test
        void shouldCalculateTimeForOpenBug() {
            JiraIssueEntity bug = createBug("BUG-8", "Open", "High",
                    OffsetDateTime.now().minusHours(48));

            long hours = bugSlaService.getResolutionTimeHours(bug);
            assertTrue(hours >= 47 && hours <= 49);
        }

        @Test
        void shouldCalculateTimeForDoneBug() {
            OffsetDateTime created = OffsetDateTime.now().minusHours(100);
            JiraIssueEntity bug = createBug("BUG-9", "Done", "High", created);
            bug.setDoneAt(created.plusHours(50));

            long hours = bugSlaService.getResolutionTimeHours(bug);
            assertEquals(50, hours);
        }
    }
}

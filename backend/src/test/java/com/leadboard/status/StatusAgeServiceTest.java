package com.leadboard.status;

import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.entity.IssueWorklogEntity;
import com.leadboard.metrics.entity.StatusChangelogEntity;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.metrics.repository.StatusChangelogRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusAgeServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 6, 28, 12, 0, 0, 0, ZoneOffset.UTC);

    @Mock private StatusChangelogRepository changelogRepository;
    @Mock private IssueWorklogRepository worklogRepository;
    @Mock private JiraIssueRepository issueRepository;
    @Mock private WorkflowConfigService workflowConfigService;

    @InjectMocks private StatusAgeService service;

    private JiraIssueEntity issue(String key, String type, String status, OffsetDateTime created) {
        JiraIssueEntity e = new JiraIssueEntity();
        e.setIssueKey(key);
        e.setProjectKey("PROJ");
        e.setIssueType(type);
        e.setStatus(status);
        e.setJiraCreatedAt(created);
        return e;
    }

    private StatusChangelogEntity transition(String key, String toStatus, OffsetDateTime at) {
        StatusChangelogEntity c = new StatusChangelogEntity();
        c.setIssueKey(key);
        c.setToStatus(toStatus);
        c.setTransitionedAt(at);
        return c;
    }

    private void category(String status, StatusCategory cat) {
        lenient().when(workflowConfigService.categorize(status, "Story", "PROJ")).thenReturn(cat);
        lenient().when(workflowConfigService.categorize(status, "Epic", "PROJ")).thenReturn(cat);
    }

    @Test
    void daysInStatus_fromLatestTransitionIntoCurrentStatus() {
        JiraIssueEntity s = issue("PROJ-1", "Story", "In Progress", NOW.minusDays(30));
        lenient().when(workflowConfigService.categorizeIssueType("Story", "PROJ")).thenReturn(BoardCategory.STORY);
        category("In Progress", StatusCategory.IN_PROGRESS);
        when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List.of("PROJ-1")))
                .thenReturn(List.of(
                        transition("PROJ-1", "To Do", NOW.minusDays(20)),
                        transition("PROJ-1", "In Progress", NOW.minusDays(10))));

        Map<String, StatusAge> r = service.compute(List.of(s), NOW);

        assertThat(r.get("PROJ-1").daysInStatus()).isEqualTo(10);
        assertThat(r.get("PROJ-1").level()).isEqualTo(StatusAge.WARNING); // story 7/14 -> 10d = warning
    }

    @Test
    void backlogAndDone_areAlwaysNormal_evenIfOld() {
        JiraIssueEntity backlog = issue("PROJ-1", "Story", "To Do", NOW.minusDays(100));
        JiraIssueEntity done = issue("PROJ-2", "Story", "Done", NOW.minusDays(100));
        lenient().when(workflowConfigService.categorizeIssueType("Story", "PROJ")).thenReturn(BoardCategory.STORY);
        category("To Do", StatusCategory.NEW);
        category("Done", StatusCategory.DONE);
        when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(anyList()))
                .thenReturn(List.of());

        Map<String, StatusAge> r = service.compute(List.of(backlog, done), NOW);

        assertThat(r.get("PROJ-1").level()).isEqualTo(StatusAge.NORMAL);
        assertThat(r.get("PROJ-2").level()).isEqualTo(StatusAge.NORMAL);
        // number still shown (since creation, no transitions)
        assertThat(r.get("PROJ-1").daysInStatus()).isEqualTo(100);
    }

    @Test
    void story_critical_whenOverRedThreshold() {
        JiraIssueEntity s = issue("PROJ-1", "Story", "In Progress", NOW.minusDays(20));
        lenient().when(workflowConfigService.categorizeIssueType("Story", "PROJ")).thenReturn(BoardCategory.STORY);
        category("In Progress", StatusCategory.IN_PROGRESS);
        when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(anyList()))
                .thenReturn(List.of()); // no transitions -> fall back to created (20d ago)

        Map<String, StatusAge> r = service.compute(List.of(s), NOW);

        assertThat(r.get("PROJ-1").daysInStatus()).isEqualTo(20);
        assertThat(r.get("PROJ-1").level()).isEqualTo(StatusAge.CRITICAL); // story red = 14
    }

    @Test
    void stuckEpic_whenSubtreeInactive() {
        JiraIssueEntity epic = issue("PROJ-1", "Epic", "In Progress", NOW.minusDays(40));
        lenient().when(workflowConfigService.categorizeIssueType("Epic", "PROJ")).thenReturn(BoardCategory.EPIC);
        category("In Progress", StatusCategory.IN_PROGRESS);
        when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List.of("PROJ-1")))
                .thenReturn(List.of(transition("PROJ-1", "In Progress", NOW.minusDays(5)))); // epic itself moved 5d ago
        // subtree: one story, no recent activity
        JiraIssueEntity story = issue("PROJ-2", "Story", "In Progress", NOW.minusDays(40));
        story.setParentKey("PROJ-1");
        when(issueRepository.findByParentKeyIn(List.of("PROJ-1"))).thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-2"))).thenReturn(List.of());
        when(worklogRepository.findByIssueKeyIn(List.of("PROJ-2"))).thenReturn(List.of());
        when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List.of("PROJ-2")))
                .thenReturn(List.of(transition("PROJ-2", "In Progress", NOW.minusDays(35)))); // last subtree activity 35d ago

        Map<String, StatusAge> r = service.compute(List.of(epic), NOW);

        // epic own time-in-status is only 5d (NORMAL), but subtree inactive 35d -> CRITICAL stuck
        StatusAge age = r.get("PROJ-1");
        assertThat(age.level()).isEqualTo(StatusAge.CRITICAL);
        assertThat(age.reason()).contains("завис");
    }

    @Test
    void activeEpic_withRecentSubtreeWorklog_isNotStuck() {
        JiraIssueEntity epic = issue("PROJ-1", "Epic", "In Progress", NOW.minusDays(40));
        lenient().when(workflowConfigService.categorizeIssueType("Epic", "PROJ")).thenReturn(BoardCategory.EPIC);
        category("In Progress", StatusCategory.IN_PROGRESS);
        when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List.of("PROJ-1")))
                .thenReturn(List.of(transition("PROJ-1", "In Progress", NOW.minusDays(5))));
        JiraIssueEntity sub = issue("PROJ-3", "Subtask", "In Progress", NOW.minusDays(40));
        sub.setParentKey("PROJ-2");
        JiraIssueEntity story = issue("PROJ-2", "Story", "In Progress", NOW.minusDays(40));
        story.setParentKey("PROJ-1");
        when(issueRepository.findByParentKeyIn(List.of("PROJ-1"))).thenReturn(List.of(story));
        when(issueRepository.findByParentKeyIn(List.of("PROJ-2"))).thenReturn(List.of(sub));
        IssueWorklogEntity w = new IssueWorklogEntity();
        w.setIssueKey("PROJ-3");
        w.setStartedDate(NOW.minusDays(2).toLocalDate());
        when(worklogRepository.findByIssueKeyIn(anyList())).thenReturn(List.of(w));
        when(changelogRepository.findByIssueKeyInOrderByIssueKeyAscTransitionedAtAsc(List.of("PROJ-2", "PROJ-3")))
                .thenReturn(List.of());

        Map<String, StatusAge> r = service.compute(List.of(epic), NOW);

        assertThat(r.get("PROJ-1").level()).isEqualTo(StatusAge.NORMAL); // recent worklog 2d ago
    }
}

package com.leadboard.team;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.dto.MemberProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository memberRepository;
    @Mock private JiraIssueRepository issueRepository;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private WorkCalendarService workCalendarService;

    @InjectMocks
    private MemberProfileService service;

    private TeamEntity team;
    private TeamMemberEntity member;
    private final LocalDate from = LocalDate.of(2026, 1, 15);
    private final LocalDate to = LocalDate.of(2026, 2, 14);

    @BeforeEach
    void setUp() {
        team = new TeamEntity();
        team.setId(1L);
        team.setName("Platform Team");

        member = new TeamMemberEntity();
        member.setId(10L);
        member.setTeam(team);
        member.setJiraAccountId("acc-123");
        member.setDisplayName("Алексей Петров");
        member.setRole("DEV");
        member.setGrade(Grade.SENIOR);
        member.setHoursPerDay(new BigDecimal("6.0"));
    }

    @Test
    void getMemberProfile_memberInfo() {
        setupBasicMocks(List.of(), List.of());

        MemberProfileResponse result = service.getMemberProfile(1L, 10L, from, to);

        assertEquals("Алексей Петров", result.member().displayName());
        assertEquals("DEV", result.member().role());
        assertEquals("SENIOR", result.member().grade());
        assertEquals(new BigDecimal("6.0"), result.member().hoursPerDay());
        assertEquals("Platform Team", result.member().teamName());
        assertEquals(1L, result.member().teamId());
    }

    @Test
    void getMemberProfile_completedTasks_dsrCalculation() {
        JiraIssueEntity completed = createSubtask("PROJ-100", "Task 1",
                14400L, 10800L, // 4h estimate, 3h spent
                OffsetDateTime.of(2026, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 2, 7, 10, 0, 0, 0, ZoneOffset.UTC));

        setupBasicMocks(List.of(completed), List.of());

        MemberProfileResponse result = service.getMemberProfile(1L, 10L, from, to);

        assertEquals(1, result.completedTasks().size());
        var task = result.completedTasks().get(0);
        assertEquals("PROJ-100", task.key());
        assertEquals(new BigDecimal("4.0"), task.estimateH());
        assertEquals(new BigDecimal("3.0"), task.spentH());
        assertEquals(new BigDecimal("0.75"), task.dsr()); // 3/4 = 0.75
        assertEquals(LocalDate.of(2026, 2, 7), task.doneDate());
    }

    @Test
    void getMemberProfile_activeAndUpcomingFiltering() {
        JiraIssueEntity activeIssue = createSubtask("PROJ-200", "Active task",
                28800L, 7200L, null, null);
        activeIssue.setStatus("In Progress");

        JiraIssueEntity upcomingIssue = createSubtask("PROJ-201", "Upcoming task",
                14400L, 0L, null, null);
        upcomingIssue.setStatus("To Do");

        JiraIssueEntity doneIssue = createSubtask("PROJ-202", "Done task",
                14400L, 14400L,
                OffsetDateTime.of(2026, 2, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC));

        setupBasicMocks(List.of(), List.of(activeIssue, upcomingIssue, doneIssue));

        when(workflowConfigService.categorize("In Progress", "Sub-task"))
                .thenReturn(StatusCategory.IN_PROGRESS);
        when(workflowConfigService.categorize("To Do", "Sub-task"))
                .thenReturn(StatusCategory.NEW);

        MemberProfileResponse result = service.getMemberProfile(1L, 10L, from, to);

        assertEquals(1, result.activeTasks().size());
        assertEquals("PROJ-200", result.activeTasks().get(0).key());
        assertEquals("In Progress", result.activeTasks().get(0).status());

        assertEquals(1, result.upcomingTasks().size());
        assertEquals("PROJ-201", result.upcomingTasks().get(0).key());
    }

    @Test
    void getMemberProfile_weeklyTrend() {
        JiraIssueEntity t1 = createSubtask("PROJ-300", "W1 task",
                14400L, 10800L, // 4h, 3h
                OffsetDateTime.of(2026, 2, 3, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC));

        JiraIssueEntity t2 = createSubtask("PROJ-301", "W1 task 2",
                7200L, 7200L, // 2h, 2h
                OffsetDateTime.of(2026, 2, 4, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 2, 6, 10, 0, 0, 0, ZoneOffset.UTC));

        setupBasicMocks(List.of(t1, t2), List.of());

        MemberProfileResponse result = service.getMemberProfile(1L, 10L, from, to);

        assertNotNull(result.weeklyTrend());
        assertEquals(8, result.weeklyTrend().size());
        // At least one week should have tasks
        assertTrue(result.weeklyTrend().stream().anyMatch(w -> w.tasksCompleted() > 0));
    }

    @Test
    void getMemberProfile_summary() {
        JiraIssueEntity t1 = createSubtask("PROJ-400", "Task 1",
                28800L, 21600L, // 8h, 6h
                OffsetDateTime.of(2026, 2, 3, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC)); // 2 days cycle

        JiraIssueEntity t2 = createSubtask("PROJ-401", "Task 2",
                14400L, 18000L, // 4h, 5h
                OffsetDateTime.of(2026, 2, 6, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 2, 7, 10, 0, 0, 0, ZoneOffset.UTC)); // 1 day cycle

        setupBasicMocks(List.of(t1, t2), List.of());
        when(workCalendarService.countWorkdays(from, to)).thenReturn(22);

        MemberProfileResponse result = service.getMemberProfile(1L, 10L, from, to);

        assertEquals(2, result.summary().completedCount());
        assertEquals(new BigDecimal("11.0"), result.summary().totalSpentH()); // 6 + 5
        assertEquals(new BigDecimal("12.0"), result.summary().totalEstimateH()); // 8 + 4
        // avgDsr = 39600 / 43200 = 0.92
        assertEquals(new BigDecimal("0.92"), result.summary().avgDsr());
        // avgCycleTime = (2 + 1) / 2 = 1.5
        assertEquals(new BigDecimal("1.5"), result.summary().avgCycleTimeDays());
    }

    @Test
    void getMemberProfile_emptyData() {
        setupBasicMocks(List.of(), List.of());

        MemberProfileResponse result = service.getMemberProfile(1L, 10L, from, to);

        assertTrue(result.completedTasks().isEmpty());
        assertTrue(result.activeTasks().isEmpty());
        assertTrue(result.upcomingTasks().isEmpty());
        assertEquals(0, result.summary().completedCount());
        assertEquals(BigDecimal.ZERO, result.summary().avgDsr());
        assertEquals(BigDecimal.ZERO, result.summary().totalSpentH());
    }

    @Test
    void getMemberProfile_teamNotFound() {
        when(teamRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        assertThrows(TeamService.TeamNotFoundException.class,
                () -> service.getMemberProfile(999L, 10L, from, to));
    }

    @Test
    void getMemberProfile_memberNotFound() {
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByIdAndTeamIdAndActiveTrue(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(TeamService.TeamMemberNotFoundException.class,
                () -> service.getMemberProfile(1L, 999L, from, to));
    }

    @Test
    void getMemberProfile_epicInfoResolution() {
        JiraIssueEntity subtask = createSubtask("PROJ-500", "Subtask",
                7200L, 3600L,
                OffsetDateTime.of(2026, 2, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 2, 2, 10, 0, 0, 0, ZoneOffset.UTC));
        subtask.setParentKey("PROJ-50");

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("PROJ-50");
        story.setIssueType("Story");
        story.setParentKey("PROJ-10");
        story.setSummary("Parent Story");

        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("PROJ-10");
        epic.setIssueType("Epic");
        epic.setSummary("The Epic");

        setupBasicMocks(List.of(subtask), List.of());
        when(issueRepository.findByIssueKey("PROJ-50")).thenReturn(Optional.of(story));
        when(issueRepository.findByIssueKey("PROJ-10")).thenReturn(Optional.of(epic));
        when(workflowConfigService.isEpic("Story")).thenReturn(false);

        MemberProfileResponse result = service.getMemberProfile(1L, 10L, from, to);

        assertEquals(1, result.completedTasks().size());
        assertEquals("PROJ-10", result.completedTasks().get(0).epicKey());
        assertEquals("The Epic", result.completedTasks().get(0).epicSummary());
    }

    // ==================== Helpers ====================

    private void setupBasicMocks(List<JiraIssueEntity> completed, List<JiraIssueEntity> allSubtasks) {
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByIdAndTeamIdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(member));
        when(issueRepository.findCompletedSubtasksByAssigneeInPeriod(
                eq("acc-123"), eq(1L), any(), any())).thenReturn(completed);
        when(issueRepository.findSubtasksByAssigneeAndTeam("acc-123", 1L)).thenReturn(allSubtasks);
        when(workCalendarService.countWorkdays(from, to)).thenReturn(22);
    }

    private JiraIssueEntity createSubtask(String key, String summary,
                                           Long estimateSec, Long spentSec,
                                           OffsetDateTime startedAt, OffsetDateTime doneAt) {
        JiraIssueEntity issue = new JiraIssueEntity();
        issue.setIssueKey(key);
        issue.setSummary(summary);
        issue.setIssueType("Sub-task");
        issue.setBoardCategory("SUBTASK");
        issue.setTeamId(1L);
        issue.setAssigneeAccountId("acc-123");
        issue.setOriginalEstimateSeconds(estimateSec);
        issue.setTimeSpentSeconds(spentSec);
        issue.setStartedAt(startedAt);
        issue.setDoneAt(doneAt);
        issue.setStatus(doneAt != null ? "Done" : "To Do");
        return issue;
    }
}

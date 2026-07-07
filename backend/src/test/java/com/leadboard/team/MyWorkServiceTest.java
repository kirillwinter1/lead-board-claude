package com.leadboard.team;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.calendar.dto.HolidayDto;
import com.leadboard.calendar.dto.WorkdaysResponseDto;
import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.dto.MyWorkResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyWorkServiceTest {

    @Mock private TeamMemberRepository memberRepository;
    @Mock private JiraIssueRepository issueRepository;
    @Mock private IssueWorklogRepository worklogRepository;
    @Mock private MemberAbsenceRepository absenceRepository;
    @Mock private AbsenceService absenceService;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private WorkCalendarService workCalendarService;
    @Mock private JiraConfigResolver jiraConfigResolver;

    private MyWorkService service;

    private final LocalDate from = LocalDate.of(2026, 1, 15);
    private final LocalDate to = LocalDate.of(2026, 2, 14);

    @BeforeEach
    void setUp() {
        MemberAnalyticsService analytics = new MemberAnalyticsService(issueRepository, workflowConfigService, workCalendarService);
        service = new MyWorkService(memberRepository, issueRepository, worklogRepository, absenceRepository,
                absenceService, workflowConfigService, workCalendarService, analytics, jiraConfigResolver);

        // Default empty calendar so tests unrelated to the worklog calendar don't NPE;
        // tests that care about the calendar override with exact-range stubs (see below).
        lenient().when(workCalendarService.getWorkdaysInfo(any(), any())).thenReturn(
                new WorkdaysResponseDto(LocalDate.MIN, LocalDate.MIN, "RU", 0, 0, 0, 0, List.of(), List.of()));
    }

    @Test
    void returnsHasMembershipFalseWhenNoTeamMember() {
        when(memberRepository.findAllByJiraAccountIdAndActiveTrue("acc-1")).thenReturn(List.of());

        MyWorkResponse r = service.getMyWork("acc-1", from, to, null);

        assertFalse(r.hasMembership());
        assertNull(r.member());
        assertTrue(r.activeTasks().isEmpty());
        assertTrue(r.upcomingAssigned().isEmpty());
        assertTrue(r.teamQueue().isEmpty());
        assertTrue(r.worklogCalendar().isEmpty());
        assertNull(r.analytics());
    }

    @Test
    void aggregatesActiveAndUpcomingAcrossTwoTeams() {
        TeamEntity teamAlpha = createTeam(1L, "Alpha", "#111111");
        TeamEntity teamBeta = createTeam(2L, "Beta", "#222222");
        TeamMemberEntity memberAlpha = createMember(10L, teamAlpha);
        TeamMemberEntity memberBeta = createMember(20L, teamBeta);

        when(memberRepository.findAllByJiraAccountIdAndActiveTrue("acc-1"))
                .thenReturn(List.of(memberAlpha, memberBeta));

        JiraIssueEntity activeSubtask = createSubtask("SUB-1", "Active work", "TYPE_X", "STATUS_ACTIVE",
                14400L, 7200L, null);
        JiraIssueEntity upcomingSubtask = createSubtask("SUB-2", "Upcoming work", "TYPE_X", "STATUS_NEW",
                7200L, 0L, null);

        when(issueRepository.findSubtasksByAssigneeAndTeam("acc-1", 1L)).thenReturn(List.of(activeSubtask));
        when(issueRepository.findSubtasksByAssigneeAndTeam("acc-1", 2L)).thenReturn(List.of(upcomingSubtask));

        when(workflowConfigService.categorize("STATUS_ACTIVE", "TYPE_X")).thenReturn(StatusCategory.IN_PROGRESS);
        when(workflowConfigService.categorize("STATUS_NEW", "TYPE_X")).thenReturn(StatusCategory.NEW);

        when(jiraConfigResolver.getBaseUrl()).thenReturn("https://jira.example.com");

        MyWorkResponse r = service.getMyWork("acc-1", from, to, null);

        assertEquals(1, r.activeTasks().size());
        assertEquals("Alpha", r.activeTasks().get(0).teamName());
        assertEquals("https://jira.example.com/browse/SUB-1", r.activeTasks().get(0).jiraUrl());

        assertEquals(1, r.upcomingAssigned().size());
        assertEquals("Beta", r.upcomingAssigned().get(0).teamName());
    }

    @Test
    void teamIdParamFiltersTaskLists() {
        TeamEntity teamAlpha = createTeam(1L, "Alpha", "#111111");
        TeamEntity teamBeta = createTeam(2L, "Beta", "#222222");
        TeamMemberEntity memberAlpha = createMember(10L, teamAlpha);
        TeamMemberEntity memberBeta = createMember(20L, teamBeta);

        when(memberRepository.findAllByJiraAccountIdAndActiveTrue("acc-1"))
                .thenReturn(List.of(memberAlpha, memberBeta));

        JiraIssueEntity activeSubtask = createSubtask("SUB-1", "Active work", "TYPE_X", "STATUS_ACTIVE",
                14400L, 7200L, null);

        when(issueRepository.findSubtasksByAssigneeAndTeam("acc-1", 1L)).thenReturn(List.of(activeSubtask));
        when(workflowConfigService.categorize("STATUS_ACTIVE", "TYPE_X")).thenReturn(StatusCategory.IN_PROGRESS);
        when(jiraConfigResolver.getBaseUrl()).thenReturn("https://jira.example.com");

        MyWorkResponse r = service.getMyWork("acc-1", from, to, 1L);

        assertEquals(1, r.activeTasks().size());
        assertEquals("SUB-1", r.activeTasks().get(0).key());
        assertTrue(r.upcomingAssigned().isEmpty());
    }

    @Test
    void skipsDoneSubtasks() {
        TeamEntity teamAlpha = createTeam(1L, "Alpha", "#111111");
        TeamMemberEntity memberAlpha = createMember(10L, teamAlpha);

        when(memberRepository.findAllByJiraAccountIdAndActiveTrue("acc-1")).thenReturn(List.of(memberAlpha));

        JiraIssueEntity doneSubtask = createSubtask("SUB-9", "Done work", "TYPE_X", "STATUS_DONE",
                14400L, 14400L, java.time.OffsetDateTime.now());

        when(issueRepository.findSubtasksByAssigneeAndTeam("acc-1", 1L)).thenReturn(List.of(doneSubtask));

        MyWorkResponse r = service.getMyWork("acc-1", from, to, null);

        assertTrue(r.activeTasks().isEmpty());
        assertTrue(r.upcomingAssigned().isEmpty());
    }

    @Test
    void teamQueueFiltersByMyPhaseAndOrdersLikeBoard() {
        TeamEntity team = createTeam(1L, "Alpha", "#111111");
        TeamMemberEntity member = createMember(10L, team);

        when(memberRepository.findAllByJiraAccountIdAndActiveTrue("acc-1")).thenReturn(List.of(member));

        JiraIssueEntity subA = createSubtask("SUB-A", "Subtask A", "TYPE_X", "STATUS_NEW", 3600L, 0L, null);
        subA.setParentKey("STORY-1");
        subA.setWorkflowRole("DEV_X");

        JiraIssueEntity subB = createSubtask("SUB-B", "Subtask B", "TYPE_X", "STATUS_NEW", 7200L, 0L, null);
        subB.setParentKey("STORY-2");
        subB.setWorkflowRole("DEV_X");

        JiraIssueEntity subC = createSubtask("SUB-C", "Subtask C", "TYPE_X", "STATUS_NEW", 1800L, 0L, null);
        subC.setParentKey("STORY-1");
        subC.setWorkflowRole("QA_X"); // different phase — must be filtered out

        when(issueRepository.findUnassignedSubtasksByTeam(1L)).thenReturn(List.of(subA, subB, subC));

        JiraIssueEntity story1 = createStory("STORY-1", "Story One", "STORY_TYPE", "STATUS_OPEN", 1);
        JiraIssueEntity story2 = createStory("STORY-2", "Story Two", "STORY_TYPE", "STATUS_OPEN", 5);

        when(issueRepository.findByIssueKeyIn(anyList())).thenReturn(List.of(story1, story2));
        when(workflowConfigService.isDone("STATUS_OPEN", "STORY_TYPE")).thenReturn(false);
        when(jiraConfigResolver.getBaseUrl()).thenReturn("https://jira.example.com");

        MyWorkResponse r = service.getMyWork("acc-1", from, to, null);

        List<MyWorkResponse.QueueStory> queue = r.teamQueue();
        assertEquals(2, queue.size());

        assertEquals("STORY-1", queue.get(0).key());
        assertEquals(1, queue.get(0).myPhaseSubtasks());
        assertEquals(0, new BigDecimal("1.0").compareTo(queue.get(0).myPhaseEstimateH()));

        assertEquals("STORY-2", queue.get(1).key());
        assertEquals(1, queue.get(1).myPhaseSubtasks());
        assertEquals(0, new BigDecimal("2.0").compareTo(queue.get(1).myPhaseEstimateH()));
    }

    @Test
    void teamQueueExcludesDoneStoriesAndCapsAtTen() {
        TeamEntity team = createTeam(1L, "Alpha", "#111111");
        TeamMemberEntity member = createMember(10L, team);

        when(memberRepository.findAllByJiraAccountIdAndActiveTrue("acc-1")).thenReturn(List.of(member));

        int doneIndex = 6;
        List<JiraIssueEntity> subtasks = new java.util.ArrayList<>();
        List<JiraIssueEntity> stories = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String storyKey = "STORY-" + i;
            JiraIssueEntity sub = createSubtask("SUB-" + i, "Subtask " + i, "TYPE_X", "STATUS_NEW", 3600L, 0L, null);
            sub.setParentKey(storyKey);
            sub.setWorkflowRole("DEV_X");
            subtasks.add(sub);

            String status = (i == doneIndex) ? "STATUS_DONE" : "STATUS_OPEN";
            stories.add(createStory(storyKey, "Story " + i, "STORY_TYPE", status, i));
        }

        when(issueRepository.findUnassignedSubtasksByTeam(1L)).thenReturn(subtasks);
        when(issueRepository.findByIssueKeyIn(anyList())).thenReturn(stories);
        when(workflowConfigService.isDone("STATUS_OPEN", "STORY_TYPE")).thenReturn(false);
        when(workflowConfigService.isDone("STATUS_DONE", "STORY_TYPE")).thenReturn(true);
        when(jiraConfigResolver.getBaseUrl()).thenReturn("https://jira.example.com");

        MyWorkResponse r = service.getMyWork("acc-1", from, to, null);

        List<MyWorkResponse.QueueStory> queue = r.teamQueue();
        assertEquals(10, queue.size());
        assertTrue(queue.stream().noneMatch(q -> q.key().equals("STORY-" + doneIndex)));
    }

    @Test
    void worklogCalendarCovers4WeeksWithNormAndAbsences() {
        LocalDate today = LocalDate.of(2026, 7, 7); // Tuesday
        LocalDate calFrom = LocalDate.of(2026, 6, 15); // Monday, 3 weeks before this week's Monday
        LocalDate calTo = LocalDate.of(2026, 7, 12);   // Sunday of this week
        LocalDate holiday = LocalDate.of(2026, 7, 1);

        TeamEntity team1 = createTeam(1L, "Alpha", "#111111");
        TeamMemberEntity member = createMember(10L, team1); // hoursPerDay = 6.0

        when(memberRepository.findAllByJiraAccountIdAndActiveTrue("acc-1")).thenReturn(List.of(member));

        // Workdays = every Mon-Fri in range except the holiday.
        List<LocalDate> workdayDates = new ArrayList<>();
        LocalDate cursor = calFrom;
        while (!cursor.isAfter(calTo)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !cursor.equals(holiday)) {
                workdayDates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        WorkdaysResponseDto calendarInfo = new WorkdaysResponseDto(calFrom, calTo, "RU",
                28, workdayDates.size(), 8, 1, workdayDates, List.of(new HolidayDto(holiday, "Test Holiday")));
        when(workCalendarService.getWorkdaysInfo(calFrom, calTo)).thenReturn(calendarInfo);

        // Worklogs on 06.07 across two issues (1h + 2h).
        // Worklogs on 16.06 across two issues with non-round seconds (600s + 600s): each rounds to
        // 0.2h, but the daily total must round the raw sum (1200s -> 0.3h), not sum the rounded parts.
        when(worklogRepository.findDailyWorklogsByAuthorPerIssue("acc-1", calFrom, calTo)).thenReturn(List.of(
                new Object[]{java.sql.Date.valueOf(LocalDate.of(2026, 7, 6)), "SUB-1", 3600L},
                new Object[]{java.sql.Date.valueOf(LocalDate.of(2026, 7, 6)), "SUB-2", 7200L},
                new Object[]{java.sql.Date.valueOf(LocalDate.of(2026, 6, 16)), "SUB-3", 600L},
                new Object[]{java.sql.Date.valueOf(LocalDate.of(2026, 6, 16)), "SUB-4", 600L}
        ));

        // Vacation absence for team1 membership, 22.06-23.06.
        MemberAbsenceEntity vacation = new MemberAbsenceEntity();
        vacation.setAbsenceType(AbsenceType.VACATION);
        vacation.setStartDate(LocalDate.of(2026, 6, 22));
        vacation.setEndDate(LocalDate.of(2026, 6, 23));
        when(absenceRepository.findByMemberIdAndDateRange(10L, calFrom, calTo)).thenReturn(List.of(vacation));

        MyWorkResponse r = service.getMyWork("acc-1", from, to, null, today);

        assertEquals(28, r.worklogCalendar().size());
        assertEquals(LocalDate.of(2026, 6, 15), r.worklogCalendar().get(0).date());

        MyWorkResponse.CalendarDay julySixth = findDay(r, LocalDate.of(2026, 7, 6));
        assertEquals(0, new BigDecimal("3.0").compareTo(julySixth.loggedH()));
        assertEquals(2, julySixth.byIssue().size());
        assertEquals("WORKDAY", julySixth.dayType());

        MyWorkResponse.CalendarDay juneTwentySecond = findDay(r, LocalDate.of(2026, 6, 22));
        assertEquals("VACATION", juneTwentySecond.absenceType());
        assertEquals(0, BigDecimal.ZERO.compareTo(juneTwentySecond.normH()));

        MyWorkResponse.CalendarDay julyFirst = findDay(r, holiday);
        assertEquals("HOLIDAY", julyFirst.dayType());

        MyWorkResponse.CalendarDay juneTwentieth = findDay(r, LocalDate.of(2026, 6, 20)); // Saturday
        assertEquals("WEEKEND", juneTwentieth.dayType());

        MyWorkResponse.CalendarDay plainWorkday = findDay(r, LocalDate.of(2026, 6, 16)); // Tuesday, no absence
        assertEquals("WORKDAY", plainWorkday.dayType());
        assertNull(plainWorkday.absenceType());
        assertEquals(0, new BigDecimal("6.0").compareTo(plainWorkday.normH()));
        // Rounding: 600s + 600s = 1200s -> 0.3h at the day level (NOT 0.2 + 0.2 = 0.4).
        assertEquals(0, new BigDecimal("0.3").compareTo(plainWorkday.loggedH()));
        assertEquals(2, plainWorkday.byIssue().size());
        MyWorkResponse.DayIssue sub3 = plainWorkday.byIssue().stream()
                .filter(bi -> bi.issueKey().equals("SUB-3")).findFirst().orElseThrow();
        MyWorkResponse.DayIssue sub4 = plainWorkday.byIssue().stream()
                .filter(bi -> bi.issueKey().equals("SUB-4")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0.2").compareTo(sub3.hours()));
        assertEquals(0, new BigDecimal("0.2").compareTo(sub4.hours()));
    }

    @Test
    void analyticsBreaksDownDsrByParentTypeAndEpic() {
        TeamEntity team = createTeam(1L, "Alpha", "#111111");
        TeamMemberEntity member = createMember(10L, team);

        when(memberRepository.findAllByJiraAccountIdAndActiveTrue("acc-1")).thenReturn(List.of(member));
        when(jiraConfigResolver.getBaseUrl()).thenReturn("https://jira.example.com");

        JiraIssueEntity epic1 = createStory("EP-1", "Epic One", "EpicType", "STATUS_OPEN", null);
        JiraIssueEntity epic2 = createStory("EP-2", "Epic Two", "EpicType", "STATUS_OPEN", null);
        JiraIssueEntity storyA = createStory("STORY-A", "Story A", "TypeA", "STATUS_OPEN", null);
        storyA.setParentKey("EP-1");
        JiraIssueEntity storyB = createStory("STORY-B", "Story B", "TypeB", "STATUS_OPEN", null);
        storyB.setParentKey("EP-2");

        when(issueRepository.findByIssueKey("EP-1")).thenReturn(Optional.of(epic1));
        when(issueRepository.findByIssueKey("EP-2")).thenReturn(Optional.of(epic2));
        when(issueRepository.findByIssueKey("STORY-A")).thenReturn(Optional.of(storyA));
        when(issueRepository.findByIssueKey("STORY-B")).thenReturn(Optional.of(storyB));

        // Two subtasks under a "TypeA" story (parent under EP-1): estimate == spent -> DSR 1.00.
        JiraIssueEntity sub1 = createSubtask("SUB-1", "Sub 1", "SubtaskType", "STATUS_DONE", 3600L, 3600L,
                OffsetDateTime.parse("2026-01-20T10:00:00Z"));
        sub1.setParentKey("STORY-A");
        JiraIssueEntity sub2 = createSubtask("SUB-2", "Sub 2", "SubtaskType", "STATUS_DONE", 3600L, 3600L,
                OffsetDateTime.parse("2026-01-21T10:00:00Z"));
        sub2.setParentKey("STORY-A");
        // One subtask under a "TypeB" story (parent under EP-2): spent double the estimate -> DSR 2.00.
        JiraIssueEntity sub3 = createSubtask("SUB-3", "Sub 3", "SubtaskType", "STATUS_DONE", 3600L, 7200L,
                OffsetDateTime.parse("2026-01-22T10:00:00Z"));
        sub3.setParentKey("STORY-B");

        when(issueRepository.findCompletedSubtasksByAssigneeInPeriod(eq("acc-1"), eq(1L), any(), any()))
                .thenReturn(List.of(sub1, sub2, sub3));

        MyWorkResponse r = service.getMyWork("acc-1", from, to, null);

        List<MyWorkResponse.DsrBreakdown> byType = r.analytics().dsrByParentType();
        assertEquals(2, byType.size());
        assertEquals("TypeB", byType.get(0).key());
        assertEquals("TypeB", byType.get(0).label());
        assertEquals(0, new BigDecimal("2.00").compareTo(byType.get(0).dsr()));
        assertEquals("TypeA", byType.get(1).key());
        assertEquals(0, new BigDecimal("1.00").compareTo(byType.get(1).dsr()));
        assertEquals(2, byType.get(1).taskCount());

        List<MyWorkResponse.DsrBreakdown> byEpic = r.analytics().dsrByEpic();
        assertEquals(2, byEpic.size());
        assertTrue(byEpic.stream().anyMatch(d -> d.key().equals("EP-1") && "Epic One".equals(d.label())
                && new BigDecimal("1.00").compareTo(d.dsr()) == 0));
        assertTrue(byEpic.stream().anyMatch(d -> d.key().equals("EP-2") && "Epic Two".equals(d.label())
                && new BigDecimal("2.00").compareTo(d.dsr()) == 0));
    }

    @Test
    void analyticsIgnoresTeamIdFilter() {
        TeamEntity teamAlpha = createTeam(1L, "Alpha", "#111111");
        TeamEntity teamBeta = createTeam(2L, "Beta", "#222222");
        TeamMemberEntity memberAlpha = createMember(10L, teamAlpha);
        TeamMemberEntity memberBeta = createMember(20L, teamBeta);

        when(memberRepository.findAllByJiraAccountIdAndActiveTrue("acc-1"))
                .thenReturn(List.of(memberAlpha, memberBeta));
        when(jiraConfigResolver.getBaseUrl()).thenReturn("https://jira.example.com");

        JiraIssueEntity completedAlpha = createSubtask("SUB-A", "Alpha work", "TYPE_X", "STATUS_DONE",
                3600L, 3600L, OffsetDateTime.parse("2026-01-20T10:00:00Z"));
        completedAlpha.setTeamId(1L);
        JiraIssueEntity completedBeta = createSubtask("SUB-B", "Beta work", "TYPE_X", "STATUS_DONE",
                3600L, 3600L, OffsetDateTime.parse("2026-01-21T10:00:00Z"));
        completedBeta.setTeamId(2L);

        when(issueRepository.findCompletedSubtasksByAssigneeInPeriod(eq("acc-1"), eq(1L), any(), any()))
                .thenReturn(List.of(completedAlpha));
        when(issueRepository.findCompletedSubtasksByAssigneeInPeriod(eq("acc-1"), eq(2L), any(), any()))
                .thenReturn(List.of(completedBeta));

        // teamId=1L narrows activeTasks/upcomingAssigned/teamQueue, but analytics must still cover both teams.
        MyWorkResponse r = service.getMyWork("acc-1", from, to, 1L);

        List<MyWorkResponse.CompletedTaskWithTeam> completedTasks = r.analytics().completedTasks();
        assertEquals(2, completedTasks.size());
        assertTrue(completedTasks.stream().anyMatch(t -> t.key().equals("SUB-A") && "Alpha".equals(t.teamName())));
        assertTrue(completedTasks.stream().anyMatch(t -> t.key().equals("SUB-B") && "Beta".equals(t.teamName())));
    }

    private MyWorkResponse.CalendarDay findDay(MyWorkResponse r, LocalDate date) {
        return r.worklogCalendar().stream()
                .filter(d -> d.date().equals(date))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No calendar day for " + date));
    }

    // ==================== Helpers ====================

    private TeamEntity createTeam(Long id, String name, String color) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setName(name);
        team.setColor(color);
        return team;
    }

    private TeamMemberEntity createMember(Long id, TeamEntity team) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(id);
        member.setTeam(team);
        member.setJiraAccountId("acc-1");
        member.setDisplayName("Member " + id);
        member.setRole("DEV_X");
        member.setGrade(Grade.SENIOR);
        member.setHoursPerDay(new BigDecimal("6.0"));
        return member;
    }

    private JiraIssueEntity createStory(String key, String summary, String issueType, String status,
                                         Integer manualOrder) {
        JiraIssueEntity issue = new JiraIssueEntity();
        issue.setIssueKey(key);
        issue.setSummary(summary);
        issue.setIssueType(issueType);
        issue.setBoardCategory("STORY");
        issue.setStatus(status);
        issue.setManualOrder(manualOrder);
        return issue;
    }

    private JiraIssueEntity createSubtask(String key, String summary, String issueType, String status,
                                           Long estimateSec, Long spentSec, java.time.OffsetDateTime doneAt) {
        JiraIssueEntity issue = new JiraIssueEntity();
        issue.setIssueKey(key);
        issue.setSummary(summary);
        issue.setIssueType(issueType);
        issue.setBoardCategory("SUBTASK");
        issue.setAssigneeAccountId("acc-1");
        issue.setOriginalEstimateSeconds(estimateSec);
        issue.setTimeSpentSeconds(spentSec);
        issue.setStatus(status);
        issue.setDoneAt(doneAt);
        return issue;
    }
}

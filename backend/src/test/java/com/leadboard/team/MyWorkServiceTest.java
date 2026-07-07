package com.leadboard.team;

import com.leadboard.calendar.WorkCalendarService;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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

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
import static org.mockito.ArgumentMatchers.anyList;
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

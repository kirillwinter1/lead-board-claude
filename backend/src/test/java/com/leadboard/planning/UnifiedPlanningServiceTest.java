package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.*;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.*;
import com.leadboard.team.dto.PlanningConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnifiedPlanningServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;
    @Mock
    private TeamService teamService;
    @Mock
    private TeamMemberRepository memberRepository;
    @Mock
    private WorkCalendarService calendarService;
    @Mock
    private WorkflowConfigService workflowConfigService;
    @Mock
    private StoryDependencyService dependencyService;

    private UnifiedPlanningService service;

    private static final Long TEAM_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new UnifiedPlanningService(
                issueRepository,
                teamService,
                memberRepository,
                calendarService,
                workflowConfigService,
                dependencyService
        );

        // Default config
        when(teamService.getPlanningConfig(TEAM_ID)).thenReturn(new PlanningConfigDto(
                PlanningConfigDto.GradeCoefficients.defaults(),
                new BigDecimal("0.2"),
                PlanningConfigDto.WipLimits.defaults(),
                PlanningConfigDto.StoryDuration.defaults()
        ));

        // Calendar always returns workdays
        when(calendarService.isWorkday(any())).thenReturn(true);
        when(calendarService.addWorkdays(any(), anyInt())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            int days = invocation.getArgument(1);
            return date.plusDays(days);
        });

        // WorkflowConfigService defaults
        when(workflowConfigService.getRoleCodesInPipelineOrder()).thenReturn(List.of("SA", "DEV", "QA"));
        when(workflowConfigService.isStory("Story")).thenReturn(true);
        when(workflowConfigService.getSubtaskRole("Analysis")).thenReturn("SA");
        when(workflowConfigService.getSubtaskRole("Development")).thenReturn("DEV");
        when(workflowConfigService.getSubtaskRole("Testing")).thenReturn("QA");
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        when(workflowConfigService.isPlanningAllowed(anyString())).thenReturn(true);
    }

    @Test
    void testBasicPlanning_SingleEpicSingleStory() {
        // Given: 1 epic with 1 story, 1 SA + 1 DEV + 1 QA
        JiraIssueEntity epic = createEpic("EPIC-1", "Test Epic", new BigDecimal("80"));
        JiraIssueEntity story = createStory("STORY-1", "Test Story", "EPIC-1", new BigDecimal("50"));
        JiraIssueEntity saSubtask = createSubtask("SUB-1", "SA Task", "STORY-1", "Analysis", 8 * 3600L, 0L);
        JiraIssueEntity devSubtask = createSubtask("SUB-2", "DEV Task", "STORY-1", "Development", 16 * 3600L, 0L);
        JiraIssueEntity qaSubtask = createSubtask("SUB-3", "QA Task", "STORY-1", "Testing", 8 * 3600L, 0L);

        when(issueRepository.findEpicsByTeamOrderByManualOrder(TEAM_ID))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKeyOrderByManualOrderAsc("EPIC-1")).thenReturn(List.of(story));
        when(issueRepository.findByParentKey("STORY-1")).thenReturn(List.of(saSubtask, devSubtask, qaSubtask));

        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                createMember("sa-1", "Anna SA", "SA", Grade.MIDDLE, new BigDecimal("8")),
                createMember("dev-1", "Bob DEV", "DEV", Grade.MIDDLE, new BigDecimal("8")),
                createMember("qa-1", "Carol QA", "QA", Grade.MIDDLE, new BigDecimal("8"))
        ));

        when(dependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));

        // When
        UnifiedPlanningResult result = service.calculatePlan(TEAM_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEAM_ID, result.teamId());
        assertEquals(1, result.epics().size());

        PlannedEpic plannedEpic = result.epics().get(0);
        assertEquals("EPIC-1", plannedEpic.epicKey());
        assertEquals(1, plannedEpic.stories().size());

        PlannedStory plannedStory = plannedEpic.stories().get(0);
        assertEquals("STORY-1", plannedStory.storyKey());
        assertNotNull(plannedStory.phases().get("SA"));
        assertNotNull(plannedStory.phases().get("DEV"));
        assertNotNull(plannedStory.phases().get("QA"));

        // Verify pipeline: SA -> DEV -> QA
        assertTrue(plannedStory.phases().get("SA").endDate()
                .isBefore(plannedStory.phases().get("DEV").startDate()) ||
                plannedStory.phases().get("SA").endDate()
                        .isEqual(plannedStory.phases().get("DEV").startDate().minusDays(1)));
    }

    @Test
    void testParallelStoriesForSameRole() {
        // Given: 1 epic with 2 stories, 2 SAs (can work in parallel)
        JiraIssueEntity epic = createEpic("EPIC-1", "Test Epic", new BigDecimal("80"));
        JiraIssueEntity story1 = createStory("STORY-1", "Story 1", "EPIC-1", new BigDecimal("60"));
        JiraIssueEntity story2 = createStory("STORY-2", "Story 2", "EPIC-1", new BigDecimal("50"));
        JiraIssueEntity sa1 = createSubtask("SUB-1", "SA Task", "STORY-1", "Analysis", 8 * 3600L, 0L);
        JiraIssueEntity sa2 = createSubtask("SUB-2", "SA Task", "STORY-2", "Analysis", 8 * 3600L, 0L);

        when(issueRepository.findEpicsByTeamOrderByManualOrder(TEAM_ID))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKeyOrderByManualOrderAsc("EPIC-1")).thenReturn(List.of(story1, story2));
        when(issueRepository.findByParentKey("STORY-1")).thenReturn(List.of(sa1));
        when(issueRepository.findByParentKey("STORY-2")).thenReturn(List.of(sa2));

        // 2 SAs available
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                createMember("sa-1", "Anna SA", "SA", Grade.MIDDLE, new BigDecimal("8")),
                createMember("sa-2", "Dan SA", "SA", Grade.MIDDLE, new BigDecimal("8"))
        ));

        when(dependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));

        // When
        UnifiedPlanningResult result = service.calculatePlan(TEAM_ID);

        // Then
        assertEquals(1, result.epics().size());
        PlannedEpic plannedEpic = result.epics().get(0);
        assertEquals(2, plannedEpic.stories().size());

        PlannedStory planned1 = plannedEpic.stories().get(0);
        PlannedStory planned2 = plannedEpic.stories().get(1);

        // Both stories should start on the same day (parallel SAs)
        assertEquals(planned1.phases().get("SA").startDate(), planned2.phases().get("SA").startDate());

        // Different assignees
        assertNotEquals(
                planned1.phases().get("SA").assigneeAccountId(),
                planned2.phases().get("SA").assigneeAccountId()
        );
    }

    @Test
    void testDaySplitting() {
        // Given: 2 stories with 4h SA each, 1 SA with 8h/day capacity
        // Both should complete on same day (4h + 4h = 8h)
        JiraIssueEntity epic = createEpic("EPIC-1", "Test Epic", new BigDecimal("80"));
        JiraIssueEntity story1 = createStory("STORY-1", "Story 1", "EPIC-1", new BigDecimal("60"));
        JiraIssueEntity story2 = createStory("STORY-2", "Story 2", "EPIC-1", new BigDecimal("50"));
        JiraIssueEntity sa1 = createSubtask("SUB-1", "SA Task", "STORY-1", "Analysis", 4 * 3600L, 0L);
        JiraIssueEntity sa2 = createSubtask("SUB-2", "SA Task", "STORY-2", "Analysis", 4 * 3600L, 0L);

        when(issueRepository.findEpicsByTeamOrderByManualOrder(TEAM_ID))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKeyOrderByManualOrderAsc("EPIC-1")).thenReturn(List.of(story1, story2));
        when(issueRepository.findByParentKey("STORY-1")).thenReturn(List.of(sa1));
        when(issueRepository.findByParentKey("STORY-2")).thenReturn(List.of(sa2));

        // Only 1 SA
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                createMember("sa-1", "Anna SA", "SA", Grade.MIDDLE, new BigDecimal("8"))
        ));

        when(dependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));

        // When
        UnifiedPlanningResult result = service.calculatePlan(TEAM_ID);

        // Then
        PlannedEpic plannedEpic = result.epics().get(0);
        PlannedStory planned1 = plannedEpic.stories().get(0);
        PlannedStory planned2 = plannedEpic.stories().get(1);

        // With risk buffer (20%), 4h becomes 4.8h, so 4.8 + 4.8 = 9.6h > 8h
        // Story 1: starts and ends same day (4.8h)
        // Story 2: starts same day but may end next day due to remaining 1.6h

        // Same assignee for both
        assertEquals("sa-1", planned1.phases().get("SA").assigneeAccountId());
        assertEquals("sa-1", planned2.phases().get("SA").assigneeAccountId());

        // First story starts today
        assertNotNull(planned1.phases().get("SA").startDate());
    }

    @Test
    void testStoryWithoutEstimate_ShowsWarning() {
        // Given: Epic with story that has no subtasks
        JiraIssueEntity epic = createEpic("EPIC-1", "Test Epic", new BigDecimal("80"));
        JiraIssueEntity story = createStory("STORY-1", "No Estimate Story", "EPIC-1", new BigDecimal("50"));

        when(issueRepository.findEpicsByTeamOrderByManualOrder(TEAM_ID))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKeyOrderByManualOrderAsc("EPIC-1")).thenReturn(List.of(story));
        when(issueRepository.findByParentKey("STORY-1")).thenReturn(List.of()); // No subtasks

        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                createMember("dev-1", "Bob DEV", "DEV", Grade.MIDDLE, new BigDecimal("8"))
        ));

        when(dependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));

        // When
        UnifiedPlanningResult result = service.calculatePlan(TEAM_ID);

        // Then
        assertEquals(1, result.warnings().size());
        assertEquals(WarningType.NO_ESTIMATE, result.warnings().get(0).type());
        assertEquals("STORY-1", result.warnings().get(0).issueKey());

        // Story is still in list but without dates
        PlannedStory plannedStory = result.epics().get(0).stories().get(0);
        assertNull(plannedStory.startDate());
        assertNull(plannedStory.endDate());
    }

    @Test
    void testDependencies_BlockedStoryWaitsForBlocker() {
        // Given: Story 2 is blocked by Story 1
        JiraIssueEntity epic = createEpic("EPIC-1", "Test Epic", new BigDecimal("80"));
        JiraIssueEntity story1 = createStory("STORY-1", "Blocker", "EPIC-1", new BigDecimal("60"));
        JiraIssueEntity story2 = createStory("STORY-2", "Blocked", "EPIC-1", new BigDecimal("50"));
        story2.setIsBlockedBy(List.of("STORY-1"));

        JiraIssueEntity dev1 = createSubtask("SUB-1", "DEV Task", "STORY-1", "Development", 8 * 3600L, 0L);
        JiraIssueEntity dev2 = createSubtask("SUB-2", "DEV Task", "STORY-2", "Development", 8 * 3600L, 0L);

        when(issueRepository.findEpicsByTeamOrderByManualOrder(TEAM_ID))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKeyOrderByManualOrderAsc("EPIC-1")).thenReturn(List.of(story1, story2));
        when(issueRepository.findByParentKey("STORY-1")).thenReturn(List.of(dev1));
        when(issueRepository.findByParentKey("STORY-2")).thenReturn(List.of(dev2));

        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                createMember("dev-1", "Bob DEV", "DEV", Grade.MIDDLE, new BigDecimal("8"))
        ));

        // Topological sort: story1 before story2
        when(dependencyService.topologicalSort(anyList(), anyMap())).thenReturn(List.of(story1, story2));

        // When
        UnifiedPlanningResult result = service.calculatePlan(TEAM_ID);

        // Then
        PlannedEpic plannedEpic = result.epics().get(0);
        assertEquals(2, plannedEpic.stories().size());

        PlannedStory blockerStory = plannedEpic.stories().stream()
                .filter(s -> s.storyKey().equals("STORY-1")).findFirst().orElseThrow();
        PlannedStory blockedStory = plannedEpic.stories().stream()
                .filter(s -> s.storyKey().equals("STORY-2")).findFirst().orElseThrow();

        // Blocked story starts after blocker ends
        assertTrue(blockedStory.startDate().isAfter(blockerStory.endDate()) ||
                blockedStory.startDate().isEqual(blockerStory.endDate().plusDays(1)));
    }

    @Test
    void testRoleTransitionBetweenEpics() {
        // Given: 2 epics, SA finishes epic 1 and moves to epic 2
        JiraIssueEntity epic1 = createEpic("EPIC-1", "First Epic", new BigDecimal("90"));
        JiraIssueEntity epic2 = createEpic("EPIC-2", "Second Epic", new BigDecimal("80"));
        JiraIssueEntity story1 = createStory("STORY-1", "Story 1", "EPIC-1", new BigDecimal("50"));
        JiraIssueEntity story2 = createStory("STORY-2", "Story 2", "EPIC-2", new BigDecimal("50"));
        JiraIssueEntity sa1 = createSubtask("SUB-1", "SA Task", "STORY-1", "Analysis", 8 * 3600L, 0L);
        JiraIssueEntity sa2 = createSubtask("SUB-2", "SA Task", "STORY-2", "Analysis", 8 * 3600L, 0L);

        when(issueRepository.findEpicsByTeamOrderByManualOrder(TEAM_ID))
                .thenReturn(List.of(epic1, epic2)); // Sorted by manual_order ASC
        when(issueRepository.findByParentKeyOrderByManualOrderAsc("EPIC-1")).thenReturn(List.of(story1));
        when(issueRepository.findByParentKeyOrderByManualOrderAsc("EPIC-2")).thenReturn(List.of(story2));
        when(issueRepository.findByParentKey("STORY-1")).thenReturn(List.of(sa1));
        when(issueRepository.findByParentKey("STORY-2")).thenReturn(List.of(sa2));

        // Only 1 SA
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                createMember("sa-1", "Anna SA", "SA", Grade.MIDDLE, new BigDecimal("8"))
        ));

        when(dependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));

        // When
        UnifiedPlanningResult result = service.calculatePlan(TEAM_ID);

        // Then
        assertEquals(2, result.epics().size());

        PlannedEpic planned1 = result.epics().get(0);
        PlannedEpic planned2 = result.epics().get(1);

        assertEquals("EPIC-1", planned1.epicKey());
        assertEquals("EPIC-2", planned2.epicKey());

        // Same SA works on both
        assertEquals("sa-1", planned1.stories().get(0).phases().get("SA").assigneeAccountId());
        assertEquals("sa-1", planned2.stories().get(0).phases().get("SA").assigneeAccountId());

        // Epic 2 starts after Epic 1's SA phase ends (or same day if day split)
        LocalDate epic1SaEnd = planned1.stories().get(0).phases().get("SA").endDate();
        LocalDate epic2SaStart = planned2.stories().get(0).phases().get("SA").startDate();
        // With risk buffer, work might spill to next day, so epic2 starts after or on the end day
        assertTrue(epic2SaStart.isAfter(epic1SaEnd) ||
                epic2SaStart.isEqual(epic1SaEnd) ||
                epic2SaStart.isEqual(epic1SaEnd.plusDays(1)),
                "Epic 2 should start after Epic 1 SA finishes. Epic1 SA end: " + epic1SaEnd + ", Epic2 SA start: " + epic2SaStart);
    }

    @Test
    void testNoCapacity_ShowsWarning() {
        // Given: Story with SA work but no SA in team
        JiraIssueEntity epic = createEpic("EPIC-1", "Test Epic", new BigDecimal("80"));
        JiraIssueEntity story = createStory("STORY-1", "Story", "EPIC-1", new BigDecimal("50"));
        JiraIssueEntity saSubtask = createSubtask("SUB-1", "SA Task", "STORY-1", "Analysis", 8 * 3600L, 0L);

        when(issueRepository.findEpicsByTeamOrderByManualOrder(TEAM_ID))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKeyOrderByManualOrderAsc("EPIC-1")).thenReturn(List.of(story));
        when(issueRepository.findByParentKey("STORY-1")).thenReturn(List.of(saSubtask));

        // Only DEV, no SA
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(
                createMember("dev-1", "Bob DEV", "DEV", Grade.MIDDLE, new BigDecimal("8"))
        ));

        when(dependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));

        // When
        UnifiedPlanningResult result = service.calculatePlan(TEAM_ID);

        // Then
        PlannedStory plannedStory = result.epics().get(0).stories().get(0);
        assertTrue(plannedStory.phases().get("SA").noCapacity());
        assertNull(plannedStory.phases().get("SA").assigneeAccountId());
    }

    // Helper methods

    private JiraIssueEntity createEpic(String key, String summary, BigDecimal autoScore) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setSummary(summary);
        epic.setIssueType("Epic");
        epic.setStatus("In Progress");
        epic.setAutoScore(autoScore);
        epic.setTeamId(TEAM_ID);
        return epic;
    }

    private JiraIssueEntity createStory(String key, String summary, String parentKey, BigDecimal autoScore) {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey(key);
        story.setSummary(summary);
        story.setIssueType("Story");
        story.setStatus("In Progress");
        story.setParentKey(parentKey);
        story.setAutoScore(autoScore);
        return story;
    }

    private JiraIssueEntity createSubtask(String key, String summary, String parentKey,
                                           String type, Long estimateSeconds, Long spentSeconds) {
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setIssueKey(key);
        subtask.setSummary(summary);
        subtask.setIssueType(type);
        subtask.setStatus("In Progress");
        subtask.setParentKey(parentKey);
        subtask.setOriginalEstimateSeconds(estimateSeconds);
        subtask.setTimeSpentSeconds(spentSeconds);
        subtask.setSubtask(true);
        return subtask;
    }

    private TeamMemberEntity createMember(String accountId, String displayName, String role,
                                           Grade grade, BigDecimal hoursPerDay) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setJiraAccountId(accountId);
        member.setDisplayName(displayName);
        member.setRole(role);
        member.setGrade(grade);
        member.setHoursPerDay(hoursPerDay);
        member.setActive(true);
        return member;
    }
}

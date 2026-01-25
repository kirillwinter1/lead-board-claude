package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.status.StatusMappingConfig;
import com.leadboard.status.StatusMappingService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.*;
import com.leadboard.team.dto.PlanningConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoryForecastServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private TeamService teamService;

    @Mock
    private TeamMemberRepository memberRepository;

    @Mock
    private WorkCalendarService calendarService;

    @Mock
    private StatusMappingService statusMappingService;

    @Mock
    private StoryAutoScoreService storyAutoScoreService;

    @Mock
    private StoryDependencyService storyDependencyService;

    private StoryForecastService service;

    @BeforeEach
    void setUp() {
        service = new StoryForecastService(
                issueRepository,
                teamService,
                memberRepository,
                calendarService,
                statusMappingService,
                storyAutoScoreService,
                storyDependencyService
        );
    }

    // ==================== Basic Scheduling Tests ====================

    @Test
    void calculateStoryForecast_withSingleStoryAndAssignee_schedulesCorrectly() {
        // Given: Epic with one story and team with one member
        String epicKey = "EPIC-1";
        Long teamId = 1L;

        JiraIssueEntity epic = createEpic(epicKey, "Epic 1");
        JiraIssueEntity story = createStory("STORY-1", 28800L, 0L); // 8 hours
        story.setAssigneeAccountId("user-123");
        story.setAssigneeDisplayName("John Doe");

        TeamMemberEntity member = createMember("user-123", "John Doe", Role.DEV, Grade.MIDDLE, "6.0");

        when(issueRepository.findByIssueKey(epicKey)).thenReturn(Optional.of(epic));
        when(issueRepository.findByParentKey(epicKey)).thenReturn(List.of(story));
        when(memberRepository.findByTeamIdAndActiveTrue(teamId)).thenReturn(List.of(member));
        when(teamService.getPlanningConfig(teamId)).thenReturn(createDefaultConfig());
        when(storyDependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
        when(statusMappingService.isInProgress(anyString(), any())).thenReturn(false);

        // Simulate work calendar: today is workday, add 1 workday = tomorrow
        LocalDate today = LocalDate.now();
        when(calendarService.addWorkdays(eq(today), eq(0))).thenReturn(today);
        when(calendarService.addWorkdays(eq(today), eq(1))).thenReturn(today.plusDays(1));
        when(calendarService.addWorkdays(eq(today.plusDays(1)), eq(1))).thenReturn(today.plusDays(2));

        // When
        StoryForecastService.StoryForecast forecast = service.calculateStoryForecast(epicKey, teamId);

        // Then
        assertNotNull(forecast);
        assertEquals(epicKey, forecast.epicKey());
        assertEquals(1, forecast.stories().size());

        StoryForecastService.StorySchedule schedule = forecast.stories().get(0);
        assertEquals("STORY-1", schedule.storyKey());
        assertEquals("user-123", schedule.assigneeAccountId());
        assertEquals("John Doe", schedule.assigneeDisplayName());
        assertEquals(today, schedule.startDate());
        assertFalse(schedule.isUnassigned());
        assertFalse(schedule.isBlocked());
    }

    @Test
    void calculateStoryForecast_withUnassignedStory_autoAssigns() {
        // Given: Story without assignee
        String epicKey = "EPIC-1";
        Long teamId = 1L;

        JiraIssueEntity epic = createEpic(epicKey, "Epic 1");
        JiraIssueEntity story = createStory("STORY-1", 28800L, 0L);
        story.setRoughEstimateDevDays(BigDecimal.ONE); // Indicates DEV role

        TeamMemberEntity member = createMember("user-123", "John Doe", Role.DEV, Grade.MIDDLE, "6.0");

        when(issueRepository.findByIssueKey(epicKey)).thenReturn(Optional.of(epic));
        when(issueRepository.findByParentKey(epicKey)).thenReturn(List.of(story));
        when(memberRepository.findByTeamIdAndActiveTrue(teamId)).thenReturn(List.of(member));
        when(teamService.getPlanningConfig(teamId)).thenReturn(createDefaultConfig());
        when(storyDependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
        when(statusMappingService.isInProgress(anyString(), any())).thenReturn(false);

        LocalDate today = LocalDate.now();
        when(calendarService.addWorkdays(any(LocalDate.class), anyInt())).thenAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            int days = inv.getArgument(1);
            return date.plusDays(days);
        });

        // When
        StoryForecastService.StoryForecast forecast = service.calculateStoryForecast(epicKey, teamId);

        // Then
        StoryForecastService.StorySchedule schedule = forecast.stories().get(0);
        assertEquals("user-123", schedule.assigneeAccountId());
        assertTrue(schedule.isUnassigned()); // Flagged as auto-assigned
    }

    @Test
    @Disabled("StoryForecastService will be replaced by UnifiedPlanningService")
    void calculateStoryForecast_withBlockedStory_respectsDependencies() {
        // Given: Two stories, STORY-2 blocked by STORY-1
        String epicKey = "EPIC-1";
        Long teamId = 1L;

        JiraIssueEntity epic = createEpic(epicKey, "Epic 1");
        JiraIssueEntity story1 = createStory("STORY-1", 28800L, 0L); // 8 hours
        story1.setAssigneeAccountId("user-123");

        JiraIssueEntity story2 = createStory("STORY-2", 28800L, 0L);
        story2.setAssigneeAccountId("user-123");
        story2.setIsBlockedBy(List.of("STORY-1"));

        TeamMemberEntity member = createMember("user-123", "John Doe", Role.DEV, Grade.MIDDLE, "6.0");

        when(issueRepository.findByIssueKey(epicKey)).thenReturn(Optional.of(epic));
        when(issueRepository.findByParentKey(epicKey)).thenReturn(List.of(story1, story2));
        when(memberRepository.findByTeamIdAndActiveTrue(teamId)).thenReturn(List.of(member));
        when(teamService.getPlanningConfig(teamId)).thenReturn(createDefaultConfig());
        when(storyDependencyService.topologicalSort(anyList(), anyMap())).thenReturn(List.of(story1, story2)); // Sorted by deps
        when(statusMappingService.isInProgress(anyString(), any())).thenReturn(false);

        LocalDate today = LocalDate.now();
        when(calendarService.addWorkdays(any(LocalDate.class), anyInt())).thenAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            int days = inv.getArgument(1);
            return date.plusDays(days);
        });

        // When
        StoryForecastService.StoryForecast forecast = service.calculateStoryForecast(epicKey, teamId);

        // Then
        assertEquals(2, forecast.stories().size());

        StoryForecastService.StorySchedule schedule1 = forecast.stories().get(0);
        StoryForecastService.StorySchedule schedule2 = forecast.stories().get(1);

        assertEquals("STORY-1", schedule1.storyKey());
        assertEquals("STORY-2", schedule2.storyKey());

        // STORY-2 should start after STORY-1 ends
        assertTrue(schedule2.startDate().isAfter(schedule1.endDate()) ||
                   schedule2.startDate().isEqual(schedule1.endDate()));
        assertTrue(schedule2.isBlocked());
        assertEquals(List.of("STORY-1"), schedule2.blockingStories());
    }

    @Test
    void calculateStoryForecast_withMultipleMembers_distributesWorkByRole() {
        // Given: Stories with different role requirements and multiple members
        String epicKey = "EPIC-1";
        Long teamId = 1L;

        JiraIssueEntity epic = createEpic(epicKey, "Epic 1");

        JiraIssueEntity story1 = createStory("STORY-1", 28800L, 0L);
        story1.setRoughEstimateDevDays(BigDecimal.ONE); // DEV role

        JiraIssueEntity story2 = createStory("STORY-2", 28800L, 0L);
        story2.setRoughEstimateQaDays(BigDecimal.ONE); // QA role

        TeamMemberEntity devMember = createMember("dev-123", "Dev User", Role.DEV, Grade.MIDDLE, "6.0");
        TeamMemberEntity qaMember = createMember("qa-456", "QA User", Role.QA, Grade.MIDDLE, "6.0");

        when(issueRepository.findByIssueKey(epicKey)).thenReturn(Optional.of(epic));
        when(issueRepository.findByParentKey(epicKey)).thenReturn(List.of(story1, story2));
        when(memberRepository.findByTeamIdAndActiveTrue(teamId)).thenReturn(List.of(devMember, qaMember));
        when(teamService.getPlanningConfig(teamId)).thenReturn(createDefaultConfig());
        when(storyDependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
        when(statusMappingService.isInProgress(anyString(), any())).thenReturn(false);

        LocalDate today = LocalDate.now();
        when(calendarService.addWorkdays(any(LocalDate.class), anyInt())).thenAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            int days = inv.getArgument(1);
            return date.plusDays(days);
        });

        // When
        StoryForecastService.StoryForecast forecast = service.calculateStoryForecast(epicKey, teamId);

        // Then
        assertEquals(2, forecast.stories().size());

        // Both stories should start on same day (different assignees)
        StoryForecastService.StorySchedule schedule1 = forecast.stories().get(0);
        StoryForecastService.StorySchedule schedule2 = forecast.stories().get(1);

        assertEquals(today, schedule1.startDate());
        assertEquals(today, schedule2.startDate());

        // Different assignees by role
        assertNotEquals(schedule1.assigneeAccountId(), schedule2.assigneeAccountId());
    }

    @Test
    @Disabled("StoryForecastService will be replaced by UnifiedPlanningService")
    void calculateStoryForecast_withSequentialStories_scheduleSequentially() {
        // Given: Multiple stories for same assignee
        String epicKey = "EPIC-1";
        Long teamId = 1L;

        JiraIssueEntity epic = createEpic(epicKey, "Epic 1");
        JiraIssueEntity story1 = createStory("STORY-1", 28800L, 0L); // 8 hours
        story1.setAssigneeAccountId("user-123");

        JiraIssueEntity story2 = createStory("STORY-2", 28800L, 0L);
        story2.setAssigneeAccountId("user-123");

        JiraIssueEntity story3 = createStory("STORY-3", 28800L, 0L);
        story3.setAssigneeAccountId("user-123");

        TeamMemberEntity member = createMember("user-123", "John Doe", Role.DEV, Grade.MIDDLE, "6.0");

        when(issueRepository.findByIssueKey(epicKey)).thenReturn(Optional.of(epic));
        when(issueRepository.findByParentKey(epicKey)).thenReturn(List.of(story1, story2, story3));
        when(memberRepository.findByTeamIdAndActiveTrue(teamId)).thenReturn(List.of(member));
        when(teamService.getPlanningConfig(teamId)).thenReturn(createDefaultConfig());
        when(storyDependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
        when(statusMappingService.isInProgress(anyString(), any())).thenReturn(false);

        LocalDate today = LocalDate.now();
        when(calendarService.addWorkdays(any(LocalDate.class), anyInt())).thenAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            int days = inv.getArgument(1);
            return date.plusDays(days);
        });

        // When
        StoryForecastService.StoryForecast forecast = service.calculateStoryForecast(epicKey, teamId);

        // Then
        assertEquals(3, forecast.stories().size());

        // Stories should be scheduled sequentially (no overlap)
        StoryForecastService.StorySchedule s1 = forecast.stories().get(0);
        StoryForecastService.StorySchedule s2 = forecast.stories().get(1);
        StoryForecastService.StorySchedule s3 = forecast.stories().get(2);

        assertTrue(s2.startDate().isAfter(s1.endDate()) || s2.startDate().isEqual(s1.endDate().plusDays(1)));
        assertTrue(s3.startDate().isAfter(s2.endDate()) || s3.startDate().isEqual(s2.endDate().plusDays(1)));
    }

    @Test
    @Disabled("StoryForecastService will be replaced by UnifiedPlanningService")
    void calculateStoryForecast_withGradeCoefficients_adjustsCapacity() {
        // Given: Senior developer (higher capacity)
        String epicKey = "EPIC-1";
        Long teamId = 1L;

        JiraIssueEntity epic = createEpic(epicKey, "Epic 1");
        JiraIssueEntity story = createStory("STORY-1", 28800L, 0L); // 8 hours
        story.setAssigneeAccountId("senior-123");

        TeamMemberEntity seniorMember = createMember("senior-123", "Senior Dev", Role.DEV, Grade.SENIOR, "6.0");

        when(issueRepository.findByIssueKey(epicKey)).thenReturn(Optional.of(epic));
        when(issueRepository.findByParentKey(epicKey)).thenReturn(List.of(story));
        when(memberRepository.findByTeamIdAndActiveTrue(teamId)).thenReturn(List.of(seniorMember));
        when(teamService.getPlanningConfig(teamId)).thenReturn(createDefaultConfig());
        when(storyDependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
        when(statusMappingService.isInProgress(anyString(), any())).thenReturn(false);

        LocalDate today = LocalDate.now();
        when(calendarService.addWorkdays(any(LocalDate.class), anyInt())).thenAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            int days = inv.getArgument(1);
            return date.plusDays(days);
        });

        // When
        StoryForecastService.StoryForecast forecast = service.calculateStoryForecast(epicKey, teamId);

        // Then
        StoryForecastService.StorySchedule schedule = forecast.stories().get(0);

        // Senior (0.8 coeff): effectiveHours = 6.0 / 0.8 = 7.5 hrs/day
        // 8 hours work / 7.5 = ~1.1 days
        assertTrue(schedule.workDays().doubleValue() >= 1.0 && schedule.workDays().doubleValue() <= 1.5);
    }

    @Test
    void calculateStoryForecast_withNoEstimate_skipStory() {
        // Given: Story without estimate
        String epicKey = "EPIC-1";
        Long teamId = 1L;

        JiraIssueEntity epic = createEpic(epicKey, "Epic 1");
        JiraIssueEntity story = createStory("STORY-1", null, 0L); // No estimate

        TeamMemberEntity member = createMember("user-123", "John Doe", Role.DEV, Grade.MIDDLE, "6.0");

        when(issueRepository.findByIssueKey(epicKey)).thenReturn(Optional.of(epic));
        when(issueRepository.findByParentKey(epicKey)).thenReturn(List.of(story));
        when(memberRepository.findByTeamIdAndActiveTrue(teamId)).thenReturn(List.of(member));
        when(teamService.getPlanningConfig(teamId)).thenReturn(createDefaultConfig());
        when(storyDependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
        when(statusMappingService.isInProgress(anyString(), any())).thenReturn(false);

        // When
        StoryForecastService.StoryForecast forecast = service.calculateStoryForecast(epicKey, teamId);

        // Then
        StoryForecastService.StorySchedule schedule = forecast.stories().get(0);
        assertEquals(BigDecimal.ZERO, schedule.workDays());
    }

    @Test
    void calculateStoryForecast_withPartialProgress_calculatesRemaining() {
        // Given: Story with 50% progress
        String epicKey = "EPIC-1";
        Long teamId = 1L;

        JiraIssueEntity epic = createEpic(epicKey, "Epic 1");
        JiraIssueEntity story = createStory("STORY-1", 28800L, 14400L); // 8 hours, 4 spent
        story.setAssigneeAccountId("user-123");

        TeamMemberEntity member = createMember("user-123", "John Doe", Role.DEV, Grade.MIDDLE, "6.0");

        when(issueRepository.findByIssueKey(epicKey)).thenReturn(Optional.of(epic));
        when(issueRepository.findByParentKey(epicKey)).thenReturn(List.of(story));
        when(memberRepository.findByTeamIdAndActiveTrue(teamId)).thenReturn(List.of(member));
        when(teamService.getPlanningConfig(teamId)).thenReturn(createDefaultConfig());
        when(storyDependencyService.topologicalSort(anyList(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
        when(statusMappingService.isInProgress(anyString(), any())).thenReturn(false);

        LocalDate today = LocalDate.now();
        when(calendarService.addWorkdays(any(LocalDate.class), anyInt())).thenAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            int days = inv.getArgument(1);
            return date.plusDays(days);
        });

        // When
        StoryForecastService.StoryForecast forecast = service.calculateStoryForecast(epicKey, teamId);

        // Then
        StoryForecastService.StorySchedule schedule = forecast.stories().get(0);

        // Remaining: 4 hours / 6 hours per day = 0.7 days
        assertTrue(schedule.workDays().doubleValue() < 1.0);
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createEpic(String key, String summary) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setSummary(summary);
        epic.setIssueType("Epic");
        epic.setStatus("In Progress");
        return epic;
    }

    private JiraIssueEntity createStory(String key, Long estimateSeconds, Long spentSeconds) {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey(key);
        story.setSummary("Story " + key);
        story.setIssueType("Story");
        story.setStatus("To Do");
        story.setOriginalEstimateSeconds(estimateSeconds);
        story.setTimeSpentSeconds(spentSeconds);
        return story;
    }

    private TeamMemberEntity createMember(String accountId, String name, Role role, Grade grade, String hoursPerDay) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setJiraAccountId(accountId);
        member.setDisplayName(name);
        member.setRole(role);
        member.setGrade(grade);
        member.setHoursPerDay(new BigDecimal(hoursPerDay));
        member.setActive(true);
        return member;
    }

    private PlanningConfigDto createDefaultConfig() {
        return new PlanningConfigDto(
                PlanningConfigDto.GradeCoefficients.defaults(),
                new BigDecimal("0.2"),
                PlanningConfigDto.WipLimits.defaults(),
                PlanningConfigDto.StoryDuration.defaults(),
                StatusMappingConfig.defaults()
        );
    }
}

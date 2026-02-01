package com.leadboard.planning;

import com.leadboard.auth.OAuthTokenRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ForecastController.class)
@AutoConfigureMockMvc(addFilters = false)
class ForecastControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ForecastService forecastService;

    @MockBean
    private OAuthTokenRepository oAuthTokenRepository;

    @MockBean
    private StoryForecastService storyForecastService;

    @MockBean
    private UnifiedPlanningService unifiedPlanningService;

    @MockBean
    private AutoScoreService autoScoreService;

    @MockBean
    private WipSnapshotService wipSnapshotService;

    @MockBean
    private RoleLoadService roleLoadService;

    @MockBean
    private JiraIssueRepository issueRepository;

    // ==================== Story Forecast Tests ====================

    @Test
    void getStoryForecast_returnsStorySchedules() throws Exception {
        // Given
        String epicKey = "EPIC-1";
        Long teamId = 1L;

        LocalDate today = LocalDate.now();
        StoryForecastService.StorySchedule schedule = new StoryForecastService.StorySchedule(
                "STORY-1",
                "user-123",
                "John Doe",
                today,
                today.plusDays(2),
                new BigDecimal("2.0"),
                false,
                false,
                List.of()
        );

        StoryForecastService.AssigneeUtilization utilization =
                new StoryForecastService.AssigneeUtilization(
                        "John Doe",
                        Role.DEV,
                        new BigDecimal("2.0"),
                        new BigDecimal("6.0")
                );

        StoryForecastService.StoryForecast forecast = new StoryForecastService.StoryForecast(
                epicKey,
                today,
                List.of(schedule),
                Map.of("user-123", utilization)
        );

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("STORY-1");
        story.setSummary("Test Story");
        story.setStatus("To Do");
        story.setAutoScore(new BigDecimal("75.0"));

        when(storyForecastService.calculateStoryForecast(epicKey, teamId)).thenReturn(forecast);
        when(issueRepository.findByIssueKey("STORY-1")).thenReturn(Optional.of(story));

        // When & Then
        mockMvc.perform(get("/api/planning/epics/{epicKey}/story-forecast", epicKey)
                        .param("teamId", teamId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epicKey").value(epicKey))
                .andExpect(jsonPath("$.epicStartDate").value(today.toString()))
                .andExpect(jsonPath("$.stories").isArray())
                .andExpect(jsonPath("$.stories[0].storyKey").value("STORY-1"))
                .andExpect(jsonPath("$.stories[0].storySummary").value("Test Story"))
                .andExpect(jsonPath("$.stories[0].assigneeAccountId").value("user-123"))
                .andExpect(jsonPath("$.stories[0].assigneeDisplayName").value("John Doe"))
                .andExpect(jsonPath("$.stories[0].startDate").value(today.toString()))
                .andExpect(jsonPath("$.stories[0].endDate").value(today.plusDays(2).toString()))
                .andExpect(jsonPath("$.stories[0].workDays").value(2.0))
                .andExpect(jsonPath("$.stories[0].isUnassigned").value(false))
                .andExpect(jsonPath("$.stories[0].isBlocked").value(false))
                .andExpect(jsonPath("$.stories[0].autoScore").value(75.0))
                .andExpect(jsonPath("$.stories[0].status").value("To Do"))
                .andExpect(jsonPath("$.assigneeUtilization['user-123'].displayName").value("John Doe"))
                .andExpect(jsonPath("$.assigneeUtilization['user-123'].role").value("DEV"))
                .andExpect(jsonPath("$.assigneeUtilization['user-123'].workDaysAssigned").value(2.0))
                .andExpect(jsonPath("$.assigneeUtilization['user-123'].effectiveHoursPerDay").value(6.0));
    }

    @Test
    void getStoryForecast_withUnassignedStory_flagsAsUnassigned() throws Exception {
        // Given
        String epicKey = "EPIC-2";
        Long teamId = 1L;

        LocalDate today = LocalDate.now();
        StoryForecastService.StorySchedule schedule = new StoryForecastService.StorySchedule(
                "STORY-2",
                "auto-assigned-123",
                "Auto User",
                today,
                today.plusDays(1),
                new BigDecimal("1.0"),
                true, // isUnassigned
                false,
                List.of()
        );

        StoryForecastService.StoryForecast forecast = new StoryForecastService.StoryForecast(
                epicKey,
                today,
                List.of(schedule),
                Map.of()
        );

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("STORY-2");
        story.setSummary("Unassigned Story");
        story.setStatus("To Do");
        story.setAutoScore(new BigDecimal("80.0"));

        when(storyForecastService.calculateStoryForecast(epicKey, teamId)).thenReturn(forecast);
        when(issueRepository.findByIssueKey("STORY-2")).thenReturn(Optional.of(story));

        // When & Then
        mockMvc.perform(get("/api/planning/epics/{epicKey}/story-forecast", epicKey)
                        .param("teamId", teamId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories[0].isUnassigned").value(true))
                .andExpect(jsonPath("$.stories[0].assigneeAccountId").value("auto-assigned-123"));
    }

    @Test
    void getStoryForecast_withBlockedStory_flagsAsBlocked() throws Exception {
        // Given
        String epicKey = "EPIC-3";
        Long teamId = 1L;

        LocalDate today = LocalDate.now();
        StoryForecastService.StorySchedule schedule = new StoryForecastService.StorySchedule(
                "STORY-3",
                "user-456",
                "Jane Doe",
                today.plusDays(3),
                today.plusDays(5),
                new BigDecimal("2.0"),
                false,
                true, // isBlocked
                List.of("STORY-1", "STORY-2") // blockingStories
        );

        StoryForecastService.StoryForecast forecast = new StoryForecastService.StoryForecast(
                epicKey,
                today,
                List.of(schedule),
                Map.of()
        );

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("STORY-3");
        story.setSummary("Blocked Story");
        story.setStatus("To Do");
        story.setAutoScore(new BigDecimal("60.0"));

        when(storyForecastService.calculateStoryForecast(epicKey, teamId)).thenReturn(forecast);
        when(issueRepository.findByIssueKey("STORY-3")).thenReturn(Optional.of(story));

        // When & Then
        mockMvc.perform(get("/api/planning/epics/{epicKey}/story-forecast", epicKey)
                        .param("teamId", teamId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories[0].isBlocked").value(true))
                .andExpect(jsonPath("$.stories[0].blockingStories").isArray())
                .andExpect(jsonPath("$.stories[0].blockingStories[0]").value("STORY-1"))
                .andExpect(jsonPath("$.stories[0].blockingStories[1]").value("STORY-2"));
    }

    @Test
    void getStoryForecast_withMultipleStories_returnsAllSchedules() throws Exception {
        // Given
        String epicKey = "EPIC-4";
        Long teamId = 1L;

        LocalDate today = LocalDate.now();
        StoryForecastService.StorySchedule schedule1 = new StoryForecastService.StorySchedule(
                "STORY-4", "user-123", "John Doe", today, today.plusDays(1),
                new BigDecimal("1.0"), false, false, List.of()
        );
        StoryForecastService.StorySchedule schedule2 = new StoryForecastService.StorySchedule(
                "STORY-5", "user-456", "Jane Doe", today, today.plusDays(2),
                new BigDecimal("2.0"), false, false, List.of()
        );

        StoryForecastService.StoryForecast forecast = new StoryForecastService.StoryForecast(
                epicKey,
                today,
                List.of(schedule1, schedule2),
                Map.of()
        );

        JiraIssueEntity story1 = new JiraIssueEntity();
        story1.setIssueKey("STORY-4");
        story1.setSummary("Story 4");
        story1.setStatus("To Do");
        story1.setAutoScore(new BigDecimal("90.0"));

        JiraIssueEntity story2 = new JiraIssueEntity();
        story2.setIssueKey("STORY-5");
        story2.setSummary("Story 5");
        story2.setStatus("In Progress");
        story2.setAutoScore(new BigDecimal("85.0"));

        when(storyForecastService.calculateStoryForecast(epicKey, teamId)).thenReturn(forecast);
        when(issueRepository.findByIssueKey("STORY-4")).thenReturn(Optional.of(story1));
        when(issueRepository.findByIssueKey("STORY-5")).thenReturn(Optional.of(story2));

        // When & Then
        mockMvc.perform(get("/api/planning/epics/{epicKey}/story-forecast", epicKey)
                        .param("teamId", teamId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories").isArray())
                .andExpect(jsonPath("$.stories.length()").value(2))
                .andExpect(jsonPath("$.stories[0].storyKey").value("STORY-4"))
                .andExpect(jsonPath("$.stories[1].storyKey").value("STORY-5"));
    }

    @Test
    void getStoryForecast_withMissingTeamIdParam_returns400() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/planning/epics/{epicKey}/story-forecast", "EPIC-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStoryForecast_returnsUtilizationMetrics() throws Exception {
        // Given
        String epicKey = "EPIC-5";
        Long teamId = 1L;

        LocalDate today = LocalDate.now();
        StoryForecastService.StorySchedule schedule = new StoryForecastService.StorySchedule(
                "STORY-6", "user-123", "Senior Dev", today, today.plusDays(3),
                new BigDecimal("3.0"), false, false, List.of()
        );

        StoryForecastService.AssigneeUtilization utilization =
                new StoryForecastService.AssigneeUtilization(
                        "Senior Dev",
                        Role.DEV,
                        new BigDecimal("10.5"),
                        new BigDecimal("7.5")
                );

        StoryForecastService.StoryForecast forecast = new StoryForecastService.StoryForecast(
                epicKey,
                today,
                List.of(schedule),
                Map.of("user-123", utilization)
        );

        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("STORY-6");
        story.setSummary("Test Story");
        story.setStatus("To Do");
        story.setAutoScore(new BigDecimal("70.0"));

        when(storyForecastService.calculateStoryForecast(epicKey, teamId)).thenReturn(forecast);
        when(issueRepository.findByIssueKey("STORY-6")).thenReturn(Optional.of(story));

        // When & Then
        mockMvc.perform(get("/api/planning/epics/{epicKey}/story-forecast", epicKey)
                        .param("teamId", teamId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeUtilization").isMap())
                .andExpect(jsonPath("$.assigneeUtilization['user-123']").exists())
                .andExpect(jsonPath("$.assigneeUtilization['user-123'].displayName").value("Senior Dev"))
                .andExpect(jsonPath("$.assigneeUtilization['user-123'].role").value("DEV"))
                .andExpect(jsonPath("$.assigneeUtilization['user-123'].workDaysAssigned").value(10.5))
                .andExpect(jsonPath("$.assigneeUtilization['user-123'].effectiveHoursPerDay").value(7.5));
    }
}

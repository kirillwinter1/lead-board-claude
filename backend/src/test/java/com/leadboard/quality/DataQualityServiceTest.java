package com.leadboard.quality;

import com.leadboard.status.StatusMappingConfig;
import com.leadboard.status.StatusMappingProperties;
import com.leadboard.status.StatusMappingService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataQualityServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private TeamMemberRepository memberRepository;

    private StatusMappingService statusMappingService;
    private DataQualityService dataQualityService;
    private StatusMappingConfig statusMapping;

    @BeforeEach
    void setUp() {
        StatusMappingProperties properties = new StatusMappingProperties();
        statusMappingService = new StatusMappingService(properties);
        statusMapping = statusMappingService.getDefaultConfig();
        dataQualityService = new DataQualityService(issueRepository, memberRepository, statusMappingService);
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createEpic(String key, String status) {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey(key);
        epic.setIssueType("Epic");
        epic.setStatus(status);
        epic.setSummary("Test Epic");
        epic.setProjectKey("TEST");
        return epic;
    }

    private JiraIssueEntity createStory(String key, String status, String parentKey) {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey(key);
        story.setIssueType("Story");
        story.setStatus(status);
        story.setParentKey(parentKey);
        story.setSummary("Test Story");
        story.setProjectKey("TEST");
        return story;
    }

    private JiraIssueEntity createSubtask(String key, String status, String parentKey) {
        JiraIssueEntity subtask = new JiraIssueEntity();
        subtask.setIssueKey(key);
        subtask.setIssueType("Sub-task");
        subtask.setStatus(status);
        subtask.setParentKey(parentKey);
        subtask.setSubtask(true);
        subtask.setSummary("Test Subtask");
        subtask.setProjectKey("TEST");
        return subtask;
    }

    // ==================== Epic Rules Tests ====================

    @Nested
    class EpicRulesTests {

        @Test
        void epicNoTeam_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(null);

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_TEAM));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.EPIC_NO_TEAM)
                    .allMatch(v -> v.severity() == DataQualitySeverity.ERROR));
        }

        @Test
        void epicWithTeam_shouldNotReturnEpicNoTeamError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(1L);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), statusMapping);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_TEAM));
        }

        @Test
        void epicTeamNoMembers_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(1L);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of());

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_TEAM_NO_MEMBERS));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.EPIC_TEAM_NO_MEMBERS)
                    .allMatch(v -> v.severity() == DataQualitySeverity.WARNING));
        }

        @Test
        void epicNoDueDate_shouldReturnInfo() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(1L);
            epic.setDueDate(null);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_DUE_DATE));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.EPIC_NO_DUE_DATE)
                    .allMatch(v -> v.severity() == DataQualitySeverity.INFO));
        }

        @Test
        void epicOverdue_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().minusDays(1));

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_OVERDUE));
        }

        @Test
        void epicOverdueButDone_shouldNotReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Done");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().minusDays(1));

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), statusMapping);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_OVERDUE));
        }

        @Test
        void epicNoEstimate_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_ESTIMATE));
        }

        @Test
        void epicWithRoughEstimate_shouldNotReturnNoEstimate() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimateDevDays(new java.math.BigDecimal("5.0"));

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), statusMapping);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_ESTIMATE));
        }

        @Test
        void epicDoneWithOpenChildren_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Done");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimateDevDays(new java.math.BigDecimal("5.0"));

            JiraIssueEntity openStory = createStory("TEST-2", "In Progress", "TEST-1");

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(openStory), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_DONE_OPEN_CHILDREN));
        }

        @Test
        void epicInProgressNoStories_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimateDevDays(new java.math.BigDecimal("5.0"));

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_IN_PROGRESS_NO_STORIES));
        }

        @Test
        void timeLoggedDirectlyOnEpic_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimateDevDays(new java.math.BigDecimal("5.0"));
            epic.setTimeSpentSeconds(3600L);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.TIME_LOGGED_NOT_IN_SUBTASK));
        }

        @Test
        void timeLoggedWrongEpicStatus_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimateDevDays(new java.math.BigDecimal("5.0"));

            JiraIssueEntity story = createStory("TEST-2", "In Progress", "TEST-1");
            story.setTimeSpentSeconds(3600L);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(story), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.TIME_LOGGED_WRONG_EPIC_STATUS));
        }
    }

    // ==================== Story Rules Tests ====================

    @Nested
    class StoryRulesTests {

        @Test
        void storyDoneWithOpenSubtasks_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Done", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(subtask), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_DONE_OPEN_CHILDREN));
        }

        @Test
        void storyInProgressNoSubtasks_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_IN_PROGRESS_NO_SUBTASKS));
        }

        @Test
        void childInProgressEpicNot_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.CHILD_IN_PROGRESS_EPIC_NOT));
        }

        @Test
        void timeLoggedDirectlyOnStory_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            story.setTimeSpentSeconds(3600L);

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(), statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.TIME_LOGGED_NOT_IN_SUBTASK));
        }
    }

    // ==================== Subtask Rules Tests ====================

    @Nested
    class SubtaskRulesTests {

        @Test
        void subtaskNoEstimate_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(0L);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic, statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_NO_ESTIMATE));
        }

        @Test
        void subtaskDoneNoEstimate_shouldNotReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "Done", "TEST-2");
            subtask.setOriginalEstimateSeconds(0L);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic, statusMapping);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_NO_ESTIMATE));
        }

        @Test
        void subtaskWorkNoEstimate_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(0L);
            subtask.setTimeSpentSeconds(3600L);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic, statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_WORK_NO_ESTIMATE));
        }

        @Test
        void subtaskOverrun_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L); // 1 hour
            subtask.setTimeSpentSeconds(6000L); // More than 1.5x (5400 seconds)

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic, statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_OVERRUN));
        }

        @Test
        void subtaskNotOverrun_shouldNotReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L); // 1 hour
            subtask.setTimeSpentSeconds(4000L); // Less than 1.5x (5400 seconds)

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic, statusMapping);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_OVERRUN));
        }

        @Test
        void subtaskInProgressStoryNot_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "New", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic, statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_IN_PROGRESS_STORY_NOT));
        }

        @Test
        void subtaskInProgressEpicNot_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic, statusMapping);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.CHILD_IN_PROGRESS_EPIC_NOT));
        }
    }

    // ==================== Blocking Errors Tests ====================

    @Nested
    class BlockingErrorsTests {

        @Test
        void epicNoTeam_isBlocking() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(null);

            assertTrue(dataQualityService.hasBlockingErrors(epic, statusMapping));
        }

        @Test
        void epicOverdue_isBlocking() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().minusDays(1));

            assertTrue(dataQualityService.hasBlockingErrors(epic, statusMapping));
        }

        @Test
        void epicTimeLoggedDirectly_isBlocking() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setTimeSpentSeconds(3600L);

            assertTrue(dataQualityService.hasBlockingErrors(epic, statusMapping));
        }

        @Test
        void epicDoneWithOpenChildren_isBlocking() {
            JiraIssueEntity epic = createEpic("TEST-1", "Done");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));

            JiraIssueEntity openStory = createStory("TEST-2", "In Progress", "TEST-1");

            when(issueRepository.findByParentKey("TEST-1")).thenReturn(List.of(openStory));

            assertTrue(dataQualityService.hasBlockingErrors(epic, statusMapping));
        }

        @Test
        void epicWithNoBlockingIssues_isNotBlocking() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));

            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            when(issueRepository.findByParentKey("TEST-1")).thenReturn(List.of(story));

            assertFalse(dataQualityService.hasBlockingErrors(epic, statusMapping));
        }
    }

    // ==================== Forecast Late Tests ====================

    @Nested
    class ForecastLateTests {

        @Test
        void forecastAfterDueDate_shouldReturnWarning() {
            LocalDate expectedDone = LocalDate.now().plusDays(10);
            LocalDate dueDate = LocalDate.now().plusDays(5);

            DataQualityViolation violation = dataQualityService.checkForecastLate(expectedDone, dueDate);

            assertNotNull(violation);
            assertEquals(DataQualityRule.EPIC_FORECAST_LATE, violation.rule());
            assertEquals(DataQualitySeverity.WARNING, violation.severity());
        }

        @Test
        void forecastBeforeDueDate_shouldReturnNull() {
            LocalDate expectedDone = LocalDate.now().plusDays(5);
            LocalDate dueDate = LocalDate.now().plusDays(10);

            DataQualityViolation violation = dataQualityService.checkForecastLate(expectedDone, dueDate);

            assertNull(violation);
        }

        @Test
        void forecastOnDueDate_shouldReturnNull() {
            LocalDate date = LocalDate.now().plusDays(10);

            DataQualityViolation violation = dataQualityService.checkForecastLate(date, date);

            assertNull(violation);
        }

        @Test
        void forecastOrDueDateNull_shouldReturnNull() {
            assertNull(dataQualityService.checkForecastLate(null, LocalDate.now()));
            assertNull(dataQualityService.checkForecastLate(LocalDate.now(), null));
            assertNull(dataQualityService.checkForecastLate(null, null));
        }
    }
}

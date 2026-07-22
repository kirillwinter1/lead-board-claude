package com.leadboard.quality;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.entity.FlagChangelogEntity;
import com.leadboard.metrics.repository.FlagChangelogRepository;
import com.leadboard.rice.RiceAssessmentRepository;
import com.leadboard.status.StatusAge;
import com.leadboard.status.StatusCategory;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataQualityServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private TeamMemberRepository memberRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    @Mock
    private RiceAssessmentRepository riceAssessmentRepository;

    @Mock
    private BugSlaService bugSlaService;

    @Mock
    private FlagChangelogRepository flagChangelogRepository;

    private DataQualityService dataQualityService;

    @BeforeEach
    void setUp() {
        // Mock WorkflowConfigService — no fallback heuristics since F38
        // isDone: only "Done" status is done
        lenient().when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        lenient().when(workflowConfigService.isDone(eq("Done"), anyString())).thenReturn(true);

        // isInProgress: "In Progress", "Development", "Developing", "In Review"
        lenient().when(workflowConfigService.isInProgress(anyString(), anyString())).thenReturn(false);
        lenient().when(workflowConfigService.isInProgress(eq("In Progress"), anyString())).thenReturn(true);
        lenient().when(workflowConfigService.isInProgress(eq("Development"), anyString())).thenReturn(true);
        lenient().when(workflowConfigService.isInProgress(eq("Developing"), anyString())).thenReturn(true);
        lenient().when(workflowConfigService.isInProgress(eq("In Review"), anyString())).thenReturn(true);

        // isEpicInProgress: "In Progress", "Developing"
        lenient().when(workflowConfigService.isEpicInProgress(anyString())).thenReturn(false);
        lenient().when(workflowConfigService.isEpicInProgress("In Progress")).thenReturn(true);
        lenient().when(workflowConfigService.isEpicInProgress("Developing")).thenReturn(true);

        // isTimeLoggingAllowed: "In Progress", "Developing"
        lenient().when(workflowConfigService.isTimeLoggingAllowed(anyString())).thenReturn(false);
        lenient().when(workflowConfigService.isTimeLoggingAllowed("In Progress")).thenReturn(true);
        lenient().when(workflowConfigService.isTimeLoggingAllowed("Developing")).thenReturn(true);

        // categorizeEpic
        lenient().when(workflowConfigService.categorizeEpic(anyString())).thenReturn(StatusCategory.NEW);
        lenient().when(workflowConfigService.categorizeEpic("In Progress")).thenReturn(StatusCategory.IN_PROGRESS);
        lenient().when(workflowConfigService.categorizeEpic("Developing")).thenReturn(StatusCategory.IN_PROGRESS);
        lenient().when(workflowConfigService.categorizeEpic("Done")).thenReturn(StatusCategory.DONE);
        lenient().when(workflowConfigService.categorizeEpic("Open")).thenReturn(StatusCategory.NEW);
        lenient().when(workflowConfigService.categorizeEpic("Backlog")).thenReturn(StatusCategory.NEW);
        lenient().when(workflowConfigService.categorizeEpic("Requirements")).thenReturn(StatusCategory.REQUIREMENTS);
        lenient().when(workflowConfigService.categorizeEpic("Planned")).thenReturn(StatusCategory.PLANNED);

        // By default no open flag entry exists — individual tests stub as needed.
        lenient().when(flagChangelogRepository.findFirstByIssueKeyAndUnflaggedAtIsNullOrderByFlaggedAtDesc(anyString()))
                .thenReturn(Optional.empty());

        dataQualityService = new DataQualityService(issueRepository, memberRepository, workflowConfigService, riceAssessmentRepository, bugSlaService, flagChangelogRepository);
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

    private JiraIssueEntity createBug(String key, String status, String parentKey) {
        JiraIssueEntity bug = new JiraIssueEntity();
        bug.setIssueKey(key);
        bug.setIssueType("Bug");
        bug.setStatus(status);
        bug.setParentKey(parentKey);
        bug.setSummary("Test Bug");
        bug.setProjectKey("TEST");
        return bug;
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

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

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

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_TEAM));
        }

        @Test
        void epicTeamNoMembers_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(1L);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of());

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_TEAM_NO_MEMBERS));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.EPIC_TEAM_NO_MEMBERS)
                    .allMatch(v -> v.severity() == DataQualitySeverity.WARNING));
        }

        @Test
        void epicNoDueDate_shouldReturnInfo() {
            JiraIssueEntity epic = createEpic("TEST-1", "Planned");
            epic.setTeamId(1L);
            epic.setDueDate(null);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

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

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_OVERDUE));
        }

        @Test
        void epicOverdueButDone_shouldNotReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Done");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().minusDays(1));

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_OVERDUE));
        }

        @Test
        void epicNoEstimate_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Planned");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_ESTIMATE));
        }

        @Test
        void epicWithRoughEstimate_shouldNotReturnNoEstimate() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimate("DEV", new java.math.BigDecimal("5.0"));

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_ESTIMATE));
        }

        @Test
        void epicDoneWithOpenChildren_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Done");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimate("DEV", new java.math.BigDecimal("5.0"));

            JiraIssueEntity openStory = createStory("TEST-2", "In Progress", "TEST-1");

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(openStory));

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_DONE_OPEN_CHILDREN));
        }

        @Test
        void epicInProgressNoStories_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimate("DEV", new java.math.BigDecimal("5.0"));

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_IN_PROGRESS_NO_STORIES));
        }

        @Test
        void timeLoggedDirectlyOnEpic_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimate("DEV", new java.math.BigDecimal("5.0"));
            epic.setTimeSpentSeconds(3600L);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.TIME_LOGGED_NOT_IN_SUBTASK));
        }

        @Test
        void timeLoggedWrongEpicStatus_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimate("DEV", new java.math.BigDecimal("5.0"));

            JiraIssueEntity story = createStory("TEST-2", "In Progress", "TEST-1");

            // Subtask under story with logged time
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setTimeSpentSeconds(3600L);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));
            when(issueRepository.findByParentKeyIn(List.of("TEST-2"))).thenReturn(List.of(subtask));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(story));

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

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(subtask));

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_DONE_OPEN_CHILDREN));
        }

        @Test
        void storyInProgressNoSubtasks_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_IN_PROGRESS_NO_SUBTASKS));
        }

        @Test
        void childInProgressEpicNot_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.CHILD_IN_PROGRESS_EPIC_NOT));
        }

        @Test
        void timeLoggedDirectlyOnStory_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            story.setTimeSpentSeconds(3600L);

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.TIME_LOGGED_NOT_IN_SUBTASK));
        }

        @Test
        void storyFullyLoggedNotDone_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            JiraIssueEntity subtask = createSubtask("TEST-3", "Done", "TEST-2");
            subtask.setOriginalEstimateSeconds(28800L); // 8h
            subtask.setTimeSpentSeconds(28800L); // 8h — fully logged

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(subtask));

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_FULLY_LOGGED_NOT_DONE));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.STORY_FULLY_LOGGED_NOT_DONE)
                    .allMatch(v -> v.severity() == DataQualitySeverity.WARNING));
        }

        @Test
        void allSubtasksDoneStoryNot_shouldReturnWarning() {
            // LB-639 case: subtasks were closed (with or without worklogs) but the story
            // status lags behind — flag it even when nothing was logged.
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            JiraIssueEntity st1 = createSubtask("TEST-3", "Done", "TEST-2");
            JiraIssueEntity st2 = createSubtask("TEST-4", "Done", "TEST-2");

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(st1, st2));

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_NOT_DONE_SUBTASKS_DONE));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.STORY_NOT_DONE_SUBTASKS_DONE)
                    .allMatch(v -> v.severity() == DataQualitySeverity.WARNING
                            && v.message().contains("Development")));
        }

        @Test
        void someSubtasksNotDone_shouldNotReturnStoryBehindWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            JiraIssueEntity st1 = createSubtask("TEST-3", "Done", "TEST-2");
            JiraIssueEntity st2 = createSubtask("TEST-4", "In Progress", "TEST-2");

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(st1, st2));

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_NOT_DONE_SUBTASKS_DONE));
        }

        @Test
        void storyDoneOrNoSubtasks_shouldNotReturnStoryBehindWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");

            JiraIssueEntity doneStory = createStory("TEST-2", "Done", "TEST-1");
            JiraIssueEntity doneSubtask = createSubtask("TEST-3", "Done", "TEST-2");
            assertFalse(dataQualityService.checkStory(doneStory, epic, List.of(doneSubtask)).stream()
                    .anyMatch(v -> v.rule() == DataQualityRule.STORY_NOT_DONE_SUBTASKS_DONE));

            JiraIssueEntity emptyStory = createStory("TEST-4", "Development", "TEST-1");
            assertFalse(dataQualityService.checkStory(emptyStory, epic, List.of()).stream()
                    .anyMatch(v -> v.rule() == DataQualityRule.STORY_NOT_DONE_SUBTASKS_DONE));
        }

        @Test
        void storyDone_shouldNotReturnFullyLoggedWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Done", "TEST-1");

            JiraIssueEntity subtask = createSubtask("TEST-3", "Done", "TEST-2");
            subtask.setOriginalEstimateSeconds(28800L);
            subtask.setTimeSpentSeconds(28800L);

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(subtask));

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_FULLY_LOGGED_NOT_DONE));
        }

        @Test
        void storyPartiallyLogged_shouldNotReturnFullyLoggedWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(28800L); // 8h
            subtask.setTimeSpentSeconds(14400L); // 4h — only half

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(subtask));

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_FULLY_LOGGED_NOT_DONE));
        }

        @Test
        void storyTodoButSubtasksHaveWork_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "New", "TEST-1");

            JiraIssueEntity subtask = createSubtask("TEST-3", "Done", "TEST-2");
            subtask.setOriginalEstimateSeconds(14400L); // 4h
            subtask.setTimeSpentSeconds(14400L); // 4h

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(subtask));

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_TODO_BUT_HAS_WORK));
        }

        @Test
        void storyInProgressWithSubtaskWork_shouldNotReturnTodoWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setTimeSpentSeconds(7200L); // 2h

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of(subtask));

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_TODO_BUT_HAS_WORK));
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

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_NO_ESTIMATE));
        }

        @Test
        void subtaskDoneNoEstimate_shouldNotReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "Done", "TEST-2");
            subtask.setOriginalEstimateSeconds(0L);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_NO_ESTIMATE));
        }

        @Test
        void subtaskWorkNoEstimate_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(0L);
            subtask.setTimeSpentSeconds(3600L);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_WORK_NO_ESTIMATE));
        }

        @Test
        void subtaskOverrun_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L); // 1 hour
            subtask.setTimeSpentSeconds(6000L); // More than 1.5x (5400 seconds)

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_OVERRUN));
        }

        @Test
        void subtaskNotOverrun_shouldNotReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L); // 1 hour
            subtask.setTimeSpentSeconds(4000L); // Less than 1.5x (5400 seconds)

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_OVERRUN));
        }

        @Test
        void subtaskInProgressStoryNot_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "New", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_IN_PROGRESS_STORY_NOT));
        }

        @Test
        void subtaskInProgressEpicNot_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.CHILD_IN_PROGRESS_EPIC_NOT));
        }

        @Test
        void subtaskDoneNoTimeLogged_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "Done", "TEST-2");
            subtask.setOriginalEstimateSeconds(28800L); // 8 hours
            subtask.setTimeSpentSeconds(null);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_DONE_NO_TIME_LOGGED));
        }

        @Test
        void subtaskDoneWithTimeLogged_shouldNotReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "Done", "TEST-2");
            subtask.setOriginalEstimateSeconds(28800L); // 8 hours
            subtask.setTimeSpentSeconds(25200L); // 7 hours

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_DONE_NO_TIME_LOGGED));
        }

        @Test
        void subtaskTimeLoggedButTodo_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "New", "TEST-2");
            subtask.setOriginalEstimateSeconds(14400L); // 4 hours
            subtask.setTimeSpentSeconds(14000L); // ~3.9 hours

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_TIME_LOGGED_BUT_TODO));
        }

        @Test
        void subtaskTimeLoggedInProgress_shouldNotReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(14400L);
            subtask.setTimeSpentSeconds(14000L);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_TIME_LOGGED_BUT_TODO));
        }
    }

    // ==================== Blocking Errors Tests ====================

    @Nested
    class BlockingErrorsTests {

        @Test
        void epicNoTeam_isBlocking() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(null);

            assertTrue(dataQualityService.hasBlockingErrors(epic));
        }

        @Test
        void epicOverdue_isBlocking() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().minusDays(1));

            assertTrue(dataQualityService.hasBlockingErrors(epic));
        }

        @Test
        void epicTimeLoggedDirectly_isBlocking() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setTimeSpentSeconds(3600L);

            assertTrue(dataQualityService.hasBlockingErrors(epic));
        }

        @Test
        void epicDoneWithOpenChildren_isBlocking() {
            JiraIssueEntity epic = createEpic("TEST-1", "Done");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));

            JiraIssueEntity openStory = createStory("TEST-2", "In Progress", "TEST-1");

            when(issueRepository.findByParentKey("TEST-1")).thenReturn(List.of(openStory));

            assertTrue(dataQualityService.hasBlockingErrors(epic));
        }

        @Test
        void epicWithNoBlockingIssues_isNotBlocking() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));

            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");

            when(issueRepository.findByParentKey("TEST-1")).thenReturn(List.of(story));

            assertFalse(dataQualityService.hasBlockingErrors(epic));
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

    // ==================== New Rules Tests (F83) ====================

    @Nested
    class AssigneeRulesTests {

        @Test
        void subtaskInProgressNoAssignee_shouldReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L);
            subtask.setAssigneeAccountId(null);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.IN_PROGRESS_NO_ASSIGNEE));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.IN_PROGRESS_NO_ASSIGNEE)
                    .allMatch(v -> v.severity() == DataQualitySeverity.ERROR));
        }

        @Test
        void subtaskInProgressWithAssignee_shouldNotReturnError() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing"); // teamId null -> no ASSIGNEE_NOT_IN_TEAM
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L);
            subtask.setAssigneeAccountId("acc-1");

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.IN_PROGRESS_NO_ASSIGNEE));
        }

        @Test
        void assigneeNotInEpicTeam_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(5L);
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L);
            subtask.setAssigneeAccountId("acc-1");
            subtask.setAssigneeDisplayName("John Doe");

            when(memberRepository.existsByJiraAccountIdAndTeamIdAndActiveTrue("acc-1", 5L)).thenReturn(false);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.ASSIGNEE_NOT_IN_TEAM));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.ASSIGNEE_NOT_IN_TEAM)
                    .allMatch(v -> v.message().contains("John Doe")));
        }

        @Test
        void assigneeInEpicTeam_shouldNotReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(5L);
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L);
            subtask.setAssigneeAccountId("acc-1");

            when(memberRepository.existsByJiraAccountIdAndTeamIdAndActiveTrue("acc-1", 5L)).thenReturn(true);

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.ASSIGNEE_NOT_IN_TEAM));
        }
    }

    @Nested
    class StalenessRulesTests {

        @Test
        void statusAgeCritical_shouldReturnInProgressTooLong() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L);
            subtask.setAssigneeAccountId("acc-1");

            StatusAge critical = new StatusAge(25, StatusAge.CRITICAL, "25д в статусе");

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic, critical);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.IN_PROGRESS_TOO_LONG));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.IN_PROGRESS_TOO_LONG)
                    .allMatch(v -> v.message().contains("25")));
        }

        @Test
        void statusAgeNormal_shouldNotReturnInProgressTooLong() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(3600L);
            subtask.setAssigneeAccountId("acc-1");

            List<DataQualityViolation> violations =
                    dataQualityService.checkSubtask(subtask, story, epic, StatusAge.normal(5));

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.IN_PROGRESS_TOO_LONG));
        }

        @Test
        void epicFlaggedLongerThanThreshold_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimate("DEV", new java.math.BigDecimal("5.0"));
            epic.setDescription("desc");
            epic.setFlagged(true);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            FlagChangelogEntity entry = new FlagChangelogEntity();
            entry.setIssueKey("TEST-1");
            entry.setFlaggedAt(OffsetDateTime.now().minusDays(20));
            when(flagChangelogRepository.findFirstByIssueKeyAndUnflaggedAtIsNullOrderByFlaggedAtDesc("TEST-1"))
                    .thenReturn(Optional.of(entry));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), null);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_FLAGGED_TOO_LONG));
        }

        @Test
        void epicFlaggedRecently_shouldNotReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimate("DEV", new java.math.BigDecimal("5.0"));
            epic.setDescription("desc");
            epic.setFlagged(true);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            FlagChangelogEntity entry = new FlagChangelogEntity();
            entry.setIssueKey("TEST-1");
            entry.setFlaggedAt(OffsetDateTime.now().minusDays(5));
            when(flagChangelogRepository.findFirstByIssueKeyAndUnflaggedAtIsNullOrderByFlaggedAtDesc("TEST-1"))
                    .thenReturn(Optional.of(entry));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of(), null);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_FLAGGED_TOO_LONG));
        }
    }

    @Nested
    class TeamFieldAndContentRulesTests {

        @Test
        void teamFieldSetButUnmapped_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(null);
            epic.setTeamFieldValue("Team Rocket");

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.TEAM_FIELD_UNMAPPED));
            // EPIC_NO_TEAM still fires alongside it (it blocks planning)
            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_TEAM));
        }

        @Test
        void teamFieldMapped_shouldNotReturnUnmapped() {
            JiraIssueEntity epic = createEpic("TEST-1", "Backlog");
            epic.setTeamId(1L);
            epic.setTeamFieldValue("Team Rocket");

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.TEAM_FIELD_UNMAPPED));
        }

        @Test
        void epicPastTodoNoDescription_shouldReturnInfo() {
            JiraIssueEntity epic = createEpic("TEST-1", "Planned");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimate("DEV", new java.math.BigDecimal("5.0"));
            epic.setDescription(null);

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_DESCRIPTION));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.EPIC_NO_DESCRIPTION)
                    .allMatch(v -> v.severity() == DataQualitySeverity.INFO));
        }

        @Test
        void epicWithDescription_shouldNotReturnNoDescription() {
            JiraIssueEntity epic = createEpic("TEST-1", "Planned");
            epic.setTeamId(1L);
            epic.setDueDate(LocalDate.now().plusDays(30));
            epic.setRoughEstimate("DEV", new java.math.BigDecimal("5.0"));
            epic.setDescription("Some meaningful description");

            when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(new TeamMemberEntity()));

            List<DataQualityViolation> violations = dataQualityService.checkEpic(epic, List.of());

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.EPIC_NO_DESCRIPTION));
        }
    }

    @Nested
    class DueDateAndEstimateRulesTests {

        @Test
        void storyDueAfterEpic_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setDueDate(LocalDate.now().plusDays(10));
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            story.setDueDate(LocalDate.now().plusDays(20));

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.CHILD_DUE_AFTER_EPIC));
        }

        @Test
        void storyDueBeforeEpic_shouldNotReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            epic.setDueDate(LocalDate.now().plusDays(20));
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            story.setDueDate(LocalDate.now().plusDays(10));

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of());

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.CHILD_DUE_AFTER_EPIC));
        }

        @Test
        void subtaskEstimateTooBig_shouldReturnInfo() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(41L * 3600L); // 41 hours > 40h threshold
            subtask.setAssigneeAccountId("acc-1");

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_ESTIMATE_TOO_BIG));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.SUBTASK_ESTIMATE_TOO_BIG)
                    .allMatch(v -> v.severity() == DataQualitySeverity.INFO));
        }

        @Test
        void subtaskEstimateWithinThreshold_shouldNotReturnInfo() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            JiraIssueEntity subtask = createSubtask("TEST-3", "In Progress", "TEST-2");
            subtask.setOriginalEstimateSeconds(20L * 3600L); // 20 hours
            subtask.setAssigneeAccountId("acc-1");

            List<DataQualityViolation> violations = dataQualityService.checkSubtask(subtask, story, epic);

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.SUBTASK_ESTIMATE_TOO_BIG));
        }
    }

    @Nested
    class BugPriorityRulesTests {

        @Test
        void bugWithoutPriority_shouldReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity bug = createBug("TEST-2", "Development", "TEST-1");
            bug.setPriority(null);

            List<DataQualityViolation> violations = dataQualityService.checkBug(bug, epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.BUG_NO_PRIORITY));
            assertTrue(violations.stream()
                    .filter(v -> v.rule() == DataQualityRule.BUG_NO_PRIORITY)
                    .allMatch(v -> v.severity() == DataQualitySeverity.WARNING));
        }

        @Test
        void bugWithPriority_shouldNotReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity bug = createBug("TEST-2", "Development", "TEST-1");
            bug.setPriority("High");

            List<DataQualityViolation> violations = dataQualityService.checkBug(bug, epic, List.of());

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.BUG_NO_PRIORITY));
        }

        @Test
        void doneBugWithoutPriority_shouldNotReturnWarning() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity bug = createBug("TEST-2", "Done", "TEST-1");
            bug.setPriority(null);

            List<DataQualityViolation> violations = dataQualityService.checkBug(bug, epic, List.of());

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.BUG_NO_PRIORITY));
        }
    }

    @Nested
    class BlockedNoProgressTests {

        @Test
        void oldStoryWithRecentBlocker_shouldNotReturnViolation() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            story.setJiraCreatedAt(OffsetDateTime.now().minusDays(100));
            story.setIsBlockedBy(List.of("BLOCK-1"));

            JiraIssueEntity blocker = createStory("BLOCK-1", "In Progress", null);
            blocker.setJiraCreatedAt(OffsetDateTime.now().minusDays(5)); // blocker created recently

            when(issueRepository.findByIssueKeyIn(List.of("BLOCK-1"))).thenReturn(List.of(blocker));

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of());

            assertFalse(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_BLOCKED_NO_PROGRESS));
        }

        @Test
        void oldStoryWithOldIdleBlocker_shouldReturnViolation() {
            JiraIssueEntity epic = createEpic("TEST-1", "Developing");
            JiraIssueEntity story = createStory("TEST-2", "Development", "TEST-1");
            story.setJiraCreatedAt(OffsetDateTime.now().minusDays(100));
            story.setIsBlockedBy(List.of("BLOCK-1"));

            JiraIssueEntity blocker = createStory("BLOCK-1", "In Progress", null);
            blocker.setJiraCreatedAt(OffsetDateTime.now().minusDays(90)); // old blocker, idle

            when(issueRepository.findByIssueKeyIn(List.of("BLOCK-1"))).thenReturn(List.of(blocker));

            List<DataQualityViolation> violations = dataQualityService.checkStory(story, epic, List.of());

            assertTrue(violations.stream().anyMatch(v -> v.rule() == DataQualityRule.STORY_BLOCKED_NO_PROGRESS));
        }
    }
}

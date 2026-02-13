package com.leadboard.simulation;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.UnifiedPlanningService;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.*;
import com.leadboard.simulation.dto.SimulationAction;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimulationPlannerTest {

    @Mock private UnifiedPlanningService planningService;
    @Mock private JiraIssueRepository issueRepository;
    @Mock private TeamMemberRepository memberRepository;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private WorkCalendarService calendarService;
    @Mock private SimulationDeviation deviation;

    private SimulationPlanner planner;

    private static final Long TEAM_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 2); // Monday

    @BeforeEach
    void setUp() {
        planner = new SimulationPlanner(
                planningService, issueRepository, memberRepository,
                workflowConfigService, calendarService, deviation
        );

        // Default: today is a workday
        when(calendarService.isWorkday(TODAY)).thenReturn(true);

        // Default deviation: return base hours
        when(deviation.applyDailyDeviation(anyDouble())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void planDay_nonWorkday_returnsEmpty() {
        when(calendarService.isWorkday(TODAY)).thenReturn(false);

        List<SimulationAction> actions = planner.planDay(TEAM_ID, TODAY);

        assertTrue(actions.isEmpty());
        verify(planningService, never()).calculatePlan(any());
    }

    @Test
    void planDay_noEpics_returnsEmpty() {
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of());
        when(planningService.calculatePlan(TEAM_ID)).thenReturn(
                new UnifiedPlanningResult(TEAM_ID, OffsetDateTime.now(), List.of(), List.of(), Map.of()));

        List<SimulationAction> actions = planner.planDay(TEAM_ID, TODAY);

        assertTrue(actions.isEmpty());
    }

    @Test
    void planDay_subtaskInTodo_generatesTransition() {
        // Setup team member
        TeamMemberEntity member = createMember("acc-1", "Dev One", "DEV");
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(member));

        // Setup plan with active DEV phase today
        PhaseSchedule devPhase = new PhaseSchedule(
                "acc-1", "Dev One", TODAY, TODAY.plusDays(5), new BigDecimal("40"), false);
        PlannedStory story = createStory("PROJ-10", "New", devPhase);
        PlannedEpic epic = createEpic("PROJ-1", List.of(story));

        when(planningService.calculatePlan(TEAM_ID)).thenReturn(
                new UnifiedPlanningResult(TEAM_ID, OffsetDateTime.now(), List.of(epic), List.of(), Map.of()));

        // Setup subtask in NEW status
        JiraIssueEntity subtask = createSubtask("PROJ-11", "New", "Разработка", 28800L, 0L);
        when(issueRepository.findByParentKey("PROJ-10")).thenReturn(List.of(subtask));
        when(workflowConfigService.getSubtaskRole("Разработка")).thenReturn("DEV");
        when(workflowConfigService.categorize("New", "Разработка")).thenReturn(StatusCategory.NEW);
        when(workflowConfigService.isDone(eq("New"), anyString())).thenReturn(false);
        when(workflowConfigService.isDone(eq("Developing"), anyString())).thenReturn(false);

        List<SimulationAction> actions = planner.planDay(TEAM_ID, TODAY);

        assertTrue(actions.stream().anyMatch(a ->
                a.type() == SimulationAction.ActionType.TRANSITION
                        && "PROJ-11".equals(a.issueKey())
                        && "In Progress".equals(a.toStatus())));
    }

    @Test
    void planDay_subtaskInProgress_generatesWorklog() {
        TeamMemberEntity member = createMember("acc-1", "Dev One", "DEV");
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(member));

        PhaseSchedule devPhase = new PhaseSchedule(
                "acc-1", "Dev One", TODAY, TODAY.plusDays(5), new BigDecimal("40"), false);
        PlannedStory story = createStory("PROJ-10", "In Progress", devPhase);
        PlannedEpic epic = createEpic("PROJ-1", List.of(story));

        when(planningService.calculatePlan(TEAM_ID)).thenReturn(
                new UnifiedPlanningResult(TEAM_ID, OffsetDateTime.now(), List.of(epic), List.of(), Map.of()));

        // Subtask in progress with remaining estimate
        JiraIssueEntity subtask = createSubtask("PROJ-11", "In Progress", "Разработка", 28800L, 7200L);
        subtask.setRemainingEstimateSeconds(21600L); // 6h remaining
        when(issueRepository.findByParentKey("PROJ-10")).thenReturn(List.of(subtask));
        when(workflowConfigService.getSubtaskRole("Разработка")).thenReturn("DEV");
        when(workflowConfigService.categorize("In Progress", "Разработка")).thenReturn(StatusCategory.IN_PROGRESS);
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);

        List<SimulationAction> actions = planner.planDay(TEAM_ID, TODAY);

        assertTrue(actions.stream().anyMatch(a ->
                a.type() == SimulationAction.ActionType.WORKLOG
                        && "PROJ-11".equals(a.issueKey())
                        && a.hoursLogged() != null && a.hoursLogged() > 0));
    }

    @Test
    void planDay_subtaskWorkComplete_generatesTransitionToDone() {
        TeamMemberEntity member = createMember("acc-1", "Dev One", "DEV");
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(member));

        PhaseSchedule devPhase = new PhaseSchedule(
                "acc-1", "Dev One", TODAY, TODAY.plusDays(5), new BigDecimal("40"), false);
        PlannedStory story = createStory("PROJ-10", "In Progress", devPhase);
        PlannedEpic epic = createEpic("PROJ-1", List.of(story));

        when(planningService.calculatePlan(TEAM_ID)).thenReturn(
                new UnifiedPlanningResult(TEAM_ID, OffsetDateTime.now(), List.of(epic), List.of(), Map.of()));

        // Subtask with 0 remaining (all logged)
        JiraIssueEntity subtask = createSubtask("PROJ-11", "In Progress", "Разработка", 28800L, 28800L);
        subtask.setRemainingEstimateSeconds(0L);
        when(issueRepository.findByParentKey("PROJ-10")).thenReturn(List.of(subtask));
        when(workflowConfigService.getSubtaskRole("Разработка")).thenReturn("DEV");
        when(workflowConfigService.categorize("In Progress", "Разработка")).thenReturn(StatusCategory.IN_PROGRESS);
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);

        List<SimulationAction> actions = planner.planDay(TEAM_ID, TODAY);

        assertTrue(actions.stream().anyMatch(a ->
                a.type() == SimulationAction.ActionType.TRANSITION
                        && "PROJ-11".equals(a.issueKey())
                        && "Done".equals(a.toStatus())));
    }

    @Test
    void planDay_phaseNotActiveToday_noActions() {
        TeamMemberEntity member = createMember("acc-1", "Dev One", "DEV");
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(member));

        // Phase starts tomorrow
        PhaseSchedule devPhase = new PhaseSchedule(
                "acc-1", "Dev One", TODAY.plusDays(1), TODAY.plusDays(5), new BigDecimal("40"), false);
        PlannedStory story = createStory("PROJ-10", "New", devPhase);
        PlannedEpic epic = createEpic("PROJ-1", List.of(story));

        when(planningService.calculatePlan(TEAM_ID)).thenReturn(
                new UnifiedPlanningResult(TEAM_ID, OffsetDateTime.now(), List.of(epic), List.of(), Map.of()));
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);

        List<SimulationAction> actions = planner.planDay(TEAM_ID, TODAY);

        assertTrue(actions.isEmpty());
    }

    @Test
    void planDay_allSubtasksDone_generatesStoryTransition() {
        TeamMemberEntity member = createMember("acc-1", "Dev One", "DEV");
        when(memberRepository.findByTeamIdAndActiveTrue(TEAM_ID)).thenReturn(List.of(member));

        PhaseSchedule devPhase = new PhaseSchedule(
                "acc-1", "Dev One", TODAY, TODAY.plusDays(5), new BigDecimal("40"), false);
        PlannedStory story = createStory("PROJ-10", "In Progress", devPhase);
        PlannedEpic epic = createEpic("PROJ-1", List.of(story));

        when(planningService.calculatePlan(TEAM_ID)).thenReturn(
                new UnifiedPlanningResult(TEAM_ID, OffsetDateTime.now(), List.of(epic), List.of(), Map.of()));

        // All subtasks already done
        JiraIssueEntity subtask = createSubtask("PROJ-11", "Done", "Разработка", 28800L, 28800L);
        when(issueRepository.findByParentKey("PROJ-10")).thenReturn(List.of(subtask));
        when(workflowConfigService.getSubtaskRole("Разработка")).thenReturn("DEV");
        when(workflowConfigService.categorize("Done", "Разработка")).thenReturn(StatusCategory.DONE);
        when(workflowConfigService.isDone("Done", "Разработка")).thenReturn(true);
        when(workflowConfigService.isDone("In Progress", "Story")).thenReturn(false);
        when(workflowConfigService.isDone("Developing", "Epic")).thenReturn(false);

        List<SimulationAction> actions = planner.planDay(TEAM_ID, TODAY);

        assertTrue(actions.stream().anyMatch(a ->
                a.type() == SimulationAction.ActionType.TRANSITION
                        && "PROJ-10".equals(a.issueKey())
                        && "Done".equals(a.toStatus())
                        && "All subtasks completed".equals(a.reason())));
    }

    // Helper methods

    private TeamMemberEntity createMember(String accountId, String name, String role) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setJiraAccountId(accountId);
        member.setDisplayName(name);
        member.setRole(role);
        member.setHoursPerDay(new BigDecimal("6.0"));
        return member;
    }

    private JiraIssueEntity createSubtask(String key, String status, String type,
                                          Long estimate, Long spent) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setStatus(status);
        entity.setIssueType(type);
        entity.setSubtask(true);
        entity.setOriginalEstimateSeconds(estimate);
        entity.setTimeSpentSeconds(spent);
        return entity;
    }

    private PlannedStory createStory(String key, String status, PhaseSchedule devPhase) {
        Map<String, PhaseSchedule> phases = devPhase != null ? Map.of("DEV", devPhase) : Map.of();
        return new PlannedStory(
                key, "Test Story", null, status,
                devPhase != null ? devPhase.startDate() : null,
                devPhase != null ? devPhase.endDate() : null,
                phases,
                List.of(), List.of(),
                "Story", "Medium", false,
                null, null, null, Map.of()
        );
    }

    private PlannedEpic createEpic(String key, List<PlannedStory> stories) {
        return new PlannedEpic(
                key, "Test Epic", null, TODAY, TODAY.plusDays(30),
                stories, Map.of(),
                "Developing", null, 0L, 0L, 0,
                Map.of(), stories.size(), stories.size(),
                false, null
        );
    }
}

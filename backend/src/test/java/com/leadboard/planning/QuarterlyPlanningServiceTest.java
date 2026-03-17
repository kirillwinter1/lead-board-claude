package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.dto.*;
import com.leadboard.rice.RiceAssessmentEntity;
import com.leadboard.rice.RiceAssessmentRepository;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuarterlyPlanningServiceTest {

    @Mock private JiraIssueRepository issueRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository memberRepository;
    @Mock private AbsenceService absenceService;
    @Mock private WorkCalendarService workCalendarService;
    @Mock private TeamService teamService;
    @Mock private RiceAssessmentRepository riceAssessmentRepository;
    @Mock private WorkflowConfigService workflowConfigService;

    private QuarterlyPlanningService service;

    @BeforeEach
    void setUp() {
        service = new QuarterlyPlanningService(
                issueRepository, teamRepository, memberRepository,
                absenceService, workCalendarService, teamService,
                riceAssessmentRepository, workflowConfigService
        );

        when(teamService.getPlanningConfig(anyLong())).thenReturn(PlanningConfigDto.defaults());
        when(workCalendarService.countWorkdays(any(), any())).thenReturn(63);
        when(workCalendarService.isWorkday(any())).thenReturn(true);
        when(absenceService.getTeamAbsenceDates(anyLong(), any(), any())).thenReturn(Map.of());
    }

    // ==================== Capacity Tests ====================

    @Test
    void testCapacityBasicThreeMembers() {
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        List<TeamMemberEntity> members = List.of(
                createMember(1L, team, "DEV", Grade.SENIOR, "6.0"),
                createMember(2L, team, "DEV", Grade.MIDDLE, "8.0"),
                createMember(3L, team, "QA", Grade.JUNIOR, "6.0")
        );
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(members);

        QuarterlyCapacityDto capacity = service.getTeamCapacity(1L, "2026Q2");

        assertNotNull(capacity);
        assertEquals(1L, capacity.teamId());
        assertEquals("2026Q2", capacity.quarter());
        assertEquals(63, capacity.totalWorkdays());

        // DEV: Senior (63 * 6/8 / 0.8 = 59.06) + Middle (63 * 8/8 / 1.0 = 63)
        BigDecimal devCapacity = capacity.capacityByRole().get("DEV");
        assertNotNull(devCapacity);
        assertTrue(devCapacity.compareTo(new BigDecimal("100")) > 0); // At least > 100

        // QA: Junior (63 * 6/8 / 1.5 = 31.5)
        BigDecimal qaCapacity = capacity.capacityByRole().get("QA");
        assertNotNull(qaCapacity);
        assertTrue(qaCapacity.compareTo(new BigDecimal("30")) > 0);
    }

    @Test
    void testCapacityWithAbsences() {
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        TeamMemberEntity member = createMember(1L, team, "DEV", Grade.MIDDLE, "8.0");
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(member));

        // 5 absence days
        Set<java.time.LocalDate> absences = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            absences.add(java.time.LocalDate.of(2026, 4, i));
        }
        when(absenceService.getTeamAbsenceDates(eq(1L), any(), any()))
                .thenReturn(Map.of("acc1", absences));

        QuarterlyCapacityDto capacity = service.getTeamCapacity(1L, "2026Q2");

        assertEquals(5, capacity.absenceDays());
        BigDecimal devCapacity = capacity.capacityByRole().get("DEV");
        // 63 workdays - 5 absences = 58 available days × (8/8) / 1.0 = 58
        assertEquals(0, devCapacity.compareTo(new BigDecimal("58.00")));
    }

    // ==================== Priority Score Tests ====================

    @Test
    void testPriorityScoreComputation() {
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of());

        // Create project with boost
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project Alpha");
        project.setManualBoost(20);
        project.setLabels(new String[]{"2026Q2"});

        // Create epic under project
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic One");
        epic.setParentKey("PROJ-1");
        epic.setTeamId(1L);
        epic.setRoughEstimates(Map.of("DEV", new BigDecimal("10")));

        when(issueRepository.findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", 1L))
                .thenReturn(List.of(epic));
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        // findDistinctQuarterLabels was replaced with findByLabelsIsNotNull + Java parsing

        RiceAssessmentEntity rice = new RiceAssessmentEntity();
        rice.setIssueKey("PROJ-1");
        rice.setNormalizedScore(new BigDecimal("65.0"));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection()))
                .thenReturn(List.of(rice));

        QuarterlyDemandDto demand = service.getTeamDemand(1L, "2026Q2");

        assertFalse(demand.projects().isEmpty());
        ProjectDemandDto pd = demand.projects().get(0);
        // Priority = RICE(65) + Boost(20) = 85
        assertEquals(0, pd.priorityScore().compareTo(new BigDecimal("85.0")));
    }

    @Test
    void testPriorityScoreClamping() {
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of());

        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setManualBoost(50);
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        epic.setParentKey("PROJ-1");
        epic.setTeamId(1L);
        epic.setRoughEstimates(Map.of("DEV", new BigDecimal("5")));

        when(issueRepository.findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", 1L))
                .thenReturn(List.of(epic));
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));

        RiceAssessmentEntity rice = new RiceAssessmentEntity();
        rice.setIssueKey("PROJ-1");
        rice.setNormalizedScore(new BigDecimal("120.0"));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of(rice));

        QuarterlyDemandDto demand = service.getTeamDemand(1L, "2026Q2");

        // 120 + 50 = 170 → clamped to 150
        ProjectDemandDto pd = demand.projects().get(0);
        assertTrue(pd.priorityScore().compareTo(new BigDecimal("150")) <= 0);
    }

    // ==================== Quarter Label Inheritance ====================

    @Test
    void testEpicInheritsQuarterFromProject() {
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of());

        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"}); // Project has quarter label

        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        epic.setParentKey("PROJ-1");
        epic.setTeamId(1L);
        epic.setLabels(null); // Epic has NO labels — should inherit from project

        when(issueRepository.findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", 1L))
                .thenReturn(List.of(epic));
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyDemandDto demand = service.getTeamDemand(1L, "2026Q2");

        // Epic should be included via project's quarter label
        assertFalse(demand.projects().isEmpty());
        assertEquals(1, demand.projects().get(0).epics().size());
    }

    // ==================== Capacity Fit ====================

    @Test
    void testCapacityFitCutoff() {
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        // Team with 20 DEV days capacity
        TeamMemberEntity member = createMember(1L, team, "DEV", Grade.MIDDLE, "8.0");
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(member));
        when(workCalendarService.countWorkdays(any(), any())).thenReturn(20);

        // Project with 3 epics: 8 + 8 + 8 = 24 DEV days demand (with 20% risk buffer = 9.6 + 9.6 + 9.6)
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epic1 = createIssue("EPIC-1", "EPIC", "Epic 1");
        epic1.setParentKey("PROJ-1");
        epic1.setTeamId(1L);
        epic1.setManualOrder(1);
        epic1.setRoughEstimates(Map.of("DEV", new BigDecimal("8")));

        JiraIssueEntity epic2 = createIssue("EPIC-2", "EPIC", "Epic 2");
        epic2.setParentKey("PROJ-1");
        epic2.setTeamId(1L);
        epic2.setManualOrder(2);
        epic2.setRoughEstimates(Map.of("DEV", new BigDecimal("8")));

        JiraIssueEntity epic3 = createIssue("EPIC-3", "EPIC", "Epic 3");
        epic3.setParentKey("PROJ-1");
        epic3.setTeamId(1L);
        epic3.setManualOrder(3);
        epic3.setRoughEstimates(Map.of("DEV", new BigDecimal("8")));

        when(issueRepository.findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", 1L))
                .thenReturn(List.of(epic1, epic2, epic3));
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyDemandDto demand = service.getTeamDemand(1L, "2026Q2");

        ProjectDemandDto pd = demand.projects().get(0);
        // With 20 days capacity and 9.6+9.6+9.6 demand, at least last epic should be overCapacity
        long overCount = pd.epics().stream().filter(EpicDemandDto::overCapacity).count();
        assertTrue(overCount > 0, "At least one epic should be over capacity");
        assertFalse(pd.fitsInCapacity());
    }

    // ==================== Unassigned Epics ====================

    @Test
    void testUnassignedEpics() {
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of());

        // Epic with quarter label but no parent project
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Orphan Epic");
        epic.setTeamId(1L);
        epic.setLabels(new String[]{"2026Q2"});
        epic.setRoughEstimates(Map.of("DEV", new BigDecimal("5")));

        when(issueRepository.findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", 1L))
                .thenReturn(List.of(epic));
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of());
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyDemandDto demand = service.getTeamDemand(1L, "2026Q2");

        assertTrue(demand.projects().isEmpty());
        assertEquals(1, demand.unassignedEpics().size());
        assertEquals("EPIC-1", demand.unassignedEpics().get(0).epicKey());
    }

    // ==================== Empty Quarter ====================

    @Test
    void testEmptyQuarter() {
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of());

        when(issueRepository.findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", 1L))
                .thenReturn(List.of());
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of());

        QuarterlyDemandDto demand = service.getTeamDemand(1L, "2026Q2");

        assertTrue(demand.projects().isEmpty());
        assertTrue(demand.unassignedEpics().isEmpty());
    }

    // ==================== Projects Overview Tests ====================

    @Test
    void testProjectsOverview_readyProject() {
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Ready Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epic1 = createIssue("EPIC-1", "EPIC", "Epic One");
        epic1.setParentKey("PROJ-1");
        epic1.setTeamId(1L);
        epic1.setRoughEstimates(Map.of("DEV", new BigDecimal("10"), "QA", new BigDecimal("5")));

        JiraIssueEntity epic2 = createIssue("EPIC-2", "EPIC", "Epic Two");
        epic2.setParentKey("PROJ-1");
        epic2.setTeamId(1L);
        epic2.setRoughEstimates(Map.of("DEV", new BigDecimal("8")));

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic1, epic2));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        TeamEntity team1 = createTeam(1L, "Alpha");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team1));
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of());

        QuarterlyProjectsResponse response = service.getProjectsOverview("2026Q2");

        assertNotNull(response);
        assertEquals("2026Q2", response.quarter());
        assertEquals(1, response.inQuarterCount());
        assertEquals(1, response.readyCount());
        assertFalse(response.projects().isEmpty());

        QuarterlyProjectOverviewDto dto = response.projects().get(0);
        assertEquals("PROJ-1", dto.projectKey());
        assertTrue(dto.inQuarter());
        assertEquals("ready", dto.planningStatus());
        assertEquals(100, dto.roughEstimateCoverage());
        assertEquals(100, dto.teamMappingCoverage());
        assertNotNull(dto.demandDays());
        assertTrue(dto.blockers().isEmpty());
    }

    @Test
    void testProjectsOverview_blockedProject() {
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Blocked Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epic1 = createIssue("EPIC-1", "EPIC", "Epic No Estimate");
        epic1.setParentKey("PROJ-1");
        epic1.setTeamId(1L);
        // No rough estimates

        JiraIssueEntity epic2 = createIssue("EPIC-2", "EPIC", "Epic No Estimate 2");
        epic2.setParentKey("PROJ-1");
        epic2.setTeamId(1L);
        // No rough estimates

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic1, epic2));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        QuarterlyProjectsResponse response = service.getProjectsOverview("2026Q2");

        QuarterlyProjectOverviewDto dto = response.projects().get(0);
        assertEquals("blocked", dto.planningStatus());
        assertEquals(0, dto.roughEstimateCoverage());
        assertNull(dto.demandDays()); // Demand unavailable when rough missing
        assertEquals("Demand unavailable", dto.forecastLabel());
        assertFalse(dto.blockers().isEmpty());
    }

    @Test
    void testProjectsOverview_partialProject() {
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Partial Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epic1 = createIssue("EPIC-1", "EPIC", "Epic Estimated");
        epic1.setParentKey("PROJ-1");
        epic1.setTeamId(1L);
        epic1.setRoughEstimates(Map.of("DEV", new BigDecimal("10")));

        // 2nd epic has rough estimates but no team
        JiraIssueEntity epic2 = createIssue("EPIC-2", "EPIC", "Epic No Team");
        epic2.setParentKey("PROJ-1");
        epic2.setTeamId(null); // No team
        epic2.setRoughEstimates(Map.of("DEV", new BigDecimal("5")));

        // 3rd epic both have
        JiraIssueEntity epic3 = createIssue("EPIC-3", "EPIC", "Epic Full");
        epic3.setParentKey("PROJ-1");
        epic3.setTeamId(1L);
        epic3.setRoughEstimates(Map.of("QA", new BigDecimal("3")));

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic1, epic2, epic3));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        QuarterlyProjectsResponse response = service.getProjectsOverview("2026Q2");

        QuarterlyProjectOverviewDto dto = response.projects().get(0);
        // roughCoverage = 100% (all 3 have rough), teamMappingCoverage = 67% (2/3 have team)
        // 67% < 80% → blocked (not partial)
        // Actually the logic: roughCoverage < 60 OR teamMappingCoverage < 80 → blocked
        assertEquals(100, dto.roughEstimateCoverage());
        // teamMappingCoverage = 2/3 = 67% which is < 80 → blocked
        assertTrue(dto.teamMappingCoverage() < 80);
    }

    @Test
    void testProjectsOverview_notInQuarter() {
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Not In Quarter");
        project.setLabels(null); // No quarter label

        JiraIssueEntity epic1 = createIssue("EPIC-1", "EPIC", "Some Epic");
        epic1.setParentKey("PROJ-1");
        epic1.setTeamId(1L);
        epic1.setRoughEstimates(Map.of("DEV", new BigDecimal("5")));

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic1));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        QuarterlyProjectsResponse response = service.getProjectsOverview("2026Q2");

        assertEquals(0, response.inQuarterCount());
        assertFalse(response.projects().isEmpty());
        assertEquals("not-added", response.projects().get(0).planningStatus());
        assertFalse(response.projects().get(0).inQuarter());
    }

    @Test
    void testTeamsOverview_basicCalculation() {
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByActiveTrue()).thenReturn(List.of(team));
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        TeamMemberEntity member = createMember(1L, team, "DEV", Grade.MIDDLE, "8.0");
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(member));

        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        epic.setParentKey("PROJ-1");
        epic.setTeamId(1L);
        epic.setRoughEstimates(Map.of("DEV", new BigDecimal("20")));

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic));
        when(issueRepository.findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", 1L))
                .thenReturn(List.of(epic));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        List<QuarterlyTeamOverviewDto> result = service.getTeamsOverview("2026Q2");

        assertFalse(result.isEmpty());
        QuarterlyTeamOverviewDto teamDto = result.get(0);
        assertEquals(1L, teamDto.teamId());
        assertTrue(teamDto.capacityDays().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(teamDto.demandDays().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(teamDto.utilization() > 0);
        assertFalse(teamDto.impactingProjects().isEmpty());
    }

    @Test
    void testTeamsOverview_overloadedTeam() {
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByActiveTrue()).thenReturn(List.of(team));
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));

        // Small capacity: 1 member, 10 workdays
        TeamMemberEntity member = createMember(1L, team, "DEV", Grade.MIDDLE, "8.0");
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of(member));
        when(workCalendarService.countWorkdays(any(), any())).thenReturn(10);

        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"});

        // Huge demand: 50d DEV demand vs 10d capacity
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Big Epic");
        epic.setParentKey("PROJ-1");
        epic.setTeamId(1L);
        epic.setRoughEstimates(Map.of("DEV", new BigDecimal("50")));

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic));
        when(issueRepository.findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", 1L))
                .thenReturn(List.of(epic));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        List<QuarterlyTeamOverviewDto> result = service.getTeamsOverview("2026Q2");

        QuarterlyTeamOverviewDto teamDto = result.get(0);
        assertTrue(teamDto.utilization() > 100, "Team should be overloaded");
        assertEquals("high", teamDto.risk());
        assertTrue(teamDto.overloadedEpics() > 0);
    }

    // ==================== Helpers ====================

    private TeamEntity createTeam(Long id, String name) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setName(name);
        team.setColor("#1558BC");
        team.setActive(true);
        return team;
    }

    private TeamMemberEntity createMember(Long id, TeamEntity team, String role, Grade grade, String hoursPerDay) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(id);
        member.setTeam(team);
        member.setRole(role);
        member.setGrade(grade);
        member.setHoursPerDay(new BigDecimal(hoursPerDay));
        member.setJiraAccountId("acc" + id);
        member.setActive(true);
        return member;
    }

    private JiraIssueEntity createIssue(String key, String boardCategory, String summary) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setIssueId("10" + key.hashCode());
        entity.setProjectKey("PROJ");
        entity.setSummary(summary);
        entity.setStatus("To Do");
        entity.setIssueType(boardCategory.equals("EPIC") ? "Epic" : boardCategory.equals("PROJECT") ? "Initiative" : "Story");
        entity.setBoardCategory(boardCategory);
        entity.setManualBoost(0);
        return entity;
    }
}

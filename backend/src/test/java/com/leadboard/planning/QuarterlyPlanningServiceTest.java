package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
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
    @Mock private JiraClient jiraClient;

    private QuarterlyPlanningService service;

    @BeforeEach
    void setUp() {
        service = new QuarterlyPlanningService(
                issueRepository, teamRepository, memberRepository,
                absenceService, workCalendarService, teamService,
                riceAssessmentRepository, workflowConfigService,
                jiraClient
        );

        when(teamService.getPlanningConfig(anyLong())).thenReturn(PlanningConfigDto.defaults());
        when(workCalendarService.countWorkdays(any(), any())).thenReturn(63);
        when(workCalendarService.isWorkday(any())).thenReturn(true);
        when(absenceService.getTeamAbsenceDates(anyLong(), any(), any())).thenReturn(Map.of());

        // Default safe returns for collections the mutate paths now touch (assignEpicToQuarter
        // / setEpicBoost pre-build a quarter snapshot which loads PROJECT/EPIC entities and
        // active teams). Tests can still override these with their own `when(...)`.
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of());
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of());
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());
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

    // ==================== F69: Epics for Quarter (Kanban) ====================

    @Test
    void getEpicsForQuarter_returnsAllEpicsWithCorrectInQuarterFlag() {
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity inQuarterEpic = createIssue("EPIC-1", "EPIC", "In Q2");
        inQuarterEpic.setParentKey("PROJ-1");
        inQuarterEpic.setLabels(new String[]{"2026Q2"});
        inQuarterEpic.setRoughEstimates(Map.of("DEV", new BigDecimal("5")));
        inQuarterEpic.setTeamId(1L);

        JiraIssueEntity otherQuarterEpic = createIssue("EPIC-2", "EPIC", "In Q1");
        otherQuarterEpic.setLabels(new String[]{"2026Q1"});
        otherQuarterEpic.setRoughEstimates(Map.of("DEV", new BigDecimal("3")));

        JiraIssueEntity backlogEpic = createIssue("EPIC-3", "EPIC", "Backlog");
        backlogEpic.setLabels(null);
        backlogEpic.setRoughEstimates(Map.of("DEV", new BigDecimal("8")));

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(inQuarterEpic, otherQuarterEpic, backlogEpic));
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyEpicsResponse response = service.getEpicsForQuarter("2026Q2");

        assertEquals("2026Q2", response.quarter());
        assertEquals(3, response.epics().size());

        Map<String, PlanningEpicDto> byKey = response.epics().stream()
                .collect(java.util.stream.Collectors.toMap(PlanningEpicDto::epicKey, e -> e));
        assertTrue(byKey.get("EPIC-1").inQuarter());
        assertEquals("2026Q2", byKey.get("EPIC-1").quarterLabel());
        assertFalse(byKey.get("EPIC-2").inQuarter());
        assertEquals("2026Q1", byKey.get("EPIC-2").quarterLabel());
        assertFalse(byKey.get("EPIC-3").inQuarter());
        assertNull(byKey.get("EPIC-3").quarterLabel());
    }

    @Test
    void getEpicsForQuarter_includesEpicsWithoutEstimate_withHasEstimateFalse() {
        JiraIssueEntity epicNoEstimate = createIssue("EPIC-1", "EPIC", "No estimate");
        epicNoEstimate.setLabels(new String[]{"2026Q2"});
        // No rough estimates set

        JiraIssueEntity epicNoTeam = createIssue("EPIC-2", "EPIC", "No team");
        epicNoTeam.setLabels(new String[]{"2026Q2"});
        epicNoTeam.setRoughEstimates(Map.of("DEV", new BigDecimal("3")));
        epicNoTeam.setTeamId(null);

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of());
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epicNoEstimate, epicNoTeam));
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyEpicsResponse response = service.getEpicsForQuarter("2026Q2");

        Map<String, PlanningEpicDto> byKey = response.epics().stream()
                .collect(java.util.stream.Collectors.toMap(PlanningEpicDto::epicKey, e -> e));
        assertFalse(byKey.get("EPIC-1").hasEstimate());
        assertEquals(BigDecimal.ZERO, byKey.get("EPIC-1").totalDemandDays());
        assertTrue(byKey.get("EPIC-2").hasEstimate());
        assertFalse(byKey.get("EPIC-2").hasTeamMapping());
    }

    @Test
    void getEpicsForQuarter_priorityScoreEqualsRicePlusBoostClamped() {
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Boosted");
        epic.setLabels(new String[]{"2026Q2"});
        epic.setManualBoost(40);
        epic.setRoughEstimates(Map.of("DEV", new BigDecimal("5")));

        RiceAssessmentEntity rice = new RiceAssessmentEntity();
        rice.setIssueKey("EPIC-1");
        rice.setNormalizedScore(new BigDecimal("130.0"));

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of());
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic));
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of(rice));

        QuarterlyEpicsResponse response = service.getEpicsForQuarter("2026Q2");

        PlanningEpicDto dto = response.epics().get(0);
        // 130 + 40 = 170 → clamped to 150
        assertEquals(0, dto.priorityScore().compareTo(new BigDecimal("150.0")));
        assertEquals(40, dto.manualBoost());
    }

    @Test
    void assignEpicToQuarter_addsQuarterLabel_callsJiraClient() {
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        epic.setLabels(new String[]{"some-other-label"});

        when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));
        when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        when(riceAssessmentRepository.findByIssueKey(anyString())).thenReturn(Optional.empty());

        PlanningEpicDto result = service.assignEpicToQuarter("EPIC-1", "2026Q2");

        verify(jiraClient).updateLabels(eq("EPIC-1"), argThat(labels ->
                labels.contains("2026Q2") && labels.contains("some-other-label")));
        verify(issueRepository).save(epic);
        assertEquals("2026Q2", result.quarterLabel());
        assertTrue(result.inQuarter());
    }

    @Test
    void assignEpicToQuarter_removesOldQuarterLabels() {
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        epic.setLabels(new String[]{"2026Q1", "feature", "2025Q4"}); // old quarters

        when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));
        when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        when(riceAssessmentRepository.findByIssueKey(anyString())).thenReturn(Optional.empty());

        service.assignEpicToQuarter("EPIC-1", "2026Q2");

        verify(jiraClient).updateLabels(eq("EPIC-1"), argThat(labels ->
                labels.size() == 2 && labels.contains("2026Q2") && labels.contains("feature")));
    }

    @Test
    void assignEpicToQuarter_nullQuarter_removesAllQuarterLabels() {
        // Passing null to assignEpicToQuarter is the canonical way to remove an epic
        // from its quarter (removeEpicFromQuarter is now a private helper).
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        epic.setLabels(new String[]{"2026Q2", "feature"});

        when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));
        when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        when(riceAssessmentRepository.findByIssueKey(anyString())).thenReturn(Optional.empty());

        PlanningEpicDto result = service.assignEpicToQuarter("EPIC-1", null);

        verify(jiraClient).updateLabels(eq("EPIC-1"), argThat(labels ->
                labels.size() == 1 && labels.contains("feature") && !labels.contains("2026Q2")));
        // The epic's persisted labels no longer contain a quarter label.
        assertNull(epic.getQuarterLabel());
        // When the caller explicitly removes the quarter the DTO must report
        // inQuarter=false even if a parent project still has a quarter label —
        // otherwise the frontend would still show the epic in the column.
        assertFalse(result.inQuarter());
    }

    @Test
    void assignEpicToQuarter_nullQuarter_inQuarterFalseEvenIfParentProjectHasQuarter() {
        // Regression: even if the epic inherits a quarter from its parent project,
        // an explicit user-driven remove must surface as inQuarter=false in the
        // immediate response (otherwise the UX feels broken).
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        epic.setLabels(new String[]{"2026Q2"});
        epic.setParentKey("PROJ-1");

        JiraIssueEntity parentProject = createIssue("PROJ-1", "PROJECT", "Project");
        parentProject.setLabels(new String[]{"2026Q2"}); // parent still tagged for quarter

        when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));
        when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        when(riceAssessmentRepository.findByIssueKey(anyString())).thenReturn(Optional.empty());
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(parentProject));

        PlanningEpicDto result = service.assignEpicToQuarter("EPIC-1", null);

        // Even though epic.parent still has 2026Q2 label, the user removed it →
        // inQuarter must be false.
        assertFalse(result.inQuarter());
    }

    @Test
    void assignEpicToQuarter_rejectsNonEpic() {
        JiraIssueEntity story = createIssue("STORY-1", "STORY", "Not an epic");
        when(issueRepository.findByIssueKey("STORY-1")).thenReturn(Optional.of(story));
        when(workflowConfigService.isEpic("Story")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.assignEpicToQuarter("STORY-1", "2026Q2"));
        verifyNoInteractions(jiraClient);
    }

    @Test
    void assignEpicToQuarter_rejectsInvalidQuarterLabel() {
        // No epic lookup should happen because validation runs first
        assertThrows(IllegalArgumentException.class,
                () -> service.assignEpicToQuarter("EPIC-1", "not-a-quarter"));
        verifyNoInteractions(jiraClient);
    }

    @Test
    void setEpicBoost_rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> service.setEpicBoost("EPIC-1", 100));
        assertThrows(IllegalArgumentException.class, () -> service.setEpicBoost("EPIC-1", -100));
        verifyNoInteractions(jiraClient);
    }

    @Test
    void setEpicBoost_persistsValidValue() {
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));
        when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        when(riceAssessmentRepository.findByIssueKey(anyString())).thenReturn(Optional.empty());

        PlanningEpicDto result = service.setEpicBoost("EPIC-1", 25);

        assertEquals(25, epic.getManualBoost());
        verify(issueRepository).save(epic);
        assertEquals(25, result.manualBoost());
        verifyNoInteractions(jiraClient); // boost is local-only
    }

    @Test
    void setEpicBoost_acceptsBoundaryValues() {
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));
        when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        when(riceAssessmentRepository.findByIssueKey(anyString())).thenReturn(Optional.empty());

        service.setEpicBoost("EPIC-1", 50);
        assertEquals(50, epic.getManualBoost());

        service.setEpicBoost("EPIC-1", -50);
        assertEquals(-50, epic.getManualBoost());
    }

    @Test
    void setEpicBoost_rejectsMissingEpic() {
        // Missing epic now surfaces as EpicNotFoundException (HTTP 404 via @ResponseStatus),
        // distinguishing it from validation errors which remain IllegalArgumentException (400).
        when(issueRepository.findByIssueKey("EPIC-MISSING")).thenReturn(Optional.empty());
        assertThrows(EpicNotFoundException.class, () -> service.setEpicBoost("EPIC-MISSING", 10));
    }

    @Test
    void assignEpicToQuarter_missingEpic_throwsEpicNotFoundException() {
        when(issueRepository.findByIssueKey("EPIC-MISSING")).thenReturn(Optional.empty());
        assertThrows(EpicNotFoundException.class,
                () -> service.assignEpicToQuarter("EPIC-MISSING", "2026Q2"));
        verifyNoInteractions(jiraClient); // never reaches Jira write
    }

    @Test
    void assignEpicToQuarter_overloadedTeamsReflectsPostMutationState() {
        // Regression for H4: previously buildPlanningEpicDto returned List.of() for
        // overloadedTeams, so the frontend would never see overload after a mutation.
        // After the fix, overloadedTeams is recomputed from a quarter snapshot.
        TeamEntity team = createTeam(7L, "Small team");
        when(teamRepository.findByActiveTrue()).thenReturn(List.of(team));
        when(teamRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(team));
        when(teamRepository.findById(7L)).thenReturn(Optional.of(team));

        // 1 member, 5 workdays → tiny capacity vs a huge epic demand
        TeamMemberEntity member = createMember(1L, team, "DEV", Grade.MIDDLE, "8.0");
        when(memberRepository.findByTeamIdAndActiveTrue(7L)).thenReturn(List.of(member));
        when(workCalendarService.countWorkdays(any(), any())).thenReturn(5);

        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Huge epic");
        epic.setLabels(new String[]{"feature"}); // pre-mutation: no quarter label
        epic.setTeamId(7L);
        epic.setRoughEstimates(Map.of("DEV", new BigDecimal("100"))); // far over capacity

        when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));
        when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        when(riceAssessmentRepository.findByIssueKey(anyString())).thenReturn(Optional.empty());
        // Snapshot consults all epics in the quarter — return the same epic so demand is non-zero
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic));

        PlanningEpicDto result = service.assignEpicToQuarter("EPIC-1", "2026Q2");

        assertTrue(result.inQuarter());
        assertEquals(List.of(7L), result.overloadedTeams(),
                "Overloaded team must be reported in the response after mutation");
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

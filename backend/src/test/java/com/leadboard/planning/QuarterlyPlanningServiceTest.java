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
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    @Mock private EpicLabelPersistenceService epicLabelPersistenceService;
    @Mock private ProjectLabelPersistenceService projectLabelPersistenceService;
    @Mock private UnifiedPlanningService unifiedPlanningService;

    private QuarterlyPlanningService service;

    @BeforeEach
    void setUp() {
        service = new QuarterlyPlanningService(
                issueRepository, teamRepository, memberRepository,
                absenceService, workCalendarService, teamService,
                riceAssessmentRepository, workflowConfigService,
                jiraClient, epicLabelPersistenceService,
                projectLabelPersistenceService, unifiedPlanningService
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

        // EpicLabelPersistenceService is now a separate Spring-proxied bean (extracted to
        // dodge the self-invocation pitfall — see EpicLabelPersistenceService Javadoc).
        // In prod it persists the new labels in a REQUIRES_NEW transaction; in unit tests
        // we simulate that side-effect on the in-memory entity returned by the issueRepository
        // mock, so the reload-and-DTO-build path in assignEpicToQuarter sees the post-write
        // state. EpicLabelPersistenceServiceTest covers the persistence behaviour itself.
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            List<String> labels = invocation.getArgument(1);
            issueRepository.findByIssueKey(key).ifPresent(e ->
                    e.setLabels(labels.toArray(new String[0])));
            return null;
        }).when(epicLabelPersistenceService).mirrorEpicLabels(anyString(), anyList());

        // F70: mirror the same in-memory write for project label mutations so the
        // post-mutation reads in setProjectDesiredQuarter / getProjectCommitment
        // see the freshly-written labels.
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            List<String> labels = invocation.getArgument(1);
            issueRepository.findByIssueKey(key).ifPresent(e ->
                    e.setLabels(labels.toArray(new String[0])));
            return null;
        }).when(projectLabelPersistenceService).mirrorProjectLabels(anyString(), anyList());
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

        // Create epic under project. F70: epic must carry its OWN committed_quarter —
        // getTeamDemand no longer inherits the parent's desired_quarter.
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic One");
        epic.setParentKey("PROJ-1");
        epic.setTeamId(1L);
        epic.setLabels(new String[]{"2026Q2"});
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
        // F70: epic must own its committed_quarter; project label is PM-only.
        epic.setLabels(new String[]{"2026Q2"});
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

    // ==================== Quarter Label (F70: no inheritance from project) ====================

    @Test
    void getTeamDemand_doesNotInheritQuarterFromProject() {
        // F70 regression (H2): pre-F70 getTeamDemand used resolveQuarterLabel
        // (inheritance from parent project's quarter), which meant the F69
        // CapacityBars widget counted 12 epics while the kanban (which uses
        // resolveCommittedQuarter) showed 8. After H2 the demand widget must
        // mirror the kanban: only epics with their own committed_quarter
        // contribute to demand. Epics that only inherit from the parent
        // project's desired_quarter must be excluded.
        TeamEntity team = createTeam(1L, "Alpha");
        when(teamRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(team));
        when(memberRepository.findByTeamIdAndActiveTrue(1L)).thenReturn(List.of());

        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"}); // PM-desired, not team-committed

        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        epic.setParentKey("PROJ-1");
        epic.setTeamId(1L);
        epic.setLabels(null); // Epic has NO committed_quarter — must NOT count

        when(issueRepository.findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", 1L))
                .thenReturn(List.of(epic));
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyDemandDto demand = service.getTeamDemand(1L, "2026Q2");

        // Epic is excluded — neither projects nor unassigned should reference it.
        assertTrue(demand.projects().isEmpty(),
                "Epic without its own committed_quarter must not contribute to team demand");
        assertTrue(demand.unassignedEpics().isEmpty());
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
        // F70: each epic must carry its own 2026Q2 committed_quarter — getTeamDemand
        // no longer inherits from the parent project's desired_quarter.
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epic1 = createIssue("EPIC-1", "EPIC", "Epic 1");
        epic1.setParentKey("PROJ-1");
        epic1.setTeamId(1L);
        epic1.setManualOrder(1);
        epic1.setLabels(new String[]{"2026Q2"});
        epic1.setRoughEstimates(Map.of("DEV", new BigDecimal("8")));

        JiraIssueEntity epic2 = createIssue("EPIC-2", "EPIC", "Epic 2");
        epic2.setParentKey("PROJ-1");
        epic2.setTeamId(1L);
        epic2.setManualOrder(2);
        epic2.setLabels(new String[]{"2026Q2"});
        epic2.setRoughEstimates(Map.of("DEV", new BigDecimal("8")));

        JiraIssueEntity epic3 = createIssue("EPIC-3", "EPIC", "Epic 3");
        epic3.setParentKey("PROJ-1");
        epic3.setTeamId(1L);
        epic3.setManualOrder(3);
        epic3.setLabels(new String[]{"2026Q2"});
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
        // F70 (H1): a project is "in quarter" only when at least one child epic
        // is explicitly committed to that quarter via its own YYYYQn label.
        // The project's own label is the PM-desired signal and no longer flips
        // inQuarter on its own (would otherwise inflate the planning picture).
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Ready Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epic1 = createIssue("EPIC-1", "EPIC", "Epic One");
        epic1.setParentKey("PROJ-1");
        epic1.setTeamId(1L);
        epic1.setLabels(new String[]{"2026Q2"});
        epic1.setRoughEstimates(Map.of("DEV", new BigDecimal("10"), "QA", new BigDecimal("5")));

        JiraIssueEntity epic2 = createIssue("EPIC-2", "EPIC", "Epic Two");
        epic2.setParentKey("PROJ-1");
        epic2.setTeamId(1L);
        epic2.setLabels(new String[]{"2026Q2"});
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
        // F70: project label is PM-desired; epics must carry committed_quarter
        // labels to count toward inQuarter and downstream blockers.
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Blocked Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epic1 = createIssue("EPIC-1", "EPIC", "Epic No Estimate");
        epic1.setParentKey("PROJ-1");
        epic1.setTeamId(1L);
        epic1.setLabels(new String[]{"2026Q2"});
        // No rough estimates

        JiraIssueEntity epic2 = createIssue("EPIC-2", "EPIC", "Epic No Estimate 2");
        epic2.setParentKey("PROJ-1");
        epic2.setTeamId(1L);
        epic2.setLabels(new String[]{"2026Q2"});
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
        // F70: project label alone no longer flips inQuarter; each epic must
        // explicitly carry the committed_quarter label to count.
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Partial Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epic1 = createIssue("EPIC-1", "EPIC", "Epic Estimated");
        epic1.setParentKey("PROJ-1");
        epic1.setTeamId(1L);
        epic1.setLabels(new String[]{"2026Q2"});
        epic1.setRoughEstimates(Map.of("DEV", new BigDecimal("10")));

        // 2nd epic has rough estimates but no team
        JiraIssueEntity epic2 = createIssue("EPIC-2", "EPIC", "Epic No Team");
        epic2.setParentKey("PROJ-1");
        epic2.setTeamId(null); // No team
        epic2.setLabels(new String[]{"2026Q2"});
        epic2.setRoughEstimates(Map.of("DEV", new BigDecimal("5")));

        // 3rd epic both have
        JiraIssueEntity epic3 = createIssue("EPIC-3", "EPIC", "Epic Full");
        epic3.setParentKey("PROJ-1");
        epic3.setTeamId(1L);
        epic3.setLabels(new String[]{"2026Q2"});
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
    void testProjectsOverview_projectLabelAloneDoesNotMakeItInQuarter() {
        // F70 regression (H1): pre-F70 a project carrying a YYYYQn label was
        // automatically treated as "in this quarter" because the label WAS the
        // quarter signal. Post-F70 the project label is desired_quarter (PM-side
        // ask) — without at least one child epic explicitly committed to that
        // quarter, the project must NOT count as in-quarter; the projects view
        // would otherwise inflate the planning picture (PMs hope, teams haven't
        // committed yet).
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "PM Hopes But No Commit");
        project.setLabels(new String[]{"2026Q2"}); // desired_quarter set

        // Child epic exists but carries NO YYYYQn label — team hasn't committed.
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Not yet committed");
        epic.setParentKey("PROJ-1");
        epic.setTeamId(1L);
        epic.setLabels(new String[]{"roadmap"});
        epic.setRoughEstimates(Map.of("DEV", new BigDecimal("5")));

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic));
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        QuarterlyProjectsResponse response = service.getProjectsOverview("2026Q2");

        assertEquals(0, response.inQuarterCount(),
                "Project must NOT be in quarter when only desired_quarter is set");
        QuarterlyProjectOverviewDto dto = response.projects().get(0);
        assertFalse(dto.inQuarter(),
                "desired_quarter alone is no longer enough — at least one committed epic required");
        assertEquals("not-added", dto.planningStatus());
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
        // DB-write is delegated to EpicLabelPersistenceService (REQUIRES_NEW, via proxy)
        // so verify the delegation explicitly — issueRepository.save is no longer called
        // from QuarterlyPlanningService on the assign path.
        verify(epicLabelPersistenceService).mirrorEpicLabels(eq("EPIC-1"), argThat(labels ->
                labels.contains("2026Q2") && labels.contains("some-other-label")));
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

    // ==================== F70: setProjectDesiredQuarter ====================

    @Test
    void setProjectDesiredQuarter_addsLabel_callsJiraClient() {
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project Alpha");
        project.setLabels(new String[]{"feature"});

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));

        ProjectQuarterCommitmentDto result = service.setProjectDesiredQuarter("PROJ-1", "2026Q2");

        verify(jiraClient).updateLabels(eq("PROJ-1"), argThat(labels ->
                labels.contains("2026Q2") && labels.contains("feature")));
        verify(projectLabelPersistenceService).mirrorProjectLabels(eq("PROJ-1"), argThat(labels ->
                labels.contains("2026Q2") && labels.contains("feature")));
        assertEquals("2026Q2", result.desiredQuarter());
        assertEquals("PROJ-1", result.projectKey());
    }

    @Test
    void setProjectDesiredQuarter_nullQuarter_removesAllQuarterLabels() {
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2", "feature", "2026Q1"});

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));

        ProjectQuarterCommitmentDto result = service.setProjectDesiredQuarter("PROJ-1", null);

        // Both YYYYQn labels must be stripped, "feature" preserved.
        verify(jiraClient).updateLabels(eq("PROJ-1"), argThat(labels ->
                labels.size() == 1 && labels.contains("feature")));
        assertNull(result.desiredQuarter());
    }

    @Test
    void setProjectDesiredQuarter_rejectsInvalidQuarter() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setProjectDesiredQuarter("PROJ-1", "not-a-quarter"));
        // Validation runs before any lookup or external call.
        verifyNoInteractions(jiraClient);
        verifyNoInteractions(projectLabelPersistenceService);
    }

    @Test
    void setProjectDesiredQuarter_rejectsNonProject() {
        // boardCategory != "PROJECT" → ProjectNotFoundException (404).
        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Some epic");
        when(issueRepository.findByIssueKey("EPIC-1")).thenReturn(Optional.of(epic));

        assertThrows(ProjectNotFoundException.class,
                () -> service.setProjectDesiredQuarter("EPIC-1", "2026Q2"));
        verifyNoInteractions(jiraClient);
        verifyNoInteractions(projectLabelPersistenceService);
    }

    @Test
    void setProjectDesiredQuarter_missingProject_throwsProjectNotFoundException() {
        when(issueRepository.findByIssueKey("PROJ-MISSING")).thenReturn(Optional.empty());
        assertThrows(ProjectNotFoundException.class,
                () -> service.setProjectDesiredQuarter("PROJ-MISSING", "2026Q2"));
        verifyNoInteractions(jiraClient);
    }

    @Test
    void setProjectDesiredQuarter_returnsFreshDesiredQuarter_evenIfPersistenceMockDoesNotMutateEntity() {
        // Regression: the outer @Transactional(readOnly = true) keeps the project
        // entity in the session L1 cache. The inner REQUIRES_NEW write uses its
        // OWN session — the outer's cached instance is never automatically
        // refreshed. If setProjectDesiredQuarter relied on re-loading via
        // findByIssueKey for its response payload, it would silently return the
        // OLD desired_quarter (Hibernate returns the cached managed instance,
        // discarding the freshly-read DB row).
        //
        // This test pins the contract: the response DTO must reflect the JUST-SET
        // quarter even if the persistence bean is a true black box that does NOT
        // mutate the entity passed around by the caller — emulating production,
        // where the inner write touches a different EntityManager.
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q1"}); // initial old desired quarter

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));
        // Override the shared @BeforeEach stub: this mock must NOT mutate the
        // outer entity — exactly the production semantic for a separate session.
        doNothing().when(projectLabelPersistenceService).mirrorProjectLabels(anyString(), anyList());

        ProjectQuarterCommitmentDto result = service.setProjectDesiredQuarter("PROJ-1", "2026Q2");

        assertEquals("2026Q2", result.desiredQuarter(),
                "Response must surface the just-saved quarter even without L1-cache refresh");
    }

    // ==================== F70: getProjectCommitment ====================

    @Test
    void getProjectCommitment_groupsEpicsByTeam() {
        // Project desired = Q2. Three teams of epics:
        //   - Team 1 (Alpha): 2 committed to Q2, 1 to Q3
        //   - Team 2 (Beta):  1 uncommitted
        //   - Team 3 has no epics
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity e1 = epicForProject("EPIC-1", "PROJ-1", 1L, "2026Q2");
        JiraIssueEntity e2 = epicForProject("EPIC-2", "PROJ-1", 1L, "2026Q2");
        JiraIssueEntity e3 = epicForProject("EPIC-3", "PROJ-1", 1L, "2026Q3");
        JiraIssueEntity e4 = epicForProject("EPIC-4", "PROJ-1", 2L, null);

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(e1, e2, e3, e4));
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);

        TeamEntity alpha = createTeam(1L, "Alpha");
        TeamEntity beta = createTeam(2L, "Beta");
        when(teamRepository.findAllById(anyIterable())).thenReturn(List.of(alpha, beta));

        ProjectQuarterCommitmentDto result = service.getProjectCommitment("PROJ-1");

        assertEquals("2026Q2", result.desiredQuarter());
        assertEquals(2, result.commitmentByTeam().size());

        Map<Long, TeamCommitmentDto> byTeam = result.commitmentByTeam().stream()
                .collect(java.util.stream.Collectors.toMap(TeamCommitmentDto::teamId, t -> t));
        TeamCommitmentDto a = byTeam.get(1L);
        assertEquals(3, a.totalEpics());
        assertEquals(2, a.committedEpics(), "2 epics committed to desired Q2");
        assertEquals(1, a.otherQuarterEpics(), "1 epic moved to Q3");
        assertEquals(0, a.uncommittedEpics());

        TeamCommitmentDto b = byTeam.get(2L);
        assertEquals(1, b.totalEpics());
        assertEquals(0, b.committedEpics());
        assertEquals(1, b.uncommittedEpics(), "Epic without committed_quarter");
    }

    @Test
    void getProjectCommitment_uncommittedEpicsCounted() {
        // Project has desired_quarter, but no child epic carries any quarter label.
        // All epics must land in the "uncommitted" bucket — committed_quarter is
        // a direct label read with no inheritance.
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity e1 = epicForProject("EPIC-1", "PROJ-1", 1L, null);
        JiraIssueEntity e2 = epicForProject("EPIC-2", "PROJ-1", 1L, null);

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(e1, e2));
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        when(teamRepository.findAllById(anyIterable())).thenReturn(List.of(createTeam(1L, "Alpha")));

        ProjectQuarterCommitmentDto result = service.getProjectCommitment("PROJ-1");

        TeamCommitmentDto a = result.commitmentByTeam().get(0);
        assertEquals(2, a.totalEpics());
        assertEquals(0, a.committedEpics());
        assertEquals(2, a.uncommittedEpics());
    }

    @Test
    void getProjectCommitment_epicsWithoutTeamFallIntoSyntheticBucket() {
        // Epics without a team mapping must surface (teamId=0) so PM can see them.
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity e1 = epicForProject("EPIC-1", "PROJ-1", null, "2026Q2");

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(e1));
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);

        ProjectQuarterCommitmentDto result = service.getProjectCommitment("PROJ-1");

        assertEquals(1, result.commitmentByTeam().size());
        TeamCommitmentDto bucket = result.commitmentByTeam().get(0);
        assertEquals(0L, bucket.teamId());
        assertEquals(1, bucket.totalEpics());
        assertEquals(1, bucket.committedEpics());
    }

    // ==================== F70: getEpicsForQuarter with onlyDesired ====================

    @Test
    void getEpicsForQuarter_onlyDesiredTrue_filtersToDesiredProjects() {
        // Two projects: PROJ-A desires Q2, PROJ-B desires Q1. The Q2 view must
        // expose epics under PROJ-A only and drop PROJ-B's epic regardless of
        // its committed_quarter label.
        JiraIssueEntity projA = createIssue("PROJ-A", "PROJECT", "Project A");
        projA.setLabels(new String[]{"2026Q2"});
        JiraIssueEntity projB = createIssue("PROJ-B", "PROJECT", "Project B");
        projB.setLabels(new String[]{"2026Q1"});

        JiraIssueEntity epicA = createIssue("EPIC-A1", "EPIC", "Under A");
        epicA.setParentKey("PROJ-A");
        epicA.setLabels(new String[]{"2026Q2"});

        JiraIssueEntity epicB = createIssue("EPIC-B1", "EPIC", "Under B");
        epicB.setParentKey("PROJ-B");
        epicB.setLabels(new String[]{"2026Q1"});

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(projA, projB));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epicA, epicB));
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyEpicsResponse response = service.getEpicsForQuarter("2026Q2", true);

        assertEquals(1, response.epics().size(), "Only PROJ-A's epic survives the filter");
        assertEquals("EPIC-A1", response.epics().get(0).epicKey());
        assertEquals("2026Q2", response.epics().get(0).projectDesiredQuarter());
        assertFalse(response.epics().get(0).isStandalone());
    }

    @Test
    void getEpicsForQuarter_onlyDesiredFalse_returnsAllEpicsBackwardCompat() {
        // F69 backward-compatible behaviour: with the toggle OFF every active
        // epic must be returned, regardless of parent project's desired quarter.
        JiraIssueEntity projA = createIssue("PROJ-A", "PROJECT", "Project A");
        projA.setLabels(new String[]{"2026Q2"});
        JiraIssueEntity projB = createIssue("PROJ-B", "PROJECT", "Project B");
        projB.setLabels(new String[]{"2026Q1"});

        JiraIssueEntity epicA = createIssue("EPIC-A1", "EPIC", "Under A");
        epicA.setParentKey("PROJ-A");
        JiraIssueEntity epicB = createIssue("EPIC-B1", "EPIC", "Under B");
        epicB.setParentKey("PROJ-B");

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(projA, projB));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epicA, epicB));
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyEpicsResponse response = service.getEpicsForQuarter("2026Q2", false);

        assertEquals(2, response.epics().size(), "Both epics must be returned when toggle is off");
    }

    @Test
    void getEpicsForQuarter_standaloneEpicsAlwaysIncluded() {
        // Standalone (no parent project) epics must survive the onlyDesired filter —
        // they have no PM-driven context and need to remain visible to the team-lead.
        JiraIssueEntity proj = createIssue("PROJ-A", "PROJECT", "Project A");
        proj.setLabels(new String[]{"2026Q1"}); // desires a different quarter

        JiraIssueEntity epicChild = createIssue("EPIC-A1", "EPIC", "Under A");
        epicChild.setParentKey("PROJ-A");

        JiraIssueEntity standalone = createIssue("EPIC-S", "EPIC", "Standalone tech-debt");
        standalone.setParentKey(null); // no parent project at all

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(proj));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epicChild, standalone));
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyEpicsResponse response = service.getEpicsForQuarter("2026Q2", true);

        // EPIC-A1 dropped (parent desires Q1), EPIC-S survives (standalone).
        assertEquals(1, response.epics().size());
        PlanningEpicDto dto = response.epics().get(0);
        assertEquals("EPIC-S", dto.epicKey());
        assertTrue(dto.isStandalone(), "Standalone flag must be set on epics without a parent project");
        assertNull(dto.projectDesiredQuarter(), "No project → no desired quarter to surface");
    }

    @Test
    void getEpicsForQuarter_projectDesiredQuarterSurfacedInDto() {
        // Regression: when parent project has a desired_quarter different from the
        // currently-viewed quarter we still need to expose projectDesiredQuarter
        // on each child epic for the "PM wants Q2" badge on the team-lead view.
        JiraIssueEntity proj = createIssue("PROJ-A", "PROJECT", "Project A");
        proj.setLabels(new String[]{"2026Q2"}); // PM-desired

        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic");
        epic.setParentKey("PROJ-A");
        epic.setLabels(new String[]{"2026Q3"}); // team committed elsewhere

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(proj));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic));
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        // Use onlyDesired=false so the epic is visible regardless of the filter.
        QuarterlyEpicsResponse response = service.getEpicsForQuarter("2026Q3", false);

        assertEquals(1, response.epics().size());
        PlanningEpicDto dto = response.epics().get(0);
        assertEquals("2026Q2", dto.projectDesiredQuarter(),
                "PM-desired quarter must propagate from parent project");
        assertEquals("2026Q3", dto.quarterLabel(), "Epic's own committed quarter is its label");
    }

    @Test
    void getEpicsForQuarter_doesNotInheritQuarterFromProject() {
        // F70 bug M1 regression: even when the parent project carries
        // desired_quarter=2026Q2, an epic without its own quarter label must
        // NOT inherit that quarter on the team-lead view. The epic must still
        // appear in the response (onlyDesired=false), but with inQuarter=false
        // and quarterLabel=null — its projectDesiredQuarter exposes the PM
        // signal separately for the "PM wants Qx" badge.
        //
        // Live repro from QA: epic LB-9 with labels {q1-2026, roadmap} (no
        // YYYYQn pattern) was incorrectly surfacing as inQuarter=true under
        // parent LB-294 with desired_quarter=2026Q2.
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "Project");
        project.setLabels(new String[]{"2026Q2"}); // desired_quarter set

        JiraIssueEntity epic = createIssue("EPIC-1", "EPIC", "Epic without quarter");
        epic.setParentKey("PROJ-1");
        // labels intentionally without any YYYYQn token — mirrors LB-9
        epic.setLabels(new String[]{"q1-2026", "roadmap"});

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(epic));
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyEpicsResponse response = service.getEpicsForQuarter("2026Q2", false);

        PlanningEpicDto dto = response.epics().stream()
                .filter(e -> "EPIC-1".equals(e.epicKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("EPIC-1 missing from response"));

        assertFalse(dto.inQuarter(),
                "Epic without committed_quarter must NOT inherit inQuarter from project's desired_quarter");
        assertNull(dto.quarterLabel(),
                "quarterLabel must reflect epic's own committed quarter (none here), not the project's");
        assertEquals("2026Q2", dto.projectDesiredQuarter(),
                "projectDesiredQuarter exposes the PM signal even when not inherited as committed");
    }

    @Test
    void getEpicsForQuarter_onlyDesired_includesUncommittedEpicsFromDesiredProjects() {
        // F70 contract: when the team-lead filters to "only PM-desired Q2", we
        // include child epics of any project whose desired_quarter is Q2 —
        // even epics that have NOT yet been committed by the team-lead. Those
        // appear in the Backlog column (inQuarter=false) with a "PM wants Q2"
        // badge so the team-lead can act on the PM's request.
        JiraIssueEntity project = createIssue("PROJ-1", "PROJECT", "PM-desired project");
        project.setLabels(new String[]{"2026Q2"}); // PM desires Q2

        JiraIssueEntity uncommitted = createIssue("EPIC-1", "EPIC", "Not yet committed");
        uncommitted.setParentKey("PROJ-1");
        // No YYYYQn label at all — team-lead has not committed.
        uncommitted.setLabels(new String[]{"feature"});

        JiraIssueEntity committed = createIssue("EPIC-2", "EPIC", "Already committed to Q2");
        committed.setParentKey("PROJ-1");
        committed.setLabels(new String[]{"2026Q2"}); // explicit commit

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("EPIC")).thenReturn(List.of(uncommitted, committed));
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());
        when(workflowConfigService.isDone(anyString(), anyString())).thenReturn(false);
        when(riceAssessmentRepository.findByIssueKeyIn(anyCollection())).thenReturn(List.of());

        QuarterlyEpicsResponse response = service.getEpicsForQuarter("2026Q2", true);

        Map<String, PlanningEpicDto> byKey = response.epics().stream()
                .collect(java.util.stream.Collectors.toMap(PlanningEpicDto::epicKey, e -> e));

        assertEquals(2, response.epics().size(), "Both epics live under a PM-desired project");

        PlanningEpicDto uncommittedDto = byKey.get("EPIC-1");
        assertNotNull(uncommittedDto, "Uncommitted epic must still be returned under onlyDesired=true");
        assertFalse(uncommittedDto.inQuarter(),
                "Uncommitted epic stays in Backlog (inQuarter=false), surfaced only via projectDesiredQuarter");
        assertNull(uncommittedDto.quarterLabel(), "No committed quarter → no inherited quarterLabel");
        assertEquals("2026Q2", uncommittedDto.projectDesiredQuarter(),
                "Uncommitted epic exposes the parent project's desired quarter for the 'PM wants Q2' badge");

        PlanningEpicDto committedDto = byKey.get("EPIC-2");
        assertTrue(committedDto.inQuarter(), "Committed epic must report inQuarter=true");
        assertEquals("2026Q2", committedDto.quarterLabel());
    }

    private JiraIssueEntity epicForProject(String key, String parentKey, Long teamId, String quarter) {
        JiraIssueEntity epic = createIssue(key, "EPIC", "Epic " + key);
        epic.setParentKey(parentKey);
        if (teamId != null) {
            epic.setTeamId(teamId);
        }
        if (quarter != null) {
            epic.setLabels(new String[]{quarter});
        }
        return epic;
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

    // --- getCurrentQuarterLabel helper (F72) ---

    @Test
    void getCurrentQuarterLabelReturnsQ1ForJanuary() {
        assertEquals("2026Q1", QuarterlyPlanningService.getCurrentQuarterLabel(java.time.LocalDate.of(2026, 1, 15)));
    }

    @Test
    void getCurrentQuarterLabelReturnsQ1ForMarch() {
        assertEquals("2026Q1", QuarterlyPlanningService.getCurrentQuarterLabel(java.time.LocalDate.of(2026, 3, 31)));
    }

    @Test
    void getCurrentQuarterLabelReturnsQ2ForApril() {
        assertEquals("2026Q2", QuarterlyPlanningService.getCurrentQuarterLabel(java.time.LocalDate.of(2026, 4, 1)));
    }

    @Test
    void getCurrentQuarterLabelReturnsQ2ForMay() {
        assertEquals("2026Q2", QuarterlyPlanningService.getCurrentQuarterLabel(java.time.LocalDate.of(2026, 5, 25)));
    }

    @Test
    void getCurrentQuarterLabelReturnsQ3ForSeptember() {
        assertEquals("2026Q3", QuarterlyPlanningService.getCurrentQuarterLabel(java.time.LocalDate.of(2026, 9, 30)));
    }

    @Test
    void getCurrentQuarterLabelReturnsQ4ForOctober() {
        assertEquals("2026Q4", QuarterlyPlanningService.getCurrentQuarterLabel(java.time.LocalDate.of(2026, 10, 1)));
    }

    @Test
    void getCurrentQuarterLabelReturnsQ4ForDecember() {
        assertEquals("2026Q4", QuarterlyPlanningService.getCurrentQuarterLabel(java.time.LocalDate.of(2026, 12, 31)));
    }

    // ==================== F86: getRemainingForQuarter ====================

    private static final Long F86_TEAM = 1L;

    private UnifiedPlanningResult.PhaseSchedule phase(LocalDate start, LocalDate end, double hours) {
        return new UnifiedPlanningResult.PhaseSchedule(null, null, start, end, BigDecimal.valueOf(hours), false);
    }

    private UnifiedPlanningResult.PhaseAggregationEntry agg(double hours, LocalDate start, LocalDate end) {
        return new UnifiedPlanningResult.PhaseAggregationEntry(BigDecimal.valueOf(hours), start, end);
    }

    private UnifiedPlanningResult.PlannedStory story(Map<String, UnifiedPlanningResult.PhaseSchedule> phases) {
        return new UnifiedPlanningResult.PlannedStory(
                "S-1", "Story", null, "In Progress", null, null,
                phases, List.of(), List.of(),
                null, null, null, null, null, null, Map.of());
    }

    private UnifiedPlanningResult.PlannedEpic epic(
            String key,
            Map<String, UnifiedPlanningResult.PhaseAggregationEntry> aggregation,
            List<UnifiedPlanningResult.PlannedStory> stories) {
        return new UnifiedPlanningResult.PlannedEpic(
                key, "Epic", null, null, null,
                stories, aggregation,
                "In Progress", null, null, null, null, Map.of(),
                stories == null ? 0 : stories.size(), 0,
                stories == null || stories.isEmpty(), Map.of(), false, false);
    }

    private void planReturns(UnifiedPlanningResult.PlannedEpic... epics) {
        when(unifiedPlanningService.calculatePlan(F86_TEAM)).thenReturn(
                new UnifiedPlanningResult(F86_TEAM, OffsetDateTime.now(),
                        List.of(epics), List.of(), Map.of()));
    }

    @Test
    void remainingNowEqualsPhaseAggregationHoursInDays() {
        Map<String, UnifiedPlanningResult.PhaseAggregationEntry> aggMap = new LinkedHashMap<>();
        aggMap.put("SA", agg(40, null, null));
        aggMap.put("DEV", agg(64, null, null));
        aggMap.put("QA", agg(16, null, null));
        planReturns(epic("LB-1", aggMap, List.of()));

        QuarterlyRemainingResponse resp = service.getRemainingForQuarter(F86_TEAM, "2026Q3");
        EpicRemainingDto dto = resp.epics().get("LB-1");

        assertEquals("2026Q3", resp.quarter());
        assertEquals(F86_TEAM, resp.teamId());
        assertTrue(dto.hasEstimate());
        assertEquals(0, new BigDecimal("5.0").compareTo(dto.remainingNowByRole().get("SA")));
        assertEquals(0, new BigDecimal("8.0").compareTo(dto.remainingNowByRole().get("DEV")));
        assertEquals(0, new BigDecimal("2.0").compareTo(dto.remainingNowByRole().get("QA")));
        assertEquals(0, new BigDecimal("15.0").compareTo(dto.remainingNowDays()));
    }

    @Test
    void phaseEntirelyBeforeQuarterStartContributesZeroAtStart() {
        Map<String, UnifiedPlanningResult.PhaseAggregationEntry> aggMap = new LinkedHashMap<>();
        aggMap.put("DEV", agg(40, null, null));
        Map<String, UnifiedPlanningResult.PhaseSchedule> phases = new LinkedHashMap<>();
        phases.put("DEV", phase(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 15), 40));
        planReturns(epic("LB-1", aggMap, List.of(story(phases))));

        EpicRemainingDto dto = service.getRemainingForQuarter(F86_TEAM, "2026Q3").epics().get("LB-1");

        assertEquals(0, new BigDecimal("5.0").compareTo(dto.remainingNowByRole().get("DEV")));
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.remainingAtQuarterStartByRole().get("DEV")));
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.remainingAtQuarterStartDays()));
    }

    @Test
    void phaseEntirelyAfterQuarterStartContributesFullHoursAtStart() {
        Map<String, UnifiedPlanningResult.PhaseAggregationEntry> aggMap = new LinkedHashMap<>();
        aggMap.put("DEV", agg(40, null, null));
        Map<String, UnifiedPlanningResult.PhaseSchedule> phases = new LinkedHashMap<>();
        phases.put("DEV", phase(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 8, 1), 40));
        planReturns(epic("LB-1", aggMap, List.of(story(phases))));

        EpicRemainingDto dto = service.getRemainingForQuarter(F86_TEAM, "2026Q3").epics().get("LB-1");

        assertEquals(0, new BigDecimal("5.0").compareTo(dto.remainingAtQuarterStartByRole().get("DEV")));
        assertEquals(0, new BigDecimal("5.0").compareTo(dto.remainingAtQuarterStartDays()));
    }

    @Test
    void phaseCrossingQuarterStartIsProratedByWorkdays() {
        // Phase 2026-06-25..2026-07-10 straddles Q3 start (2026-07-01).
        // workdays(whole) = 12, workdays(Ds..end) = 8 → 80h * 8/12 = 53.3333h → 6.7 person-days.
        when(workCalendarService.countWorkdays(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 10)))
                .thenReturn(12);
        when(workCalendarService.countWorkdays(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10)))
                .thenReturn(8);

        Map<String, UnifiedPlanningResult.PhaseAggregationEntry> aggMap = new LinkedHashMap<>();
        aggMap.put("DEV", agg(80, null, null));
        Map<String, UnifiedPlanningResult.PhaseSchedule> phases = new LinkedHashMap<>();
        phases.put("DEV", phase(LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 10), 80));
        planReturns(epic("LB-1", aggMap, List.of(story(phases))));

        EpicRemainingDto dto = service.getRemainingForQuarter(F86_TEAM, "2026Q3").epics().get("LB-1");

        assertEquals(0, new BigDecimal("10.0").compareTo(dto.remainingNowByRole().get("DEV")));
        assertEquals(0, new BigDecimal("6.7").compareTo(dto.remainingAtQuarterStartByRole().get("DEV")));
        assertEquals(0, new BigDecimal("6.7").compareTo(dto.remainingAtQuarterStartDays()));
    }

    @Test
    void phaseWithNullDatesCountsFullHoursAtStart() {
        // e.g. noCapacity phase — not placed on the calendar → conservatively remains.
        Map<String, UnifiedPlanningResult.PhaseAggregationEntry> aggMap = new LinkedHashMap<>();
        aggMap.put("DEV", agg(24, null, null));
        Map<String, UnifiedPlanningResult.PhaseSchedule> phases = new LinkedHashMap<>();
        phases.put("DEV", UnifiedPlanningResult.PhaseSchedule.noCapacity(BigDecimal.valueOf(24)));
        planReturns(epic("LB-1", aggMap, List.of(story(phases))));

        EpicRemainingDto dto = service.getRemainingForQuarter(F86_TEAM, "2026Q3").epics().get("LB-1");

        assertEquals(0, new BigDecimal("3.0").compareTo(dto.remainingAtQuarterStartByRole().get("DEV")));
    }

    @Test
    void epicWithoutEstimateHasNoEstimateAndZeros() {
        planReturns(epic("LB-1", Map.of(), List.of()));

        EpicRemainingDto dto = service.getRemainingForQuarter(F86_TEAM, "2026Q3").epics().get("LB-1");

        assertFalse(dto.hasEstimate());
        assertTrue(dto.remainingNowByRole().isEmpty());
        assertTrue(dto.remainingAtQuarterStartByRole().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.remainingNowDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.remainingAtQuarterStartDays()));
    }

    @Test
    void parsesQuarterLabelToCorrectStartDate() {
        record QC(String label, LocalDate start) {}
        List<QC> cases = List.of(
                new QC("2026Q1", LocalDate.of(2026, 1, 1)),
                new QC("2026Q2", LocalDate.of(2026, 4, 1)),
                new QC("2026Q3", LocalDate.of(2026, 7, 1)),
                new QC("2026Q4", LocalDate.of(2026, 10, 1)));

        for (QC c : cases) {
            LocalDate ds = c.start();
            Map<String, UnifiedPlanningResult.PhaseAggregationEntry> aggMap = new LinkedHashMap<>();
            aggMap.put("DEV", agg(8, ds, ds));                                  // starts on Ds → full
            aggMap.put("SA", agg(8, ds.minusDays(10), ds.minusDays(1)));         // ends before Ds → zero
            planReturns(epic("LB-1", aggMap, List.of()));

            EpicRemainingDto dto = service.getRemainingForQuarter(F86_TEAM, c.label()).epics().get("LB-1");

            assertEquals(0, new BigDecimal("1.0").compareTo(dto.remainingAtQuarterStartByRole().get("DEV")), c.label());
            assertEquals(0, BigDecimal.ZERO.compareTo(dto.remainingAtQuarterStartByRole().get("SA")), c.label());
        }
    }

    @Test
    void getRemainingRejectsInvalidQuarterLabel() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getRemainingForQuarter(F86_TEAM, "2026Q5"));
        assertThrows(IllegalArgumentException.class,
                () -> service.getRemainingForQuarter(F86_TEAM, "bad"));
    }
}

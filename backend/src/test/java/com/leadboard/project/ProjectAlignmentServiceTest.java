package com.leadboard.project;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.UnifiedPlanningService;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.PlannedEpic;
import com.leadboard.rice.RiceAssessmentService;
import com.leadboard.rice.dto.RiceAssessmentDto;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectAlignmentServiceTest {

    @Mock private JiraIssueRepository issueRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private UnifiedPlanningService unifiedPlanningService;
    @Mock private WorkflowConfigService workflowConfigService;
    @Mock private RiceAssessmentService riceAssessmentService;

    private ProjectService projectService;
    private ProjectAlignmentService alignmentService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(issueRepository, teamRepository,
                unifiedPlanningService, workflowConfigService, riceAssessmentService);
        alignmentService = new ProjectAlignmentService(issueRepository, projectService,
                workflowConfigService, riceAssessmentService, teamRepository);
    }

    @Test
    void returnsEpicLaggingWhenDelayAboveGracePeriod() {
        JiraIssueEntity project = createProject("PROJ-1");
        JiraIssueEntity epic1 = createEpic("PROJ-10", 1L, "Open");
        JiraIssueEntity epic2 = createEpic("PROJ-11", 2L, "Open");

        setupProjectWithEpics(project, List.of(epic1, epic2));

        // Epic1 ends March 10, Epic2 ends March 20
        // Average = March 15, Epic2 delay = 5 days
        setupPlanning(1L, "PROJ-10", LocalDate.of(2026, 3, 10));
        setupPlanning(2L, "PROJ-11", LocalDate.of(2026, 3, 20));

        when(workflowConfigService.isDone("Open", "Epic")).thenReturn(false);
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        List<ProjectRecommendation> recs = alignmentService.getRecommendations("PROJ-1");

        assertTrue(recs.stream().anyMatch(r ->
                r.type() == RecommendationType.EPIC_LAGGING && "PROJ-11".equals(r.epicKey()) && r.delayDays() == 5));
    }

    @Test
    void doesNotRecommendWhenDelayWithinGracePeriod() {
        JiraIssueEntity project = createProject("PROJ-1");
        JiraIssueEntity epic1 = createEpic("PROJ-10", 1L, "Open");
        JiraIssueEntity epic2 = createEpic("PROJ-11", 2L, "Open");

        setupProjectWithEpics(project, List.of(epic1, epic2));

        // Epic1 ends March 14, Epic2 ends March 16
        // Average = March 15, Epic2 delay = 1 day (within grace)
        setupPlanning(1L, "PROJ-10", LocalDate.of(2026, 3, 14));
        setupPlanning(2L, "PROJ-11", LocalDate.of(2026, 3, 16));

        when(workflowConfigService.isDone("Open", "Epic")).thenReturn(false);
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        List<ProjectRecommendation> recs = alignmentService.getRecommendations("PROJ-1");

        assertTrue(recs.stream().noneMatch(r -> r.type() == RecommendationType.EPIC_LAGGING));
    }

    @Test
    void returnsAllEpicsDoneWhenAllDone() {
        JiraIssueEntity project = createProject("PROJ-1");
        JiraIssueEntity epic1 = createEpic("PROJ-10", 1L, "Done");

        setupProjectWithEpics(project, List.of(epic1));
        when(workflowConfigService.isDone("Done", "Epic")).thenReturn(true);
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        List<ProjectRecommendation> recs = alignmentService.getRecommendations("PROJ-1");

        assertEquals(1, recs.size());
        assertEquals(RecommendationType.ALL_EPICS_DONE, recs.get(0).type());
        assertEquals("INFO", recs.get(0).severity());
    }

    @Test
    void returnsEpicNoForecastWhenNoPlannedEndDate() {
        JiraIssueEntity project = createProject("PROJ-1");
        JiraIssueEntity epic1 = createEpic("PROJ-10", 1L, "Open");
        JiraIssueEntity epic2 = createEpic("PROJ-11", 2L, "Open");

        setupProjectWithEpics(project, List.of(epic1, epic2));

        // Epic1 has planning, Epic2 has no planning data
        setupPlanning(1L, "PROJ-10", LocalDate.of(2026, 3, 15));
        // Team 2 returns no planned epics
        when(unifiedPlanningService.calculatePlan(2L)).thenReturn(
                new UnifiedPlanningResult(2L, OffsetDateTime.now(), List.of(), List.of(), Map.of()));

        when(workflowConfigService.isDone("Open", "Epic")).thenReturn(false);
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        List<ProjectRecommendation> recs = alignmentService.getRecommendations("PROJ-1");

        assertTrue(recs.stream().anyMatch(r ->
                r.type() == RecommendationType.EPIC_NO_FORECAST && "PROJ-11".equals(r.epicKey())));
    }

    @Test
    void returnsRiceNotFilledWhenMissing() {
        JiraIssueEntity project = createProject("PROJ-1");
        JiraIssueEntity epic1 = createEpic("PROJ-10", 1L, "In Progress");

        setupProjectWithEpics(project, List.of(epic1));
        setupPlanning(1L, "PROJ-10", LocalDate.of(2026, 3, 15));

        when(workflowConfigService.isDone("In Progress", "Epic")).thenReturn(false);
        when(workflowConfigService.getStatusScoreWeight("In Progress")).thenReturn(25);
        when(riceAssessmentService.getAssessment("PROJ-1")).thenReturn(null);
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        List<ProjectRecommendation> recs = alignmentService.getRecommendations("PROJ-1");

        assertTrue(recs.stream().anyMatch(r -> r.type() == RecommendationType.RICE_NOT_FILLED));
    }

    @Test
    void excludesDoneEpicsFromAverageCalculation() {
        JiraIssueEntity project = createProject("PROJ-1");
        JiraIssueEntity epic1 = createEpic("PROJ-10", 1L, "Done");
        JiraIssueEntity epic2 = createEpic("PROJ-11", 2L, "Open");
        JiraIssueEntity epic3 = createEpic("PROJ-12", 3L, "Open");

        setupProjectWithEpics(project, List.of(epic1, epic2, epic3));

        // Done epic has very early date — should be excluded from average
        setupPlanning(1L, "PROJ-10", LocalDate.of(2026, 1, 1));
        setupPlanning(2L, "PROJ-11", LocalDate.of(2026, 3, 10));
        setupPlanning(3L, "PROJ-12", LocalDate.of(2026, 3, 20));

        when(workflowConfigService.isDone("Done", "Epic")).thenReturn(true);
        when(workflowConfigService.isDone("Open", "Epic")).thenReturn(false);
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        List<ProjectRecommendation> recs = alignmentService.getRecommendations("PROJ-1");

        // Average of open epics = (March 10 + March 20) / 2 = March 15
        // PROJ-12 delay = 5 days
        assertTrue(recs.stream().anyMatch(r ->
                r.type() == RecommendationType.EPIC_LAGGING && "PROJ-12".equals(r.epicKey()) && r.delayDays() == 5));
    }

    @Test
    void handlesProjectWithSingleNonDoneEpic() {
        JiraIssueEntity project = createProject("PROJ-1");
        JiraIssueEntity epic1 = createEpic("PROJ-10", 1L, "Open");

        setupProjectWithEpics(project, List.of(epic1));
        setupPlanning(1L, "PROJ-10", LocalDate.of(2026, 3, 15));

        when(workflowConfigService.isDone("Open", "Epic")).thenReturn(false);
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        List<ProjectRecommendation> recs = alignmentService.getRecommendations("PROJ-1");

        // < 2 epics with forecast → no EPIC_LAGGING
        assertTrue(recs.stream().noneMatch(r -> r.type() == RecommendationType.EPIC_LAGGING));
    }

    @Test
    void preloadAlignmentData_returnsDelayMap() {
        JiraIssueEntity epic1 = createEpic("PROJ-10", 1L, "Open");
        epic1.setParentKey("PROJ-1");
        JiraIssueEntity epic2 = createEpic("PROJ-11", 2L, "Open");
        epic2.setParentKey("PROJ-1");

        JiraIssueEntity project = createProject("PROJ-1");

        when(issueRepository.findByIssueKeyIn(List.of("PROJ-1"))).thenReturn(List.of(project));
        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));
        when(issueRepository.findByParentKeyAndBoardCategory("PROJ-1", "EPIC")).thenReturn(List.of(epic1, epic2));
        when(issueRepository.findByIssueKeyIn(List.of("PROJ-10", "PROJ-11"))).thenReturn(List.of(epic1, epic2));

        setupPlanning(1L, "PROJ-10", LocalDate.of(2026, 3, 10));
        setupPlanning(2L, "PROJ-11", LocalDate.of(2026, 3, 20));

        when(workflowConfigService.isDone("Open", "Epic")).thenReturn(false);

        Map<String, Integer> result = alignmentService.preloadAlignmentData(List.of(epic1, epic2));

        // Average = March 15, PROJ-11 delay = 5
        assertTrue(result.containsKey("PROJ-11"));
        assertEquals(5, result.get("PROJ-11"));
        // PROJ-10 is 5 days ahead, no delay
        assertFalse(result.containsKey("PROJ-10"));
    }

    @Test
    void preloadAlignmentData_emptyWhenNoProjectEpics() {
        JiraIssueEntity epic = createEpic("EPIC-1", 1L, "Open");
        // No parent key — standalone epic

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of());

        Map<String, Integer> result = alignmentService.preloadAlignmentData(List.of(epic));

        assertTrue(result.isEmpty());
    }

    // ==================== Helpers ====================

    private JiraIssueEntity createProject(String key) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setSummary("Project " + key);
        entity.setStatus("In Progress");
        entity.setBoardCategory("PROJECT");
        entity.setIssueType("Epic");
        return entity;
    }

    private JiraIssueEntity createEpic(String key, Long teamId, String status) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setSummary("Epic " + key);
        entity.setStatus(status);
        entity.setBoardCategory("EPIC");
        entity.setIssueType("Epic");
        entity.setTeamId(teamId);
        return entity;
    }

    private void setupProjectWithEpics(JiraIssueEntity project, List<JiraIssueEntity> epics) {
        when(issueRepository.findByIssueKey(project.getIssueKey())).thenReturn(Optional.of(project));

        List<String> epicKeys = epics.stream().map(JiraIssueEntity::getIssueKey).toList();
        when(issueRepository.findByParentKeyAndBoardCategory(project.getIssueKey(), "EPIC")).thenReturn(epics);
        when(issueRepository.findByIssueKeyIn(epicKeys)).thenReturn(epics);
    }

    private void setupPlanning(Long teamId, String epicKey, LocalDate endDate) {
        PlannedEpic pe = new PlannedEpic(epicKey, "Epic " + epicKey, BigDecimal.ONE,
                LocalDate.of(2026, 1, 1), endDate,
                List.of(), Map.of(), "Open", null,
                36000L, 18000L, 50, Map.of(), 5, 3,
                false, null, false);

        when(unifiedPlanningService.calculatePlan(teamId)).thenReturn(
                new UnifiedPlanningResult(teamId, OffsetDateTime.now(), List.of(pe), List.of(), Map.of()));
    }
}

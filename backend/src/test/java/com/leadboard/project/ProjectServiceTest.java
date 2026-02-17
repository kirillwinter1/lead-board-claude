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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UnifiedPlanningService unifiedPlanningService;

    @Mock
    private WorkflowConfigService workflowConfigService;

    @Mock
    private RiceAssessmentService riceAssessmentService;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(issueRepository, teamRepository,
                unifiedPlanningService, workflowConfigService, riceAssessmentService);
    }

    @Test
    void listProjects_includesProgressAndExpectedDone() {
        JiraIssueEntity project = createIssue("PROJ-1", "My Project", "In Progress", "PROJECT");
        JiraIssueEntity epic1 = createIssue("PROJ-10", "Epic 1", "Open", "EPIC");
        epic1.setTeamId(1L);
        JiraIssueEntity epic2 = createIssue("PROJ-11", "Epic 2", "Done", "EPIC");
        epic2.setTeamId(1L);

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByParentKeyAndBoardCategory("PROJ-1", "EPIC")).thenReturn(List.of(epic1, epic2));
        when(issueRepository.findByIssueKeyIn(List.of("PROJ-10", "PROJ-11"))).thenReturn(List.of(epic1, epic2));

        when(workflowConfigService.isDone("Open", "Эпик")).thenReturn(false);
        when(workflowConfigService.isDone("Done", "Эпик")).thenReturn(true);

        PlannedEpic pe1 = new PlannedEpic("PROJ-10", "Epic 1", BigDecimal.ONE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 15),
                List.of(), Map.of(), "Open", null,
                36000L, 18000L, 50, Map.of(), 5, 3,
                false, null, false);

        UnifiedPlanningResult plan = new UnifiedPlanningResult(1L, OffsetDateTime.now(),
                List.of(pe1), List.of(), Map.of());
        when(unifiedPlanningService.calculatePlan(1L)).thenReturn(plan);

        List<ProjectDto> result = projectService.listProjects();

        assertEquals(1, result.size());
        ProjectDto dto = result.get(0);
        assertEquals("PROJ-1", dto.issueKey());
        assertEquals(2, dto.childEpicCount());
        assertEquals(1, dto.completedEpicCount());
        assertEquals(50, dto.progressPercent());
        assertEquals(LocalDate.of(2026, 3, 15), dto.expectedDone());
    }

    @Test
    void getProjectWithEpics_enrichesEpicProgress() {
        JiraIssueEntity project = createIssue("PROJ-1", "My Project", "In Progress", "PROJECT");
        JiraIssueEntity epic1 = createIssue("PROJ-10", "Epic 1", "Open", "EPIC");
        epic1.setTeamId(1L);
        epic1.setDueDate(LocalDate.of(2026, 6, 1));

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));
        when(issueRepository.findByParentKeyAndBoardCategory("PROJ-1", "EPIC")).thenReturn(List.of(epic1));
        when(issueRepository.findByIssueKeyIn(List.of("PROJ-10"))).thenReturn(List.of(epic1));

        TeamEntity team = new TeamEntity();
        team.setId(1L);
        team.setName("Team Alpha");
        team.setActive(true);
        when(teamRepository.findByActiveTrue()).thenReturn(List.of(team));

        when(workflowConfigService.isDone("Open", "Эпик")).thenReturn(false);

        PlannedEpic pe1 = new PlannedEpic("PROJ-10", "Epic 1", BigDecimal.ONE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30),
                List.of(), Map.of(), "Open", LocalDate.of(2026, 6, 1),
                72000L, 36000L, 50, Map.of(), 10, 5,
                false, null, false);

        UnifiedPlanningResult plan = new UnifiedPlanningResult(1L, OffsetDateTime.now(),
                List.of(pe1), List.of(), Map.of());
        when(unifiedPlanningService.calculatePlan(1L)).thenReturn(plan);

        ProjectDetailDto result = projectService.getProjectWithEpics("PROJ-1");

        assertEquals("PROJ-1", result.issueKey());
        assertEquals(0, result.completedEpicCount());
        assertEquals(0, result.progressPercent());
        assertEquals(LocalDate.of(2026, 4, 30), result.expectedDone());

        assertEquals(1, result.epics().size());
        ChildEpicDto epicDto = result.epics().get(0);
        assertEquals("PROJ-10", epicDto.issueKey());
        assertEquals("Team Alpha", epicDto.teamName());
        assertEquals(72000L, epicDto.estimateSeconds());
        assertEquals(36000L, epicDto.loggedSeconds());
        assertEquals(50, epicDto.progressPercent());
        assertEquals(LocalDate.of(2026, 4, 30), epicDto.expectedDone());
        assertEquals(LocalDate.of(2026, 6, 1), epicDto.dueDate());
    }

    @Test
    void listProjects_handlesEmptyEpicList() {
        JiraIssueEntity project = createIssue("PROJ-1", "Empty Project", "Open", "PROJECT");

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByParentKeyAndBoardCategory("PROJ-1", "EPIC")).thenReturn(List.of());

        List<ProjectDto> result = projectService.listProjects();

        assertEquals(1, result.size());
        ProjectDto dto = result.get(0);
        assertEquals(0, dto.childEpicCount());
        assertEquals(0, dto.completedEpicCount());
        assertEquals(0, dto.progressPercent());
        assertNull(dto.expectedDone());
    }

    @Test
    void listProjects_handlesPlanningServiceFailure() {
        JiraIssueEntity project = createIssue("PROJ-1", "My Project", "In Progress", "PROJECT");
        JiraIssueEntity epic1 = createIssue("PROJ-10", "Epic 1", "Open", "EPIC");
        epic1.setTeamId(1L);
        epic1.setDueDate(LocalDate.of(2026, 5, 15));

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByParentKeyAndBoardCategory("PROJ-1", "EPIC")).thenReturn(List.of(epic1));
        when(issueRepository.findByIssueKeyIn(List.of("PROJ-10"))).thenReturn(List.of(epic1));

        when(workflowConfigService.isDone("Open", "Эпик")).thenReturn(false);
        when(unifiedPlanningService.calculatePlan(1L)).thenThrow(new RuntimeException("Planning failed"));

        List<ProjectDto> result = projectService.listProjects();

        assertEquals(1, result.size());
        ProjectDto dto = result.get(0);
        assertEquals(1, dto.childEpicCount());
        assertEquals(0, dto.completedEpicCount());
        // Falls back to dueDate when planning fails
        assertEquals(LocalDate.of(2026, 5, 15), dto.expectedDone());
    }

    @Test
    void getProjectWithEpics_issueLinkMode() {
        JiraIssueEntity project = createIssue("PROJ-1", "My Project", "In Progress", "PROJECT");
        project.setChildEpicKeys(new String[]{"PROJ-10", "PROJ-11"});

        JiraIssueEntity epic1 = createIssue("PROJ-10", "Epic 1", "Open", "EPIC");
        JiraIssueEntity epic2 = createIssue("PROJ-11", "Epic 2", "Done", "EPIC");

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));
        when(issueRepository.findByParentKeyAndBoardCategory("PROJ-1", "EPIC")).thenReturn(List.of());
        when(issueRepository.findByIssueKeyIn(List.of("PROJ-10", "PROJ-11"))).thenReturn(List.of(epic1, epic2));
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());

        when(workflowConfigService.isDone("Open", "Эпик")).thenReturn(false);
        when(workflowConfigService.isDone("Done", "Эпик")).thenReturn(true);

        ProjectDetailDto result = projectService.getProjectWithEpics("PROJ-1");

        assertEquals("PROJ-1", result.issueKey());
        assertEquals(2, result.epics().size());
        assertEquals(1, result.completedEpicCount());
        assertEquals(50, result.progressPercent());
    }

    @Test
    void listProjects_includesRiceScore() {
        JiraIssueEntity project = createIssue("PROJ-1", "My Project", "Open", "PROJECT");

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByParentKeyAndBoardCategory("PROJ-1", "EPIC")).thenReturn(List.of());

        RiceAssessmentDto riceDto = new RiceAssessmentDto(
                1L, "PROJ-1", 1L, "Business", null,
                new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("0.8"),
                null, null, new BigDecimal("2"),
                new BigDecimal("20.00"), new BigDecimal("65.50"),
                List.of()
        );
        when(riceAssessmentService.getAssessments(Set.of("PROJ-1")))
                .thenReturn(Map.of("PROJ-1", riceDto));

        List<ProjectDto> result = projectService.listProjects();

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("20.00").compareTo(result.get(0).riceScore()));
        assertEquals(0, new BigDecimal("65.50").compareTo(result.get(0).riceNormalizedScore()));
    }

    @Test
    void listProjects_riceIsNullWhenNoAssessment() {
        JiraIssueEntity project = createIssue("PROJ-1", "My Project", "Open", "PROJECT");

        when(issueRepository.findByBoardCategory("PROJECT")).thenReturn(List.of(project));
        when(issueRepository.findByParentKeyAndBoardCategory("PROJ-1", "EPIC")).thenReturn(List.of());
        when(riceAssessmentService.getAssessments(Set.of("PROJ-1"))).thenReturn(Map.of());

        List<ProjectDto> result = projectService.listProjects();

        assertEquals(1, result.size());
        assertNull(result.get(0).riceScore());
        assertNull(result.get(0).riceNormalizedScore());
    }

    @Test
    void getProjectWithEpics_computesDelayDays() {
        JiraIssueEntity project = createIssue("PROJ-1", "My Project", "In Progress", "PROJECT");
        JiraIssueEntity epic1 = createIssue("PROJ-10", "Epic 1", "Open", "EPIC");
        epic1.setTeamId(1L);
        JiraIssueEntity epic2 = createIssue("PROJ-11", "Epic 2", "Open", "EPIC");
        epic2.setTeamId(2L);

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));
        when(issueRepository.findByParentKeyAndBoardCategory("PROJ-1", "EPIC")).thenReturn(List.of(epic1, epic2));
        when(issueRepository.findByIssueKeyIn(List.of("PROJ-10", "PROJ-11"))).thenReturn(List.of(epic1, epic2));
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());
        when(workflowConfigService.isDone("Open", "Эпик")).thenReturn(false);

        // Epic1 ends March 10, Epic2 ends March 20 → average = March 15
        PlannedEpic pe1 = new PlannedEpic("PROJ-10", "Epic 1", BigDecimal.ONE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 10),
                List.of(), Map.of(), "Open", null,
                36000L, 18000L, 50, Map.of(), 5, 3,
                false, null, false);
        PlannedEpic pe2 = new PlannedEpic("PROJ-11", "Epic 2", BigDecimal.ONE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 20),
                List.of(), Map.of(), "Open", null,
                36000L, 18000L, 50, Map.of(), 5, 3,
                false, null, false);

        when(unifiedPlanningService.calculatePlan(1L)).thenReturn(
                new UnifiedPlanningResult(1L, OffsetDateTime.now(), List.of(pe1), List.of(), Map.of()));
        when(unifiedPlanningService.calculatePlan(2L)).thenReturn(
                new UnifiedPlanningResult(2L, OffsetDateTime.now(), List.of(pe2), List.of(), Map.of()));

        ProjectDetailDto result = projectService.getProjectWithEpics("PROJ-1");

        ChildEpicDto dto1 = result.epics().stream().filter(e -> "PROJ-10".equals(e.issueKey())).findFirst().orElseThrow();
        ChildEpicDto dto2 = result.epics().stream().filter(e -> "PROJ-11".equals(e.issueKey())).findFirst().orElseThrow();

        // PROJ-10 is 5 days ahead → delay = 0
        assertEquals(0, dto1.delayDays());
        // PROJ-11 is 5 days behind → delay = 5
        assertEquals(5, dto2.delayDays());
    }

    @Test
    void getProjectWithEpics_delayDaysNullWhenNoForecast() {
        JiraIssueEntity project = createIssue("PROJ-1", "My Project", "In Progress", "PROJECT");
        JiraIssueEntity epic1 = createIssue("PROJ-10", "Epic 1", "Open", "EPIC");
        epic1.setTeamId(1L);

        when(issueRepository.findByIssueKey("PROJ-1")).thenReturn(Optional.of(project));
        when(issueRepository.findByParentKeyAndBoardCategory("PROJ-1", "EPIC")).thenReturn(List.of(epic1));
        when(issueRepository.findByIssueKeyIn(List.of("PROJ-10"))).thenReturn(List.of(epic1));
        when(teamRepository.findByActiveTrue()).thenReturn(List.of());
        when(workflowConfigService.isDone("Open", "Эпик")).thenReturn(false);

        // No planning data for this epic's team
        when(unifiedPlanningService.calculatePlan(1L)).thenReturn(
                new UnifiedPlanningResult(1L, OffsetDateTime.now(), List.of(), List.of(), Map.of()));

        ProjectDetailDto result = projectService.getProjectWithEpics("PROJ-1");

        assertEquals(1, result.epics().size());
        assertNull(result.epics().get(0).delayDays());
    }

    private JiraIssueEntity createIssue(String key, String summary, String status, String boardCategory) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setSummary(summary);
        entity.setStatus(status);
        entity.setBoardCategory(boardCategory);
        entity.setIssueType("Эпик");
        return entity;
    }
}

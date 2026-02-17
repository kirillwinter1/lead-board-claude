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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final JiraIssueRepository issueRepository;
    private final TeamRepository teamRepository;
    private final UnifiedPlanningService unifiedPlanningService;
    private final WorkflowConfigService workflowConfigService;
    private final RiceAssessmentService riceAssessmentService;

    public ProjectService(JiraIssueRepository issueRepository,
                          TeamRepository teamRepository,
                          UnifiedPlanningService unifiedPlanningService,
                          WorkflowConfigService workflowConfigService,
                          RiceAssessmentService riceAssessmentService) {
        this.issueRepository = issueRepository;
        this.teamRepository = teamRepository;
        this.unifiedPlanningService = unifiedPlanningService;
        this.workflowConfigService = workflowConfigService;
        this.riceAssessmentService = riceAssessmentService;
    }

    public List<ProjectDto> listProjects() {
        List<JiraIssueEntity> projects = issueRepository.findByBoardCategory("PROJECT");

        // Batch load RICE assessments for all projects
        Set<String> projectKeys = projects.stream().map(JiraIssueEntity::getIssueKey).collect(Collectors.toSet());
        Map<String, RiceAssessmentDto> riceMap = projectKeys.isEmpty()
                ? Map.of()
                : riceAssessmentService.getAssessments(projectKeys);

        return projects.stream().map(p -> {
            List<JiraIssueEntity> epics = findChildEpics(p);
            int epicCount = epics.size();
            int completedCount = countCompletedEpics(epics);
            int progressPct = epicCount > 0 ? (completedCount * 100) / epicCount : 0;

            Map<String, PlannedEpic> planningMap = buildEpicPlanningMap(epics);
            LocalDate expectedDone = computeExpectedDone(epics, planningMap);

            RiceAssessmentDto rice = riceMap.get(p.getIssueKey());

            return new ProjectDto(
                    p.getIssueKey(),
                    p.getSummary(),
                    p.getStatus(),
                    p.getAssigneeDisplayName(),
                    epicCount,
                    completedCount,
                    progressPct,
                    expectedDone,
                    rice != null ? rice.riceScore() : null,
                    rice != null ? rice.normalizedScore() : null
            );
        }).toList();
    }

    public ProjectDetailDto getProjectWithEpics(String issueKey) {
        JiraIssueEntity project = issueRepository.findByIssueKey(issueKey)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + issueKey));

        List<JiraIssueEntity> epics = findChildEpics(project);
        Map<Long, String> teamNames = loadTeamNames();
        Map<String, PlannedEpic> planningMap = buildEpicPlanningMap(epics);

        int completedCount = countCompletedEpics(epics);
        int progressPct = epics.size() > 0 ? (completedCount * 100) / epics.size() : 0;
        LocalDate expectedDone = computeExpectedDone(epics, planningMap);

        List<ChildEpicDto> epicDtos = epics.stream().map(e -> {
            PlannedEpic planned = planningMap.get(e.getIssueKey());

            Long estimateSeconds = null;
            Long loggedSeconds = null;
            Integer epicProgressPct = null;
            LocalDate epicExpectedDone = null;

            if (planned != null) {
                estimateSeconds = planned.totalEstimateSeconds();
                loggedSeconds = planned.totalLoggedSeconds();
                epicProgressPct = planned.progressPercent();
                epicExpectedDone = planned.endDate();
            }

            return new ChildEpicDto(
                    e.getIssueKey(),
                    e.getSummary(),
                    e.getStatus(),
                    e.getTeamId() != null ? teamNames.getOrDefault(e.getTeamId(), null) : null,
                    estimateSeconds,
                    loggedSeconds,
                    epicProgressPct,
                    epicExpectedDone,
                    e.getDueDate()
            );
        }).toList();

        RiceAssessmentDto rice = riceAssessmentService.getAssessment(project.getIssueKey());

        return new ProjectDetailDto(
                project.getIssueKey(),
                project.getSummary(),
                project.getStatus(),
                project.getAssigneeDisplayName(),
                completedCount,
                progressPct,
                expectedDone,
                rice != null ? rice.riceScore() : null,
                rice != null ? rice.normalizedScore() : null,
                epicDtos
        );
    }

    /**
     * Build a map of epicKey → PlannedEpic from UnifiedPlanningService.
     * Collects unique teamIds from epics, calls calculatePlan for each team.
     */
    private Map<String, PlannedEpic> buildEpicPlanningMap(List<JiraIssueEntity> epics) {
        Set<Long> teamIds = epics.stream()
                .map(JiraIssueEntity::getTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, PlannedEpic> result = new HashMap<>();

        for (Long teamId : teamIds) {
            try {
                UnifiedPlanningResult plan = unifiedPlanningService.calculatePlan(teamId);
                for (PlannedEpic pe : plan.epics()) {
                    result.put(pe.epicKey(), pe);
                }
            } catch (Exception e) {
                log.warn("Failed to calculate plan for team {}: {}", teamId, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Count epics that are in DONE status category.
     */
    private int countCompletedEpics(List<JiraIssueEntity> epics) {
        return (int) epics.stream()
                .filter(e -> workflowConfigService.isDone(e.getStatus(), e.getIssueType()))
                .count();
    }

    /**
     * Compute project expectedDone = max(endDate) across all non-completed epics from planning.
     * Falls back to max(dueDate) if no planning data available.
     */
    private LocalDate computeExpectedDone(List<JiraIssueEntity> epics, Map<String, PlannedEpic> planningMap) {
        LocalDate maxDate = null;

        for (JiraIssueEntity epic : epics) {
            if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) {
                continue;
            }

            PlannedEpic planned = planningMap.get(epic.getIssueKey());
            LocalDate endDate = planned != null ? planned.endDate() : null;

            if (endDate == null) {
                endDate = epic.getDueDate();
            }

            if (endDate != null && (maxDate == null || endDate.isAfter(maxDate))) {
                maxDate = endDate;
            }
        }

        return maxDate;
    }

    /**
     * Find child epics using both methods:
     * 1. Parent mode: epics whose parentKey = project issueKey
     * 2. Issue links: keys stored in childEpicKeys during sync, filtered to EPIC category
     */
    private List<JiraIssueEntity> findChildEpics(JiraIssueEntity project) {
        Set<String> epicKeys = new LinkedHashSet<>();

        // 1. Parent mode
        List<JiraIssueEntity> parentEpics = issueRepository.findByParentKeyAndBoardCategory(project.getIssueKey(), "EPIC");
        parentEpics.forEach(e -> epicKeys.add(e.getIssueKey()));

        // 2. Issue link mode — childEpicKeys contains all linked issues, filter to EPICs
        String[] linkedKeys = project.getChildEpicKeys();
        if (linkedKeys != null && linkedKeys.length > 0) {
            List<JiraIssueEntity> linkedIssues = issueRepository.findByIssueKeyIn(Arrays.asList(linkedKeys));
            linkedIssues.stream()
                    .filter(i -> "EPIC".equals(i.getBoardCategory()))
                    .forEach(e -> epicKeys.add(e.getIssueKey()));
        }

        if (epicKeys.isEmpty()) return List.of();

        // Fetch all unique epics
        return issueRepository.findByIssueKeyIn(new ArrayList<>(epicKeys));
    }

    private Map<Long, String> loadTeamNames() {
        return teamRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(TeamEntity::getId, TeamEntity::getName));
    }
}

package com.leadboard.project;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.dto.UnifiedPlanningResult.PlannedEpic;
import com.leadboard.rice.RiceAssessmentService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectAlignmentService {

    private static final Logger log = LoggerFactory.getLogger(ProjectAlignmentService.class);
    private static final int GRACE_PERIOD_DAYS = 2;

    private final JiraIssueRepository issueRepository;
    private final ProjectService projectService;
    private final WorkflowConfigService workflowConfigService;
    private final RiceAssessmentService riceAssessmentService;
    private final TeamRepository teamRepository;

    public ProjectAlignmentService(JiraIssueRepository issueRepository,
                                   ProjectService projectService,
                                   WorkflowConfigService workflowConfigService,
                                   RiceAssessmentService riceAssessmentService,
                                   TeamRepository teamRepository) {
        this.issueRepository = issueRepository;
        this.projectService = projectService;
        this.workflowConfigService = workflowConfigService;
        this.riceAssessmentService = riceAssessmentService;
        this.teamRepository = teamRepository;
    }

    /**
     * Generate alignment recommendations for a project.
     */
    public List<ProjectRecommendation> getRecommendations(String projectKey) {
        JiraIssueEntity project = issueRepository.findByIssueKey(projectKey)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectKey));

        List<JiraIssueEntity> epics = projectService.findChildEpics(project);
        if (epics.isEmpty()) {
            return List.of();
        }

        Map<Long, String> teamNames = loadTeamNames();
        Map<String, PlannedEpic> planningMap = projectService.buildEpicPlanningMap(epics);

        List<JiraIssueEntity> nonDoneEpics = epics.stream()
                .filter(e -> !workflowConfigService.isDone(e.getStatus(), e.getIssueType()))
                .toList();

        // All epics done
        if (nonDoneEpics.isEmpty()) {
            return List.of(new ProjectRecommendation(
                    RecommendationType.ALL_EPICS_DONE,
                    "INFO",
                    "All epics in this project are completed.",
                    null, null, null
            ));
        }

        List<ProjectRecommendation> recommendations = new ArrayList<>();

        // Compute average expected done
        LocalDate averageExpectedDone = projectService.computeAverageExpectedDone(epics, planningMap);

        for (JiraIssueEntity epic : nonDoneEpics) {
            PlannedEpic planned = planningMap.get(epic.getIssueKey());
            LocalDate endDate = planned != null ? planned.endDate() : null;
            String teamName = epic.getTeamId() != null ? teamNames.get(epic.getTeamId()) : null;

            if (endDate == null) {
                recommendations.add(new ProjectRecommendation(
                        RecommendationType.EPIC_NO_FORECAST,
                        "WARNING",
                        String.format("Epic %s (%s) has no forecast end date. Add estimates to get alignment data.",
                                epic.getIssueKey(), epic.getSummary()),
                        epic.getIssueKey(),
                        teamName,
                        null
                ));
                continue;
            }

            if (averageExpectedDone != null) {
                long delay = ChronoUnit.DAYS.between(averageExpectedDone, endDate);
                if (delay > GRACE_PERIOD_DAYS) {
                    int delayDays = (int) delay;
                    recommendations.add(new ProjectRecommendation(
                            RecommendationType.EPIC_LAGGING,
                            "WARNING",
                            String.format("Epic %s (%s) is %d days behind the project average.%s",
                                    epic.getIssueKey(), epic.getSummary(), delayDays,
                                    teamName != null ? " Team: " + teamName : ""),
                            epic.getIssueKey(),
                            teamName,
                            delayDays
                    ));
                }
            }
        }

        // Check RICE
        if (riceAssessmentService.getAssessment(projectKey) == null) {
            // Check if project is past PLANNING status (has non-new epics)
            boolean hasActiveEpics = nonDoneEpics.stream()
                    .anyMatch(e -> {
                        int weight = workflowConfigService.getStatusScoreWeight(e.getStatus());
                        return weight > 0;
                    });
            if (hasActiveEpics) {
                recommendations.add(new ProjectRecommendation(
                        RecommendationType.RICE_NOT_FILLED,
                        "INFO",
                        "This project has no RICE assessment. Fill in RICE scores for better prioritization.",
                        null, null, null
                ));
            }
        }

        return recommendations;
    }

    /**
     * Preload alignment data for batch AutoScore calculation.
     * Returns epicKey → delayDays (only positive delays).
     */
    public Map<String, Integer> preloadAlignmentData(List<JiraIssueEntity> epics) {
        // Group epics by project
        Map<String, List<JiraIssueEntity>> projectEpics = new HashMap<>();

        // Collect parent keys that are projects
        Set<String> parentKeys = epics.stream()
                .map(JiraIssueEntity::getParentKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, String> epicToProjectKey = new HashMap<>();

        if (!parentKeys.isEmpty()) {
            List<JiraIssueEntity> parents = issueRepository.findByIssueKeyIn(new ArrayList<>(parentKeys));
            Set<String> projectKeys = parents.stream()
                    .filter(p -> "PROJECT".equals(p.getBoardCategory()))
                    .map(JiraIssueEntity::getIssueKey)
                    .collect(Collectors.toSet());

            for (JiraIssueEntity epic : epics) {
                if (epic.getParentKey() != null && projectKeys.contains(epic.getParentKey())) {
                    epicToProjectKey.put(epic.getIssueKey(), epic.getParentKey());
                }
            }
        }

        // Also check project → epic links
        List<JiraIssueEntity> allProjects = issueRepository.findByBoardCategory("PROJECT");
        Set<String> epicKeySet = epics.stream().map(JiraIssueEntity::getIssueKey).collect(Collectors.toSet());

        for (JiraIssueEntity project : allProjects) {
            String[] childKeys = project.getChildEpicKeys();
            if (childKeys != null) {
                for (String childKey : childKeys) {
                    if (epicKeySet.contains(childKey) && !epicToProjectKey.containsKey(childKey)) {
                        epicToProjectKey.put(childKey, project.getIssueKey());
                    }
                }
            }
        }

        // Group epics by project key
        Map<String, JiraIssueEntity> epicByKey = epics.stream()
                .collect(Collectors.toMap(JiraIssueEntity::getIssueKey, e -> e, (a, b) -> a));

        for (Map.Entry<String, String> entry : epicToProjectKey.entrySet()) {
            projectEpics.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(epicByKey.get(entry.getKey()));
        }

        // For each project, find ALL child epics (not just the ones in the input list)
        Map<String, Integer> result = new HashMap<>();

        for (Map.Entry<String, List<JiraIssueEntity>> entry : projectEpics.entrySet()) {
            String projectKey = entry.getKey();
            try {
                JiraIssueEntity project = issueRepository.findByIssueKey(projectKey).orElse(null);
                if (project == null) continue;

                List<JiraIssueEntity> allChildEpics = projectService.findChildEpics(project);
                Map<String, PlannedEpic> planningMap = projectService.buildEpicPlanningMap(allChildEpics);
                LocalDate average = projectService.computeAverageExpectedDone(allChildEpics, planningMap);

                if (average == null) continue;

                for (JiraIssueEntity epic : entry.getValue()) {
                    if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) continue;

                    PlannedEpic planned = planningMap.get(epic.getIssueKey());
                    LocalDate endDate = planned != null ? planned.endDate() : null;
                    if (endDate == null) continue;

                    long delay = ChronoUnit.DAYS.between(average, endDate);
                    if (delay > 0) {
                        result.put(epic.getIssueKey(), (int) delay);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to compute alignment for project {}: {}", projectKey, e.getMessage());
            }
        }

        return result;
    }

    private Map<Long, String> loadTeamNames() {
        return teamRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(TeamEntity::getId, TeamEntity::getName));
    }
}

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class QuarterlyPlanningService {

    private static final Logger log = LoggerFactory.getLogger(QuarterlyPlanningService.class);

    private final JiraIssueRepository issueRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final AbsenceService absenceService;
    private final WorkCalendarService workCalendarService;
    private final TeamService teamService;
    private final RiceAssessmentRepository riceAssessmentRepository;
    private final WorkflowConfigService workflowConfigService;
    private final JiraClient jiraClient;

    public QuarterlyPlanningService(JiraIssueRepository issueRepository,
                                     TeamRepository teamRepository,
                                     TeamMemberRepository memberRepository,
                                     AbsenceService absenceService,
                                     WorkCalendarService workCalendarService,
                                     TeamService teamService,
                                     RiceAssessmentRepository riceAssessmentRepository,
                                     WorkflowConfigService workflowConfigService,
                                     @Autowired(required = false) JiraClient jiraClient) {
        this.issueRepository = issueRepository;
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.absenceService = absenceService;
        this.workCalendarService = workCalendarService;
        this.teamService = teamService;
        this.riceAssessmentRepository = riceAssessmentRepository;
        this.workflowConfigService = workflowConfigService;
        this.jiraClient = jiraClient;
    }

    // ==================== Capacity ====================

    public QuarterlyCapacityDto getTeamCapacity(Long teamId, String quarterLabel) {
        QuarterRange range = QuarterRange.of(quarterLabel);
        TeamEntity team = teamRepository.findByIdAndActiveTrue(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        int totalWorkdays = workCalendarService.countWorkdays(range.startDate(), range.endDate());
        List<TeamMemberEntity> members = memberRepository.findByTeamIdAndActiveTrue(teamId);

        Map<String, Set<LocalDate>> absenceDates = absenceService.getTeamAbsenceDates(
                teamId, range.startDate(), range.endDate());

        PlanningConfigDto config = teamService.getPlanningConfig(teamId);
        PlanningConfigDto.GradeCoefficients gradeCoeffs = config.gradeCoefficients() != null
                ? config.gradeCoefficients()
                : PlanningConfigDto.GradeCoefficients.defaults();

        Map<String, BigDecimal> capacityByRole = new LinkedHashMap<>();
        int totalAbsenceDays = 0;

        for (TeamMemberEntity member : members) {
            String role = member.getRole();
            Set<LocalDate> memberAbsences = absenceDates.getOrDefault(member.getJiraAccountId(), Set.of());
            int absenceWorkdays = countWorkdaysInDates(memberAbsences, range.startDate(), range.endDate());
            totalAbsenceDays += absenceWorkdays;

            int availableWorkdays = totalWorkdays - absenceWorkdays;
            BigDecimal hoursPerDay = member.getHoursPerDay() != null ? member.getHoursPerDay() : new BigDecimal("6.0");
            BigDecimal gradeCoeff = getGradeCoefficient(member.getGrade(), gradeCoeffs);

            // Effective days = available workdays × (hours/8) / gradeCoefficient
            BigDecimal effectiveDays = new BigDecimal(availableWorkdays)
                    .multiply(hoursPerDay)
                    .divide(new BigDecimal("8.0"), 2, RoundingMode.HALF_UP)
                    .divide(gradeCoeff, 2, RoundingMode.HALF_UP);

            capacityByRole.merge(role, effectiveDays, BigDecimal::add);
        }

        return new QuarterlyCapacityDto(
                teamId, team.getName(), team.getColor(), quarterLabel,
                capacityByRole, totalWorkdays, totalAbsenceDays
        );
    }

    // ==================== Demand ====================

    public QuarterlyDemandDto getTeamDemand(Long teamId, String quarterLabel) {
        TeamEntity team = teamRepository.findByIdAndActiveTrue(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        QuarterlyCapacityDto capacity = getTeamCapacity(teamId, quarterLabel);

        // Load all team epics
        List<JiraIssueEntity> allEpics = issueRepository.findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", teamId);

        // Load all projects
        Map<String, JiraIssueEntity> projectsByKey = new HashMap<>();
        List<JiraIssueEntity> allProjects = issueRepository.findByBoardCategory("PROJECT");
        for (JiraIssueEntity project : allProjects) {
            projectsByKey.put(project.getIssueKey(), project);
        }

        // Build reverse index: epicKey -> projectKey (from parentKey and childEpicKeys)
        Map<String, String> epicToProjectKey = buildEpicToProjectIndex(allProjects, allEpics);

        // Resolve quarter labels: direct label or inherited from parent project
        List<JiraIssueEntity> quarterEpics = new ArrayList<>();
        for (JiraIssueEntity epic : allEpics) {
            if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) {
                continue; // Skip completed epics
            }
            String epicQuarter = resolveQuarterLabel(epic, epicToProjectKey, projectsByKey);
            if (quarterLabel.equals(epicQuarter)) {
                quarterEpics.add(epic);
            }
        }

        // Get RICE scores for projects
        Set<String> projectKeys = new HashSet<>();
        for (JiraIssueEntity epic : quarterEpics) {
            String projKey = epicToProjectKey.get(epic.getIssueKey());
            if (projKey != null) {
                projectKeys.add(projKey);
            }
        }
        Map<String, RiceAssessmentEntity> riceByKey = loadRiceScores(projectKeys);

        // Group epics by parent project
        Map<String, List<JiraIssueEntity>> epicsByProject = new LinkedHashMap<>();
        List<JiraIssueEntity> unassigned = new ArrayList<>();

        for (JiraIssueEntity epic : quarterEpics) {
            String projKey = epicToProjectKey.get(epic.getIssueKey());
            if (projKey != null && projectsByKey.containsKey(projKey)) {
                epicsByProject.computeIfAbsent(projKey, k -> new ArrayList<>()).add(epic);
            } else {
                unassigned.add(epic);
            }
        }

        // Get risk buffer
        PlanningConfigDto config = teamService.getPlanningConfig(teamId);
        BigDecimal riskBuffer = config.riskBuffer() != null ? config.riskBuffer() : new BigDecimal("0.2");

        // Build project demand DTOs sorted by priority score
        List<ProjectDemandDto> projectDemands = new ArrayList<>();
        Map<String, BigDecimal> remainingCapacity = new LinkedHashMap<>(capacity.capacityByRole());

        for (Map.Entry<String, List<JiraIssueEntity>> entry : epicsByProject.entrySet()) {
            String projectKey = entry.getKey();
            JiraIssueEntity project = projectsByKey.get(projectKey);
            List<JiraIssueEntity> epics = entry.getValue();
            RiceAssessmentEntity rice = riceByKey.get(projectKey);

            BigDecimal riceScore = rice != null ? rice.getNormalizedScore() : BigDecimal.ZERO;
            Integer boost = project != null ? project.getManualBoost() : 0;
            BigDecimal priorityScore = computePriorityScore(riceScore, boost);

            projectDemands.add(buildProjectDemand(
                    projectKey, project, epics, priorityScore, riceScore, boost,
                    riskBuffer, remainingCapacity, quarterLabel
            ));
        }

        // Sort by priority score descending
        projectDemands.sort(Comparator.comparing(ProjectDemandDto::priorityScore).reversed());

        // Re-compute overCapacity based on sorted priority order
        remainingCapacity = new LinkedHashMap<>(capacity.capacityByRole());
        List<ProjectDemandDto> sortedProjects = new ArrayList<>();
        for (ProjectDemandDto pd : projectDemands) {
            ProjectDemandDto recalculated = recalculateCapacityFit(pd, riskBuffer, remainingCapacity);
            sortedProjects.add(recalculated);
        }

        // Build unassigned epic demands
        List<EpicDemandDto> unassignedDemands = new ArrayList<>();
        for (JiraIssueEntity epic : unassigned) {
            unassignedDemands.add(buildEpicDemand(epic, riskBuffer, remainingCapacity, quarterLabel));
        }

        return new QuarterlyDemandDto(
                teamId, team.getName(), quarterLabel,
                capacity, sortedProjects, unassignedDemands
        );
    }

    // ==================== Summary ====================

    public QuarterlySummaryDto getSummary(String quarterLabel) {
        List<TeamEntity> teams = teamRepository.findByActiveTrue();
        List<String> availableQuarters = extractQuarterLabels();

        List<QuarterlySummaryDto.TeamQuarterlySnapshotDto> snapshots = new ArrayList<>();

        for (TeamEntity team : teams) {
            try {
                QuarterlyCapacityDto capacity = getTeamCapacity(team.getId(), quarterLabel);
                QuarterlyDemandDto demand = getTeamDemand(team.getId(), quarterLabel);

                Map<String, BigDecimal> demandByRole = aggregateDemand(demand);
                Map<String, BigDecimal> utilizationPct = computeUtilization(capacity.capacityByRole(), demandByRole);
                boolean overloaded = utilizationPct.values().stream()
                        .anyMatch(pct -> pct.compareTo(new BigDecimal("100")) > 0);

                snapshots.add(new QuarterlySummaryDto.TeamQuarterlySnapshotDto(
                        team.getId(), team.getName(), team.getColor(),
                        capacity.capacityByRole(), demandByRole, utilizationPct, overloaded
                ));
            } catch (Exception e) {
                log.warn("Failed to compute quarterly snapshot for team {}: {}", team.getName(), e.getMessage());
            }
        }

        return new QuarterlySummaryDto(quarterLabel, snapshots, availableQuarters);
    }

    // ==================== Project View ====================

    public ProjectViewDto getProjectView(String projectKey, String quarterLabel) {
        JiraIssueEntity project = issueRepository.findByIssueKey(projectKey)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectKey));

        RiceAssessmentEntity rice = riceAssessmentRepository.findByIssueKey(projectKey).orElse(null);
        BigDecimal riceScore = rice != null ? rice.getNormalizedScore() : BigDecimal.ZERO;
        Integer boost = project.getManualBoost() != null ? project.getManualBoost() : 0;
        BigDecimal priorityScore = computePriorityScore(riceScore, boost);

        // Find child epics
        String[] childKeys = project.getChildEpicKeys();
        List<JiraIssueEntity> childEpics = childKeys != null && childKeys.length > 0
                ? issueRepository.findByIssueKeyIn(List.of(childKeys))
                : List.of();

        // Filter by quarter (direct or inherited from project)
        // All child epics belong to this project
        Map<String, String> epicToProj = new HashMap<>();
        for (JiraIssueEntity epic : childEpics) {
            epicToProj.put(epic.getIssueKey(), projectKey);
        }
        Map<String, JiraIssueEntity> projectMap = Map.of(projectKey, project);
        List<JiraIssueEntity> quarterEpics = childEpics.stream()
                .filter(e -> !workflowConfigService.isDone(e.getStatus(), e.getIssueType()))
                .filter(e -> quarterLabel.equals(resolveQuarterLabel(e, epicToProj, projectMap)))
                .toList();

        // Group by team
        Map<Long, List<JiraIssueEntity>> epicsByTeam = quarterEpics.stream()
                .filter(e -> e.getTeamId() != null)
                .collect(Collectors.groupingBy(JiraIssueEntity::getTeamId));

        PlanningConfigDto defaultConfig = PlanningConfigDto.defaults();
        BigDecimal defaultRiskBuffer = new BigDecimal("0.2");

        List<ProjectViewDto.TeamAllocationDto> teamAllocations = new ArrayList<>();
        for (Map.Entry<Long, List<JiraIssueEntity>> entry : epicsByTeam.entrySet()) {
            Long teamId = entry.getKey();
            List<JiraIssueEntity> epics = entry.getValue();

            TeamEntity team = teamRepository.findById(teamId).orElse(null);
            if (team == null) continue;

            QuarterlyCapacityDto capacity = getTeamCapacity(teamId, quarterLabel);

            PlanningConfigDto config = teamService.getPlanningConfig(teamId);
            BigDecimal riskBuffer = config.riskBuffer() != null ? config.riskBuffer() : defaultRiskBuffer;

            Map<String, BigDecimal> projectDemand = new LinkedHashMap<>();
            List<EpicDemandDto> epicDemands = new ArrayList<>();
            for (JiraIssueEntity epic : epics) {
                Map<String, BigDecimal> epicDemand = computeEpicDemand(epic, riskBuffer);
                epicDemands.add(new EpicDemandDto(
                        epic.getIssueKey(), epic.getSummary(), epic.getStatus(),
                        epic.getManualOrder(), epicDemand, false, quarterLabel
                ));
                for (Map.Entry<String, BigDecimal> de : epicDemand.entrySet()) {
                    projectDemand.merge(de.getKey(), de.getValue(), BigDecimal::add);
                }
            }

            boolean overloaded = false;
            for (Map.Entry<String, BigDecimal> de : projectDemand.entrySet()) {
                BigDecimal cap = capacity.capacityByRole().getOrDefault(de.getKey(), BigDecimal.ZERO);
                if (de.getValue().compareTo(cap) > 0) {
                    overloaded = true;
                    break;
                }
            }

            teamAllocations.add(new ProjectViewDto.TeamAllocationDto(
                    teamId, team.getName(), team.getColor(),
                    epicDemands, capacity.capacityByRole(), projectDemand, overloaded
            ));
        }

        return new ProjectViewDto(projectKey, project.getSummary(), priorityScore, boost, quarterLabel, teamAllocations);
    }

    // ==================== Boost ====================

    @Transactional
    public void updateProjectBoost(String projectKey, int boost) {
        JiraIssueEntity project = issueRepository.findByIssueKey(projectKey)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectKey));
        int clamped = Math.max(-50, Math.min(50, boost));
        project.setManualBoost(clamped);
        issueRepository.save(project);
    }

    // ==================== Available Quarters ====================

    public List<String> getAvailableQuarters() {
        List<String> quarters = extractQuarterLabels();
        // Ensure current quarter is always included
        String current = QuarterRange.currentQuarterLabel();
        if (!quarters.contains(current)) {
            quarters = new ArrayList<>(quarters);
            quarters.add(current);
            quarters.sort(Comparator.naturalOrder());
        }
        return quarters;
    }

    // ==================== Projects Overview ====================

    public QuarterlyProjectsResponse getProjectsOverview(String quarterLabel) {
        List<JiraIssueEntity> allProjects = issueRepository.findByBoardCategory("PROJECT");
        List<TeamEntity> allTeams = teamRepository.findByActiveTrue();
        Map<Long, TeamEntity> teamsById = new HashMap<>();
        for (TeamEntity team : allTeams) {
            teamsById.put(team.getId(), team);
        }

        // Load all epics (across all teams) for project overview
        List<JiraIssueEntity> allEpics = issueRepository.findByBoardCategory("EPIC");
        Map<String, String> epicToProjectKey = buildEpicToProjectIndex(allProjects, allEpics);
        Map<String, JiraIssueEntity> projectsByKey = new HashMap<>();
        for (JiraIssueEntity p : allProjects) {
            projectsByKey.put(p.getIssueKey(), p);
        }

        // Build project -> child epics map
        Map<String, List<JiraIssueEntity>> epicsByProject = new LinkedHashMap<>();
        for (JiraIssueEntity epic : allEpics) {
            String projKey = epicToProjectKey.get(epic.getIssueKey());
            if (projKey != null) {
                epicsByProject.computeIfAbsent(projKey, k -> new ArrayList<>()).add(epic);
            }
        }

        // RICE scores
        Set<String> projectKeys = new HashSet<>(projectsByKey.keySet());
        Map<String, RiceAssessmentEntity> riceByKey = loadRiceScores(projectKeys);

        // Default risk buffer for demand calculation
        BigDecimal defaultRiskBuffer = new BigDecimal("0.2");

        List<QuarterlyProjectOverviewDto> projectDtos = new ArrayList<>();
        Set<Long> teamsInvolvedSet = new HashSet<>();
        int totalEpicsInQuarter = 0;
        int estimatedEpicsInQuarter = 0;

        for (JiraIssueEntity project : allProjects) {
            String projectKey = project.getIssueKey();
            List<JiraIssueEntity> childEpics = epicsByProject.getOrDefault(projectKey, List.of());

            // Filter to non-done epics
            List<JiraIssueEntity> activeEpics = childEpics.stream()
                    .filter(e -> !workflowConfigService.isDone(e.getStatus(), e.getIssueType()))
                    .toList();

            // Resolve quarter for each active epic
            List<JiraIssueEntity> quarterEpics = new ArrayList<>();
            for (JiraIssueEntity epic : activeEpics) {
                String epicQuarter = resolveQuarterLabel(epic, epicToProjectKey, projectsByKey);
                if (quarterLabel.equals(epicQuarter)) {
                    quarterEpics.add(epic);
                }
            }

            // Determine if project is in quarter
            boolean projectHasQuarterLabel = quarterLabel.equals(project.getQuarterLabel());
            boolean hasEpicsInQuarter = !quarterEpics.isEmpty();
            boolean inQuarter = projectHasQuarterLabel || hasEpicsInQuarter;

            // Use all active epics for coverage calculations if project is in quarter
            List<JiraIssueEntity> epicsForCoverage = inQuarter ? activeEpics : List.of();

            // Rough estimate coverage
            int totalEpicCount = epicsForCoverage.size();
            int roughEstimatedCount = 0;
            int teamMappedCount = 0;
            for (JiraIssueEntity epic : epicsForCoverage) {
                if (epic.getRoughEstimates() != null && !epic.getRoughEstimates().isEmpty()) {
                    roughEstimatedCount++;
                }
                if (epic.getTeamId() != null) {
                    teamMappedCount++;
                }
            }

            int roughCoverage = totalEpicCount > 0 ? Math.round((roughEstimatedCount * 100f) / totalEpicCount) : 0;
            int teamMappingCoverage = totalEpicCount > 0 ? Math.round((teamMappedCount * 100f) / totalEpicCount) : 0;

            // Planning status
            String planningStatus;
            if (!inQuarter) {
                planningStatus = "not-added";
            } else if (roughCoverage == 100 && teamMappingCoverage == 100) {
                planningStatus = "ready";
            } else if (roughCoverage < 60 || teamMappingCoverage < 80) {
                planningStatus = "blocked";
            } else {
                planningStatus = "partial";
            }

            // Demand calculation
            BigDecimal demandDays = null;
            if (inQuarter && roughCoverage == 100) {
                BigDecimal totalDemand = BigDecimal.ZERO;
                for (JiraIssueEntity epic : quarterEpics) {
                    Map<String, BigDecimal> epicDemand = computeEpicDemand(epic, defaultRiskBuffer);
                    for (BigDecimal val : epicDemand.values()) {
                        totalDemand = totalDemand.add(val);
                    }
                }
                demandDays = totalDemand;
            }

            // Forecast label
            String forecastLabel = demandDays != null
                    ? demandDays.setScale(0, java.math.RoundingMode.HALF_UP) + "d demand"
                    : "Demand unavailable";

            // Blockers
            List<String> blockers = new ArrayList<>();
            if (inQuarter) {
                int missingRough = totalEpicCount - roughEstimatedCount;
                if (missingRough > 0) {
                    blockers.add(missingRough + " epic" + (missingRough > 1 ? "s" : "") + " without rough estimates");
                }
                int missingTeam = totalEpicCount - teamMappedCount;
                if (missingTeam > 0) {
                    blockers.add(missingTeam + " epic" + (missingTeam > 1 ? "s" : "") + " without team mapping");
                }
            }

            // Risk
            String risk;
            if (blockers.size() >= 2 || roughCoverage < 50) {
                risk = "high";
            } else if (!blockers.isEmpty() || roughCoverage < 80 || teamMappingCoverage < 90) {
                risk = "medium";
            } else {
                risk = "low";
            }

            // Teams
            Set<Long> projectTeamIds = new LinkedHashSet<>();
            for (JiraIssueEntity epic : activeEpics) {
                if (epic.getTeamId() != null) {
                    projectTeamIds.add(epic.getTeamId());
                }
            }
            List<QuarterlyProjectOverviewDto.TeamRef> teamRefs = new ArrayList<>();
            for (Long teamId : projectTeamIds) {
                TeamEntity team = teamsById.get(teamId);
                if (team != null) {
                    teamRefs.add(new QuarterlyProjectOverviewDto.TeamRef(team.getId(), team.getName(), team.getColor()));
                }
            }

            // RICE + Priority
            RiceAssessmentEntity rice = riceByKey.get(projectKey);
            BigDecimal riceScore = rice != null ? rice.getNormalizedScore() : BigDecimal.ZERO;
            Integer boost = project.getManualBoost() != null ? project.getManualBoost() : 0;
            BigDecimal priorityScore = computePriorityScore(riceScore, boost);

            // Epic overview DTOs
            List<QuarterlyProjectOverviewDto.EpicOverviewDto> epicOverviews = new ArrayList<>();
            for (JiraIssueEntity epic : activeEpics) {
                boolean hasRough = epic.getRoughEstimates() != null && !epic.getRoughEstimates().isEmpty();
                boolean hasTeam = epic.getTeamId() != null;

                List<QuarterlyProjectOverviewDto.TeamRef> epicTeams = new ArrayList<>();
                if (epic.getTeamId() != null) {
                    TeamEntity team = teamsById.get(epic.getTeamId());
                    if (team != null) {
                        epicTeams.add(new QuarterlyProjectOverviewDto.TeamRef(team.getId(), team.getName(), team.getColor()));
                    }
                }

                List<String> epicBlockers = new ArrayList<>();
                if (!hasRough) epicBlockers.add("No rough estimates");
                if (!hasTeam) epicBlockers.add("No team mapping");

                epicOverviews.add(new QuarterlyProjectOverviewDto.EpicOverviewDto(
                        epic.getIssueKey(), epic.getSummary(), epicTeams, hasRough, hasTeam, epicBlockers
                ));
            }

            projectDtos.add(new QuarterlyProjectOverviewDto(
                    projectKey, project.getSummary(), inQuarter,
                    inQuarter ? quarterLabel : null,
                    priorityScore, riceScore, boost,
                    totalEpicCount, roughCoverage, teamMappingCoverage,
                    planningStatus, demandDays, forecastLabel, risk,
                    teamRefs, blockers, epicOverviews
            ));

            // Summary stats
            if (inQuarter) {
                teamsInvolvedSet.addAll(projectTeamIds);
                totalEpicsInQuarter += totalEpicCount;
                estimatedEpicsInQuarter += roughEstimatedCount;
            }
        }

        // Sort by priority descending
        projectDtos.sort(Comparator.comparing(QuarterlyProjectOverviewDto::priorityScore).reversed());

        // Summary counts
        int inQuarterCount = 0, readyCount = 0, blockedCount = 0, partialCount = 0;
        for (QuarterlyProjectOverviewDto dto : projectDtos) {
            switch (dto.planningStatus()) {
                case "ready" -> { inQuarterCount++; readyCount++; }
                case "partial" -> { inQuarterCount++; partialCount++; }
                case "blocked" -> { inQuarterCount++; blockedCount++; }
                default -> {} // not-added
            }
        }

        int roughCoveragePct = totalEpicsInQuarter > 0
                ? Math.round((estimatedEpicsInQuarter * 100f) / totalEpicsInQuarter)
                : 0;

        return new QuarterlyProjectsResponse(
                quarterLabel, inQuarterCount, readyCount, blockedCount, partialCount,
                teamsInvolvedSet.size(), totalEpicsInQuarter, roughCoveragePct,
                projectDtos
        );
    }

    // ==================== Teams Overview ====================

    public List<QuarterlyTeamOverviewDto> getTeamsOverview(String quarterLabel) {
        List<TeamEntity> teams = teamRepository.findByActiveTrue();
        List<JiraIssueEntity> allProjects = issueRepository.findByBoardCategory("PROJECT");
        List<JiraIssueEntity> allEpics = issueRepository.findByBoardCategory("EPIC");
        Map<String, String> epicToProjectKey = buildEpicToProjectIndex(allProjects, allEpics);
        Map<String, JiraIssueEntity> projectsByKey = new HashMap<>();
        for (JiraIssueEntity p : allProjects) {
            projectsByKey.put(p.getIssueKey(), p);
        }

        BigDecimal defaultRiskBuffer = new BigDecimal("0.2");

        // Pre-compute project planning statuses
        Map<String, String> projectStatusMap = new HashMap<>();
        QuarterlyProjectsResponse projectsOverview = getProjectsOverview(quarterLabel);
        for (QuarterlyProjectOverviewDto po : projectsOverview.projects()) {
            projectStatusMap.put(po.projectKey(), po.planningStatus());
        }

        List<QuarterlyTeamOverviewDto> result = new ArrayList<>();

        for (TeamEntity team : teams) {
            try {
                QuarterlyCapacityDto capacity = getTeamCapacity(team.getId(), quarterLabel);

                // Find team's quarter epics
                List<JiraIssueEntity> teamEpics = issueRepository
                        .findByBoardCategoryAndTeamIdOrderByManualOrderAsc("EPIC", team.getId());
                List<JiraIssueEntity> quarterEpics = new ArrayList<>();
                for (JiraIssueEntity epic : teamEpics) {
                    if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) continue;
                    String epicQuarter = resolveQuarterLabel(epic, epicToProjectKey, projectsByKey);
                    if (quarterLabel.equals(epicQuarter)) {
                        quarterEpics.add(epic);
                    }
                }

                // Compute demand by role
                PlanningConfigDto config = teamService.getPlanningConfig(team.getId());
                BigDecimal riskBuffer = config.riskBuffer() != null ? config.riskBuffer() : defaultRiskBuffer;

                Map<String, BigDecimal> demandByRole = new LinkedHashMap<>();
                Map<String, BigDecimal> remainingCap = new LinkedHashMap<>(capacity.capacityByRole());
                int overloadedEpics = 0;

                for (JiraIssueEntity epic : quarterEpics) {
                    Map<String, BigDecimal> epicDemand = computeEpicDemand(epic, riskBuffer);
                    boolean epicOverloaded = false;
                    for (Map.Entry<String, BigDecimal> entry : epicDemand.entrySet()) {
                        demandByRole.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
                        BigDecimal remaining = remainingCap.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                        if (remaining.compareTo(entry.getValue()) < 0) {
                            epicOverloaded = true;
                        }
                        remainingCap.put(entry.getKey(), remaining.subtract(entry.getValue()));
                    }
                    if (epicOverloaded) overloadedEpics++;
                }

                BigDecimal totalCapacity = capacity.capacityByRole().values().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalDemand = demandByRole.values().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal gapDays = totalCapacity.subtract(totalDemand);
                int utilization = totalCapacity.compareTo(BigDecimal.ZERO) > 0
                        ? totalDemand.multiply(new BigDecimal("100"))
                            .divide(totalCapacity, 0, java.math.RoundingMode.HALF_UP).intValue()
                        : 0;

                String risk = utilization > 100 ? "high" : utilization > 85 ? "medium" : "low";

                // Impacting projects
                Set<String> impactingProjectKeys = new LinkedHashSet<>();
                for (JiraIssueEntity epic : quarterEpics) {
                    String projKey = epicToProjectKey.get(epic.getIssueKey());
                    if (projKey != null) {
                        impactingProjectKeys.add(projKey);
                    }
                }

                List<QuarterlyTeamOverviewDto.ProjectRef> impactingProjects = new ArrayList<>();
                for (String projKey : impactingProjectKeys) {
                    JiraIssueEntity proj = projectsByKey.get(projKey);
                    if (proj != null) {
                        impactingProjects.add(new QuarterlyTeamOverviewDto.ProjectRef(
                                projKey, proj.getSummary(),
                                projectStatusMap.getOrDefault(projKey, "not-added")
                        ));
                    }
                }

                result.add(new QuarterlyTeamOverviewDto(
                        team.getId(), team.getName(), team.getColor(),
                        totalCapacity, totalDemand, gapDays, utilization,
                        capacity.capacityByRole(), demandByRole,
                        overloadedEpics, risk, impactingProjects
                ));
            } catch (Exception e) {
                log.warn("Failed to compute teams overview for team {}: {}", team.getName(), e.getMessage());
            }
        }

        // Sort by utilization descending
        result.sort(Comparator.comparingInt(QuarterlyTeamOverviewDto::utilization).reversed());
        return result;
    }

    // ==================== F69: Epics for Quarter (Kanban view) ====================

    /**
     * Returns every epic in the system enriched with quarter membership,
     * priority score, demand, and capacity-overload flags for the requested quarter.
     */
    public QuarterlyEpicsResponse getEpicsForQuarter(String quarterLabel) {
        List<JiraIssueEntity> allEpics = loadAllEpics();
        List<JiraIssueEntity> allProjects = issueRepository.findByBoardCategory("PROJECT");

        Map<String, JiraIssueEntity> projectsByKey = new HashMap<>();
        for (JiraIssueEntity p : allProjects) {
            projectsByKey.put(p.getIssueKey(), p);
        }
        Map<String, String> epicToProjectKey = buildEpicToProjectIndex(allProjects, allEpics);

        Map<Long, TeamEntity> teamsById = new HashMap<>();
        for (TeamEntity t : teamRepository.findByActiveTrue()) {
            teamsById.put(t.getId(), t);
        }

        // RICE scores indexed by epic key (RICE is on epics OR projects — try epic first, fallback project)
        Set<String> riceKeys = new HashSet<>();
        for (JiraIssueEntity e : allEpics) riceKeys.add(e.getIssueKey());
        riceKeys.addAll(projectsByKey.keySet());
        Map<String, RiceAssessmentEntity> riceByKey = loadRiceScores(riceKeys);

        BigDecimal defaultRiskBuffer = new BigDecimal("0.2");

        // Pre-compute capacity-by-role per team for overload detection
        Map<Long, Map<String, BigDecimal>> capacityByTeam = new HashMap<>();
        Map<Long, BigDecimal> riskBufferByTeam = new HashMap<>();
        for (TeamEntity team : teamsById.values()) {
            try {
                capacityByTeam.put(team.getId(), getTeamCapacity(team.getId(), quarterLabel).capacityByRole());
                PlanningConfigDto cfg = teamService.getPlanningConfig(team.getId());
                riskBufferByTeam.put(team.getId(),
                        cfg.riskBuffer() != null ? cfg.riskBuffer() : defaultRiskBuffer);
            } catch (Exception e) {
                log.warn("Failed to compute capacity for team {}: {}", team.getName(), e.getMessage());
                capacityByTeam.put(team.getId(), Map.of());
                riskBufferByTeam.put(team.getId(), defaultRiskBuffer);
            }
        }

        // Sum demand by team for all epics currently in the requested quarter
        Map<Long, Map<String, BigDecimal>> demandByTeamInQuarter = new HashMap<>();
        for (JiraIssueEntity epic : allEpics) {
            if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) continue;
            String epicQuarter = resolveQuarterLabel(epic, epicToProjectKey, projectsByKey);
            if (!quarterLabel.equals(epicQuarter)) continue;
            Long teamId = epic.getTeamId();
            if (teamId == null) continue;
            BigDecimal risk = riskBufferByTeam.getOrDefault(teamId, defaultRiskBuffer);
            Map<String, BigDecimal> epicDemand = computeEpicDemand(epic, risk);
            Map<String, BigDecimal> teamDemand = demandByTeamInQuarter.computeIfAbsent(teamId, k -> new LinkedHashMap<>());
            for (Map.Entry<String, BigDecimal> e : epicDemand.entrySet()) {
                teamDemand.merge(e.getKey(), e.getValue(), BigDecimal::add);
            }
        }

        // Set of teams currently overloaded in this quarter
        Set<Long> overloadedTeamIds = new HashSet<>();
        for (Map.Entry<Long, Map<String, BigDecimal>> entry : demandByTeamInQuarter.entrySet()) {
            Long teamId = entry.getKey();
            Map<String, BigDecimal> capacity = capacityByTeam.getOrDefault(teamId, Map.of());
            for (Map.Entry<String, BigDecimal> roleDemand : entry.getValue().entrySet()) {
                BigDecimal cap = capacity.getOrDefault(roleDemand.getKey(), BigDecimal.ZERO);
                if (roleDemand.getValue().compareTo(cap) > 0) {
                    overloadedTeamIds.add(teamId);
                    break;
                }
            }
        }

        List<PlanningEpicDto> result = new ArrayList<>();
        for (JiraIssueEntity epic : allEpics) {
            if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) continue;

            String projectKey = epicToProjectKey.get(epic.getIssueKey());
            JiraIssueEntity project = projectKey != null ? projectsByKey.get(projectKey) : null;

            String epicQuarter = resolveQuarterLabel(epic, epicToProjectKey, projectsByKey);
            boolean inQuarter = quarterLabel.equals(epicQuarter);

            // RICE on epic first, fall back to parent project's RICE
            RiceAssessmentEntity rice = riceByKey.get(epic.getIssueKey());
            if (rice == null && projectKey != null) rice = riceByKey.get(projectKey);
            BigDecimal riceScore = rice != null ? rice.getNormalizedScore() : BigDecimal.ZERO;
            Integer boost = epic.getManualBoost() != null ? epic.getManualBoost() : 0;
            BigDecimal priorityScore = computePriorityScore(riceScore, boost);

            BigDecimal riskBuffer = epic.getTeamId() != null
                    ? riskBufferByTeam.getOrDefault(epic.getTeamId(), defaultRiskBuffer)
                    : defaultRiskBuffer;
            Map<String, BigDecimal> demand = computeEpicDemand(epic, riskBuffer);
            BigDecimal totalDemand = demand.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            boolean hasEstimate = epic.getRoughEstimates() != null && !epic.getRoughEstimates().isEmpty();
            boolean hasTeamMapping = epic.getTeamId() != null;

            List<PlanningEpicDto.TeamRef> epicTeams = new ArrayList<>();
            if (epic.getTeamId() != null) {
                TeamEntity team = teamsById.get(epic.getTeamId());
                if (team != null) {
                    epicTeams.add(new PlanningEpicDto.TeamRef(team.getId(), team.getName(), team.getColor()));
                }
            }

            // Per-epic overload: only relevant if epic is currently in this quarter
            List<Long> epicOverloaded = new ArrayList<>();
            if (inQuarter && epic.getTeamId() != null && overloadedTeamIds.contains(epic.getTeamId())) {
                epicOverloaded.add(epic.getTeamId());
            }

            result.add(new PlanningEpicDto(
                    epic.getIssueKey(),
                    epic.getSummary(),
                    null, // iconUrl resolved by frontend via WorkflowConfigContext
                    epic.getIssueType(),
                    projectKey,
                    project != null ? project.getSummary() : null,
                    epicQuarter,
                    inQuarter,
                    riceScore,
                    boost,
                    priorityScore,
                    epicTeams,
                    demand,
                    totalDemand,
                    hasEstimate,
                    hasTeamMapping,
                    epicOverloaded
            ));
        }

        // Sort by priority score descending so frontend Backlog ordering is consistent
        result.sort(Comparator.comparing(PlanningEpicDto::priorityScore).reversed());

        return new QuarterlyEpicsResponse(quarterLabel, result);
    }

    /**
     * Assign or remove the quarter label for an epic. Writes to Jira first (source of truth),
     * then mirrors the change in the local entity. Passing {@code null} for quarter removes
     * any existing YYYYQn labels.
     */
    @Transactional
    public PlanningEpicDto assignEpicToQuarter(String epicKey, String quarter) {
        if (quarter != null && !QUARTER_LABEL_PATTERN.matcher(quarter).matches()) {
            throw new IllegalArgumentException("Invalid quarter label: " + quarter);
        }

        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicKey));
        if (!workflowConfigService.isEpic(epic.getIssueType())) {
            throw new IllegalArgumentException("Issue is not an Epic: " + epicKey);
        }

        // Build new labels: drop existing YYYYQn entries, append the new quarter (if any)
        List<String> newLabels = new ArrayList<>();
        if (epic.getLabels() != null) {
            for (String label : epic.getLabels()) {
                if (label != null && !QUARTER_LABEL_PATTERN.matcher(label).matches()) {
                    newLabels.add(label);
                }
            }
        }
        if (quarter != null) {
            newLabels.add(quarter);
        }

        // Jira is the source of truth — write there first, only mirror locally on success
        if (jiraClient != null) {
            jiraClient.updateLabels(epicKey, newLabels);
        }

        epic.setLabels(newLabels.toArray(new String[0]));
        issueRepository.save(epic);

        return buildPlanningEpicDto(epic, quarter != null ? quarter : currentLookupQuarter(epic));
    }

    @Transactional
    public PlanningEpicDto removeEpicFromQuarter(String epicKey) {
        return assignEpicToQuarter(epicKey, null);
    }

    /**
     * Set the manual boost on an epic. Validates the range [-50, 50].
     */
    @Transactional
    public PlanningEpicDto setEpicBoost(String epicKey, int boost) {
        if (boost < -50 || boost > 50) {
            throw new IllegalArgumentException("Boost must be in [-50, 50], got: " + boost);
        }
        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicKey));
        if (!workflowConfigService.isEpic(epic.getIssueType())) {
            throw new IllegalArgumentException("Issue is not an Epic: " + epicKey);
        }

        epic.setManualBoost(boost);
        issueRepository.save(epic);

        return buildPlanningEpicDto(epic, currentLookupQuarter(epic));
    }

    private String currentLookupQuarter(JiraIssueEntity epic) {
        // Resolve epic's current quarter (direct label first, otherwise inherited from parent project)
        String direct = epic.getQuarterLabel();
        if (direct != null) return direct;

        if (epic.getParentKey() != null) {
            JiraIssueEntity parent = issueRepository.findByIssueKey(epic.getParentKey()).orElse(null);
            if (parent != null) return parent.getQuarterLabel();
        }
        return null;
    }

    private PlanningEpicDto buildPlanningEpicDto(JiraIssueEntity epic, String contextQuarter) {
        // Lightweight DTO builder for single-epic responses after mutations.
        // Loads parent project for project metadata; computes priority + demand without full quarter scan.
        //
        // Project association resolution mirrors getEpicsForQuarter:
        //   1. epic.parentKey (Jira parent link)
        //   2. fallback: any PROJECT whose childEpicKeys contains this epic (Jira issue link)
        // The fallback is required because some epics are linked from the project side only
        // (issue-link "is parent of") without a parentKey on the epic itself.
        JiraIssueEntity project = null;
        String parentKey = epic.getParentKey();
        if (parentKey != null) {
            project = issueRepository.findByIssueKey(parentKey).orElse(null);
        }
        if (project == null) {
            // Reverse lookup via childEpicKeys on PROJECT entities
            for (JiraIssueEntity candidate : issueRepository.findByBoardCategory("PROJECT")) {
                String[] children = candidate.getChildEpicKeys();
                if (children == null) continue;
                for (String child : children) {
                    if (epic.getIssueKey().equals(child)) {
                        project = candidate;
                        break;
                    }
                }
                if (project != null) break;
            }
        }
        String projectKey = project != null ? project.getIssueKey() : null;

        TeamEntity team = epic.getTeamId() != null
                ? teamRepository.findById(epic.getTeamId()).orElse(null)
                : null;

        RiceAssessmentEntity rice = riceAssessmentRepository.findByIssueKey(epic.getIssueKey()).orElse(null);
        if (rice == null && projectKey != null) {
            rice = riceAssessmentRepository.findByIssueKey(projectKey).orElse(null);
        }
        BigDecimal riceScore = rice != null ? rice.getNormalizedScore() : BigDecimal.ZERO;
        Integer boost = epic.getManualBoost() != null ? epic.getManualBoost() : 0;
        BigDecimal priorityScore = computePriorityScore(riceScore, boost);

        BigDecimal riskBuffer = new BigDecimal("0.2");
        if (epic.getTeamId() != null) {
            try {
                PlanningConfigDto cfg = teamService.getPlanningConfig(epic.getTeamId());
                if (cfg.riskBuffer() != null) riskBuffer = cfg.riskBuffer();
            } catch (Exception ignore) {
                // Use default risk buffer
            }
        }
        Map<String, BigDecimal> demand = computeEpicDemand(epic, riskBuffer);
        BigDecimal totalDemand = demand.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PlanningEpicDto.TeamRef> teamRefs = team != null
                ? List.of(new PlanningEpicDto.TeamRef(team.getId(), team.getName(), team.getColor()))
                : List.of();

        String epicQuarter = epic.getQuarterLabel();
        if (epicQuarter == null && project != null) epicQuarter = project.getQuarterLabel();

        boolean inQuarter = contextQuarter != null && contextQuarter.equals(epicQuarter);

        return new PlanningEpicDto(
                epic.getIssueKey(),
                epic.getSummary(),
                null,
                epic.getIssueType(),
                projectKey,
                project != null ? project.getSummary() : null,
                epicQuarter,
                inQuarter,
                riceScore,
                boost,
                priorityScore,
                teamRefs,
                demand,
                totalDemand,
                epic.getRoughEstimates() != null && !epic.getRoughEstimates().isEmpty(),
                epic.getTeamId() != null,
                List.of()
        );
    }

    private List<JiraIssueEntity> loadAllEpics() {
        // Use board_category for efficiency; that is what sync sets based on isEpic()
        return issueRepository.findByBoardCategory("EPIC");
    }

    // ==================== Private Helpers ====================

    private String resolveQuarterLabel(JiraIssueEntity epic,
                                       Map<String, String> epicToProjectKey,
                                       Map<String, JiraIssueEntity> projectsByKey) {
        // Direct label on epic takes priority
        String directLabel = epic.getQuarterLabel();
        if (directLabel != null) {
            return directLabel;
        }
        // Inherit from parent project (via reverse index: parentKey or childEpicKeys)
        String projKey = epicToProjectKey.get(epic.getIssueKey());
        if (projKey != null) {
            JiraIssueEntity parent = projectsByKey.get(projKey);
            if (parent != null) {
                return parent.getQuarterLabel();
            }
        }
        return null;
    }

    private Map<String, String> buildEpicToProjectIndex(
            List<JiraIssueEntity> projects, List<JiraIssueEntity> epics) {
        Map<String, String> epicToProject = new HashMap<>();
        Set<String> epicKeys = new HashSet<>();
        for (JiraIssueEntity epic : epics) {
            epicKeys.add(epic.getIssueKey());
        }
        for (JiraIssueEntity project : projects) {
            // parentKey mode
            for (JiraIssueEntity epic : epics) {
                if (project.getIssueKey().equals(epic.getParentKey())) {
                    epicToProject.putIfAbsent(epic.getIssueKey(), project.getIssueKey());
                }
            }
            // childEpicKeys mode (issue links)
            String[] linkedKeys = project.getChildEpicKeys();
            if (linkedKeys != null) {
                for (String lk : linkedKeys) {
                    if (epicKeys.contains(lk)) {
                        epicToProject.putIfAbsent(lk, project.getIssueKey());
                    }
                }
            }
        }
        return epicToProject;
    }

    private Map<String, BigDecimal> computeEpicDemand(JiraIssueEntity epic, BigDecimal riskBuffer) {
        Map<String, BigDecimal> roughEstimates = epic.getRoughEstimates();
        if (roughEstimates == null || roughEstimates.isEmpty()) {
            return Map.of();
        }
        BigDecimal multiplier = BigDecimal.ONE.add(riskBuffer);
        Map<String, BigDecimal> demand = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : roughEstimates.entrySet()) {
            demand.put(entry.getKey(), entry.getValue().multiply(multiplier).setScale(1, RoundingMode.HALF_UP));
        }
        return demand;
    }

    private BigDecimal computePriorityScore(BigDecimal riceNormalizedScore, Integer manualBoost) {
        BigDecimal score = (riceNormalizedScore != null ? riceNormalizedScore : BigDecimal.ZERO)
                .add(new BigDecimal(manualBoost != null ? manualBoost : 0));
        // Clamp 0..150
        if (score.compareTo(BigDecimal.ZERO) < 0) score = BigDecimal.ZERO;
        if (score.compareTo(new BigDecimal("150")) > 0) score = new BigDecimal("150");
        return score.setScale(1, RoundingMode.HALF_UP);
    }

    private Map<String, RiceAssessmentEntity> loadRiceScores(Set<String> issueKeys) {
        if (issueKeys.isEmpty()) return Map.of();
        return riceAssessmentRepository.findByIssueKeyIn(issueKeys).stream()
                .collect(Collectors.toMap(RiceAssessmentEntity::getIssueKey, e -> e));
    }

    private ProjectDemandDto buildProjectDemand(
            String projectKey, JiraIssueEntity project,
            List<JiraIssueEntity> epics,
            BigDecimal priorityScore, BigDecimal riceScore, Integer boost,
            BigDecimal riskBuffer, Map<String, BigDecimal> remainingCapacity,
            String quarterLabel
    ) {
        Map<String, BigDecimal> totalDemand = new LinkedHashMap<>();
        List<EpicDemandDto> epicDemands = new ArrayList<>();

        for (JiraIssueEntity epic : epics) {
            EpicDemandDto epicDemand = buildEpicDemand(epic, riskBuffer, remainingCapacity, quarterLabel);
            epicDemands.add(epicDemand);
            for (Map.Entry<String, BigDecimal> entry : epicDemand.demandByRole().entrySet()) {
                totalDemand.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
            }
        }

        boolean fitsInCapacity = epicDemands.stream().noneMatch(EpicDemandDto::overCapacity);

        return new ProjectDemandDto(
                projectKey,
                project != null ? project.getSummary() : projectKey,
                project != null ? project.getStatus() : null,
                priorityScore, riceScore, boost,
                totalDemand, epicDemands, fitsInCapacity
        );
    }

    private EpicDemandDto buildEpicDemand(
            JiraIssueEntity epic, BigDecimal riskBuffer,
            Map<String, BigDecimal> remainingCapacity, String quarterLabel
    ) {
        Map<String, BigDecimal> demand = computeEpicDemand(epic, riskBuffer);
        boolean overCapacity = false;

        for (Map.Entry<String, BigDecimal> entry : demand.entrySet()) {
            BigDecimal remaining = remainingCapacity.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (remaining.compareTo(entry.getValue()) < 0) {
                overCapacity = true;
            }
            remainingCapacity.put(entry.getKey(), remaining.subtract(entry.getValue()));
        }

        return new EpicDemandDto(
                epic.getIssueKey(), epic.getSummary(), epic.getStatus(),
                epic.getManualOrder(), demand, overCapacity, quarterLabel
        );
    }

    private ProjectDemandDto recalculateCapacityFit(
            ProjectDemandDto original, BigDecimal riskBuffer,
            Map<String, BigDecimal> remainingCapacity
    ) {
        List<EpicDemandDto> recalcEpics = new ArrayList<>();
        boolean allFit = true;

        for (EpicDemandDto epic : original.epics()) {
            boolean overCapacity = false;
            for (Map.Entry<String, BigDecimal> entry : epic.demandByRole().entrySet()) {
                BigDecimal remaining = remainingCapacity.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                if (remaining.compareTo(entry.getValue()) < 0) {
                    overCapacity = true;
                }
                remainingCapacity.put(entry.getKey(), remaining.subtract(entry.getValue()));
            }
            if (overCapacity) allFit = false;
            recalcEpics.add(new EpicDemandDto(
                    epic.epicKey(), epic.summary(), epic.status(),
                    epic.manualOrder(), epic.demandByRole(), overCapacity, epic.quarterLabel()
            ));
        }

        return new ProjectDemandDto(
                original.projectKey(), original.summary(), original.status(),
                original.priorityScore(), original.riceNormalizedScore(), original.manualBoost(),
                original.totalDemandByRole(), recalcEpics, allFit
        );
    }

    private Map<String, BigDecimal> aggregateDemand(QuarterlyDemandDto demand) {
        Map<String, BigDecimal> total = new LinkedHashMap<>();
        for (ProjectDemandDto project : demand.projects()) {
            for (Map.Entry<String, BigDecimal> entry : project.totalDemandByRole().entrySet()) {
                total.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
            }
        }
        for (EpicDemandDto epic : demand.unassignedEpics()) {
            for (Map.Entry<String, BigDecimal> entry : epic.demandByRole().entrySet()) {
                total.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
            }
        }
        return total;
    }

    private Map<String, BigDecimal> computeUtilization(
            Map<String, BigDecimal> capacity, Map<String, BigDecimal> demand
    ) {
        Map<String, BigDecimal> utilization = new LinkedHashMap<>();
        Set<String> allRoles = new LinkedHashSet<>();
        allRoles.addAll(capacity.keySet());
        allRoles.addAll(demand.keySet());

        for (String role : allRoles) {
            BigDecimal cap = capacity.getOrDefault(role, BigDecimal.ZERO);
            BigDecimal dem = demand.getOrDefault(role, BigDecimal.ZERO);
            if (cap.compareTo(BigDecimal.ZERO) > 0) {
                utilization.put(role, dem.multiply(new BigDecimal("100"))
                        .divide(cap, 1, RoundingMode.HALF_UP));
            } else if (dem.compareTo(BigDecimal.ZERO) > 0) {
                utilization.put(role, new BigDecimal("999.9")); // Infinite overload
            } else {
                utilization.put(role, BigDecimal.ZERO);
            }
        }
        return utilization;
    }

    private BigDecimal getGradeCoefficient(Grade grade, PlanningConfigDto.GradeCoefficients coefficients) {
        return switch (grade) {
            case SENIOR -> coefficients.senior();
            case JUNIOR -> coefficients.junior();
            default -> coefficients.middle();
        };
    }

    private static final Pattern QUARTER_LABEL_PATTERN = Pattern.compile("\\d{4}Q[1-4]");

    private List<String> extractQuarterLabels() {
        List<JiraIssueEntity> withLabels = issueRepository.findByLabelsIsNotNull();
        TreeSet<String> quarters = new TreeSet<>();
        for (JiraIssueEntity entity : withLabels) {
            if (entity.getLabels() != null) {
                for (String label : entity.getLabels()) {
                    if (label != null && QUARTER_LABEL_PATTERN.matcher(label).matches()) {
                        quarters.add(label);
                    }
                }
            }
        }
        return new ArrayList<>(quarters);
    }

    private int countWorkdaysInDates(Set<LocalDate> dates, LocalDate rangeStart, LocalDate rangeEnd) {
        int count = 0;
        for (LocalDate date : dates) {
            if (!date.isBefore(rangeStart) && !date.isAfter(rangeEnd) && workCalendarService.isWorkday(date)) {
                count++;
            }
        }
        return count;
    }
}

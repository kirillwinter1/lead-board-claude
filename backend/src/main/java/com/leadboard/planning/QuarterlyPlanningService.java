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

    /**
     * Default risk buffer multiplier applied to rough estimates when a team has
     * no explicit {@code planning_config.riskBuffer}. 20% reflects the historical
     * average overhead observed across teams.
     */
    private static final BigDecimal DEFAULT_RISK_BUFFER = new BigDecimal("0.2");

    /** Canonical quarter-label format used throughout F67-F70 (YYYYQn, e.g. "2026Q2"). */
    private static final Pattern QUARTER_LABEL_PATTERN = Pattern.compile("\\d{4}Q[1-4]");

    /**
     * Returns the quarter label string (e.g. "2026Q2") for the given date.
     * Used by AutoScoreCalculator to compare epic's quarter label with the active quarter.
     */
    public static String getCurrentQuarterLabel(LocalDate today) {
        int quarter = (today.getMonthValue() - 1) / 3 + 1;
        return today.getYear() + "Q" + quarter;
    }

    /**
     * Synthetic team name surfaced in {@link #getProjectCommitment} for epics
     * that lack a team mapping. Kept as a constant so the UI label is in one
     * place rather than scattered as inline string literals.
     */
    private static final String UNASSIGNED_TEAM_LABEL = "Unassigned";

    private final JiraIssueRepository issueRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final AbsenceService absenceService;
    private final WorkCalendarService workCalendarService;
    private final TeamService teamService;
    private final RiceAssessmentRepository riceAssessmentRepository;
    private final WorkflowConfigService workflowConfigService;
    private final JiraClient jiraClient;
    private final EpicLabelPersistenceService epicLabelPersistenceService;
    private final ProjectLabelPersistenceService projectLabelPersistenceService;

    public QuarterlyPlanningService(JiraIssueRepository issueRepository,
                                     TeamRepository teamRepository,
                                     TeamMemberRepository memberRepository,
                                     AbsenceService absenceService,
                                     WorkCalendarService workCalendarService,
                                     TeamService teamService,
                                     RiceAssessmentRepository riceAssessmentRepository,
                                     WorkflowConfigService workflowConfigService,
                                     JiraClient jiraClient,
                                     EpicLabelPersistenceService epicLabelPersistenceService,
                                     ProjectLabelPersistenceService projectLabelPersistenceService) {
        this.issueRepository = issueRepository;
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.absenceService = absenceService;
        this.workCalendarService = workCalendarService;
        this.teamService = teamService;
        this.riceAssessmentRepository = riceAssessmentRepository;
        this.workflowConfigService = workflowConfigService;
        this.jiraClient = jiraClient;
        this.epicLabelPersistenceService = epicLabelPersistenceService;
        this.projectLabelPersistenceService = projectLabelPersistenceService;
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

        // F70: team-lead demand counts epics with an explicit committed_quarter
        // ONLY. The team-lead's CapacityBars (F69) feed off this method, and it
        // must stay consistent with the kanban (getEpicsForQuarter), which also
        // uses resolveCommittedQuarter. Inheriting from the parent project's
        // desired_quarter here would inflate the demand widget relative to the
        // kanban — PMs see 12 epics committed, team-leads see 8 in their board.
        List<JiraIssueEntity> quarterEpics = new ArrayList<>();
        for (JiraIssueEntity epic : allEpics) {
            if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) {
                continue; // Skip completed epics
            }
            String epicQuarter = resolveCommittedQuarter(epic);
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
        BigDecimal riskBuffer = config.riskBuffer() != null ? config.riskBuffer() : DEFAULT_RISK_BUFFER;

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

        List<ProjectViewDto.TeamAllocationDto> teamAllocations = new ArrayList<>();
        for (Map.Entry<Long, List<JiraIssueEntity>> entry : epicsByTeam.entrySet()) {
            Long teamId = entry.getKey();
            List<JiraIssueEntity> epics = entry.getValue();

            TeamEntity team = teamRepository.findById(teamId).orElse(null);
            if (team == null) continue;

            QuarterlyCapacityDto capacity = getTeamCapacity(teamId, quarterLabel);

            PlanningConfigDto config = teamService.getPlanningConfig(teamId);
            BigDecimal riskBuffer = config.riskBuffer() != null ? config.riskBuffer() : DEFAULT_RISK_BUFFER;

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

            // F70: "project in quarter" = at least one epic explicitly committed
            // to this quarter via its own label. The project's desired_quarter
            // (PM-signal) is intentionally NOT used as the inQuarter indicator —
            // pre-F70 the project label was a synonym for "project is in this
            // quarter", but post-F70 the semantics are split:
            //   - project label  = PM wants the quarter (desired_quarter)
            //   - epic label     = team-lead committed (committed_quarter)
            // Using desired_quarter here would inflate the "in scope" picture
            // for projects PMs hope for but teams have not yet committed to.
            //
            // Inheritance is also dropped: only epics whose OWN labels carry a
            // YYYYQn token count. Falling back to the project's desired_quarter
            // for unlabelled epics would erase the "uncommitted" signal that
            // makes the F70 model meaningful.
            List<JiraIssueEntity> quarterEpics = new ArrayList<>();
            for (JiraIssueEntity epic : activeEpics) {
                String epicQuarter = resolveCommittedQuarter(epic);
                if (quarterLabel.equals(epicQuarter)) {
                    quarterEpics.add(epic);
                }
            }

            // Determine if project is in quarter — strictly based on committed epics.
            boolean hasEpicsInQuarter = !quarterEpics.isEmpty();
            boolean inQuarter = hasEpicsInQuarter;

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
                    Map<String, BigDecimal> epicDemand = computeEpicDemand(epic, DEFAULT_RISK_BUFFER);
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
        return getTeamsOverview(quarterLabel, null);
    }

    /**
     * Same as {@link #getTeamsOverview(String)} but only returns teams whose id
     * is in {@code scopedTeamIds}. Pass {@code null} to opt out of scoping.
     *
     * <p>An empty (non-null) set returns an empty list — the controller uses
     * this to enforce "TEAM_LEAD with no team membership sees nothing".</p>
     */
    public List<QuarterlyTeamOverviewDto> getTeamsOverview(String quarterLabel, Set<Long> scopedTeamIds) {
        List<TeamEntity> teams = teamRepository.findByActiveTrue();
        if (scopedTeamIds != null) {
            teams = teams.stream()
                    .filter(t -> scopedTeamIds.contains(t.getId()))
                    .toList();
        }
        List<JiraIssueEntity> allProjects = issueRepository.findByBoardCategory("PROJECT");
        List<JiraIssueEntity> allEpics = issueRepository.findByBoardCategory("EPIC");
        Map<String, String> epicToProjectKey = buildEpicToProjectIndex(allProjects, allEpics);
        Map<String, JiraIssueEntity> projectsByKey = new HashMap<>();
        for (JiraIssueEntity p : allProjects) {
            projectsByKey.put(p.getIssueKey(), p);
        }

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
                BigDecimal riskBuffer = config.riskBuffer() != null ? config.riskBuffer() : DEFAULT_RISK_BUFFER;

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
     * Backwards-compatible overload that defaults to {@code onlyDesired=true} —
     * matches the F70 endpoint default. Use the two-argument form when the caller
     * explicitly wants the unfiltered (F69) view.
     */
    public QuarterlyEpicsResponse getEpicsForQuarter(String quarterLabel) {
        return getEpicsForQuarter(quarterLabel, true, null);
    }

    /**
     * Returns epics enriched with quarter membership, priority score, demand,
     * and capacity-overload flags for the requested quarter.
     *
     * <p>When {@code onlyDesired=true} (F70 default), the result is filtered to
     * epics that are either:</p>
     * <ul>
     *   <li>Children of a project whose desired quarter matches {@code quarterLabel}, or</li>
     *   <li>Standalone — no parent project at all (technical debt / small tasks).</li>
     * </ul>
     * <p>Standalone epics are <em>always</em> included regardless of the filter:
     * they have no PM-driven project context and would otherwise disappear from
     * the team-lead view.</p>
     *
     * <p>When {@code onlyDesired=false}, every active epic is returned — the
     * original F69 behaviour for the "show everything" toggle.</p>
     */
    public QuarterlyEpicsResponse getEpicsForQuarter(String quarterLabel, boolean onlyDesired) {
        return getEpicsForQuarter(quarterLabel, onlyDesired, null);
    }

    /**
     * Same as {@link #getEpicsForQuarter(String, boolean)} but only returns
     * epics mapped to a team in {@code scopedTeamIds}. Pass {@code null} for
     * the unscoped view.
     *
     * <p>Epics with no team mapping ({@code teamId == null}) are excluded when
     * scoping is active — a TEAM_LEAD has nothing actionable to do with an
     * unmapped epic and seeing it would only add noise.</p>
     */
    public QuarterlyEpicsResponse getEpicsForQuarter(String quarterLabel, boolean onlyDesired, Set<Long> scopedTeamIds) {
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

        // Pre-compute capacity-by-role per team for overload detection
        // TODO: batch capacity loads (pre-existing N+1 — getTeamCapacity loads members/absences
        //       per team). Mirrors the same pattern in getSummary/getTeamsOverview. See ai-ru/TECH_DEBT.md.
        Map<Long, Map<String, BigDecimal>> capacityByTeam = new HashMap<>();
        Map<Long, BigDecimal> riskBufferByTeam = new HashMap<>();
        for (TeamEntity team : teamsById.values()) {
            try {
                capacityByTeam.put(team.getId(), getTeamCapacity(team.getId(), quarterLabel).capacityByRole());
                PlanningConfigDto cfg = teamService.getPlanningConfig(team.getId());
                riskBufferByTeam.put(team.getId(),
                        cfg.riskBuffer() != null ? cfg.riskBuffer() : DEFAULT_RISK_BUFFER);
            } catch (Exception e) {
                log.warn("Failed to compute capacity for team {}: {}", team.getName(), e.getMessage());
                capacityByTeam.put(team.getId(), Map.of());
                riskBufferByTeam.put(team.getId(), DEFAULT_RISK_BUFFER);
            }
        }

        // Sum demand by team for all epics currently in the requested quarter.
        // F70: team-lead view counts only epics with an explicit committed_quarter —
        // inheritance from the parent project's desired_quarter must NOT inflate
        // demand (see resolveCommittedQuarter doc and F70 spec, "Наследование").
        Map<Long, Map<String, BigDecimal>> demandByTeamInQuarter = new HashMap<>();
        for (JiraIssueEntity epic : allEpics) {
            if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) continue;
            String epicQuarter = resolveCommittedQuarter(epic);
            if (!quarterLabel.equals(epicQuarter)) continue;
            Long teamId = epic.getTeamId();
            if (teamId == null) continue;
            BigDecimal risk = riskBufferByTeam.getOrDefault(teamId, DEFAULT_RISK_BUFFER);
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

            // Team-lead scoping: drop epics outside the caller's team(s).
            // Unmapped epics (teamId == null) are also dropped — they are not
            // actionable for a team lead and would add noise.
            if (scopedTeamIds != null) {
                Long epicTeamId = epic.getTeamId();
                if (epicTeamId == null || !scopedTeamIds.contains(epicTeamId)) continue;
            }

            String projectKey = epicToProjectKey.get(epic.getIssueKey());
            JiraIssueEntity project = projectKey != null ? projectsByKey.get(projectKey) : null;
            boolean isStandalone = project == null;
            // F70: desired_quarter is read directly off the parent project's labels —
            // no inheritance or fallback applies here (resolveDesiredQuarter only
            // looks at the project itself).
            String projectDesiredQuarter = project != null ? resolveDesiredQuarter(project) : null;

            // F70 filter: when onlyDesired=true, drop epics whose parent project does
            // not desire the requested quarter. Standalone epics (no parent project)
            // always pass — they are first-class technical-debt / small-task slots in
            // the team-lead view and PM has no opinion on them.
            if (onlyDesired && !isStandalone) {
                if (!quarterLabel.equals(projectDesiredQuarter)) {
                    continue;
                }
            }

            // F70: team-lead view — "epic in quarter" means EXPLICITLY committed.
            // No inheritance from the parent project's desired_quarter; an epic
            // surfaces in the InQuarter column only if its own labels carry a
            // YYYYQn token. The desired quarter is surfaced separately via
            // projectDesiredQuarter for the "PM wants Qx" badge.
            String epicQuarter = resolveCommittedQuarter(epic);
            boolean inQuarter = quarterLabel.equals(epicQuarter);

            // RICE on epic first, fall back to parent project's RICE
            RiceAssessmentEntity rice = riceByKey.get(epic.getIssueKey());
            if (rice == null && projectKey != null) rice = riceByKey.get(projectKey);
            BigDecimal riceScore = rice != null ? rice.getNormalizedScore() : BigDecimal.ZERO;
            Integer boost = epic.getManualBoost() != null ? epic.getManualBoost() : 0;
            BigDecimal priorityScore = computePriorityScore(riceScore, boost);

            BigDecimal riskBuffer = epic.getTeamId() != null
                    ? riskBufferByTeam.getOrDefault(epic.getTeamId(), DEFAULT_RISK_BUFFER)
                    : DEFAULT_RISK_BUFFER;
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
                    epicOverloaded,
                    projectDesiredQuarter,
                    isStandalone
            ));
        }

        // Sort by priority score descending so frontend Backlog ordering is consistent
        result.sort(Comparator.comparing(PlanningEpicDto::priorityScore).reversed());

        return new QuarterlyEpicsResponse(quarterLabel, result);
    }

    /**
     * Assign or remove the quarter label for an epic.
     *
     * <p>Jira is the source of truth — the Jira API write happens FIRST, outside of a
     * database transaction. Only if Jira accepts the write do we mirror the new labels
     * into the local entity in a separate short transaction. If Jira fails the local
     * state is never touched (consistent). If the local save fails after a successful
     * Jira write, the error is logged with enough context for manual reconciliation
     * (Jira will be ahead of the DB until the next sync overwrites the local copy).</p>
     *
     * <p>Passing {@code null} for {@code quarter} removes any existing YYYYQn labels.
     * When the user explicitly removes the quarter, the returned DTO reports
     * {@code inQuarter=false} regardless of any quarter inherited from the parent
     * project — otherwise the frontend would still see the epic in the quarter and
     * the UX would feel broken.</p>
     *
     * <p>This method itself carries no method-level {@code @Transactional}; the
     * class-level {@code @Transactional(readOnly = true)} therefore applies whenever
     * the method is reached via the Spring proxy. The actual DB write is delegated
     * to {@link EpicLabelPersistenceService#mirrorEpicLabels(String, java.util.List)},
     * which runs in a fresh {@code REQUIRES_NEW} writable transaction — this avoids
     * the Spring AOP self-invocation pitfall (a {@code this.}-called {@code @Transactional}
     * method would inherit the read-only outer transaction, and Hibernate's
     * {@code FlushMode.MANUAL} would silently drop the save).</p>
     */
    public PlanningEpicDto assignEpicToQuarter(String epicKey, String quarter) {
        if (quarter != null && !QUARTER_LABEL_PATTERN.matcher(quarter).matches()) {
            throw new IllegalArgumentException("Invalid quarter label: " + quarter);
        }

        // Read-only lookup outside the write transaction to validate the epic exists
        // and to compute the target label set.
        JiraIssueEntity epic = findEpicOrThrow(epicKey);

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

        // 1. Jira write — outside any DB transaction. If this throws, DB state is untouched.
        jiraClient.updateLabels(epicKey, newLabels);

        // 2. Mirror the labels locally in a short, isolated transaction. The dedicated
        //    bean call goes through the Spring proxy with REQUIRES_NEW, so the save
        //    actually reaches the DB even though the outer call-site is read-only.
        //    If this fails after a successful Jira write we log loudly so an operator
        //    can reconcile manually (next Jira sync will normally fix the drift).
        try {
            epicLabelPersistenceService.mirrorEpicLabels(epicKey, newLabels);
        } catch (RuntimeException e) {
            log.error("Jira label updated but DB save failed for epic={}, manual reconciliation required",
                    epicKey, e);
            throw e;
        }

        // Reload to get the freshly persisted state for the response DTO.
        JiraIssueEntity refreshed = findEpicOrThrow(epicKey);

        // Pre-compute the quarter snapshot ONCE (capacity + demand) so the DTO's
        // overloadedTeams list reflects the post-mutation state. When quarter==null
        // (user removed), we must report inQuarter=false regardless of inheritance —
        // pass null contextQuarter so buildPlanningEpicDto honours that explicitly.
        String contextQuarter = quarter;
        List<JiraIssueEntity> allProjectsSnapshot = issueRepository.findByBoardCategory("PROJECT");
        QuarterSnapshot snapshot = buildQuarterSnapshot(contextQuarter, allProjectsSnapshot);
        return buildPlanningEpicDto(refreshed, contextQuarter, allProjectsSnapshot, snapshot);
    }

    /**
     * Set the manual boost on an epic. Validates the range [-50, 50].
     *
     * <p>Boost is local-only and is NOT propagated to Jira (unlike quarter
     * assignment which writes the label back to Jira).</p>
     */
    @Transactional
    public PlanningEpicDto setEpicBoost(String epicKey, int boost) {
        if (boost < -50 || boost > 50) {
            throw new IllegalArgumentException("Boost must be in [-50, 50], got: " + boost);
        }
        JiraIssueEntity epic = findEpicOrThrow(epicKey);

        epic.setManualBoost(boost);
        issueRepository.save(epic);

        // For boost mutations the epic's current quarter (direct or inherited) is
        // the right context — boost does not change quarter membership.
        String contextQuarter = currentLookupQuarter(epic);
        List<JiraIssueEntity> allProjectsSnapshot = issueRepository.findByBoardCategory("PROJECT");
        QuarterSnapshot snapshot = buildQuarterSnapshot(contextQuarter, allProjectsSnapshot);
        return buildPlanningEpicDto(epic, contextQuarter, allProjectsSnapshot, snapshot);
    }

    /**
     * Look up an epic by key, throwing {@link EpicNotFoundException} (HTTP 404)
     * when absent or {@link IllegalArgumentException} (HTTP 400) when the issue
     * is not an Epic. "Not found" is semantically a 404; type-mismatch is
     * client-side validation.
     */
    private JiraIssueEntity findEpicOrThrow(String epicKey) {
        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new EpicNotFoundException("Epic not found: " + epicKey));
        if (!workflowConfigService.isEpic(epic.getIssueType())) {
            throw new IllegalArgumentException("Issue is not an Epic: " + epicKey);
        }
        return epic;
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

    /**
     * Snapshot of team capacity and demand for a given quarter, captured ONCE per
     * mutation so {@link #buildPlanningEpicDto} can populate overloadedTeams without
     * re-running quarter-wide scans for each call.
     */
    private record QuarterSnapshot(
            String quarterLabel,
            Map<Long, Map<String, BigDecimal>> capacityByTeam,
            Map<Long, Map<String, BigDecimal>> demandByTeam,
            Map<Long, BigDecimal> riskBufferByTeam,
            Set<Long> overloadedTeamIds
    ) {
        static QuarterSnapshot empty() {
            return new QuarterSnapshot(null, Map.of(), Map.of(), Map.of(), Set.of());
        }
    }

    /**
     * Build a fresh capacity/demand snapshot for the requested quarter. Iterates
     * every active team to compute capacity-by-role and sums per-team demand from
     * all epics currently in that quarter. Mirrors the logic of
     * {@link #getEpicsForQuarter(String)} but is scoped to overload detection only.
     *
     * <p>When {@code quarterLabel} is {@code null} (user removed the quarter from
     * an epic) the snapshot is empty — overload calculations are not meaningful
     * outside of a specific quarter.</p>
     */
    private QuarterSnapshot buildQuarterSnapshot(String quarterLabel, List<JiraIssueEntity> allProjects) {
        if (quarterLabel == null) {
            return QuarterSnapshot.empty();
        }

        List<JiraIssueEntity> allEpics = loadAllEpics();
        Map<String, JiraIssueEntity> projectsByKey = new HashMap<>();
        for (JiraIssueEntity p : allProjects) {
            projectsByKey.put(p.getIssueKey(), p);
        }
        Map<String, String> epicToProjectKey = buildEpicToProjectIndex(allProjects, allEpics);

        List<TeamEntity> teams = teamRepository.findByActiveTrue();
        Map<Long, Map<String, BigDecimal>> capacityByTeam = new HashMap<>();
        Map<Long, BigDecimal> riskBufferByTeam = new HashMap<>();
        for (TeamEntity team : teams) {
            try {
                capacityByTeam.put(team.getId(),
                        getTeamCapacity(team.getId(), quarterLabel).capacityByRole());
                PlanningConfigDto cfg = teamService.getPlanningConfig(team.getId());
                riskBufferByTeam.put(team.getId(),
                        cfg.riskBuffer() != null ? cfg.riskBuffer() : DEFAULT_RISK_BUFFER);
            } catch (Exception e) {
                log.warn("Failed to compute capacity for team {} in snapshot: {}",
                        team.getName(), e.getMessage());
                capacityByTeam.put(team.getId(), Map.of());
                riskBufferByTeam.put(team.getId(), DEFAULT_RISK_BUFFER);
            }
        }

        Map<Long, Map<String, BigDecimal>> demandByTeam = new HashMap<>();
        for (JiraIssueEntity epic : allEpics) {
            if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) continue;
            // F70: snapshot serves buildPlanningEpicDto (team-lead view), so
            // overload detection must mirror the same "committed only" rule
            // applied in getEpicsForQuarter. Inheritance from parent project's
            // desired_quarter is intentionally excluded.
            String epicQuarter = resolveCommittedQuarter(epic);
            if (!quarterLabel.equals(epicQuarter)) continue;
            Long teamId = epic.getTeamId();
            if (teamId == null) continue;
            BigDecimal risk = riskBufferByTeam.getOrDefault(teamId, DEFAULT_RISK_BUFFER);
            Map<String, BigDecimal> epicDemand = computeEpicDemand(epic, risk);
            Map<String, BigDecimal> teamDemand = demandByTeam.computeIfAbsent(teamId, k -> new LinkedHashMap<>());
            for (Map.Entry<String, BigDecimal> e : epicDemand.entrySet()) {
                teamDemand.merge(e.getKey(), e.getValue(), BigDecimal::add);
            }
        }

        Set<Long> overloadedTeamIds = new HashSet<>();
        for (Map.Entry<Long, Map<String, BigDecimal>> entry : demandByTeam.entrySet()) {
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

        return new QuarterSnapshot(quarterLabel, capacityByTeam, demandByTeam,
                riskBufferByTeam, overloadedTeamIds);
    }

    /**
     * Build a {@link PlanningEpicDto} for a single epic after a mutation.
     * Receives the pre-fetched project list and quarter snapshot so we never
     * re-issue {@code findByBoardCategory("PROJECT")} per call — that was the
     * N+1 source flagged by code review.
     */
    private PlanningEpicDto buildPlanningEpicDto(
            JiraIssueEntity epic,
            String contextQuarter,
            List<JiraIssueEntity> allProjects,
            QuarterSnapshot snapshot
    ) {
        // Project association resolution mirrors getEpicsForQuarter:
        //   1. epic.parentKey (Jira parent link)
        //   2. fallback: any PROJECT whose childEpicKeys contains this epic (Jira issue link)
        // The fallback is required because some epics are linked from the project side only
        // (issue-link "is parent of") without a parentKey on the epic itself.
        JiraIssueEntity project = null;
        String parentKey = epic.getParentKey();
        if (parentKey != null) {
            for (JiraIssueEntity p : allProjects) {
                if (parentKey.equals(p.getIssueKey())) {
                    project = p;
                    break;
                }
            }
            if (project == null) {
                // Parent may be outside the PROJECT-category list (rare); fall back to direct lookup.
                project = issueRepository.findByIssueKey(parentKey).orElse(null);
            }
        }
        if (project == null) {
            // Reverse lookup via childEpicKeys on PROJECT entities (already loaded)
            for (JiraIssueEntity candidate : allProjects) {
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

        BigDecimal riskBuffer = DEFAULT_RISK_BUFFER;
        if (epic.getTeamId() != null) {
            BigDecimal snapshotRisk = snapshot.riskBufferByTeam().get(epic.getTeamId());
            if (snapshotRisk != null) {
                riskBuffer = snapshotRisk;
            } else {
                try {
                    PlanningConfigDto cfg = teamService.getPlanningConfig(epic.getTeamId());
                    if (cfg.riskBuffer() != null) riskBuffer = cfg.riskBuffer();
                } catch (Exception ignore) {
                    // Use default risk buffer
                }
            }
        }
        Map<String, BigDecimal> demand = computeEpicDemand(epic, riskBuffer);
        BigDecimal totalDemand = demand.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PlanningEpicDto.TeamRef> teamRefs = team != null
                ? List.of(new PlanningEpicDto.TeamRef(team.getId(), team.getName(), team.getColor()))
                : List.of();

        // F70: team-lead view returns the epic's OWN committed quarter only.
        // No fallback to the parent project's quarter label — that path used to
        // inherit the project's desired_quarter (PM-side) and incorrectly mark
        // uncommitted epics as "in quarter". The desired_quarter is surfaced
        // separately via projectDesiredQuarter below.
        String epicQuarter = resolveCommittedQuarter(epic);

        // inQuarter semantics:
        //   - contextQuarter == null  →  user explicitly removed the quarter; report false.
        //   - contextQuarter != null  →  epic is "in quarter" iff its committed quarter matches.
        boolean inQuarter = contextQuarter != null && contextQuarter.equals(epicQuarter);

        // Overloaded teams: only meaningful while the epic is in a specific quarter.
        // Use the pre-built snapshot so the result reflects post-mutation state.
        List<Long> overloadedTeams = new ArrayList<>();
        if (inQuarter && epic.getTeamId() != null
                && snapshot.overloadedTeamIds().contains(epic.getTeamId())) {
            overloadedTeams.add(epic.getTeamId());
        }

        // F70 enrichment: surface the parent project's desired_quarter so the
        // frontend can render a "PM wants Q2" badge after a mutation. isStandalone
        // mirrors the field used by getEpicsForQuarter (no parent project).
        String projectDesiredQuarter = project != null ? resolveDesiredQuarter(project) : null;
        boolean isStandalone = project == null;

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
                overloadedTeams,
                projectDesiredQuarter,
                isStandalone
        );
    }

    private List<JiraIssueEntity> loadAllEpics() {
        // Use board_category for efficiency; that is what sync sets based on isEpic()
        return issueRepository.findByBoardCategory("EPIC");
    }

    // ==================== F70: Customer-Driven Quarter Planning ====================

    /**
     * Set or clear the project's desired quarter (PM-facing mutation).
     *
     * <p>Mirrors the {@link #assignEpicToQuarter} flow: validates input, removes
     * any existing {@code YYYYQn} label from the project, appends the new quarter
     * (if non-null), writes to Jira FIRST, then mirrors locally via the dedicated
     * {@link ProjectLabelPersistenceService} (REQUIRES_NEW) — sidestepping the
     * Spring AOP self-invocation pitfall that bites {@code @Transactional(readOnly=true)}
     * services.</p>
     *
     * @param projectKey Jira project-issue key (e.g. {@code "PROJ-1"})
     * @param quarter target quarter label (e.g. {@code "2026Q2"}) or {@code null} to clear
     * @return up-to-date commitment view aggregated by team
     * @throws IllegalArgumentException on invalid quarter format
     * @throws ProjectNotFoundException if the issue is missing or not a project
     */
    public ProjectQuarterCommitmentDto setProjectDesiredQuarter(String projectKey, String quarter) {
        if (quarter != null && !QUARTER_LABEL_PATTERN.matcher(quarter).matches()) {
            throw new IllegalArgumentException("Invalid quarter label: " + quarter);
        }

        JiraIssueEntity project = findProjectOrThrow(projectKey);

        // Build the new label set: drop existing YYYYQn entries, append the new quarter (if any).
        List<String> newLabels = new ArrayList<>();
        if (project.getLabels() != null) {
            for (String label : project.getLabels()) {
                if (label != null && !QUARTER_LABEL_PATTERN.matcher(label).matches()) {
                    newLabels.add(label);
                }
            }
        }
        if (quarter != null) {
            newLabels.add(quarter);
        }

        // 1. Jira write — outside any DB transaction. If this throws, DB state is untouched.
        jiraClient.updateLabels(projectKey, newLabels);

        // 2. Mirror locally in a short, isolated transaction via the dedicated bean
        //    so the proxy honours REQUIRES_NEW (and the save actually hits the DB
        //    despite the outer read-only context).
        try {
            projectLabelPersistenceService.mirrorProjectLabels(projectKey, newLabels);
        } catch (RuntimeException e) {
            log.error("Jira label updated but DB save failed for project={}, manual reconciliation required",
                    projectKey, e);
            throw e;
        }

        // Sync the outer session's L1 cache with what the inner REQUIRES_NEW
        // transaction just committed. Without this, the subsequent
        // getProjectCommitment call would re-fetch the project via Spring Data's
        // findByIssueKey, hit the outer session's L1 cache, and see the OLD
        // labels (Hibernate honours object identity per session — the row read
        // from DB is discarded in favour of the already-managed instance).
        // The result the user receives would lie about the just-saved quarter
        // until the next request creates a fresh session. Mutating the managed
        // entity is safe here: the surrounding transaction is read-only with
        // FlushMode.MANUAL, so this change is never auto-flushed back to the DB.
        project.setLabels(newLabels.toArray(new String[0]));

        return getProjectCommitment(projectKey);
    }

    /**
     * Compute the PM-facing commitment view for a project: per-team breakdown of
     * how many child epics are committed to the project's desired quarter, how
     * many slipped to a different quarter, and how many remain uncommitted.
     *
     * <p>Epic-level resolution here is intentionally DIRECT (no inheritance from
     * the project): the whole point of the F70 model is that PM-desired and
     * team-committed are independent signals, so falling back to the project's
     * quarter for unlabelled epics would erase the "uncommitted" signal.</p>
     *
     * <p>Epics without a team mapping fall into a synthetic bucket with
     * {@code teamId = 0} so the UI can render them as "Unassigned"
     * (see {@link #UNASSIGNED_TEAM_LABEL}).</p>
     */
    public ProjectQuarterCommitmentDto getProjectCommitment(String projectKey) {
        JiraIssueEntity project = findProjectOrThrow(projectKey);
        String desiredQuarter = resolveDesiredQuarter(project);

        // Collect child epics via the same two mechanisms used elsewhere in this
        // service (parentKey OR childEpicKeys on the project) so the commitment
        // view matches the rest of the planning surface.
        // TODO(F70 review H3): loadAllEpics() is a full board_category="EPIC" table
        //   scan run on every project-expand on ProjectsPage. With 1000+ epics
        //   this becomes painful for a per-row UI interaction. Replace with a
        //   narrow query — `findByParentKeyOrIssueKeyIn(projectKey, childKeySet)`
        //   — to fetch only the epics actually attached to this project.
        //   Tracked in ai-ru/TECH_DEBT.md (Производительность → F70).
        List<JiraIssueEntity> allEpics = loadAllEpics();
        Set<String> childKeySet = new HashSet<>();
        if (project.getChildEpicKeys() != null) {
            Collections.addAll(childKeySet, project.getChildEpicKeys());
        }
        List<JiraIssueEntity> childEpics = new ArrayList<>();
        for (JiraIssueEntity epic : allEpics) {
            if (workflowConfigService.isDone(epic.getStatus(), epic.getIssueType())) continue;
            boolean viaParent = projectKey.equals(epic.getParentKey());
            boolean viaLink = childKeySet.contains(epic.getIssueKey());
            if (viaParent || viaLink) {
                childEpics.add(epic);
            }
        }

        // Group epics by team and tally (committed / other-quarter / uncommitted).
        // teamId == 0 is the synthetic "no team mapping" bucket.
        Map<Long, int[]> tallyByTeam = new LinkedHashMap<>(); // [total, committed, other, uncommitted]
        for (JiraIssueEntity epic : childEpics) {
            long teamKey = epic.getTeamId() != null ? epic.getTeamId() : 0L;
            int[] tally = tallyByTeam.computeIfAbsent(teamKey, k -> new int[4]);
            tally[0]++; // total

            String committed = resolveCommittedQuarter(epic);
            if (committed == null) {
                tally[3]++; // uncommitted
            } else if (desiredQuarter != null && desiredQuarter.equals(committed)) {
                tally[1]++; // committed to desired
            } else {
                tally[2]++; // committed elsewhere
            }
        }

        // Resolve team metadata once. We only fetch teams that actually appear in
        // the breakdown — avoids a teamRepository scan when the project is empty.
        Map<Long, TeamEntity> teamsById = new HashMap<>();
        Set<Long> realTeamIds = new HashSet<>(tallyByTeam.keySet());
        realTeamIds.remove(0L);
        if (!realTeamIds.isEmpty()) {
            for (TeamEntity team : teamRepository.findAllById(realTeamIds)) {
                teamsById.put(team.getId(), team);
            }
        }

        List<TeamCommitmentDto> commitmentByTeam = new ArrayList<>(tallyByTeam.size());
        for (Map.Entry<Long, int[]> entry : tallyByTeam.entrySet()) {
            long teamId = entry.getKey();
            int[] t = entry.getValue();
            String teamName;
            String teamColor;
            if (teamId == 0L) {
                teamName = UNASSIGNED_TEAM_LABEL;
                teamColor = null;
            } else {
                TeamEntity team = teamsById.get(teamId);
                teamName = team != null ? team.getName() : "Team #" + teamId;
                teamColor = team != null ? team.getColor() : null;
            }
            commitmentByTeam.add(new TeamCommitmentDto(
                    teamId, teamName, teamColor,
                    t[0], t[1], t[2], t[3]
            ));
        }
        // Deterministic ordering: real teams first by name, "no team" bucket last.
        commitmentByTeam.sort((a, b) -> {
            if (a.teamId() == 0L && b.teamId() != 0L) return 1;
            if (a.teamId() != 0L && b.teamId() == 0L) return -1;
            return a.teamName().compareToIgnoreCase(b.teamName());
        });

        return new ProjectQuarterCommitmentDto(
                projectKey,
                project.getSummary(),
                desiredQuarter,
                commitmentByTeam
        );
    }

    /**
     * Find the project entity by key, verifying its board category is PROJECT.
     * "Not a project" is treated as 404 (same as "missing") — from the caller's
     * perspective there is no project to operate on under that key.
     */
    private JiraIssueEntity findProjectOrThrow(String projectKey) {
        JiraIssueEntity project = issueRepository.findByIssueKey(projectKey)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + projectKey));
        // Prefer the persisted board_category (set by sync via workflow config) so we
        // don't have to re-categorise on every read; fall back to workflowConfigService
        // when the column has not yet been populated for this row.
        String boardCategory = project.getBoardCategory();
        boolean isProject;
        if (boardCategory != null) {
            isProject = "PROJECT".equals(boardCategory);
        } else {
            isProject = workflowConfigService.isProject(project.getIssueType());
        }
        if (!isProject) {
            throw new ProjectNotFoundException(
                    "Issue " + projectKey + " is not a project (boardCategory=" + boardCategory + ")");
        }
        return project;
    }

    /**
     * Read the direct quarter label from an epic with NO inheritance from the parent
     * project. This is the F70-semantic "committed_quarter" — used everywhere the
     * team-lead's explicit commitment matters (commitment view, PM badges).
     */
    private String resolveCommittedQuarter(JiraIssueEntity epic) {
        String[] labels = epic.getLabels();
        if (labels == null) return null;
        for (String label : labels) {
            if (label != null && QUARTER_LABEL_PATTERN.matcher(label).matches()) {
                return label;
            }
        }
        return null;
    }

    /**
     * Read the direct quarter label from a project. Same lookup as
     * {@link #resolveCommittedQuarter} — separate name for readability at call
     * sites where the semantic is "what the customer wants".
     */
    private String resolveDesiredQuarter(JiraIssueEntity project) {
        return resolveCommittedQuarter(project);
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

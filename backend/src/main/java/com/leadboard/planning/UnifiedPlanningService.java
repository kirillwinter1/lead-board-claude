package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.*;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.Grade;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import com.leadboard.team.TeamService;
import com.leadboard.team.dto.PlanningConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified planning algorithm.
 *
 * Key rules:
 * 1. Epics are planned by priority (AutoScore DESC) - top epic is closed first
 * 2. Pipeline is dynamic from WorkflowConfigService (default SA→DEV→QA)
 * 3. One person per phase (cannot have 2 SAs on same story)
 * 4. Parallelism between stories - multiple SAs can work on different stories
 * 5. Day splitting - 3h on story A + 5h on story B in same day is OK
 * 6. Role transitions between epics - when SA finishes all work in epic, takes next epic
 * 7. Dependencies - blocked story waits for FULL completion of blocker (all phases)
 * 8. Auto-assign - algorithm decides assignees (Jira assignee is ignored)
 * 9. Estimates from subtasks (rough estimate only for epics in Planned status without subtasks)
 * 10. Risk buffer 20% applied
 * 11. Stories without estimates are not planned, shown as warnings
 */
@Service
public class UnifiedPlanningService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedPlanningService.class);
    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("8");
    private static final BigDecimal SECONDS_PER_HOUR = new BigDecimal("3600");

    private final JiraIssueRepository issueRepository;
    private final TeamService teamService;
    private final TeamMemberRepository memberRepository;
    private final WorkCalendarService calendarService;
    private final WorkflowConfigService workflowConfigService;
    private final StoryDependencyService dependencyService;

    public UnifiedPlanningService(
            JiraIssueRepository issueRepository,
            TeamService teamService,
            TeamMemberRepository memberRepository,
            WorkCalendarService calendarService,
            WorkflowConfigService workflowConfigService,
            StoryDependencyService dependencyService
    ) {
        this.issueRepository = issueRepository;
        this.teamService = teamService;
        this.memberRepository = memberRepository;
        this.calendarService = calendarService;
        this.workflowConfigService = workflowConfigService;
        this.dependencyService = dependencyService;
    }

    /**
     * Main entry point for unified planning.
     */
    public UnifiedPlanningResult calculatePlan(Long teamId) {
        log.info("Starting unified planning for team {}", teamId);

        // 1. Load configuration
        PlanningConfigDto config = teamService.getPlanningConfig(teamId);
        BigDecimal riskBuffer = config.riskBuffer() != null ? config.riskBuffer() : new BigDecimal("0.2");
        PlanningConfigDto.GradeCoefficients gradeCoeffs = config.gradeCoefficients() != null
                ? config.gradeCoefficients()
                : PlanningConfigDto.GradeCoefficients.defaults();

        // 2. Load team members and build assignee schedules
        List<TeamMemberEntity> members = memberRepository.findByTeamIdAndActiveTrue(teamId);
        Map<String, AssigneeSchedule> assigneeSchedules = buildAssigneeSchedules(members, gradeCoeffs);

        // 3. Load epics sorted by AutoScore
        List<JiraIssueEntity> epics = getEpicsSorted(teamId);

        // 4. Build work calendar helper
        AssigneeSchedule.WorkCalendarHelper calendarHelper = createCalendarHelper();

        // 5. Get dynamic pipeline roles
        List<String> pipelineRoles = workflowConfigService.getRoleCodesInPipelineOrder();

        // 6. Plan all stories across all epics
        List<PlannedEpic> plannedEpics = new ArrayList<>();
        List<PlanningWarning> globalWarnings = new ArrayList<>();
        Map<String, LocalDate> storyEndDates = new HashMap<>(); // For dependency tracking

        for (JiraIssueEntity epic : epics) {
            PlannedEpic plannedEpic = planEpic(
                    epic,
                    assigneeSchedules,
                    storyEndDates,
                    calendarHelper,
                    riskBuffer,
                    pipelineRoles,
                    globalWarnings
            );
            plannedEpics.add(plannedEpic);
        }

        // 7. Build assignee utilization map
        Map<String, AssigneeUtilization> utilization = buildUtilization(assigneeSchedules);

        log.info("Unified planning completed: {} epics, {} warnings",
                plannedEpics.size(), globalWarnings.size());

        return new UnifiedPlanningResult(
                teamId,
                OffsetDateTime.now(),
                plannedEpics,
                globalWarnings,
                utilization
        );
    }

    /**
     * Plans a single epic with all its stories.
     */
    private PlannedEpic planEpic(
            JiraIssueEntity epic,
            Map<String, AssigneeSchedule> assigneeSchedules,
            Map<String, LocalDate> storyEndDates,
            AssigneeSchedule.WorkCalendarHelper calendarHelper,
            BigDecimal riskBuffer,
            List<String> pipelineRoles,
            List<PlanningWarning> globalWarnings
    ) {
        String epicKey = epic.getIssueKey();
        log.debug("Planning epic {}", epicKey);

        // Get stories sorted by AutoScore with dependencies
        List<JiraIssueEntity> stories = getStoriesSorted(epicKey);

        // Check if epic has no stories but has rough estimates (planned epic)
        if (stories.isEmpty() && workflowConfigService.isPlanningAllowed(epic.getStatus()) && hasRoughEstimates(epic)) {
            return planEpicByRoughEstimates(epic, assigneeSchedules, calendarHelper, riskBuffer, pipelineRoles);
        }

        List<PlannedStory> plannedStories = new ArrayList<>();
        LocalDate epicStartDate = null;
        LocalDate epicEndDate = null;

        // Dynamic aggregation accumulators by role
        Map<String, BigDecimal> totalRoleHours = new LinkedHashMap<>();
        Map<String, LocalDate> roleStartDates = new HashMap<>();
        Map<String, LocalDate> roleEndDates = new HashMap<>();
        for (String role : pipelineRoles) {
            totalRoleHours.put(role, BigDecimal.ZERO);
        }

        // Epic progress accumulators by role
        Map<String, Long> roleEstimate = new HashMap<>();
        Map<String, Long> roleLogged = new HashMap<>();
        Map<String, Boolean> roleExists = new HashMap<>();
        Map<String, Boolean> roleDone = new HashMap<>();
        for (String role : pipelineRoles) {
            roleEstimate.put(role, 0L);
            roleLogged.put(role, 0L);
            roleExists.put(role, false);
            roleDone.put(role, true);
        }

        long epicTotalEstimate = 0;
        long epicTotalLogged = 0;
        int storiesTotal = stories.size();
        int storiesActive = 0;

        for (JiraIssueEntity story : stories) {
            // Skip done stories
            if (workflowConfigService.isDone(story.getStatus(), story.getIssueType())) {
                continue;
            }

            // Skip flagged stories
            if (Boolean.TRUE.equals(story.getFlagged())) {
                globalWarnings.add(new PlanningWarning(
                        story.getIssueKey(),
                        WarningType.FLAGGED,
                        "Story is flagged (work paused)"
                ));
                continue;
            }

            // Extract phase hours from subtasks (dynamic by role)
            Map<String, BigDecimal> phaseHoursMap = extractPhaseHoursMap(story, epic);

            // Check if story has estimates
            boolean isEmpty = phaseHoursMap.values().stream()
                    .allMatch(h -> h.compareTo(BigDecimal.ZERO) == 0);

            if (isEmpty) {
                globalWarnings.add(new PlanningWarning(
                        story.getIssueKey(),
                        WarningType.NO_ESTIMATE,
                        "Story has no subtasks with estimates"
                ));

                StoryProgressData progressData = extractProgressData(story);
                plannedStories.add(new PlannedStory(
                        story.getIssueKey(),
                        story.getSummary(),
                        story.getAutoScore(),
                        story.getStatus(),
                        null, null,
                        Map.of(),
                        story.getIsBlockedBy() != null ? story.getIsBlockedBy() : List.of(),
                        List.of(new PlanningWarning(story.getIssueKey(), WarningType.NO_ESTIMATE, "No estimate")),
                        story.getIssueType(),
                        story.getPriority(),
                        story.getFlagged(),
                        progressData.totalEstimate(),
                        progressData.totalLogged(),
                        progressData.progressPercent(),
                        progressData.roleProgress()
                ));
                continue;
            }

            // Apply risk buffer
            BigDecimal multiplier = BigDecimal.ONE.add(riskBuffer);
            Map<String, BigDecimal> bufferedHours = new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> entry : phaseHoursMap.entrySet()) {
                bufferedHours.put(entry.getKey(),
                        entry.getValue().multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
            }

            // Plan the story with dynamic pipeline
            PlannedStory plannedStory = planStory(
                    story,
                    bufferedHours,
                    pipelineRoles,
                    assigneeSchedules,
                    storyEndDates,
                    calendarHelper
            );

            plannedStories.add(plannedStory);

            // Track end date for dependencies
            if (plannedStory.endDate() != null) {
                storyEndDates.put(story.getIssueKey(), plannedStory.endDate());
            }

            // Update epic dates
            if (plannedStory.startDate() != null) {
                if (epicStartDate == null || plannedStory.startDate().isBefore(epicStartDate)) {
                    epicStartDate = plannedStory.startDate();
                }
            }
            if (plannedStory.endDate() != null) {
                if (epicEndDate == null || plannedStory.endDate().isAfter(epicEndDate)) {
                    epicEndDate = plannedStory.endDate();
                }
            }

            // Aggregate phase data dynamically
            Map<String, PhaseSchedule> phases = plannedStory.phases();
            for (Map.Entry<String, PhaseSchedule> e : phases.entrySet()) {
                aggregatePhaseData(e.getValue(), e.getKey(), totalRoleHours, roleStartDates, roleEndDates);
            }

            // Accumulate epic progress from story
            if (plannedStory.totalEstimateSeconds() != null) {
                epicTotalEstimate += plannedStory.totalEstimateSeconds();
            }
            if (plannedStory.totalLoggedSeconds() != null) {
                epicTotalLogged += plannedStory.totalLoggedSeconds();
            }

            // Accumulate role progress
            Map<String, PhaseProgressInfo> rp = plannedStory.roleProgress();
            if (rp != null) {
                for (Map.Entry<String, PhaseProgressInfo> e : rp.entrySet()) {
                    accumulateRoleProgress(e.getValue(), e.getKey(), roleEstimate, roleLogged, roleExists, roleDone);
                }
            }

            // Count active story
            if (!workflowConfigService.isDone(plannedStory.status(), story.getIssueType())) {
                storiesActive++;
            }
        }

        // Build aggregation map
        Map<String, PhaseAggregationEntry> aggregation = new LinkedHashMap<>();
        for (String role : pipelineRoles) {
            BigDecimal hours = totalRoleHours.getOrDefault(role, BigDecimal.ZERO);
            if (hours.compareTo(BigDecimal.ZERO) > 0 || roleStartDates.containsKey(role)) {
                aggregation.put(role, new PhaseAggregationEntry(
                        hours,
                        roleStartDates.get(role),
                        roleEndDates.get(role)
                ));
            }
        }

        // Calculate epic progress percent
        int epicProgressPercent = epicTotalEstimate > 0
                ? (int) Math.min(100, (epicTotalLogged * 100) / epicTotalEstimate)
                : 0;

        // Build role progress map
        Map<String, PhaseProgressInfo> epicRoleProgress = new LinkedHashMap<>();
        for (String role : pipelineRoles) {
            if (roleExists.getOrDefault(role, false)) {
                epicRoleProgress.put(role, new PhaseProgressInfo(
                        roleEstimate.get(role), roleLogged.get(role), roleDone.get(role)));
            }
        }

        LocalDate dueDate = epic.getDueDate();

        boolean isRoughEstimate = plannedStories.isEmpty() && hasRoughEstimates(epic);

        return new PlannedEpic(
                epicKey,
                epic.getSummary(),
                epic.getAutoScore(),
                epicStartDate,
                epicEndDate,
                plannedStories,
                aggregation,
                epic.getStatus(),
                dueDate,
                epicTotalEstimate,
                epicTotalLogged,
                epicProgressPercent,
                epicRoleProgress,
                storiesTotal,
                storiesActive,
                isRoughEstimate,
                epic.getRoughEstimates(),
                epic.getFlagged()
        );
    }

    private void aggregatePhaseData(PhaseSchedule phase, String roleCode,
                                     Map<String, BigDecimal> totalRoleHours,
                                     Map<String, LocalDate> roleStartDates,
                                     Map<String, LocalDate> roleEndDates) {
        if (phase == null) return;
        totalRoleHours.merge(roleCode, phase.hours(), BigDecimal::add);
        if (phase.startDate() != null) {
            roleStartDates.merge(roleCode, phase.startDate(),
                    (existing, newDate) -> newDate.isBefore(existing) ? newDate : existing);
        }
        if (phase.endDate() != null) {
            roleEndDates.merge(roleCode, phase.endDate(),
                    (existing, newDate) -> newDate.isAfter(existing) ? newDate : existing);
        }
    }

    private void accumulateRoleProgress(PhaseProgressInfo info, String roleCode,
                                         Map<String, Long> roleEstimate, Map<String, Long> roleLogged,
                                         Map<String, Boolean> roleExists, Map<String, Boolean> roleDone) {
        if (info == null) return;
        roleEstimate.merge(roleCode, info.estimateSeconds() != null ? info.estimateSeconds() : 0L, Long::sum);
        roleLogged.merge(roleCode, info.loggedSeconds() != null ? info.loggedSeconds() : 0L, Long::sum);
        roleExists.put(roleCode, true);
        if (!info.completed()) roleDone.put(roleCode, false);
    }

    /**
     * Plans a single story with dynamic pipeline (roles in pipeline order).
     */
    private PlannedStory planStory(
            JiraIssueEntity story,
            Map<String, BigDecimal> phaseHoursMap,
            List<String> pipelineRoles,
            Map<String, AssigneeSchedule> assigneeSchedules,
            Map<String, LocalDate> storyEndDates,
            AssigneeSchedule.WorkCalendarHelper calendarHelper
    ) {
        String storyKey = story.getIssueKey();
        List<PlanningWarning> warnings = new ArrayList<>();

        // Determine earliest start based on dependencies
        LocalDate earliestStart = LocalDate.now();
        List<String> blockedBy = story.getIsBlockedBy();
        if (blockedBy != null && !blockedBy.isEmpty()) {
            for (String blockerKey : blockedBy) {
                LocalDate blockerEnd = storyEndDates.get(blockerKey);
                if (blockerEnd != null) {
                    LocalDate afterBlocker = calendarHelper.nextWorkday(blockerEnd);
                    if (afterBlocker.isAfter(earliestStart)) {
                        earliestStart = afterBlocker;
                    }
                }
            }
        }

        // Plan phases sequentially by pipeline order
        LocalDate currentDate = earliestStart;
        Map<String, PhaseSchedule> roleSchedules = new LinkedHashMap<>();

        for (String roleCode : pipelineRoles) {
            BigDecimal hours = phaseHoursMap.getOrDefault(roleCode, BigDecimal.ZERO);
            if (hours.compareTo(BigDecimal.ZERO) > 0) {
                PhaseSchedule schedule = planPhase(
                        roleCode,
                        hours,
                        currentDate,
                        assigneeSchedules,
                        calendarHelper,
                        warnings,
                        storyKey
                );
                roleSchedules.put(roleCode, schedule);
                if (schedule != null && schedule.endDate() != null) {
                    currentDate = calendarHelper.nextWorkday(schedule.endDate());
                }
            }
        }

        // Determine story dates from first and last phase
        LocalDate storyStart = null;
        LocalDate storyEnd = null;
        for (String roleCode : pipelineRoles) {
            PhaseSchedule schedule = roleSchedules.get(roleCode);
            if (schedule != null && schedule.startDate() != null && storyStart == null) {
                storyStart = schedule.startDate();
            }
            if (schedule != null && schedule.endDate() != null) {
                storyEnd = schedule.endDate();
            }
        }

        // Get progress data from subtasks
        StoryProgressData progressData = extractProgressData(story);

        return new PlannedStory(
                storyKey,
                story.getSummary(),
                story.getAutoScore(),
                story.getStatus(),
                storyStart,
                storyEnd,
                roleSchedules,
                blockedBy != null ? blockedBy : List.of(),
                warnings,
                story.getIssueType(),
                story.getPriority(),
                story.getFlagged(),
                progressData.totalEstimate(),
                progressData.totalLogged(),
                progressData.progressPercent(),
                progressData.roleProgress()
        );
    }

    /**
     * Plans a single phase, finding the earliest available assignee with matching role code.
     */
    private PhaseSchedule planPhase(
            String roleCode,
            BigDecimal hours,
            LocalDate startAfter,
            Map<String, AssigneeSchedule> assigneeSchedules,
            AssigneeSchedule.WorkCalendarHelper calendarHelper,
            List<PlanningWarning> warnings,
            String storyKey
    ) {
        // Find earliest available assignee with matching role
        AssigneeSchedule bestAssignee = null;
        LocalDate bestAvailableDate = null;

        for (AssigneeSchedule schedule : assigneeSchedules.values()) {
            if (!roleCode.equals(schedule.getRoleCode())) {
                continue;
            }

            LocalDate availableDate = schedule.findFirstAvailableDate(startAfter, calendarHelper);

            if (bestAssignee == null || availableDate.isBefore(bestAvailableDate)) {
                bestAssignee = schedule;
                bestAvailableDate = availableDate;
            }
        }

        if (bestAssignee == null) {
            // No capacity for this role
            warnings.add(new PlanningWarning(
                    storyKey,
                    WarningType.NO_CAPACITY,
                    "No " + roleCode + " capacity in team"
            ));
            return PhaseSchedule.noCapacity(hours);
        }

        // Allocate hours to this assignee
        AssigneeSchedule.AllocationResult allocation = bestAssignee.allocateHours(
                hours,
                startAfter,
                calendarHelper
        );

        return new PhaseSchedule(
                bestAssignee.getAccountId(),
                bestAssignee.getDisplayName(),
                allocation.startDate(),
                allocation.endDate(),
                hours,
                false
        );
    }

    /**
     * Extracts phase hours from story's subtasks (dynamic by role code).
     */
    private Map<String, BigDecimal> extractPhaseHoursMap(JiraIssueEntity story, JiraIssueEntity epic) {
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());

        Map<String, BigDecimal> roleHours = new LinkedHashMap<>();

        for (JiraIssueEntity subtask : subtasks) {
            // Skip done subtasks
            if (workflowConfigService.isDone(subtask.getStatus(), subtask.getIssueType())) {
                continue;
            }

            // Calculate remaining hours - prefer explicit remaining estimate from Jira
            long remainingSeconds;
            if (subtask.getRemainingEstimateSeconds() != null) {
                remainingSeconds = subtask.getRemainingEstimateSeconds();
            } else {
                long estimateSeconds = subtask.getOriginalEstimateSeconds() != null ? subtask.getOriginalEstimateSeconds() : 0;
                long spentSeconds = subtask.getTimeSpentSeconds() != null ? subtask.getTimeSpentSeconds() : 0;
                remainingSeconds = Math.max(0, estimateSeconds - spentSeconds);
            }

            if (remainingSeconds <= 0) {
                continue;
            }

            BigDecimal remainingHours = new BigDecimal(remainingSeconds)
                    .divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);

            // Determine role by subtask type (dynamic)
            String roleCode = workflowConfigService.getSubtaskRole(subtask.getIssueType());
            roleHours.merge(roleCode, remainingHours, BigDecimal::add);
        }

        // If no subtask estimates and epic is in Planned status, use rough estimates from JSONB
        boolean allZero = roleHours.values().stream()
                .allMatch(h -> h.compareTo(BigDecimal.ZERO) == 0);

        if ((roleHours.isEmpty() || allZero) && workflowConfigService.isPlanningAllowed(epic.getStatus())) {
            Map<String, BigDecimal> roughEstimates = story.getRoughEstimates();
            if (roughEstimates != null) {
                for (Map.Entry<String, BigDecimal> entry : roughEstimates.entrySet()) {
                    if (entry.getValue() != null && entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                        roleHours.put(entry.getKey(), entry.getValue().multiply(HOURS_PER_DAY));
                    }
                }
            }
        }

        return roleHours;
    }

    /**
     * Extracts progress data from story's subtasks (for tooltip display).
     */
    private StoryProgressData extractProgressData(JiraIssueEntity story) {
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());

        // Dynamic role accumulators
        Map<String, Long> roleEstimate = new HashMap<>();
        Map<String, Long> roleLogged = new HashMap<>();
        Map<String, Boolean> roleExists = new HashMap<>();
        Map<String, Boolean> roleDone = new HashMap<>();

        for (JiraIssueEntity subtask : subtasks) {
            long est = subtask.getEffectiveEstimateSeconds();
            long logged = subtask.getTimeSpentSeconds() != null ? subtask.getTimeSpentSeconds() : 0;
            boolean isDone = workflowConfigService.isDone(subtask.getStatus(), subtask.getIssueType());

            String roleCode = workflowConfigService.getSubtaskRole(subtask.getIssueType());

            roleEstimate.merge(roleCode, est, Long::sum);
            roleLogged.merge(roleCode, logged, Long::sum);
            roleExists.put(roleCode, true);
            if (!isDone) {
                roleDone.put(roleCode, false);
            } else {
                roleDone.putIfAbsent(roleCode, true);
            }
        }

        long totalEstimate = roleEstimate.values().stream().mapToLong(Long::longValue).sum();
        long totalLogged = roleLogged.values().stream().mapToLong(Long::longValue).sum();
        int progressPercent = totalEstimate > 0 ? (int) Math.min(100, (totalLogged * 100) / totalEstimate) : 0;

        // Build dynamic role progress map
        Map<String, PhaseProgressInfo> roleProgress = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : roleExists.entrySet()) {
            if (entry.getValue()) {
                String role = entry.getKey();
                roleProgress.put(role, new PhaseProgressInfo(
                        roleEstimate.get(role), roleLogged.get(role),
                        roleDone.getOrDefault(role, true)));
            }
        }

        return new StoryProgressData(totalEstimate, totalLogged, progressPercent, roleProgress);
    }

    /**
     * Builds assignee schedules from team members.
     */
    private Map<String, AssigneeSchedule> buildAssigneeSchedules(
            List<TeamMemberEntity> members,
            PlanningConfigDto.GradeCoefficients gradeCoeffs
    ) {
        Map<String, AssigneeSchedule> schedules = new HashMap<>();

        for (TeamMemberEntity member : members) {
            BigDecimal gradeCoeff = getGradeCoefficient(member.getGrade(), gradeCoeffs);
            BigDecimal effectiveHours = member.getHoursPerDay()
                    .divide(gradeCoeff, 2, RoundingMode.HALF_UP);

            schedules.put(
                    member.getJiraAccountId(),
                    new AssigneeSchedule(
                            member.getJiraAccountId(),
                            member.getDisplayName(),
                            member.getRole(),
                            effectiveHours
                    )
            );
        }

        return schedules;
    }

    private BigDecimal getGradeCoefficient(Grade grade, PlanningConfigDto.GradeCoefficients coefficients) {
        return switch (grade) {
            case SENIOR -> coefficients.senior() != null ? coefficients.senior() : new BigDecimal("0.8");
            case MIDDLE -> coefficients.middle() != null ? coefficients.middle() : new BigDecimal("1.0");
            case JUNIOR -> coefficients.junior() != null ? coefficients.junior() : new BigDecimal("1.5");
        };
    }

    /**
     * Gets epics sorted by manual_order ASC.
     */
    private List<JiraIssueEntity> getEpicsSorted(Long teamId) {
        List<JiraIssueEntity> epics = issueRepository.findEpicsByTeamOrderByManualOrder(teamId);

        // Filter out done epics and those not allowed for planning
        return epics.stream()
                .filter(e -> !workflowConfigService.isDone(e.getStatus(), e.getIssueType()))
                .filter(e -> workflowConfigService.isPlanningAllowed(e.getStatus()))
                .toList();
    }

    /**
     * Gets stories for an epic, sorted by manual_order with dependencies.
     */
    private List<JiraIssueEntity> getStoriesSorted(String epicKey) {
        List<JiraIssueEntity> children = issueRepository.findByParentKeyOrderByManualOrderAsc(epicKey);

        // Filter to stories only (not subtasks) - uses dynamic config
        List<JiraIssueEntity> stories = children.stream()
                .filter(c -> workflowConfigService.isStory(c.getIssueType()))
                .toList();

        if (stories.isEmpty()) {
            return stories;
        }

        // Build order map (use manualOrder, fallback to autoScore for dependencies)
        Map<String, Double> storyScores = new HashMap<>();
        for (JiraIssueEntity story : stories) {
            Integer manualOrder = story.getManualOrder();
            if (manualOrder != null) {
                storyScores.put(story.getIssueKey(), -manualOrder.doubleValue());
            } else {
                BigDecimal score = story.getAutoScore() != null ? story.getAutoScore() : BigDecimal.ZERO;
                storyScores.put(story.getIssueKey(), score.doubleValue());
            }
        }

        // Topological sort with dependencies
        return dependencyService.topologicalSort(stories, storyScores);
    }

    /**
     * Creates work calendar helper.
     */
    private AssigneeSchedule.WorkCalendarHelper createCalendarHelper() {
        return new AssigneeSchedule.WorkCalendarHelper() {
            @Override
            public LocalDate ensureWorkday(LocalDate date) {
                if (calendarService.isWorkday(date)) {
                    return date;
                }
                return calendarService.addWorkdays(date, 1);
            }

            @Override
            public LocalDate nextWorkday(LocalDate date) {
                return calendarService.addWorkdays(date, 1);
            }

            @Override
            public boolean isWorkday(LocalDate date) {
                return calendarService.isWorkday(date);
            }
        };
    }

    /**
     * Checks if epic has any rough estimates set.
     */
    private boolean hasRoughEstimates(JiraIssueEntity epic) {
        Map<String, BigDecimal> estimates = epic.getRoughEstimates();
        if (estimates != null && !estimates.isEmpty()) {
            return estimates.values().stream()
                    .anyMatch(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0);
        }
        return false;
    }

    /**
     * Plans an epic directly by its rough estimates (when no stories exist).
     */
    private PlannedEpic planEpicByRoughEstimates(
            JiraIssueEntity epic,
            Map<String, AssigneeSchedule> assigneeSchedules,
            AssigneeSchedule.WorkCalendarHelper calendarHelper,
            BigDecimal riskBuffer,
            List<String> pipelineRoles
    ) {
        String epicKey = epic.getIssueKey();
        log.info("Planning epic {} by rough estimates (no stories)", epicKey);

        // Get rough estimates from JSONB and convert to hours
        Map<String, BigDecimal> roleHours = new LinkedHashMap<>();
        Map<String, BigDecimal> roughEstimates = epic.getRoughEstimates();
        if (roughEstimates != null) {
            for (Map.Entry<String, BigDecimal> entry : roughEstimates.entrySet()) {
                if (entry.getValue() != null && entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                    roleHours.put(entry.getKey(), entry.getValue().multiply(HOURS_PER_DAY));
                }
            }
        }
        // Apply risk buffer
        BigDecimal multiplier = BigDecimal.ONE.add(riskBuffer);
        for (Map.Entry<String, BigDecimal> entry : roleHours.entrySet()) {
            entry.setValue(entry.getValue().multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
        }

        // Plan phases sequentially by pipeline order
        LocalDate currentDate = LocalDate.now();
        List<PlanningWarning> warnings = new ArrayList<>();
        Map<String, LocalDate> startDates = new HashMap<>();
        Map<String, LocalDate> endDates = new HashMap<>();

        for (String roleCode : pipelineRoles) {
            BigDecimal hours = roleHours.getOrDefault(roleCode, BigDecimal.ZERO);
            if (hours.compareTo(BigDecimal.ZERO) > 0) {
                PhaseSchedule schedule = planPhase(
                        roleCode, hours, currentDate, assigneeSchedules, calendarHelper, warnings, epicKey
                );
                if (schedule != null && schedule.startDate() != null) {
                    startDates.put(roleCode, schedule.startDate());
                    endDates.put(roleCode, schedule.endDate());
                    currentDate = calendarHelper.nextWorkday(schedule.endDate());
                }
            }
        }

        // Determine epic dates from first/last role
        LocalDate epicStartDate = null;
        LocalDate epicEndDate = null;
        for (String roleCode : pipelineRoles) {
            if (startDates.containsKey(roleCode) && epicStartDate == null) {
                epicStartDate = startDates.get(roleCode);
            }
            if (endDates.containsKey(roleCode)) {
                epicEndDate = endDates.get(roleCode);
            }
        }

        // Build phase aggregation map
        Map<String, PhaseAggregationEntry> aggregation = new LinkedHashMap<>();
        for (String role : pipelineRoles) {
            BigDecimal hours = roleHours.getOrDefault(role, BigDecimal.ZERO);
            if (hours.compareTo(BigDecimal.ZERO) > 0) {
                aggregation.put(role, new PhaseAggregationEntry(
                        hours, startDates.get(role), endDates.get(role)));
            }
        }

        return new PlannedEpic(
                epicKey,
                epic.getSummary(),
                epic.getAutoScore(),
                epicStartDate,
                epicEndDate,
                List.of(),
                aggregation,
                epic.getStatus(),
                epic.getDueDate(),
                0L,
                0L,
                0,
                Map.of(),
                0,
                0,
                true,
                epic.getRoughEstimates(),
                epic.getFlagged()
        );
    }

    /**
     * Builds utilization map from assignee schedules.
     */
    private Map<String, AssigneeUtilization> buildUtilization(Map<String, AssigneeSchedule> schedules) {
        Map<String, AssigneeUtilization> result = new HashMap<>();

        for (Map.Entry<String, AssigneeSchedule> entry : schedules.entrySet()) {
            AssigneeSchedule schedule = entry.getValue();

            result.put(entry.getKey(), new AssigneeUtilization(
                    schedule.getDisplayName(),
                    schedule.getRoleCode(),
                    schedule.getTotalAssignedHours(),
                    schedule.getEffectiveHoursPerDay(),
                    schedule.getDailyLoad()
            ));
        }

        return result;
    }

    /**
     * Aggregated progress data from subtasks.
     */
    private record StoryProgressData(
            long totalEstimate,
            long totalLogged,
            int progressPercent,
            Map<String, PhaseProgressInfo> roleProgress
    ) {
        static StoryProgressData empty() {
            return new StoryProgressData(0, 0, 0, Map.of());
        }
    }
}

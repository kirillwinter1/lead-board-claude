package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.UnifiedPlanningResult.*;
import com.leadboard.status.StatusMappingConfig;
import com.leadboard.status.StatusMappingService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.Grade;
import com.leadboard.team.Role;
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
 * 2. Pipeline SA→DEV→QA is strictly sequential within each story
 * 3. One person per phase (cannot have 2 SAs on same story)
 * 4. Parallelism between stories - multiple SAs can work on different stories
 * 5. Day splitting - 3h on story A + 5h on story B in same day is OK
 * 6. Role transitions between epics - when SA finishes all work in epic, takes next epic
 * 7. Dependencies - blocked story waits for FULL completion of blocker (SA+DEV+QA)
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
    private static final List<String> EPIC_TYPES = List.of("Epic", "Эпик");
    private static final List<String> STORY_TYPES = List.of("Story", "История", "Bug", "Баг");
    private static final List<String> PLANNED_STATUSES = List.of("Planned", "Запланировано", "To Do", "К выполнению");

    private final JiraIssueRepository issueRepository;
    private final TeamService teamService;
    private final TeamMemberRepository memberRepository;
    private final WorkCalendarService calendarService;
    private final StatusMappingService statusMappingService;
    private final StoryDependencyService dependencyService;

    public UnifiedPlanningService(
            JiraIssueRepository issueRepository,
            TeamService teamService,
            TeamMemberRepository memberRepository,
            WorkCalendarService calendarService,
            StatusMappingService statusMappingService,
            StoryDependencyService dependencyService
    ) {
        this.issueRepository = issueRepository;
        this.teamService = teamService;
        this.memberRepository = memberRepository;
        this.calendarService = calendarService;
        this.statusMappingService = statusMappingService;
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
        StatusMappingConfig statusMapping = config.statusMapping();
        PlanningConfigDto.GradeCoefficients gradeCoeffs = config.gradeCoefficients() != null
                ? config.gradeCoefficients()
                : PlanningConfigDto.GradeCoefficients.defaults();

        // 2. Load team members and build assignee schedules
        List<TeamMemberEntity> members = memberRepository.findByTeamIdAndActiveTrue(teamId);
        Map<String, AssigneeSchedule> assigneeSchedules = buildAssigneeSchedules(members, gradeCoeffs);

        // 3. Load epics sorted by AutoScore
        List<JiraIssueEntity> epics = getEpicsSorted(teamId, statusMapping);

        // 4. Build work calendar helper
        AssigneeSchedule.WorkCalendarHelper calendarHelper = createCalendarHelper();

        // 5. Plan all stories across all epics
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
                    statusMapping,
                    globalWarnings
            );
            plannedEpics.add(plannedEpic);
        }

        // 6. Build assignee utilization map
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
            StatusMappingConfig statusMapping,
            List<PlanningWarning> globalWarnings
    ) {
        String epicKey = epic.getIssueKey();
        log.debug("Planning epic {}", epicKey);

        // Get stories sorted by AutoScore with dependencies
        List<JiraIssueEntity> stories = getStoriesSorted(epicKey, statusMapping);

        List<PlannedStory> plannedStories = new ArrayList<>();
        LocalDate epicStartDate = null;
        LocalDate epicEndDate = null;

        // Aggregation accumulators
        BigDecimal totalSaHours = BigDecimal.ZERO;
        BigDecimal totalDevHours = BigDecimal.ZERO;
        BigDecimal totalQaHours = BigDecimal.ZERO;
        LocalDate saStartDate = null, saEndDate = null;
        LocalDate devStartDate = null, devEndDate = null;
        LocalDate qaStartDate = null, qaEndDate = null;

        // Epic progress accumulators
        long epicTotalEstimate = 0;
        long epicTotalLogged = 0;
        long saEstimate = 0, saLogged = 0;
        long devEstimate = 0, devLogged = 0;
        long qaEstimate = 0, qaLogged = 0;
        boolean saExists = false, devExists = false, qaExists = false;
        boolean saDone = true, devDone = true, qaDone = true;
        int storiesTotal = stories.size();
        int storiesActive = 0;

        for (JiraIssueEntity story : stories) {
            // Skip done stories
            if (statusMappingService.isDone(story.getStatus(), statusMapping)) {
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

            // Extract phase hours from subtasks
            PhaseHours phaseHours = extractPhaseHours(story, epic, statusMapping);

            // Check if story has estimates
            if (phaseHours.isEmpty()) {
                globalWarnings.add(new PlanningWarning(
                        story.getIssueKey(),
                        WarningType.NO_ESTIMATE,
                        "Story has no subtasks with estimates"
                ));

                // Add unplanned story to list with warning
                StoryProgressData progressData = extractProgressData(story, statusMapping);
                plannedStories.add(new PlannedStory(
                        story.getIssueKey(),
                        story.getSummary(),
                        story.getAutoScore(),
                        story.getStatus(),
                        null, null,
                        PlannedPhases.empty(),
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
            phaseHours = phaseHours.applyBuffer(riskBuffer);

            // Plan the story
            PlannedStory plannedStory = planStory(
                    story,
                    phaseHours,
                    assigneeSchedules,
                    storyEndDates,
                    calendarHelper,
                    statusMapping
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

            // Aggregate phase data
            PlannedPhases phases = plannedStory.phases();
            if (phases.sa() != null) {
                totalSaHours = totalSaHours.add(phases.sa().hours());
                if (saStartDate == null || (phases.sa().startDate() != null && phases.sa().startDate().isBefore(saStartDate))) {
                    saStartDate = phases.sa().startDate();
                }
                if (saEndDate == null || (phases.sa().endDate() != null && phases.sa().endDate().isAfter(saEndDate))) {
                    saEndDate = phases.sa().endDate();
                }
            }
            if (phases.dev() != null) {
                totalDevHours = totalDevHours.add(phases.dev().hours());
                if (devStartDate == null || (phases.dev().startDate() != null && phases.dev().startDate().isBefore(devStartDate))) {
                    devStartDate = phases.dev().startDate();
                }
                if (devEndDate == null || (phases.dev().endDate() != null && phases.dev().endDate().isAfter(devEndDate))) {
                    devEndDate = phases.dev().endDate();
                }
            }
            if (phases.qa() != null) {
                totalQaHours = totalQaHours.add(phases.qa().hours());
                if (qaStartDate == null || (phases.qa().startDate() != null && phases.qa().startDate().isBefore(qaStartDate))) {
                    qaStartDate = phases.qa().startDate();
                }
                if (qaEndDate == null || (phases.qa().endDate() != null && phases.qa().endDate().isAfter(qaEndDate))) {
                    qaEndDate = phases.qa().endDate();
                }
            }

            // Accumulate epic progress from story
            if (plannedStory.totalEstimateSeconds() != null) {
                epicTotalEstimate += plannedStory.totalEstimateSeconds();
            }
            if (plannedStory.totalLoggedSeconds() != null) {
                epicTotalLogged += plannedStory.totalLoggedSeconds();
            }

            // Accumulate role progress
            RoleProgressInfo rp = plannedStory.roleProgress();
            if (rp != null) {
                if (rp.sa() != null) {
                    saEstimate += rp.sa().estimateSeconds() != null ? rp.sa().estimateSeconds() : 0;
                    saLogged += rp.sa().loggedSeconds() != null ? rp.sa().loggedSeconds() : 0;
                    saExists = true;
                    if (!rp.sa().completed()) saDone = false;
                }
                if (rp.dev() != null) {
                    devEstimate += rp.dev().estimateSeconds() != null ? rp.dev().estimateSeconds() : 0;
                    devLogged += rp.dev().loggedSeconds() != null ? rp.dev().loggedSeconds() : 0;
                    devExists = true;
                    if (!rp.dev().completed()) devDone = false;
                }
                if (rp.qa() != null) {
                    qaEstimate += rp.qa().estimateSeconds() != null ? rp.qa().estimateSeconds() : 0;
                    qaLogged += rp.qa().loggedSeconds() != null ? rp.qa().loggedSeconds() : 0;
                    qaExists = true;
                    if (!rp.qa().completed()) qaDone = false;
                }
            }

            // Count active story
            if (!statusMappingService.isDone(plannedStory.status(), statusMapping)) {
                storiesActive++;
            }
        }

        PhaseAggregation aggregation = new PhaseAggregation(
                totalSaHours, totalDevHours, totalQaHours,
                saStartDate, saEndDate,
                devStartDate, devEndDate,
                qaStartDate, qaEndDate
        );

        // Calculate epic progress percent
        int epicProgressPercent = epicTotalEstimate > 0
                ? (int) Math.min(100, (epicTotalLogged * 100) / epicTotalEstimate)
                : 0;

        // Build role progress for epic
        RoleProgressInfo epicRoleProgress = new RoleProgressInfo(
                saExists ? new PhaseProgressInfo(saEstimate, saLogged, saDone) : null,
                devExists ? new PhaseProgressInfo(devEstimate, devLogged, devDone) : null,
                qaExists ? new PhaseProgressInfo(qaEstimate, qaLogged, qaDone) : null
        );

        // Get due date from epic entity
        LocalDate dueDate = epic.getDueDate();

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
                storiesActive
        );
    }

    /**
     * Plans a single story with SA → DEV → QA pipeline.
     */
    private PlannedStory planStory(
            JiraIssueEntity story,
            PhaseHours phaseHours,
            Map<String, AssigneeSchedule> assigneeSchedules,
            Map<String, LocalDate> storyEndDates,
            AssigneeSchedule.WorkCalendarHelper calendarHelper,
            StatusMappingConfig statusMapping
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

        // Plan phases sequentially: SA → DEV → QA
        LocalDate currentDate = earliestStart;

        // SA Phase
        PhaseSchedule saSchedule = null;
        if (phaseHours.sa().compareTo(BigDecimal.ZERO) > 0) {
            saSchedule = planPhase(
                    Role.SA,
                    phaseHours.sa(),
                    currentDate,
                    assigneeSchedules,
                    calendarHelper,
                    warnings,
                    storyKey
            );
            if (saSchedule != null && saSchedule.endDate() != null) {
                currentDate = calendarHelper.nextWorkday(saSchedule.endDate());
            }
        }

        // DEV Phase (starts after SA)
        PhaseSchedule devSchedule = null;
        if (phaseHours.dev().compareTo(BigDecimal.ZERO) > 0) {
            devSchedule = planPhase(
                    Role.DEV,
                    phaseHours.dev(),
                    currentDate,
                    assigneeSchedules,
                    calendarHelper,
                    warnings,
                    storyKey
            );
            if (devSchedule != null && devSchedule.endDate() != null) {
                currentDate = calendarHelper.nextWorkday(devSchedule.endDate());
            }
        }

        // QA Phase (starts after DEV)
        PhaseSchedule qaSchedule = null;
        if (phaseHours.qa().compareTo(BigDecimal.ZERO) > 0) {
            qaSchedule = planPhase(
                    Role.QA,
                    phaseHours.qa(),
                    currentDate,
                    assigneeSchedules,
                    calendarHelper,
                    warnings,
                    storyKey
            );
        }

        // Determine story dates
        LocalDate storyStart = null;
        LocalDate storyEnd = null;

        if (saSchedule != null && saSchedule.startDate() != null) {
            storyStart = saSchedule.startDate();
        } else if (devSchedule != null && devSchedule.startDate() != null) {
            storyStart = devSchedule.startDate();
        } else if (qaSchedule != null && qaSchedule.startDate() != null) {
            storyStart = qaSchedule.startDate();
        }

        if (qaSchedule != null && qaSchedule.endDate() != null) {
            storyEnd = qaSchedule.endDate();
        } else if (devSchedule != null && devSchedule.endDate() != null) {
            storyEnd = devSchedule.endDate();
        } else if (saSchedule != null && saSchedule.endDate() != null) {
            storyEnd = saSchedule.endDate();
        }

        // Get progress data from subtasks
        StoryProgressData progressData = extractProgressData(story, statusMapping);

        return new PlannedStory(
                storyKey,
                story.getSummary(),
                story.getAutoScore(),
                story.getStatus(),
                storyStart,
                storyEnd,
                new PlannedPhases(saSchedule, devSchedule, qaSchedule),
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
     * Plans a single phase, finding the earliest available assignee.
     */
    private PhaseSchedule planPhase(
            Role role,
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
            if (schedule.getRole() != role) {
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
                    "No " + role + " capacity in team"
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
     * Extracts phase hours from story's subtasks.
     */
    private PhaseHours extractPhaseHours(JiraIssueEntity story, JiraIssueEntity epic, StatusMappingConfig statusMapping) {
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());

        BigDecimal saHours = BigDecimal.ZERO;
        BigDecimal devHours = BigDecimal.ZERO;
        BigDecimal qaHours = BigDecimal.ZERO;

        for (JiraIssueEntity subtask : subtasks) {
            // Skip done subtasks
            if (statusMappingService.isDone(subtask.getStatus(), statusMapping)) {
                continue;
            }

            // Calculate remaining hours - prefer explicit remaining estimate from Jira
            long remainingSeconds;
            if (subtask.getRemainingEstimateSeconds() != null) {
                // Use explicit remaining estimate (user set this in Jira)
                remainingSeconds = subtask.getRemainingEstimateSeconds();
            } else {
                // Fallback: calculate from original estimate - spent
                long estimateSeconds = subtask.getOriginalEstimateSeconds() != null ? subtask.getOriginalEstimateSeconds() : 0;
                long spentSeconds = subtask.getTimeSpentSeconds() != null ? subtask.getTimeSpentSeconds() : 0;
                remainingSeconds = Math.max(0, estimateSeconds - spentSeconds);
            }

            if (remainingSeconds <= 0) {
                continue;
            }

            BigDecimal remainingHours = new BigDecimal(remainingSeconds)
                    .divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);

            // Determine role by subtask type
            String phase = statusMappingService.determinePhase(
                    subtask.getStatus(),
                    subtask.getIssueType(),
                    statusMapping
            );

            switch (phase) {
                case "SA" -> saHours = saHours.add(remainingHours);
                case "QA" -> qaHours = qaHours.add(remainingHours);
                default -> devHours = devHours.add(remainingHours);
            }
        }

        // If no subtask estimates and epic is in Planned status, use rough estimate
        if (saHours.compareTo(BigDecimal.ZERO) == 0 &&
                devHours.compareTo(BigDecimal.ZERO) == 0 &&
                qaHours.compareTo(BigDecimal.ZERO) == 0) {

            if (isPlannedStatus(epic.getStatus())) {
                // Use story's rough estimate if available, otherwise skip
                if (story.getRoughEstimateSaDays() != null) {
                    saHours = story.getRoughEstimateSaDays().multiply(HOURS_PER_DAY);
                }
                if (story.getRoughEstimateDevDays() != null) {
                    devHours = story.getRoughEstimateDevDays().multiply(HOURS_PER_DAY);
                }
                if (story.getRoughEstimateQaDays() != null) {
                    qaHours = story.getRoughEstimateQaDays().multiply(HOURS_PER_DAY);
                }
            }
        }

        return new PhaseHours(saHours, devHours, qaHours);
    }

    /**
     * Extracts progress data from story's subtasks (for tooltip display).
     * Uses JiraIssueEntity.getEffectiveEstimateSeconds() as single source of truth.
     */
    private StoryProgressData extractProgressData(JiraIssueEntity story, StatusMappingConfig statusMapping) {
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());

        long saEstimate = 0, saLogged = 0;
        long devEstimate = 0, devLogged = 0;
        long qaEstimate = 0, qaLogged = 0;
        boolean saDone = true, devDone = true, qaDone = true;
        boolean saExists = false, devExists = false, qaExists = false;

        for (JiraIssueEntity subtask : subtasks) {
            // Use effective estimate from entity (single source of truth)
            long est = subtask.getEffectiveEstimateSeconds();
            long log = subtask.getTimeSpentSeconds() != null ? subtask.getTimeSpentSeconds() : 0;
            boolean isDone = statusMappingService.isDone(subtask.getStatus(), statusMapping);

            String phase = statusMappingService.determinePhase(
                    subtask.getStatus(),
                    subtask.getIssueType(),
                    statusMapping
            );

            switch (phase) {
                case "SA" -> {
                    saEstimate += est;
                    saLogged += log;
                    saExists = true;
                    if (!isDone) saDone = false;
                }
                case "QA" -> {
                    qaEstimate += est;
                    qaLogged += log;
                    qaExists = true;
                    if (!isDone) qaDone = false;
                }
                default -> {
                    devEstimate += est;
                    devLogged += log;
                    devExists = true;
                    if (!isDone) devDone = false;
                }
            }
        }

        long totalEstimate = saEstimate + devEstimate + qaEstimate;
        long totalLogged = saLogged + devLogged + qaLogged;
        int progressPercent = totalEstimate > 0 ? (int) Math.min(100, (totalLogged * 100) / totalEstimate) : 0;

        RoleProgressInfo roleProgress = new RoleProgressInfo(
                saExists ? new PhaseProgressInfo(saEstimate, saLogged, saDone) : null,
                devExists ? new PhaseProgressInfo(devEstimate, devLogged, devDone) : null,
                qaExists ? new PhaseProgressInfo(qaEstimate, qaLogged, qaDone) : null
        );

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
     * Gets epics sorted by AutoScore DESC.
     */
    private List<JiraIssueEntity> getEpicsSorted(Long teamId, StatusMappingConfig statusMapping) {
        List<JiraIssueEntity> epics = issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(
                EPIC_TYPES, teamId);

        // Filter out done epics and those not allowed for planning
        return epics.stream()
                .filter(e -> !statusMappingService.isDone(e.getStatus(), statusMapping))
                .filter(e -> statusMappingService.isPlanningAllowed(e.getStatus(), statusMapping))
                .toList();
    }

    /**
     * Gets stories for an epic, sorted by AutoScore with dependencies.
     */
    private List<JiraIssueEntity> getStoriesSorted(String epicKey, StatusMappingConfig statusMapping) {
        List<JiraIssueEntity> children = issueRepository.findByParentKey(epicKey);

        // Filter to stories only (not subtasks)
        List<JiraIssueEntity> stories = children.stream()
                .filter(c -> STORY_TYPES.stream()
                        .anyMatch(t -> t.equalsIgnoreCase(c.getIssueType())))
                .toList();

        if (stories.isEmpty()) {
            return stories;
        }

        // Build score map
        Map<String, Double> storyScores = new HashMap<>();
        for (JiraIssueEntity story : stories) {
            BigDecimal score = story.getAutoScore() != null ? story.getAutoScore() : BigDecimal.ZERO;
            storyScores.put(story.getIssueKey(), score.doubleValue());
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
     * Checks if epic status is "Planned".
     */
    private boolean isPlannedStatus(String status) {
        if (status == null) return false;
        return PLANNED_STATUSES.stream()
                .anyMatch(s -> s.equalsIgnoreCase(status));
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
                    schedule.getRole().name(),
                    schedule.getTotalAssignedHours(),
                    schedule.getEffectiveHoursPerDay(),
                    schedule.getDailyLoad()
            ));
        }

        return result;
    }

    /**
     * Phase hours container.
     */
    private record PhaseHours(BigDecimal sa, BigDecimal dev, BigDecimal qa) {
        boolean isEmpty() {
            return sa.compareTo(BigDecimal.ZERO) == 0 &&
                    dev.compareTo(BigDecimal.ZERO) == 0 &&
                    qa.compareTo(BigDecimal.ZERO) == 0;
        }

        PhaseHours applyBuffer(BigDecimal buffer) {
            BigDecimal multiplier = BigDecimal.ONE.add(buffer);
            return new PhaseHours(
                    sa.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                    dev.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
                    qa.multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
            );
        }
    }

    /**
     * Aggregated progress data from subtasks.
     */
    private record StoryProgressData(
            long totalEstimate,
            long totalLogged,
            int progressPercent,
            RoleProgressInfo roleProgress
    ) {
        static StoryProgressData empty() {
            return new StoryProgressData(0, 0, 0, RoleProgressInfo.empty());
        }
    }
}

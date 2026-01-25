package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.status.StatusMappingConfig;
import com.leadboard.status.StatusMappingService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.*;
import com.leadboard.team.dto.PlanningConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for story-level scheduling with assignee-based capacity allocation.
 *
 * Algorithm:
 * 1. Get epic's stories sorted by AutoScore with dependencies resolved
 * 2. Build assignee schedule map tracking each team member's next available date
 * 3. For each story:
 *    - Determine assignee (from story or auto-assign to available member)
 *    - Calculate remaining work = estimate - timeSpent
 *    - Check dependencies - can work start?
 *    - Find earliest start date (assignee available + dependencies met + epic start)
 *    - Calculate duration based on assignee's effective capacity (hoursPerDay / gradeCoefficient)
 *    - Update assignee's next available date
 * 4. Return complete schedule with start/end dates for all stories
 */
@Service
public class StoryForecastService {

    private static final Logger log = LoggerFactory.getLogger(StoryForecastService.class);
    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("8");

    private final JiraIssueRepository issueRepository;
    private final TeamService teamService;
    private final TeamMemberRepository memberRepository;
    private final WorkCalendarService calendarService;
    private final StatusMappingService statusMappingService;
    private final StoryAutoScoreService storyAutoScoreService;
    private final StoryDependencyService storyDependencyService;

    public StoryForecastService(
            JiraIssueRepository issueRepository,
            TeamService teamService,
            TeamMemberRepository memberRepository,
            WorkCalendarService calendarService,
            StatusMappingService statusMappingService,
            StoryAutoScoreService storyAutoScoreService,
            StoryDependencyService storyDependencyService
    ) {
        this.issueRepository = issueRepository;
        this.teamService = teamService;
        this.memberRepository = memberRepository;
        this.calendarService = calendarService;
        this.statusMappingService = statusMappingService;
        this.storyAutoScoreService = storyAutoScoreService;
        this.storyDependencyService = storyDependencyService;
    }

    /**
     * Calculate story forecast for an epic.
     */
    public StoryForecast calculateStoryForecast(String epicKey, Long teamId) {
        // Get epic
        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicKey));

        // Get team configuration
        PlanningConfigDto config = teamService.getPlanningConfig(teamId);
        PlanningConfigDto.GradeCoefficients gradeCoefficients = config.gradeCoefficients() != null
                ? config.gradeCoefficients()
                : PlanningConfigDto.GradeCoefficients.defaults();
        StatusMappingConfig statusMapping = config.statusMapping();

        // Get team members
        List<TeamMemberEntity> members = memberRepository.findByTeamIdAndActiveTrue(teamId);

        // Get stories for epic
        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epicKey);

        // Get story AutoScores
        Map<String, Double> storyScores = new HashMap<>();
        for (JiraIssueEntity story : stories) {
            BigDecimal autoScore = story.getAutoScore() != null ? story.getAutoScore() : BigDecimal.ZERO;
            storyScores.put(story.getIssueKey(), autoScore.doubleValue());
        }

        // Sort stories by dependencies and AutoScore
        List<JiraIssueEntity> sortedStories = storyDependencyService.topologicalSort(stories, storyScores);

        // Build assignee schedules
        Map<String, AssigneeSchedule> assigneeSchedules = buildAssigneeSchedules(members, gradeCoefficients);

        // Calculate story schedules
        LocalDate epicStartDate = determineEpicStartDate(epic, statusMapping);
        List<StorySchedule> schedules = calculateStorySchedules(
                sortedStories,
                assigneeSchedules,
                epicStartDate,
                statusMapping
        );

        // Calculate assignee utilization
        Map<String, AssigneeUtilization> utilization = calculateAssigneeUtilization(assigneeSchedules, schedules);

        return new StoryForecast(
                epicKey,
                epicStartDate,
                schedules,
                utilization
        );
    }

    /**
     * Build assignee schedule map for capacity tracking.
     */
    private Map<String, AssigneeSchedule> buildAssigneeSchedules(
            List<TeamMemberEntity> members,
            PlanningConfigDto.GradeCoefficients gradeCoefficients
    ) {
        Map<String, AssigneeSchedule> schedules = new HashMap<>();
        LocalDate today = LocalDate.now();

        for (TeamMemberEntity member : members) {
            BigDecimal gradeCoefficient = switch (member.getGrade()) {
                case SENIOR -> gradeCoefficients.senior();
                case MIDDLE -> gradeCoefficients.middle();
                case JUNIOR -> gradeCoefficients.junior();
            };

            // Effective hours per day = hoursPerDay / gradeCoefficient
            // e.g., Senior (0.8): 6.0 / 0.8 = 7.5 effective hours per day
            BigDecimal effectiveHoursPerDay = member.getHoursPerDay()
                    .divide(gradeCoefficient, 2, RoundingMode.HALF_UP);

            schedules.put(
                    member.getJiraAccountId(),
                    new AssigneeSchedule(
                            member.getJiraAccountId(),
                            member.getDisplayName(),
                            member.getRole(),
                            effectiveHoursPerDay,
                            today // Start from today
                    )
            );
        }

        return schedules;
    }

    /**
     * Calculate schedules for all stories.
     */
    private List<StorySchedule> calculateStorySchedules(
            List<JiraIssueEntity> stories,
            Map<String, AssigneeSchedule> assigneeSchedules,
            LocalDate epicStartDate,
            StatusMappingConfig statusMapping
    ) {
        List<StorySchedule> schedules = new ArrayList<>();
        Map<String, LocalDate> storyEndDates = new HashMap<>(); // For dependency tracking

        for (JiraIssueEntity story : stories) {
            StorySchedule schedule = calculateSingleStorySchedule(
                    story,
                    assigneeSchedules,
                    epicStartDate,
                    storyEndDates,
                    statusMapping
            );
            schedules.add(schedule);
            storyEndDates.put(story.getIssueKey(), schedule.endDate());
        }

        return schedules;
    }

    /**
     * Calculate schedule for a single story.
     */
    private StorySchedule calculateSingleStorySchedule(
            JiraIssueEntity story,
            Map<String, AssigneeSchedule> assigneeSchedules,
            LocalDate epicStartDate,
            Map<String, LocalDate> storyEndDates,
            StatusMappingConfig statusMapping
    ) {
        String storyKey = story.getIssueKey();

        // Find assignee
        String assigneeAccountId = story.getAssigneeAccountId();
        boolean isUnassigned = (assigneeAccountId == null || !assigneeSchedules.containsKey(assigneeAccountId));

        if (isUnassigned) {
            // Auto-assign to earliest available member with matching role
            assigneeAccountId = findBestAssignee(story, assigneeSchedules);
        }

        // Calculate remaining work
        BigDecimal remainingHours = calculateRemainingWork(story);
        if (remainingHours.compareTo(BigDecimal.ZERO) <= 0) {
            // Story is complete or has no estimate
            return new StorySchedule(
                    storyKey,
                    assigneeAccountId,
                    story.getAssigneeDisplayName(),
                    epicStartDate,
                    epicStartDate,
                    BigDecimal.ZERO,
                    isUnassigned,
                    false,
                    List.of()
            );
        }

        AssigneeSchedule assigneeSchedule = assigneeSchedules.get(assigneeAccountId);

        // Check dependencies
        List<String> blockingStories = new ArrayList<>();
        LocalDate dependenciesMetDate = epicStartDate;
        if (story.getIsBlockedBy() != null && !story.getIsBlockedBy().isEmpty()) {
            for (String blocker : story.getIsBlockedBy()) {
                if (storyEndDates.containsKey(blocker)) {
                    LocalDate blockerEndDate = storyEndDates.get(blocker);
                    if (blockerEndDate.isAfter(dependenciesMetDate)) {
                        dependenciesMetDate = blockerEndDate;
                    }
                    blockingStories.add(blocker);
                }
            }
        }

        // Determine earliest start date
        LocalDate assigneeAvailable = assigneeSchedule.nextAvailableDate();
        LocalDate earliestStart = maxDate(
                maxDate(assigneeAvailable, dependenciesMetDate),
                epicStartDate
        );

        // Calculate duration in work days
        BigDecimal workDays = remainingHours.divide(assigneeSchedule.effectiveHoursPerDay(), 1, RoundingMode.HALF_UP);

        // Calculate end date using work calendar
        LocalDate startDate = calendarService.addWorkdays(earliestStart, 0); // Find next workday
        LocalDate endDate = calendarService.addWorkdays(startDate, workDays.intValue());

        // Update assignee's next available date
        assigneeSchedule.setNextAvailableDate(calendarService.addWorkdays(endDate, 1));

        return new StorySchedule(
                storyKey,
                assigneeAccountId,
                assigneeSchedule.displayName(),
                startDate,
                endDate,
                workDays,
                isUnassigned,
                !blockingStories.isEmpty(),
                blockingStories
        );
    }

    /**
     * Find best assignee for unassigned story.
     * Prefers earliest available member with matching role.
     */
    private String findBestAssignee(JiraIssueEntity story, Map<String, AssigneeSchedule> assigneeSchedules) {
        // Determine required role based on story estimate distribution
        Role requiredRole = determineRequiredRole(story);

        // Find earliest available member with matching role
        String bestAssignee = null;
        LocalDate earliestDate = null;

        for (AssigneeSchedule schedule : assigneeSchedules.values()) {
            if (schedule.role() == requiredRole) {
                if (bestAssignee == null || schedule.nextAvailableDate().isBefore(earliestDate)) {
                    bestAssignee = schedule.jiraAccountId();
                    earliestDate = schedule.nextAvailableDate();
                }
            }
        }

        // Fallback: any earliest available member
        if (bestAssignee == null) {
            for (AssigneeSchedule schedule : assigneeSchedules.values()) {
                if (bestAssignee == null || schedule.nextAvailableDate().isBefore(earliestDate)) {
                    bestAssignee = schedule.jiraAccountId();
                    earliestDate = schedule.nextAvailableDate();
                }
            }
        }

        return bestAssignee;
    }

    /**
     * Determine required role based on story estimate distribution.
     */
    private Role determineRequiredRole(JiraIssueEntity story) {
        BigDecimal saDays = story.getRoughEstimateSaDays() != null ? story.getRoughEstimateSaDays() : BigDecimal.ZERO;
        BigDecimal devDays = story.getRoughEstimateDevDays() != null ? story.getRoughEstimateDevDays() : BigDecimal.ZERO;
        BigDecimal qaDays = story.getRoughEstimateQaDays() != null ? story.getRoughEstimateQaDays() : BigDecimal.ZERO;

        // Return role with highest estimate
        if (saDays.compareTo(devDays) >= 0 && saDays.compareTo(qaDays) >= 0) {
            return Role.SA;
        } else if (qaDays.compareTo(devDays) >= 0) {
            return Role.QA;
        } else {
            return Role.DEV;
        }
    }

    /**
     * Calculate remaining work for a story.
     */
    private BigDecimal calculateRemainingWork(JiraIssueEntity story) {
        Long estimateSeconds = story.getOriginalEstimateSeconds();
        Long spentSeconds = story.getTimeSpentSeconds() != null ? story.getTimeSpentSeconds() : 0L;

        if (estimateSeconds == null || estimateSeconds <= 0) {
            return BigDecimal.ZERO;
        }

        long remainingSeconds = Math.max(0, estimateSeconds - spentSeconds);
        return new BigDecimal(remainingSeconds).divide(new BigDecimal("3600"), 1, RoundingMode.HALF_UP);
    }

    /**
     * Determine epic start date based on current status.
     */
    private LocalDate determineEpicStartDate(JiraIssueEntity epic, StatusMappingConfig statusMapping) {
        String status = epic.getStatus();
        boolean isInProgress = statusMappingService.isInProgress(status, statusMapping);

        if (isInProgress) {
            return LocalDate.now();
        } else {
            return LocalDate.now(); // Could be more sophisticated
        }
    }

    /**
     * Calculate assignee utilization.
     */
    private Map<String, AssigneeUtilization> calculateAssigneeUtilization(
            Map<String, AssigneeSchedule> assigneeSchedules,
            List<StorySchedule> schedules
    ) {
        Map<String, BigDecimal> workDaysAssigned = new HashMap<>();

        for (StorySchedule schedule : schedules) {
            String assigneeId = schedule.assigneeAccountId();
            if (assigneeId != null) {
                workDaysAssigned.merge(assigneeId, schedule.workDays(), BigDecimal::add);
            }
        }

        Map<String, AssigneeUtilization> utilization = new HashMap<>();
        for (Map.Entry<String, AssigneeSchedule> entry : assigneeSchedules.entrySet()) {
            String assigneeId = entry.getKey();
            AssigneeSchedule schedule = entry.getValue();
            BigDecimal assigned = workDaysAssigned.getOrDefault(assigneeId, BigDecimal.ZERO);

            utilization.put(
                    assigneeId,
                    new AssigneeUtilization(
                            schedule.displayName(),
                            schedule.role(),
                            assigned,
                            schedule.effectiveHoursPerDay()
                    )
            );
        }

        return utilization;
    }

    private LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    /**
     * Assignee schedule tracking.
     */
    private static class AssigneeSchedule {
        private final String jiraAccountId;
        private final String displayName;
        private final Role role;
        private final BigDecimal effectiveHoursPerDay;
        private LocalDate nextAvailableDate;

        public AssigneeSchedule(String jiraAccountId, String displayName, Role role,
                                BigDecimal effectiveHoursPerDay, LocalDate nextAvailableDate) {
            this.jiraAccountId = jiraAccountId;
            this.displayName = displayName;
            this.role = role;
            this.effectiveHoursPerDay = effectiveHoursPerDay;
            this.nextAvailableDate = nextAvailableDate;
        }

        public String jiraAccountId() { return jiraAccountId; }
        public String displayName() { return displayName; }
        public Role role() { return role; }
        public BigDecimal effectiveHoursPerDay() { return effectiveHoursPerDay; }
        public LocalDate nextAvailableDate() { return nextAvailableDate; }
        public void setNextAvailableDate(LocalDate date) { this.nextAvailableDate = date; }
    }

    /**
     * Story forecast result.
     */
    public record StoryForecast(
            String epicKey,
            LocalDate epicStartDate,
            List<StorySchedule> stories,
            Map<String, AssigneeUtilization> assigneeUtilization
    ) {}

    /**
     * Individual story schedule.
     */
    public record StorySchedule(
            String storyKey,
            String assigneeAccountId,
            String assigneeDisplayName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal workDays,
            boolean isUnassigned,
            boolean isBlocked,
            List<String> blockingStories
    ) {}

    /**
     * Assignee utilization info.
     */
    public record AssigneeUtilization(
            String displayName,
            Role role,
            BigDecimal workDaysAssigned,
            BigDecimal effectiveHoursPerDay
    ) {}
}

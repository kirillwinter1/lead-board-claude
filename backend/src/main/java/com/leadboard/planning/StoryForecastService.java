package com.leadboard.planning;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
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
    private final WorkflowConfigService workflowConfigService;
    private final StoryAutoScoreService storyAutoScoreService;
    private final StoryDependencyService storyDependencyService;

    public StoryForecastService(
            JiraIssueRepository issueRepository,
            TeamService teamService,
            TeamMemberRepository memberRepository,
            WorkCalendarService calendarService,
            WorkflowConfigService workflowConfigService,
            StoryAutoScoreService storyAutoScoreService,
            StoryDependencyService storyDependencyService
    ) {
        this.issueRepository = issueRepository;
        this.teamService = teamService;
        this.memberRepository = memberRepository;
        this.calendarService = calendarService;
        this.workflowConfigService = workflowConfigService;
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
        Map<String, AssigneeScheduleInner> assigneeSchedules = buildAssigneeSchedules(members, gradeCoefficients);

        // Calculate story schedules
        LocalDate epicStartDate = determineEpicStartDate(epic);
        List<StorySchedule> schedules = calculateStorySchedules(
                sortedStories,
                assigneeSchedules,
                epicStartDate
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
    private Map<String, AssigneeScheduleInner> buildAssigneeSchedules(
            List<TeamMemberEntity> members,
            PlanningConfigDto.GradeCoefficients gradeCoefficients
    ) {
        Map<String, AssigneeScheduleInner> schedules = new HashMap<>();
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
                    new AssigneeScheduleInner(
                            member.getJiraAccountId(),
                            member.getDisplayName(),
                            member.getRoleCode(),
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
            Map<String, AssigneeScheduleInner> assigneeSchedules,
            LocalDate epicStartDate
    ) {
        List<StorySchedule> schedules = new ArrayList<>();
        Map<String, LocalDate> storyEndDates = new HashMap<>(); // For dependency tracking

        for (JiraIssueEntity story : stories) {
            StorySchedule schedule = calculateSingleStorySchedule(
                    story,
                    assigneeSchedules,
                    epicStartDate,
                    storyEndDates
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
            Map<String, AssigneeScheduleInner> assigneeSchedules,
            LocalDate epicStartDate,
            Map<String, LocalDate> storyEndDates
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

        AssigneeScheduleInner assigneeSchedule = assigneeSchedules.get(assigneeAccountId);

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
    private String findBestAssignee(JiraIssueEntity story, Map<String, AssigneeScheduleInner> assigneeSchedules) {
        // Determine required role based on story estimate distribution
        String requiredRole = determineRequiredRole(story);

        // Find earliest available member with matching role
        String bestAssignee = null;
        LocalDate earliestDate = null;

        for (AssigneeScheduleInner schedule : assigneeSchedules.values()) {
            if (schedule.roleCode().equals(requiredRole)) {
                if (bestAssignee == null || schedule.nextAvailableDate().isBefore(earliestDate)) {
                    bestAssignee = schedule.jiraAccountId();
                    earliestDate = schedule.nextAvailableDate();
                }
            }
        }

        // Fallback: any earliest available member
        if (bestAssignee == null) {
            for (AssigneeScheduleInner schedule : assigneeSchedules.values()) {
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
     * Uses dynamic roughEstimates Map with fallback to legacy fields.
     */
    private String determineRequiredRole(JiraIssueEntity story) {
        // Try dynamic rough estimates first
        Map<String, BigDecimal> roughEstimates = story.getRoughEstimates();
        if (roughEstimates != null && !roughEstimates.isEmpty()) {
            String maxRole = null;
            BigDecimal maxValue = BigDecimal.ZERO;

            for (Map.Entry<String, BigDecimal> entry : roughEstimates.entrySet()) {
                BigDecimal value = entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO;
                if (maxRole == null || value.compareTo(maxValue) > 0) {
                    maxRole = entry.getKey();
                    maxValue = value;
                }
            }

            if (maxRole != null) {
                return maxRole;
            }
        }

        // Fallback to legacy fields
        BigDecimal saDays = story.getRoughEstimateSaDays() != null ? story.getRoughEstimateSaDays() : BigDecimal.ZERO;
        BigDecimal devDays = story.getRoughEstimateDevDays() != null ? story.getRoughEstimateDevDays() : BigDecimal.ZERO;
        BigDecimal qaDays = story.getRoughEstimateQaDays() != null ? story.getRoughEstimateQaDays() : BigDecimal.ZERO;

        // Return role with highest estimate
        if (saDays.compareTo(devDays) >= 0 && saDays.compareTo(qaDays) >= 0) {
            return "SA";
        } else if (qaDays.compareTo(devDays) >= 0) {
            return "QA";
        } else {
            return "DEV";
        }
    }

    /**
     * Calculate remaining work for a story.
     */
    private BigDecimal calculateRemainingWork(JiraIssueEntity story) {
        // Aggregate estimate and time spent from subtasks (data quality rule: estimates only on subtasks)
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());

        long totalEstimateSeconds = 0;
        long totalSpentSeconds = 0;

        for (JiraIssueEntity subtask : subtasks) {
            if (subtask.getOriginalEstimateSeconds() != null) {
                totalEstimateSeconds += subtask.getOriginalEstimateSeconds();
            }
            if (subtask.getTimeSpentSeconds() != null) {
                totalSpentSeconds += subtask.getTimeSpentSeconds();
            }
        }

        if (totalEstimateSeconds <= 0) {
            return BigDecimal.ZERO;
        }

        long remainingSeconds = Math.max(0, totalEstimateSeconds - totalSpentSeconds);
        return new BigDecimal(remainingSeconds).divide(new BigDecimal("3600"), 1, RoundingMode.HALF_UP);
    }

    /**
     * Determine epic start date based on current status.
     * Uses WorkflowConfigService for dynamic status categorization.
     */
    private LocalDate determineEpicStartDate(JiraIssueEntity epic) {
        String status = epic.getStatus();
        String issueType = epic.getIssueType();
        boolean isInProgress = workflowConfigService.isInProgress(status, issueType);

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
            Map<String, AssigneeScheduleInner> assigneeSchedules,
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
        for (Map.Entry<String, AssigneeScheduleInner> entry : assigneeSchedules.entrySet()) {
            String assigneeId = entry.getKey();
            AssigneeScheduleInner schedule = entry.getValue();
            BigDecimal assigned = workDaysAssigned.getOrDefault(assigneeId, BigDecimal.ZERO);

            utilization.put(
                    assigneeId,
                    new AssigneeUtilization(
                            schedule.displayName(),
                            schedule.roleCode(),
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
     * Inner assignee schedule tracking (distinct from top-level AssigneeSchedule class).
     * Uses String roleCode instead of Role enum for dynamic role support.
     */
    private static class AssigneeScheduleInner {
        private final String jiraAccountId;
        private final String displayName;
        private final String roleCode;
        private final BigDecimal effectiveHoursPerDay;
        private LocalDate nextAvailableDate;

        public AssigneeScheduleInner(String jiraAccountId, String displayName, String roleCode,
                                BigDecimal effectiveHoursPerDay, LocalDate nextAvailableDate) {
            this.jiraAccountId = jiraAccountId;
            this.displayName = displayName;
            this.roleCode = roleCode;
            this.effectiveHoursPerDay = effectiveHoursPerDay;
            this.nextAvailableDate = nextAvailableDate;
        }

        public String jiraAccountId() { return jiraAccountId; }
        public String displayName() { return displayName; }
        public String roleCode() { return roleCode; }
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
     * Uses String roleCode instead of Role enum.
     */
    public record AssigneeUtilization(
            String displayName,
            String roleCode,
            BigDecimal workDaysAssigned,
            BigDecimal effectiveHoursPerDay
    ) {}
}

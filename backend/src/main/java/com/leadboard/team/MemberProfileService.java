package com.leadboard.team;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.dto.MemberProfileResponse;
import com.leadboard.team.dto.MemberProfileResponse.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MemberProfileService {

    private static final BigDecimal SECONDS_PER_HOUR = new BigDecimal(3600);
    private static final int TREND_WEEKS = 8;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;
    private final WorkCalendarService workCalendarService;

    public MemberProfileService(
            TeamRepository teamRepository,
            TeamMemberRepository memberRepository,
            JiraIssueRepository issueRepository,
            WorkflowConfigService workflowConfigService,
            WorkCalendarService workCalendarService
    ) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
        this.workCalendarService = workCalendarService;
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getMemberProfile(Long teamId, Long memberId, LocalDate from, LocalDate to) {
        TeamEntity team = teamRepository.findByIdAndActiveTrue(teamId)
                .orElseThrow(() -> new TeamService.TeamNotFoundException("Team not found: " + teamId));

        TeamMemberEntity member = memberRepository.findByIdAndTeamIdAndActiveTrue(memberId, teamId)
                .orElseThrow(() -> new TeamService.TeamMemberNotFoundException("Team member not found: " + memberId));

        String accountId = member.getJiraAccountId();

        // Completed subtasks in the period
        OffsetDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        List<JiraIssueEntity> completedIssues = issueRepository.findCompletedSubtasksByAssigneeInPeriod(
                accountId, teamId, fromDt, toDt);

        // All subtasks for active/upcoming
        List<JiraIssueEntity> allSubtasks = issueRepository.findSubtasksByAssigneeAndTeam(accountId, teamId);

        // Epic info cache: parentKey -> parent issue, grandparentKey -> epic issue
        Map<String, JiraIssueEntity> issueCache = new HashMap<>();

        // Build completed tasks
        List<CompletedTask> completedTasks = completedIssues.stream()
                .map(issue -> buildCompletedTask(issue, issueCache))
                .toList();

        // Build active and upcoming tasks
        List<ActiveTask> activeTasks = new ArrayList<>();
        List<ActiveTask> upcomingTasks = new ArrayList<>();

        for (JiraIssueEntity issue : allSubtasks) {
            if (issue.getDoneAt() != null) continue; // skip completed

            StatusCategory statusCat = workflowConfigService.categorize(issue.getStatus(), issue.getIssueType());
            if (statusCat == StatusCategory.IN_PROGRESS || statusCat == StatusCategory.PLANNED) {
                activeTasks.add(buildActiveTask(issue, issueCache));
            } else if (statusCat.isNotStarted()) {
                upcomingTasks.add(buildActiveTask(issue, issueCache));
            }
        }

        // Weekly trend
        List<WeeklyTrend> weeklyTrend = buildWeeklyTrend(completedIssues, to);

        // Summary
        MemberSummary summary = buildSummary(completedIssues, member, from, to);

        // Member info
        MemberInfo memberInfo = new MemberInfo(
                member.getId(),
                member.getDisplayName(),
                member.getRole(),
                member.getGrade().name(),
                member.getHoursPerDay(),
                team.getName(),
                team.getId()
        );

        return new MemberProfileResponse(memberInfo, completedTasks, activeTasks, upcomingTasks, weeklyTrend, summary);
    }

    private CompletedTask buildCompletedTask(JiraIssueEntity issue, Map<String, JiraIssueEntity> cache) {
        BigDecimal estimateH = secondsToHours(issue.getOriginalEstimateSeconds());
        BigDecimal spentH = secondsToHours(issue.getTimeSpentSeconds());
        BigDecimal dsr = calculateDsr(issue);

        LocalDate doneDate = issue.getDoneAt() != null ? issue.getDoneAt().toLocalDate() : null;

        String[] epicInfo = resolveEpicInfo(issue, cache);

        return new CompletedTask(
                issue.getIssueKey(),
                issue.getSummary(),
                epicInfo[0],
                epicInfo[1],
                estimateH,
                spentH,
                dsr,
                doneDate
        );
    }

    private ActiveTask buildActiveTask(JiraIssueEntity issue, Map<String, JiraIssueEntity> cache) {
        BigDecimal estimateH = secondsToHours(issue.getOriginalEstimateSeconds());
        BigDecimal spentH = secondsToHours(issue.getTimeSpentSeconds());

        String[] epicInfo = resolveEpicInfo(issue, cache);

        return new ActiveTask(
                issue.getIssueKey(),
                issue.getSummary(),
                epicInfo[0],
                epicInfo[1],
                estimateH,
                spentH,
                issue.getStatus()
        );
    }

    /**
     * Resolves epic key and summary for a subtask by traversing parent chain:
     * subtask → story (parent) → epic (grandparent)
     * Returns [epicKey, epicSummary]
     */
    private String[] resolveEpicInfo(JiraIssueEntity subtask, Map<String, JiraIssueEntity> cache) {
        String epicKey = null;
        String epicSummary = null;

        if (subtask.getParentKey() != null) {
            JiraIssueEntity parent = cache.computeIfAbsent(subtask.getParentKey(),
                    key -> issueRepository.findByIssueKey(key).orElse(null));

            if (parent != null) {
                // Check if parent is the epic itself
                if (workflowConfigService.isEpic(parent.getIssueType())) {
                    epicKey = parent.getIssueKey();
                    epicSummary = parent.getSummary();
                } else if (parent.getParentKey() != null) {
                    // Parent is story, grandparent should be epic
                    JiraIssueEntity grandparent = cache.computeIfAbsent(parent.getParentKey(),
                            key -> issueRepository.findByIssueKey(key).orElse(null));
                    if (grandparent != null) {
                        epicKey = grandparent.getIssueKey();
                        epicSummary = grandparent.getSummary();
                    }
                }
            }
        }

        return new String[]{epicKey, epicSummary};
    }

    private List<WeeklyTrend> buildWeeklyTrend(List<JiraIssueEntity> completedIssues, LocalDate endDate) {
        // Group completed tasks by ISO week
        Map<Integer, List<JiraIssueEntity>> byWeek = completedIssues.stream()
                .filter(i -> i.getDoneAt() != null)
                .collect(Collectors.groupingBy(i -> {
                    LocalDate date = i.getDoneAt().toLocalDate();
                    return date.getYear() * 100 + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                }));

        // Generate last TREND_WEEKS weeks
        List<WeeklyTrend> trend = new ArrayList<>();
        LocalDate currentWeekStart = endDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        DateTimeFormatter weekFormatter = DateTimeFormatter.ofPattern("d MMM", new Locale("ru"));

        for (int i = TREND_WEEKS - 1; i >= 0; i--) {
            LocalDate weekStart = currentWeekStart.minusWeeks(i);
            int weekKey = weekStart.getYear() * 100 + weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

            List<JiraIssueEntity> weekIssues = byWeek.getOrDefault(weekKey, List.of());

            int tasksCompleted = weekIssues.size();
            BigDecimal hoursLogged = weekIssues.stream()
                    .map(issue -> secondsToHours(issue.getTimeSpentSeconds()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal weekDsr = null;
            if (!weekIssues.isEmpty()) {
                long totalEstimate = weekIssues.stream()
                        .mapToLong(i2 -> i2.getOriginalEstimateSeconds() != null ? i2.getOriginalEstimateSeconds() : 0)
                        .sum();
                long totalSpent = weekIssues.stream()
                        .mapToLong(i2 -> i2.getTimeSpentSeconds() != null ? i2.getTimeSpentSeconds() : 0)
                        .sum();
                if (totalEstimate > 0) {
                    weekDsr = new BigDecimal(totalSpent).divide(new BigDecimal(totalEstimate), 2, RoundingMode.HALF_UP);
                }
            }

            trend.add(new WeeklyTrend(
                    weekStart.format(weekFormatter),
                    weekStart,
                    weekDsr,
                    tasksCompleted,
                    hoursLogged.setScale(1, RoundingMode.HALF_UP)
            ));
        }

        return trend;
    }

    private MemberSummary buildSummary(List<JiraIssueEntity> completedIssues, TeamMemberEntity member,
                                       LocalDate from, LocalDate to) {
        int completedCount = completedIssues.size();

        long totalEstimateSec = completedIssues.stream()
                .mapToLong(i -> i.getOriginalEstimateSeconds() != null ? i.getOriginalEstimateSeconds() : 0)
                .sum();
        long totalSpentSec = completedIssues.stream()
                .mapToLong(i -> i.getTimeSpentSeconds() != null ? i.getTimeSpentSeconds() : 0)
                .sum();

        BigDecimal totalEstimateH = secondsToHours(totalEstimateSec);
        BigDecimal totalSpentH = secondsToHours(totalSpentSec);

        BigDecimal avgDsr = totalEstimateSec > 0
                ? new BigDecimal(totalSpentSec).divide(new BigDecimal(totalEstimateSec), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Average cycle time: startedAt → doneAt in days
        List<Long> cycleTimes = completedIssues.stream()
                .filter(i -> i.getStartedAt() != null && i.getDoneAt() != null)
                .map(i -> Duration.between(i.getStartedAt(), i.getDoneAt()).toDays())
                .toList();

        BigDecimal avgCycleTimeDays = cycleTimes.isEmpty() ? BigDecimal.ZERO
                : new BigDecimal(cycleTimes.stream().mapToLong(Long::longValue).sum())
                        .divide(new BigDecimal(cycleTimes.size()), 1, RoundingMode.HALF_UP);

        // Utilization: totalSpentH / (workdays * hoursPerDay) * 100
        int workdays = workCalendarService.countWorkdays(from, to);
        BigDecimal capacityH = member.getHoursPerDay().multiply(new BigDecimal(workdays));
        BigDecimal utilization = capacityH.compareTo(BigDecimal.ZERO) > 0
                ? totalSpentH.divide(capacityH, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100))
                        .setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new MemberSummary(completedCount, avgDsr, avgCycleTimeDays, utilization, totalSpentH, totalEstimateH);
    }

    private BigDecimal calculateDsr(JiraIssueEntity issue) {
        if (issue.getOriginalEstimateSeconds() == null || issue.getOriginalEstimateSeconds() == 0) {
            return null;
        }
        long spent = issue.getTimeSpentSeconds() != null ? issue.getTimeSpentSeconds() : 0;
        return new BigDecimal(spent)
                .divide(new BigDecimal(issue.getOriginalEstimateSeconds()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal secondsToHours(Long seconds) {
        if (seconds == null || seconds == 0) return BigDecimal.ZERO;
        return new BigDecimal(seconds).divide(SECONDS_PER_HOUR, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal secondsToHours(long seconds) {
        if (seconds == 0) return BigDecimal.ZERO;
        return new BigDecimal(seconds).divide(SECONDS_PER_HOUR, 1, RoundingMode.HALF_UP);
    }
}

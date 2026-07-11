package com.leadboard.team;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.calendar.dto.HolidayDto;
import com.leadboard.calendar.dto.WorkdaysResponseDto;
import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.dto.AbsenceDto;
import com.leadboard.team.dto.MemberProfileResponse.MemberSummary;
import com.leadboard.team.dto.MemberProfileResponse.WeeklyTrend;
import com.leadboard.team.dto.MyWorkResponse;
import com.leadboard.team.dto.MyWorkResponse.CalendarDay;
import com.leadboard.team.dto.MyWorkResponse.CompletedTaskWithTeam;
import com.leadboard.team.dto.MyWorkResponse.DayIssue;
import com.leadboard.team.dto.MyWorkResponse.DsrBreakdown;
import com.leadboard.team.dto.MyWorkResponse.MyAnalytics;
import com.leadboard.team.dto.MyWorkResponse.MyMemberInfo;
import com.leadboard.team.dto.MyWorkResponse.MyTask;
import com.leadboard.team.dto.MyWorkResponse.QueueStory;
import com.leadboard.team.dto.MyWorkResponse.TeamRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Personal work desk — F88 "My Work". Aggregates active/upcoming tasks, team queue,
 * worklog calendar and personal analytics (DSR breakdowns by parent type and epic)
 * across every active membership of a Jira account.
 */
@Service
public class MyWorkService {

    private static final Comparator<DsrBreakdown> DSR_DESC_NULLS_LAST =
            Comparator.comparing(DsrBreakdown::dsr, Comparator.nullsLast(Comparator.reverseOrder()));

    private final TeamMemberRepository memberRepository;
    private final JiraIssueRepository issueRepository;
    private final IssueWorklogRepository worklogRepository;
    private final MemberAbsenceRepository absenceRepository;
    private final AbsenceService absenceService;
    private final WorkflowConfigService workflowConfigService;
    private final WorkCalendarService workCalendarService;
    private final MemberAnalyticsService analytics;
    private final JiraConfigResolver jiraConfigResolver;

    public MyWorkService(TeamMemberRepository memberRepository, JiraIssueRepository issueRepository,
                          IssueWorklogRepository worklogRepository, MemberAbsenceRepository absenceRepository,
                          AbsenceService absenceService, WorkflowConfigService workflowConfigService,
                          WorkCalendarService workCalendarService, MemberAnalyticsService analytics,
                          JiraConfigResolver jiraConfigResolver) {
        this.memberRepository = memberRepository;
        this.issueRepository = issueRepository;
        this.worklogRepository = worklogRepository;
        this.absenceRepository = absenceRepository;
        this.absenceService = absenceService;
        this.workflowConfigService = workflowConfigService;
        this.workCalendarService = workCalendarService;
        this.analytics = analytics;
        this.jiraConfigResolver = jiraConfigResolver;
    }

    @Transactional(readOnly = true)
    public MyWorkResponse getMyWork(String accountId, LocalDate from, LocalDate to, Long teamId) {
        return getMyWork(accountId, from, to, teamId, LocalDate.now());
    }

    // package-private перегрузка с today — для детерминированных тестов календаря (Task 4/5)
    MyWorkResponse getMyWork(String accountId, LocalDate from, LocalDate to, Long teamId, LocalDate today) {
        List<TeamMemberEntity> members = memberRepository.findAllByJiraAccountIdAndActiveTrue(accountId);
        if (members.isEmpty()) {
            return new MyWorkResponse(false, null, List.of(), List.of(), List.of(), List.of(), List.of(), null);
        }

        TeamMemberEntity primary = members.get(0);
        List<TeamRef> teams = members.stream()
                .map(m -> new TeamRef(m.getTeam().getId(), m.getTeam().getName(), m.getTeam().getColor()))
                .toList();
        MyMemberInfo memberInfo = new MyMemberInfo(
                primary.getDisplayName(),
                primary.getAvatarUrl(),
                primary.getRole(),
                primary.getGrade() != null ? primary.getGrade().name() : null,
                primary.getHoursPerDay(),
                teams
        );

        List<AbsenceDto> upcomingAbsences = new ArrayList<>();
        for (TeamMemberEntity m : members) {
            upcomingAbsences.addAll(absenceService.getUpcomingAbsences(m.getId()));
        }

        List<TeamMemberEntity> taskMembers = teamId == null
                ? members
                : members.stream().filter(m -> m.getTeam().getId().equals(teamId)).toList();

        Map<String, JiraIssueEntity> issueCache = new HashMap<>();
        List<MyTask> activeTasks = new ArrayList<>();
        List<MyTask> upcomingAssigned = new ArrayList<>();

        for (TeamMemberEntity m : taskMembers) {
            List<JiraIssueEntity> subtasks = issueRepository.findSubtasksByAssigneeAndTeam(accountId, m.getTeam().getId());
            for (JiraIssueEntity subtask : subtasks) {
                if (subtask.getDoneAt() != null) continue;

                StatusCategory cat = workflowConfigService.categorize(subtask.getStatus(), subtask.getIssueType());
                if (cat == StatusCategory.IN_PROGRESS || cat == StatusCategory.PLANNED || cat == StatusCategory.DEV_DONE) {
                    activeTasks.add(buildMyTask(subtask, m, issueCache));
                } else if (cat.isNotStarted()) {
                    upcomingAssigned.add(buildMyTask(subtask, m, issueCache));
                }
            }
        }

        Comparator<MyTask> byTeamThenKey = Comparator.comparing(MyTask::teamName).thenComparing(MyTask::key);
        activeTasks.sort(byTeamThenKey);
        upcomingAssigned.sort(byTeamThenKey);

        List<QueueStory> teamQueue = buildTeamQueue(taskMembers, issueCache);

        List<CalendarDay> worklogCalendar = buildWorklogCalendarForMonth(
                accountId, members, primary.getHoursPerDay(), YearMonth.from(today));

        MyAnalytics myAnalytics = buildAnalytics(accountId, members, primary.getHoursPerDay(), from, to, issueCache);

        return new MyWorkResponse(true, memberInfo, upcomingAbsences, activeTasks, upcomingAssigned,
                teamQueue, worklogCalendar, myAnalytics);
    }

    /**
     * Worklog-календарь за произвольный месяц — для навигации по месяцам с фронта (стрелки
     * «предыдущий / следующий месяц»). Резолвит активные членства аккаунта так же, как
     * {@link #getMyWork}: если пользователь не состоит ни в одной команде — возвращает пустой список.
     * Норму дня берём из первого (primary) членства, как и первый рендер.
     */
    @Transactional(readOnly = true)
    public List<CalendarDay> getWorklogCalendar(String accountId, YearMonth month) {
        List<TeamMemberEntity> members = memberRepository.findAllByJiraAccountIdAndActiveTrue(accountId);
        if (members.isEmpty()) {
            return List.of();
        }
        TeamMemberEntity primary = members.get(0);
        return buildWorklogCalendarForMonth(accountId, members, primary.getHoursPerDay(), month);
    }

    /**
     * Personal analytics (summary, weekly trend, completed tasks, DSR breakdowns) across every
     * membership of the account — unlike active/upcoming tasks and the team queue, this deliberately
     * ignores the {@code teamId} request filter so a member always sees their full completed history.
     */
    private MyAnalytics buildAnalytics(String accountId, List<TeamMemberEntity> members, BigDecimal hoursPerDay,
                                        LocalDate from, LocalDate to, Map<String, JiraIssueEntity> cache) {
        OffsetDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();

        Map<Long, TeamMemberEntity> memberByTeamId = new HashMap<>();
        List<JiraIssueEntity> completed = new ArrayList<>();
        for (TeamMemberEntity m : members) {
            Long teamId = m.getTeam().getId();
            memberByTeamId.put(teamId, m);
            completed.addAll(issueRepository.findCompletedSubtasksByAssigneeInPeriod(accountId, teamId, fromDt, toDt));
        }
        completed.sort(Comparator.comparing(JiraIssueEntity::getDoneAt, Comparator.nullsLast(Comparator.reverseOrder())));

        MemberSummary summary = analytics.buildSummary(completed, hoursPerDay, from, to);
        List<WeeklyTrend> weeklyTrend = analytics.buildWeeklyTrend(completed, to);

        List<CompletedTaskWithTeam> completedTasks = completed.stream()
                .map(issue -> buildCompletedTaskWithTeam(issue, cache, memberByTeamId))
                .toList();

        List<DsrBreakdown> dsrByParentType = buildDsrByParentType(completed, cache);
        List<DsrBreakdown> dsrByEpic = buildDsrByEpic(completed, cache);

        return new MyAnalytics(summary, weeklyTrend, completedTasks, dsrByParentType, dsrByEpic);
    }

    private CompletedTaskWithTeam buildCompletedTaskWithTeam(JiraIssueEntity issue, Map<String, JiraIssueEntity> cache,
                                                               Map<Long, TeamMemberEntity> memberByTeamId) {
        BigDecimal estimateH = analytics.secondsToHours(issue.getOriginalEstimateSeconds());
        BigDecimal spentH = analytics.secondsToHours(issue.getTimeSpentSeconds());
        BigDecimal remainingH = analytics.secondsToHours(issue.getRemainingEstimateSeconds());
        BigDecimal dsr = analytics.calculateDsr(issue);
        LocalDate doneDate = issue.getDoneAt() != null ? issue.getDoneAt().toLocalDate() : null;
        String[] epicInfo = analytics.resolveEpicInfo(issue, cache);

        TeamMemberEntity member = memberByTeamId.get(issue.getTeamId());
        TeamEntity team = member != null ? member.getTeam() : null;

        return new CompletedTaskWithTeam(
                issue.getIssueKey(),
                issue.getSummary(),
                epicInfo[0],
                epicInfo[1],
                team != null ? team.getId() : null,
                team != null ? team.getName() : null,
                team != null ? team.getColor() : null,
                estimateH,
                spentH,
                remainingH,
                dsr,
                doneDate,
                jiraConfigResolver.getBaseUrl() + "/browse/" + issue.getIssueKey()
        );
    }

    /**
     * DSR grouped by the completed subtask's parent issue type (e.g. Story vs Bug), worst DSR first.
     * A missing/unresolvable parent falls into the "Unknown" bucket.
     */
    private List<DsrBreakdown> buildDsrByParentType(List<JiraIssueEntity> completed, Map<String, JiraIssueEntity> cache) {
        Map<String, List<JiraIssueEntity>> byType = new LinkedHashMap<>();
        for (JiraIssueEntity sub : completed) {
            JiraIssueEntity parent = sub.getParentKey() != null
                    ? cache.computeIfAbsent(sub.getParentKey(), key -> issueRepository.findByIssueKey(key).orElse(null))
                    : null;
            String type = parent != null ? parent.getIssueType() : "Unknown";
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(sub);
        }

        return byType.entrySet().stream()
                .map(e -> buildDsrBreakdown(e.getKey(), e.getKey(), e.getValue()))
                .sorted(DSR_DESC_NULLS_LAST)
                .toList();
    }

    /**
     * DSR grouped by epic (resolved via the subtask's parent -> grandparent chain), worst DSR first,
     * capped at the 10 worst epics. Subtasks without a resolvable epic are excluded from this cut.
     */
    private List<DsrBreakdown> buildDsrByEpic(List<JiraIssueEntity> completed, Map<String, JiraIssueEntity> cache) {
        Map<String, List<JiraIssueEntity>> byEpic = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (JiraIssueEntity sub : completed) {
            String[] epicInfo = analytics.resolveEpicInfo(sub, cache);
            String epicKey = epicInfo[0];
            if (epicKey == null) continue;
            byEpic.computeIfAbsent(epicKey, k -> new ArrayList<>()).add(sub);
            labels.putIfAbsent(epicKey, epicInfo[1] != null ? epicInfo[1] : epicKey);
        }

        return byEpic.entrySet().stream()
                .map(e -> buildDsrBreakdown(e.getKey(), labels.get(e.getKey()), e.getValue()))
                .sorted(DSR_DESC_NULLS_LAST)
                .limit(10)
                .toList();
    }

    // Sums raw seconds across the group and converts once — summing already-rounded per-task hours
    // would drift (see the worklog-calendar rounding note in buildWorklogCalendar).
    private DsrBreakdown buildDsrBreakdown(String key, String label, List<JiraIssueEntity> issues) {
        long estimateSec = issues.stream()
                .mapToLong(i -> i.getOriginalEstimateSeconds() != null ? i.getOriginalEstimateSeconds() : 0L)
                .sum();
        long spentSec = issues.stream()
                .mapToLong(i -> i.getTimeSpentSeconds() != null ? i.getTimeSpentSeconds() : 0L)
                .sum();
        BigDecimal dsr = estimateSec > 0
                ? new BigDecimal(spentSec).divide(new BigDecimal(estimateSec), 2, RoundingMode.HALF_UP)
                : null;
        return new DsrBreakdown(key, label, issues.size(), analytics.secondsToHours(estimateSec),
                analytics.secondsToHours(spentSec), dsr);
    }

    /**
     * Worklog-календарь за один календарный месяц, выровненный по полным ISO-неделям Mon–Sun.
     * Сетка начинается с понедельника ≤ 1-го числа месяца и кончается воскресеньем ≥ последнего числа,
     * поэтому в неё попадают «хвостовые» дни соседних месяцев (фронт их приглушает). Независимо от
     * from/to запроса. Показывает залогированные часы по дням (с разбивкой по задачам), тип дня
     * (WORKDAY/WEEKEND/HOLIDAY) и любое отсутствие, покрывающее день, по всем членствам аккаунта.
     */
    List<CalendarDay> buildWorklogCalendarForMonth(String accountId, List<TeamMemberEntity> allMembers,
                                                     BigDecimal hoursPerDay, YearMonth month) {
        LocalDate calFrom = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate calTo = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return buildWorklogCalendarForRange(accountId, allMembers, hoursPerDay, calFrom, calTo);
    }

    /**
     * Общая логика построения worklog-календаря за явный диапазон [calFrom, calTo]. Для каждого дня:
     * залогированные часы (с разбивкой по задачам), тип дня (WORKDAY/WEEKEND/HOLIDAY), норма дня
     * (hoursPerDay для рабочего дня без отсутствия, иначе 0) и тип отсутствия по всем членствам.
     */
    private List<CalendarDay> buildWorklogCalendarForRange(String accountId, List<TeamMemberEntity> allMembers,
                                                             BigDecimal hoursPerDay, LocalDate calFrom, LocalDate calTo) {
        WorkdaysResponseDto calendarInfo = workCalendarService.getWorkdaysInfo(calFrom, calTo);
        Set<LocalDate> holidayDates = calendarInfo.holidayList().stream()
                .map(HolidayDto::date)
                .collect(Collectors.toSet());
        Set<LocalDate> workdayDates = new HashSet<>(calendarInfo.workdayDates());

        Map<LocalDate, List<DayIssue>> byIssuePerDay = new HashMap<>();
        Map<LocalDate, Long> secondsPerDay = new HashMap<>();
        List<Object[]> rawWorklogs = worklogRepository.findDailyWorklogsByAuthorPerIssue(accountId, calFrom, calTo);
        for (Object[] row : rawWorklogs) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            String issueKey = (String) row[1];
            long totalSeconds = ((Number) row[2]).longValue();

            // Per-issue value is rounded for display; the daily total rounds the raw-second sum once
            // (rounding each issue then summing would drift, e.g. 600s + 600s = 0.2h + 0.2h = 0.4h ≠ 0.3h).
            byIssuePerDay.computeIfAbsent(date, k -> new ArrayList<>())
                    .add(new DayIssue(issueKey, analytics.secondsToHours(totalSeconds)));
            secondsPerDay.merge(date, totalSeconds, Long::sum);
        }

        Map<LocalDate, String> absenceByDate = new HashMap<>();
        for (TeamMemberEntity member : allMembers) {
            List<MemberAbsenceEntity> absences = absenceRepository.findByMemberIdAndDateRange(member.getId(), calFrom, calTo);
            for (MemberAbsenceEntity absence : absences) {
                LocalDate d = absence.getStartDate().isBefore(calFrom) ? calFrom : absence.getStartDate();
                LocalDate end = absence.getEndDate().isAfter(calTo) ? calTo : absence.getEndDate();
                while (!d.isAfter(end)) {
                    absenceByDate.put(d, absence.getAbsenceType().name());
                    d = d.plusDays(1);
                }
            }
        }

        List<CalendarDay> days = new ArrayList<>();
        LocalDate current = calFrom;
        while (!current.isAfter(calTo)) {
            String dayType;
            if (holidayDates.contains(current)) {
                dayType = "HOLIDAY";
            } else if (workdayDates.contains(current)) {
                dayType = "WORKDAY";
            } else {
                dayType = "WEEKEND";
            }

            String absenceType = absenceByDate.get(current);
            BigDecimal normH = "WORKDAY".equals(dayType) && absenceType == null ? hoursPerDay : BigDecimal.ZERO;
            BigDecimal loggedH = analytics.secondsToHours(secondsPerDay.getOrDefault(current, 0L));
            List<DayIssue> byIssue = byIssuePerDay.getOrDefault(current, List.of());

            days.add(new CalendarDay(current, dayType, loggedH, normH, absenceType, byIssue));
            current = current.plusDays(1);
        }

        return days;
    }

    /**
     * Team queue — nearest board stories that still have unassigned subtasks of the member's phase.
     * For every membership we look at unassigned subtasks in that team, keep only those whose phase
     * (workflowRole, falling back to WorkflowConfigService.getSubtaskRole) matches this membership's
     * role, group them by parent story, drop done parents, order parents like the board
     * (manualOrder asc nulls last → autoScore desc nulls last) and cap the merged result at 10.
     */
    private List<QueueStory> buildTeamQueue(List<TeamMemberEntity> taskMembers, Map<String, JiraIssueEntity> cache) {
        Comparator<JiraIssueEntity> boardOrder = Comparator
                .comparing(JiraIssueEntity::getManualOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(JiraIssueEntity::getAutoScore, Comparator.nullsLast(Comparator.reverseOrder()));

        List<QueueStory> merged = new ArrayList<>();

        for (TeamMemberEntity member : taskMembers) {
            TeamEntity team = member.getTeam();
            String myRole = member.getRole();

            List<JiraIssueEntity> unassigned = issueRepository.findUnassignedSubtasksByTeam(team.getId());

            // Keep only subtasks whose phase matches this membership's role, grouped by parent story.
            Map<String, List<JiraIssueEntity>> byParent = new LinkedHashMap<>();
            for (JiraIssueEntity sub : unassigned) {
                if (sub.getParentKey() == null) continue;
                if (sub.getDoneAt() != null) continue;
                String phase = sub.getWorkflowRole() != null
                        ? sub.getWorkflowRole()
                        : workflowConfigService.getSubtaskRole(sub.getIssueType());
                if (phase == null || !phase.equals(myRole)) continue;
                byParent.computeIfAbsent(sub.getParentKey(), k -> new ArrayList<>()).add(sub);
            }

            if (byParent.isEmpty()) continue;

            // Resolve parents into cache.
            List<String> missing = byParent.keySet().stream()
                    .filter(k -> !cache.containsKey(k))
                    .toList();
            if (!missing.isEmpty()) {
                for (JiraIssueEntity parent : issueRepository.findByIssueKeyIn(missing)) {
                    cache.put(parent.getIssueKey(), parent);
                }
            }

            // Collect eligible (non-done, resolved) parents, then order like the board.
            List<JiraIssueEntity> parents = new ArrayList<>();
            for (String parentKey : byParent.keySet()) {
                JiraIssueEntity parent = cache.get(parentKey);
                if (parent == null) continue;
                if (workflowConfigService.isDone(parent.getStatus(), parent.getIssueType())) continue;
                parents.add(parent);
            }
            parents.sort(boardOrder);

            for (JiraIssueEntity story : parents) {
                List<JiraIssueEntity> subs = byParent.get(story.getIssueKey());
                long estimateSec = subs.stream()
                        .mapToLong(s -> s.getOriginalEstimateSeconds() != null ? s.getOriginalEstimateSeconds() : 0L)
                        .sum();
                String[] epicInfo = analytics.resolveEpicInfo(subs.get(0), cache);

                merged.add(new QueueStory(
                        story.getIssueKey(),
                        story.getSummary(),
                        story.getIssueType(),
                        story.getStatus(),
                        team.getId(),
                        team.getName(),
                        team.getColor(),
                        epicInfo[0],
                        epicInfo[1],
                        subs.size(),
                        analytics.secondsToHours(estimateSec),
                        jiraConfigResolver.getBaseUrl() + "/browse/" + story.getIssueKey()
                ));
            }
        }

        return merged.stream().limit(10).toList();
    }

    private MyTask buildMyTask(JiraIssueEntity subtask, TeamMemberEntity member, Map<String, JiraIssueEntity> cache) {
        JiraIssueEntity parent = subtask.getParentKey() != null
                ? cache.computeIfAbsent(subtask.getParentKey(), key -> issueRepository.findByIssueKey(key).orElse(null))
                : null;
        String parentSummary = parent != null ? parent.getSummary() : null;

        String[] epicInfo = analytics.resolveEpicInfo(subtask, cache);

        TeamEntity team = member.getTeam();

        return new MyTask(
                subtask.getIssueKey(),
                subtask.getSummary(),
                subtask.getIssueType(),
                subtask.getStatus(),
                subtask.getParentKey(),
                parentSummary,
                epicInfo[0],
                epicInfo[1],
                team.getId(),
                team.getName(),
                team.getColor(),
                analytics.secondsToHours(subtask.getOriginalEstimateSeconds()),
                analytics.secondsToHours(subtask.getTimeSpentSeconds()),
                analytics.secondsToHours(subtask.getRemainingEstimateSeconds()),
                jiraConfigResolver.getBaseUrl() + "/browse/" + subtask.getIssueKey()
        );
    }
}

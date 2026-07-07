package com.leadboard.team.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Personal work desk for a Jira account — F88 "My Work".
 * Aggregates membership, active/upcoming tasks, team queue, worklog calendar
 * and analytics across every active team membership of the account.
 */
public record MyWorkResponse(
        boolean hasMembership,
        MyMemberInfo member,                     // null если !hasMembership
        List<AbsenceDto> upcomingAbsences,
        List<MyTask> activeTasks,
        List<MyTask> upcomingAssigned,
        List<QueueStory> teamQueue,              // Task 3, до тех пор List.of()
        List<CalendarDay> worklogCalendar,       // Task 4, до тех пор List.of()
        MyAnalytics analytics                    // Task 5, до тех пор null
) {
    public record MyMemberInfo(String displayName, String avatarUrl, String role, String grade,
                               BigDecimal hoursPerDay, List<TeamRef> teams) {}

    public record TeamRef(Long teamId, String teamName, String teamColor) {}

    public record MyTask(String key, String summary, String issueType, String status,
                         String parentKey, String parentSummary, String epicKey, String epicSummary,
                         Long teamId, String teamName, String teamColor,
                         BigDecimal estimateH, BigDecimal spentH, String jiraUrl) {}

    public record QueueStory(String key, String summary, String issueType, String status,
                             Long teamId, String teamName, String teamColor,
                             String epicKey, String epicSummary,
                             int myPhaseSubtasks, BigDecimal myPhaseEstimateH, String jiraUrl) {}

    public record CalendarDay(LocalDate date, String dayType,        // WORKDAY|WEEKEND|HOLIDAY
                              BigDecimal loggedH, BigDecimal normH,
                              String absenceType, List<DayIssue> byIssue) {}   // absenceType null если нет

    public record DayIssue(String issueKey, BigDecimal hours) {}

    public record MyAnalytics(MemberProfileResponse.MemberSummary summary,
                              List<MemberProfileResponse.WeeklyTrend> weeklyTrend,
                              List<CompletedTaskWithTeam> completedTasks,
                              List<DsrBreakdown> dsrByParentType,
                              List<DsrBreakdown> dsrByEpic) {}

    public record CompletedTaskWithTeam(String key, String summary, String epicKey, String epicSummary,
                                        Long teamId, String teamName, String teamColor,
                                        BigDecimal estimateH, BigDecimal spentH, BigDecimal dsr,
                                        LocalDate doneDate, String jiraUrl) {}

    public record DsrBreakdown(String key, String label, int taskCount,
                               BigDecimal estimateH, BigDecimal spentH, BigDecimal dsr) {}
}

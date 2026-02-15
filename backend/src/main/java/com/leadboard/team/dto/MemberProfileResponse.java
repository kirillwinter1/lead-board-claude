package com.leadboard.team.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MemberProfileResponse(
    MemberInfo member,
    List<CompletedTask> completedTasks,
    List<ActiveTask> activeTasks,
    List<ActiveTask> upcomingTasks,
    List<WeeklyTrend> weeklyTrend,
    MemberSummary summary
) {
    public record MemberInfo(
        Long id,
        String displayName,
        String role,
        String grade,
        BigDecimal hoursPerDay,
        String teamName,
        Long teamId,
        String avatarUrl
    ) {}

    public record CompletedTask(
        String key,
        String summary,
        String epicKey,
        String epicSummary,
        BigDecimal estimateH,
        BigDecimal spentH,
        BigDecimal dsr,
        LocalDate doneDate
    ) {}

    public record ActiveTask(
        String key,
        String summary,
        String epicKey,
        String epicSummary,
        BigDecimal estimateH,
        BigDecimal spentH,
        String status
    ) {}

    public record WeeklyTrend(
        String week,
        LocalDate weekStart,
        BigDecimal dsr,
        int tasksCompleted,
        BigDecimal hoursLogged
    ) {}

    public record MemberSummary(
        int completedCount,
        BigDecimal avgDsr,
        BigDecimal avgCycleTimeDays,
        BigDecimal utilization,
        BigDecimal totalSpentH,
        BigDecimal totalEstimateH
    ) {}
}

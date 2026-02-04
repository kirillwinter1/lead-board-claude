package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.metrics.dto.VelocityResponse;
import com.leadboard.metrics.dto.VelocityResponse.WeeklyVelocity;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VelocityService {

    private final TeamMemberRepository teamMemberRepository;
    private final WorkCalendarService calendarService;
    private final JdbcTemplate jdbcTemplate;

    public VelocityService(TeamMemberRepository teamMemberRepository,
                           WorkCalendarService calendarService,
                           JdbcTemplate jdbcTemplate) {
        this.teamMemberRepository = teamMemberRepository;
        this.calendarService = calendarService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Calculate team velocity (logged hours vs capacity) for a period.
     */
    public VelocityResponse calculateVelocity(Long teamId, LocalDate from, LocalDate to) {
        // Get active team members
        List<TeamMemberEntity> members = teamMemberRepository.findByTeamIdAndActiveTrue(teamId);

        // Calculate total hours per day capacity for the team
        BigDecimal dailyCapacity = members.stream()
                .map(TeamMemberEntity::getHoursPerDay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get logged hours by week
        Map<LocalDate, BigDecimal> loggedByWeek = getLoggedHoursByWeek(teamId, from, to);

        // Build weekly velocity data
        List<WeeklyVelocity> byWeek = new ArrayList<>();
        BigDecimal totalCapacity = BigDecimal.ZERO;
        BigDecimal totalLogged = BigDecimal.ZERO;

        LocalDate weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!weekStart.isAfter(to)) {
            LocalDate weekEnd = weekStart.plusDays(6);

            // Count working days in this week (within the selected period)
            LocalDate effectiveStart = weekStart.isBefore(from) ? from : weekStart;
            LocalDate effectiveEnd = weekEnd.isAfter(to) ? to : weekEnd;

            int workingDays = calendarService.countWorkdays(effectiveStart, effectiveEnd);
            BigDecimal weekCapacity = dailyCapacity.multiply(BigDecimal.valueOf(workingDays));

            BigDecimal weekLogged = loggedByWeek.getOrDefault(weekStart, BigDecimal.ZERO);

            BigDecimal utilization = weekCapacity.compareTo(BigDecimal.ZERO) > 0
                    ? weekLogged.divide(weekCapacity, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            byWeek.add(new WeeklyVelocity(
                    weekStart,
                    weekCapacity.setScale(1, RoundingMode.HALF_UP),
                    weekLogged.setScale(1, RoundingMode.HALF_UP),
                    utilization
            ));

            totalCapacity = totalCapacity.add(weekCapacity);
            totalLogged = totalLogged.add(weekLogged);

            weekStart = weekStart.plusWeeks(1);
        }

        BigDecimal totalUtilization = totalCapacity.compareTo(BigDecimal.ZERO) > 0
                ? totalLogged.divide(totalCapacity, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new VelocityResponse(
                teamId,
                from,
                to,
                totalCapacity.setScale(1, RoundingMode.HALF_UP),
                totalLogged.setScale(1, RoundingMode.HALF_UP),
                totalUtilization,
                byWeek
        );
    }

    /**
     * Get logged hours (time_spent) grouped by week for a team.
     */
    private Map<LocalDate, BigDecimal> getLoggedHoursByWeek(Long teamId, LocalDate from, LocalDate to) {
        String sql = """
            SELECT
                DATE_TRUNC('week', COALESCE(done_at, updated_at))::date as week_start,
                COALESCE(SUM(time_spent_seconds), 0) / 3600.0 as hours
            FROM jira_issues
            WHERE team_id = ?
              AND (
                (done_at BETWEEN ? AND ?)
                OR (done_at IS NULL AND updated_at BETWEEN ? AND ? AND time_spent_seconds > 0)
              )
            GROUP BY DATE_TRUNC('week', COALESCE(done_at, updated_at))
            ORDER BY week_start
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql, teamId, from, to.plusDays(1), from, to.plusDays(1));

        return rows.stream()
                .filter(row -> row.get("week_start") != null)
                .collect(Collectors.toMap(
                        row -> ((java.sql.Date) row.get("week_start")).toLocalDate(),
                        row -> new BigDecimal(row.get("hours").toString()),
                        (a, b) -> a.add(b)
                ));
    }
}

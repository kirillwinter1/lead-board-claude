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
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
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
     * Get logged hours distributed proportionally across weeks for completed issues.
     * Each issue's time_spent is spread from started_at (or done_at) to done_at.
     */
    private Map<LocalDate, BigDecimal> getLoggedHoursByWeek(Long teamId, LocalDate from, LocalDate to) {
        String sql = """
            SELECT
                COALESCE(time_spent_seconds, 0) as time_spent,
                started_at::date as started,
                done_at::date as done
            FROM jira_issues
            WHERE team_id = ?
              AND done_at IS NOT NULL
              AND done_at BETWEEN ? AND ?
              AND COALESCE(time_spent_seconds, 0) > 0
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql, teamId, from, to.plusDays(1));

        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            long timeSpentSeconds = ((Number) row.get("time_spent")).longValue();
            BigDecimal hours = BigDecimal.valueOf(timeSpentSeconds).divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);

            LocalDate doneDate = row.get("done") != null ? ((java.sql.Date) row.get("done")).toLocalDate() : null;
            LocalDate startedDate = row.get("started") != null ? ((java.sql.Date) row.get("started")).toLocalDate() : null;

            if (doneDate == null) continue;

            // If no started_at, attribute all to done_at week (fallback)
            if (startedDate == null || !startedDate.isBefore(doneDate)) {
                LocalDate weekStart = doneDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                result.merge(weekStart, hours, BigDecimal::add);
                continue;
            }

            // Distribute proportionally across weeks from startedDate to doneDate
            long totalDays = ChronoUnit.DAYS.between(startedDate, doneDate);
            if (totalDays <= 0) totalDays = 1;

            LocalDate weekStart = startedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            while (!weekStart.isAfter(doneDate)) {
                LocalDate weekEnd = weekStart.plusDays(6);
                LocalDate overlapStart = startedDate.isAfter(weekStart) ? startedDate : weekStart;
                LocalDate overlapEnd = doneDate.isBefore(weekEnd) ? doneDate : weekEnd;

                long overlapDays = ChronoUnit.DAYS.between(overlapStart, overlapEnd);
                if (overlapDays > 0) {
                    BigDecimal fraction = BigDecimal.valueOf(overlapDays).divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);
                    BigDecimal weekHours = hours.multiply(fraction).setScale(4, RoundingMode.HALF_UP);
                    result.merge(weekStart, weekHours, BigDecimal::add);
                }

                weekStart = weekStart.plusWeeks(1);
            }
        }

        return result;
    }
}

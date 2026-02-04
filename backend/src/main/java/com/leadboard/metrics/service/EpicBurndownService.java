package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.metrics.dto.EpicBurndownResponse;
import com.leadboard.metrics.dto.EpicBurndownResponse.BurndownPoint;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EpicBurndownService {

    private final JiraIssueRepository issueRepository;
    private final WorkCalendarService calendarService;
    private final JdbcTemplate jdbcTemplate;

    public EpicBurndownService(JiraIssueRepository issueRepository,
                                WorkCalendarService calendarService,
                                JdbcTemplate jdbcTemplate) {
        this.issueRepository = issueRepository;
        this.calendarService = calendarService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Calculate burndown chart data for an epic.
     */
    public EpicBurndownResponse calculateBurndown(String epicKey) {
        // Get epic
        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicKey));

        // Get all subtasks for this epic (via stories)
        List<JiraIssueEntity> subtasks = getEpicSubtasks(epicKey);

        if (subtasks.isEmpty()) {
            return new EpicBurndownResponse(
                    epicKey,
                    epic.getSummary(),
                    null,
                    null,
                    0,
                    List.of(),
                    List.of()
            );
        }

        // Calculate total estimate
        int totalEstimateSeconds = subtasks.stream()
                .mapToInt(s -> s.getOriginalEstimateSeconds() != null ? s.getOriginalEstimateSeconds().intValue() : 0)
                .sum();
        int totalEstimateHours = totalEstimateSeconds / 3600;

        // Find date range
        LocalDate startDate = subtasks.stream()
                .filter(s -> s.getStartedAt() != null)
                .map(s -> s.getStartedAt().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(epic.getJiraCreatedAt() != null ? epic.getJiraCreatedAt().toLocalDate() : LocalDate.now());

        LocalDate endDate = epic.getDoneAt() != null
                ? epic.getDoneAt().toLocalDate()
                : LocalDate.now();

        // If epic is not done, extend to today
        if (epic.getDoneAt() == null && endDate.isBefore(LocalDate.now())) {
            endDate = LocalDate.now();
        }

        // Build ideal burndown line (linear from total to 0)
        List<BurndownPoint> idealLine = buildIdealLine(startDate, endDate, totalEstimateHours);

        // Build actual burndown line based on time spent
        List<BurndownPoint> actualLine = buildActualLine(epicKey, startDate, endDate, totalEstimateHours);

        return new EpicBurndownResponse(
                epicKey,
                epic.getSummary(),
                startDate,
                endDate,
                totalEstimateHours,
                idealLine,
                actualLine
        );
    }

    /**
     * Get list of epics for a team (for selector).
     */
    public List<EpicInfo> getEpicsForTeam(Long teamId) {
        String sql = """
            SELECT issue_key, summary, status, done_at
            FROM jira_issues
            WHERE team_id = ?
              AND issue_type = 'Epic'
            ORDER BY
              CASE WHEN done_at IS NULL THEN 0 ELSE 1 END,
              COALESCE(done_at, jira_created_at) DESC
            LIMIT 50
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new EpicInfo(
                rs.getString("issue_key"),
                rs.getString("summary"),
                rs.getString("status"),
                rs.getDate("done_at") != null
        ), teamId);
    }

    /**
     * Get all subtasks under an epic (via stories).
     */
    private List<JiraIssueEntity> getEpicSubtasks(String epicKey) {
        // Get stories for this epic
        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epicKey);

        // Get subtasks for all stories
        List<String> storyKeys = stories.stream()
                .map(JiraIssueEntity::getIssueKey)
                .collect(Collectors.toList());

        if (storyKeys.isEmpty()) {
            return List.of();
        }

        return issueRepository.findByParentKeyIn(storyKeys);
    }

    /**
     * Build ideal burndown line (linear).
     */
    private List<BurndownPoint> buildIdealLine(LocalDate start, LocalDate end, int totalHours) {
        List<BurndownPoint> points = new ArrayList<>();

        int workingDays = calendarService.countWorkdays(start, end);
        if (workingDays <= 0) {
            points.add(new BurndownPoint(start, totalHours));
            points.add(new BurndownPoint(end, 0));
            return points;
        }

        double hoursPerDay = (double) totalHours / workingDays;

        LocalDate current = start;
        int remaining = totalHours;
        int workingDaysSoFar = 0;

        while (!current.isAfter(end)) {
            if (calendarService.isWorkday(current)) {
                workingDaysSoFar++;
                remaining = totalHours - (int) (hoursPerDay * workingDaysSoFar);
                if (remaining < 0) remaining = 0;
            }
            points.add(new BurndownPoint(current, remaining));
            current = current.plusDays(1);
        }

        return points;
    }

    /**
     * Build actual burndown line based on time spent.
     */
    private List<BurndownPoint> buildActualLine(String epicKey, LocalDate start, LocalDate end, int totalHours) {
        // Get time spent by day
        String sql = """
            SELECT
                DATE(COALESCE(st.done_at, st.updated_at)) as work_date,
                SUM(st.time_spent_seconds) / 3600 as hours_spent
            FROM jira_issues st
            JOIN jira_issues s ON st.parent_key = s.issue_key
            WHERE s.parent_key = ?
              AND st.time_spent_seconds > 0
            GROUP BY DATE(COALESCE(st.done_at, st.updated_at))
            ORDER BY work_date
            """;

        Map<LocalDate, Integer> spentByDay = jdbcTemplate.query(sql, (rs, rowNum) -> {
            java.sql.Date date = rs.getDate("work_date");
            int hours = rs.getInt("hours_spent");
            return Map.entry(date.toLocalDate(), hours);
        }, epicKey).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));

        // Build actual line
        List<BurndownPoint> points = new ArrayList<>();
        LocalDate current = start;
        int totalSpent = 0;

        while (!current.isAfter(end)) {
            totalSpent += spentByDay.getOrDefault(current, 0);
            int remaining = totalHours - totalSpent;
            if (remaining < 0) remaining = 0;
            points.add(new BurndownPoint(current, remaining));
            current = current.plusDays(1);
        }

        return points;
    }

    public record EpicInfo(String key, String summary, String status, boolean completed) {}
}

package com.leadboard.metrics.service;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.dto.EpicBurndownResponse;
import com.leadboard.metrics.dto.EpicBurndownResponse.BurndownPoint;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class EpicBurndownService {

    private final JiraIssueRepository issueRepository;
    private final WorkCalendarService calendarService;
    private final WorkflowConfigService workflowConfigService;
    private final JdbcTemplate jdbcTemplate;

    public EpicBurndownService(JiraIssueRepository issueRepository,
                                WorkCalendarService calendarService,
                                WorkflowConfigService workflowConfigService,
                                JdbcTemplate jdbcTemplate) {
        this.issueRepository = issueRepository;
        this.calendarService = calendarService;
        this.workflowConfigService = workflowConfigService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Calculate burndown chart data for an epic.
     * Uses story-count based burndown: tracks remaining stories over time.
     */
    public EpicBurndownResponse calculateBurndown(String epicKey) {
        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicKey));

        // Get all stories/bugs for this epic (direct children)
        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epicKey).stream()
                .filter(s -> workflowConfigService.isStoryOrBug(s.getIssueType()))
                .toList();

        if (stories.isEmpty()) {
            return new EpicBurndownResponse(
                    epicKey, epic.getSummary(), null, null, 0, List.of(), List.of());
        }

        int totalStories = stories.size();

        // Find date range
        LocalDate startDate = stories.stream()
                .map(s -> {
                    if (s.getStartedAt() != null) return s.getStartedAt().toLocalDate();
                    if (s.getJiraCreatedAt() != null) return s.getJiraCreatedAt().toLocalDate();
                    return LocalDate.now();
                })
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate endDate = epic.getDoneAt() != null
                ? epic.getDoneAt().toLocalDate()
                : LocalDate.now();

        if (endDate.isBefore(startDate)) {
            endDate = startDate;
        }

        List<BurndownPoint> idealLine = buildIdealLine(startDate, endDate, totalStories);
        List<BurndownPoint> actualLine = buildActualLine(stories, startDate, endDate, totalStories);

        return new EpicBurndownResponse(
                epicKey, epic.getSummary(), startDate, endDate, totalStories, idealLine, actualLine);
    }

    /**
     * Get list of epics for a team (in-progress and closed).
     */
    public List<EpicInfo> getEpicsForTeam(Long teamId) {
        String sql = """
            SELECT issue_key, summary, status, done_at, started_at
            FROM jira_issues
            WHERE team_id = ?
              AND board_category = 'EPIC'
              AND started_at IS NOT NULL
            ORDER BY
              CASE WHEN done_at IS NULL THEN 0 ELSE 1 END,
              COALESCE(done_at, started_at) DESC
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
     * Build ideal burndown line (linear descent over working days).
     */
    private List<BurndownPoint> buildIdealLine(LocalDate start, LocalDate end, int totalItems) {
        List<BurndownPoint> points = new ArrayList<>();

        int workingDays = calendarService.countWorkdays(start, end);
        if (workingDays <= 0) {
            points.add(new BurndownPoint(start, totalItems));
            points.add(new BurndownPoint(end, 0));
            return points;
        }

        double itemsPerDay = (double) totalItems / workingDays;
        LocalDate current = start;
        int workingDaysSoFar = 0;

        while (!current.isAfter(end)) {
            if (calendarService.isWorkday(current)) {
                workingDaysSoFar++;
            }
            int remaining = totalItems - (int) (itemsPerDay * workingDaysSoFar);
            if (remaining < 0) remaining = 0;
            points.add(new BurndownPoint(current, remaining));
            current = current.plusDays(1);
        }

        return points;
    }

    /**
     * Build actual burndown line based on story completion dates.
     */
    private List<BurndownPoint> buildActualLine(List<JiraIssueEntity> stories,
                                                 LocalDate start, LocalDate end, int total) {
        List<LocalDate> doneDates = stories.stream()
                .filter(s -> s.getDoneAt() != null)
                .map(s -> s.getDoneAt().toLocalDate())
                .sorted()
                .toList();

        List<BurndownPoint> points = new ArrayList<>();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            LocalDate date = current;
            long doneByDate = doneDates.stream().filter(d -> !d.isAfter(date)).count();
            int remaining = total - (int) doneByDate;
            points.add(new BurndownPoint(current, remaining));
            current = current.plusDays(1);
        }

        return points;
    }

    public record EpicInfo(String key, String summary, String status, boolean completed) {}
}

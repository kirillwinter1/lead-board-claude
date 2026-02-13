package com.leadboard.planning;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.RoleBreakdown;
import com.leadboard.planning.dto.RoleLoadResponse;
import com.leadboard.planning.dto.StoryForecastResponse;
import com.leadboard.planning.dto.StoryInfo;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.WipHistoryResponse;
import com.leadboard.planning.dto.WipHistoryResponse.WipDataPoint;
import com.leadboard.planning.dto.WipHistoryResponse.WipRoleData;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API для прогнозирования.
 */
@RestController
@RequestMapping("/api/planning")
public class ForecastController {

    private final ForecastService forecastService;
    private final StoryForecastService storyForecastService;
    private final UnifiedPlanningService unifiedPlanningService;
    private final AutoScoreService autoScoreService;
    private final WipSnapshotService wipSnapshotService;
    private final RoleLoadService roleLoadService;
    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;

    public ForecastController(
            ForecastService forecastService,
            StoryForecastService storyForecastService,
            UnifiedPlanningService unifiedPlanningService,
            AutoScoreService autoScoreService,
            WipSnapshotService wipSnapshotService,
            RoleLoadService roleLoadService,
            JiraIssueRepository issueRepository,
            WorkflowConfigService workflowConfigService
    ) {
        this.forecastService = forecastService;
        this.storyForecastService = storyForecastService;
        this.unifiedPlanningService = unifiedPlanningService;
        this.autoScoreService = autoScoreService;
        this.wipSnapshotService = wipSnapshotService;
        this.roleLoadService = roleLoadService;
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
    }

    @GetMapping("/forecast")
    public ResponseEntity<ForecastResponse> getForecast(
            @RequestParam Long teamId,
            @RequestParam(required = false) List<String> statuses) {
        ForecastResponse forecast = forecastService.calculateForecast(teamId, statuses);
        return ResponseEntity.ok(forecast);
    }

    @GetMapping("/unified")
    public ResponseEntity<UnifiedPlanningResult> getUnifiedPlan(@RequestParam Long teamId) {
        UnifiedPlanningResult result = unifiedPlanningService.calculatePlan(teamId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate(
            @RequestParam(required = false) Long teamId) {
        int epicsUpdated;
        if (teamId != null) {
            epicsUpdated = autoScoreService.recalculateForTeam(teamId);
        } else {
            epicsUpdated = autoScoreService.recalculateAll();
        }
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "epicsUpdated", epicsUpdated
        ));
    }

    @GetMapping("/wip-history")
    public ResponseEntity<WipHistoryResponse> getWipHistory(
            @RequestParam Long teamId,
            @RequestParam(defaultValue = "30") int days) {

        List<WipSnapshotEntity> snapshots = wipSnapshotService.getRecentHistory(teamId, days);

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);

        List<WipDataPoint> dataPoints = snapshots.stream()
                .map(s -> {
                    Map<String, WipRoleData> roleData = new LinkedHashMap<>();
                    if (s.getRoleWipData() != null) {
                        s.getRoleWipData().forEach((role, entry) ->
                            roleData.put(role, new WipRoleData(entry.limit(), entry.current()))
                        );
                    }
                    return new WipDataPoint(
                            s.getSnapshotDate(),
                            s.getTeamWipLimit(),
                            s.getTeamWipCurrent(),
                            roleData,
                            s.getEpicsInQueue(),
                            s.getTotalEpics()
                    );
                })
                .toList();

        return ResponseEntity.ok(new WipHistoryResponse(teamId, from, to, dataPoints));
    }

    @PostMapping("/wip-snapshot")
    public ResponseEntity<Map<String, Object>> createWipSnapshot(@RequestParam Long teamId) {
        WipSnapshotEntity snapshot = wipSnapshotService.createSnapshot(teamId);
        return ResponseEntity.ok(Map.of(
                "status", "created",
                "date", snapshot.getSnapshotDate().toString(),
                "teamWip", snapshot.getTeamWipCurrent() + "/" + snapshot.getTeamWipLimit()
        ));
    }

    @GetMapping("/role-load")
    public ResponseEntity<RoleLoadResponse> getRoleLoad(@RequestParam Long teamId) {
        RoleLoadResponse roleLoad = roleLoadService.calculateRoleLoad(teamId);
        return ResponseEntity.ok(roleLoad);
    }

    @GetMapping("/epics/{epicKey}/story-forecast")
    public ResponseEntity<StoryForecastResponse> getStoryForecast(
            @PathVariable String epicKey,
            @RequestParam Long teamId) {

        StoryForecastService.StoryForecast forecast = storyForecastService.calculateStoryForecast(epicKey, teamId);

        List<StoryForecastResponse.StoryScheduleDto> storyDtos = forecast.stories().stream()
                .map(schedule -> {
                    JiraIssueEntity story = issueRepository.findByIssueKey(schedule.storyKey()).orElse(null);

                    Long totalEstimate = null;
                    Long totalSpent = null;

                    if (story != null) {
                        List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());
                        if (!subtasks.isEmpty()) {
                            long subtaskEstimate = 0;
                            long subtaskSpent = 0;
                            for (JiraIssueEntity subtask : subtasks) {
                                if (subtask.getOriginalEstimateSeconds() != null) {
                                    subtaskEstimate += subtask.getOriginalEstimateSeconds();
                                }
                                if (subtask.getTimeSpentSeconds() != null) {
                                    subtaskSpent += subtask.getTimeSpentSeconds();
                                }
                            }
                            if (subtaskEstimate > 0) totalEstimate = subtaskEstimate;
                            if (subtaskSpent > 0) totalSpent = subtaskSpent;
                        }
                    }

                    return new StoryForecastResponse.StoryScheduleDto(
                            schedule.storyKey(),
                            story != null ? story.getSummary() : null,
                            schedule.assigneeAccountId(),
                            schedule.assigneeDisplayName(),
                            schedule.startDate(),
                            schedule.endDate(),
                            schedule.workDays(),
                            schedule.isUnassigned(),
                            schedule.isBlocked(),
                            schedule.blockingStories(),
                            story != null ? story.getAutoScore() : null,
                            story != null ? story.getStatus() : null,
                            totalSpent,
                            totalEstimate
                    );
                })
                .collect(Collectors.toList());

        Map<String, StoryForecastResponse.AssigneeUtilizationDto> utilizationDtos = forecast.assigneeUtilization().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new StoryForecastResponse.AssigneeUtilizationDto(
                                e.getValue().displayName(),
                                e.getValue().roleCode(),
                                e.getValue().workDaysAssigned(),
                                e.getValue().effectiveHoursPerDay()
                        )
                ));

        StoryForecastResponse response = new StoryForecastResponse(
                forecast.epicKey(),
                forecast.epicStartDate(),
                storyDtos,
                utilizationDtos
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Получает child issues (stories) для эпика.
     * Агрегирует время из подзадач. Включает breakdown по ролям динамически.
     */
    @GetMapping("/epics/{epicKey}/stories")
    public ResponseEntity<List<StoryInfo>> getEpicStories(@PathVariable String epicKey) {
        List<JiraIssueEntity> childIssues = issueRepository.findByParentKey(epicKey);

        List<StoryInfo> stories = childIssues.stream()
                .map(issue -> {
                    List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(issue.getIssueKey());

                    // Dynamic role accumulation
                    Map<String, long[]> roleData = new LinkedHashMap<>(); // [estimate, logged]

                    for (JiraIssueEntity subtask : subtasks) {
                        String role = workflowConfigService.getSubtaskRole(subtask.getIssueType());
                        long est = subtask.getOriginalEstimateSeconds() != null ? subtask.getOriginalEstimateSeconds() : 0;
                        long spent = subtask.getTimeSpentSeconds() != null ? subtask.getTimeSpentSeconds() : 0;

                        roleData.computeIfAbsent(role, k -> new long[2]);
                        roleData.get(role)[0] += est;
                        roleData.get(role)[1] += spent;
                    }

                    long totalEstimateVal = roleData.values().stream().mapToLong(d -> d[0]).sum();
                    long totalSpentVal = roleData.values().stream().mapToLong(d -> d[1]).sum();
                    Long totalEstimate = totalEstimateVal > 0 ? totalEstimateVal : null;
                    Long totalSpent = totalSpentVal;

                    // Build role breakdowns map
                    Map<String, RoleBreakdown> roleBreakdowns = new LinkedHashMap<>();
                    for (Map.Entry<String, long[]> entry : roleData.entrySet()) {
                        long est = entry.getValue()[0];
                        long logged = entry.getValue()[1];
                        if (est > 0 || logged > 0) {
                            roleBreakdowns.put(entry.getKey(), new RoleBreakdown(
                                    est > 0 ? est : null,
                                    logged > 0 ? logged : null
                            ));
                        }
                    }

                    // Calculate expected done date
                    LocalDate expectedDone = null;
                    if (totalEstimate != null && totalEstimate > 0) {
                        long remainingSeconds = totalEstimate - (totalSpent != null ? totalSpent : 0);
                        if (remainingSeconds > 0) {
                            double remainingHours = remainingSeconds / 3600.0;
                            int workDays = (int) Math.ceil(remainingHours / 8.0);
                            expectedDone = LocalDate.now().plusDays(workDays);
                        } else {
                            expectedDone = LocalDate.now();
                        }
                    }

                    return new StoryInfo(
                            issue.getIssueKey(),
                            issue.getSummary(),
                            issue.getStatus(),
                            issue.getIssueType(),
                            null,
                            null,
                            expectedDone,
                            totalEstimate,
                            totalSpent,
                            workflowConfigService.determinePhase(issue.getStatus(), issue.getIssueType()),
                            roleBreakdowns,
                            issue.getAutoScore()
                    );
                })
                .toList();

        return ResponseEntity.ok(stories);
    }
}

package com.leadboard.planning;

import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.RoleBreakdown;
import com.leadboard.planning.dto.RoleLoadResponse;
import com.leadboard.planning.dto.StoryForecastResponse;
import com.leadboard.planning.dto.StoryInfo;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import com.leadboard.planning.dto.WipHistoryResponse;
import com.leadboard.planning.dto.WipHistoryResponse.WipDataPoint;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    public ForecastController(
            ForecastService forecastService,
            StoryForecastService storyForecastService,
            UnifiedPlanningService unifiedPlanningService,
            AutoScoreService autoScoreService,
            WipSnapshotService wipSnapshotService,
            RoleLoadService roleLoadService,
            JiraIssueRepository issueRepository
    ) {
        this.forecastService = forecastService;
        this.storyForecastService = storyForecastService;
        this.unifiedPlanningService = unifiedPlanningService;
        this.autoScoreService = autoScoreService;
        this.wipSnapshotService = wipSnapshotService;
        this.roleLoadService = roleLoadService;
        this.issueRepository = issueRepository;
    }

    /**
     * Получает прогноз для команды (legacy endpoint).
     *
     * @param teamId ID команды
     * @param statuses фильтр по статусам (опционально)
     */
    @GetMapping("/forecast")
    public ResponseEntity<ForecastResponse> getForecast(
            @RequestParam Long teamId,
            @RequestParam(required = false) List<String> statuses) {

        ForecastResponse forecast = forecastService.calculateForecast(teamId, statuses);
        return ResponseEntity.ok(forecast);
    }

    /**
     * Unified planning - единый алгоритм планирования.
     *
     * Планирует все эпики и стори команды с учётом:
     * - Приоритета эпиков (AutoScore)
     * - Pipeline SA→DEV→QA внутри каждой стори
     * - Capacity и доступности членов команды
     * - Dependencies между сторями
     * - Рабочего календаря
     *
     * @param teamId ID команды
     */
    @GetMapping("/unified")
    public ResponseEntity<UnifiedPlanningResult> getUnifiedPlan(@RequestParam Long teamId) {
        UnifiedPlanningResult result = unifiedPlanningService.calculatePlan(teamId);
        return ResponseEntity.ok(result);
    }

    /**
     * Пересчитывает AutoScore и прогноз для команды.
     */
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

    /**
     * Получает историю WIP для построения графика.
     *
     * @param teamId ID команды
     * @param days количество дней истории (по умолчанию 30)
     */
    @GetMapping("/wip-history")
    public ResponseEntity<WipHistoryResponse> getWipHistory(
            @RequestParam Long teamId,
            @RequestParam(defaultValue = "30") int days) {

        List<WipSnapshotEntity> snapshots = wipSnapshotService.getRecentHistory(teamId, days);

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);

        List<WipDataPoint> dataPoints = snapshots.stream()
                .map(s -> new WipDataPoint(
                        s.getSnapshotDate(),
                        s.getTeamWipLimit(),
                        s.getTeamWipCurrent(),
                        s.getSaWipLimit(),
                        s.getSaWipCurrent(),
                        s.getDevWipLimit(),
                        s.getDevWipCurrent(),
                        s.getQaWipLimit(),
                        s.getQaWipCurrent(),
                        s.getEpicsInQueue(),
                        s.getTotalEpics()
                ))
                .toList();

        return ResponseEntity.ok(new WipHistoryResponse(teamId, from, to, dataPoints));
    }

    /**
     * Создаёт снапшот WIP для команды (для ручного запуска).
     */
    @PostMapping("/wip-snapshot")
    public ResponseEntity<Map<String, Object>> createWipSnapshot(@RequestParam Long teamId) {
        WipSnapshotEntity snapshot = wipSnapshotService.createSnapshot(teamId);
        return ResponseEntity.ok(Map.of(
                "status", "created",
                "date", snapshot.getSnapshotDate().toString(),
                "teamWip", snapshot.getTeamWipCurrent() + "/" + snapshot.getTeamWipLimit()
        ));
    }

    /**
     * Получает загрузку команды по ролям (SA/DEV/QA).
     *
     * @param teamId ID команды
     */
    @GetMapping("/role-load")
    public ResponseEntity<RoleLoadResponse> getRoleLoad(@RequestParam Long teamId) {
        RoleLoadResponse roleLoad = roleLoadService.calculateRoleLoad(teamId);
        return ResponseEntity.ok(roleLoad);
    }

    /**
     * Получает story-level forecast для эпика с учетом assignee и capacity.
     *
     * @param epicKey ключ эпика (например, LB-123)
     * @param teamId ID команды
     */
    @GetMapping("/epics/{epicKey}/story-forecast")
    public ResponseEntity<StoryForecastResponse> getStoryForecast(
            @PathVariable String epicKey,
            @RequestParam Long teamId) {

        StoryForecastService.StoryForecast forecast = storyForecastService.calculateStoryForecast(epicKey, teamId);

        // Convert to DTO with subtask aggregation
        List<StoryForecastResponse.StoryScheduleDto> storyDtos = forecast.stories().stream()
                .map(schedule -> {
                    JiraIssueEntity story = issueRepository.findByIssueKey(schedule.storyKey()).orElse(null);

                    // Aggregate time and estimates from subtasks
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

                            if (subtaskEstimate > 0) {
                                totalEstimate = subtaskEstimate;
                            }
                            if (subtaskSpent > 0) {
                                totalSpent = subtaskSpent;
                            }
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
     * Агрегирует время из подзадач если у сторя нет собственного timeSpent.
     * Включает breakdown по ролям (SA/DEV/QA).
     *
     * @param epicKey ключ эпика (например, LB-123)
     */
    @GetMapping("/epics/{epicKey}/stories")
    public ResponseEntity<List<StoryInfo>> getEpicStories(@PathVariable String epicKey) {
        List<JiraIssueEntity> childIssues = issueRepository.findByParentKey(epicKey);

        List<StoryInfo> stories = childIssues.stream()
                .map(issue -> {
                    // Агрегируем время и эстимейты из подзадач
                    List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(issue.getIssueKey());

                    // All estimates and time logging come only from subtasks
                    long saEstimate = 0, saLogged = 0;
                    long devEstimate = 0, devLogged = 0;
                    long qaEstimate = 0, qaLogged = 0;

                    for (JiraIssueEntity subtask : subtasks) {
                        String role = StoryInfo.determineRole(subtask.getIssueType());
                        long est = subtask.getOriginalEstimateSeconds() != null ? subtask.getOriginalEstimateSeconds() : 0;
                        long spent = subtask.getTimeSpentSeconds() != null ? subtask.getTimeSpentSeconds() : 0;

                        switch (role) {
                            case "SA" -> { saEstimate += est; saLogged += spent; }
                            case "QA" -> { qaEstimate += est; qaLogged += spent; }
                            default -> { devEstimate += est; devLogged += spent; }
                        }
                    }

                    Long totalEstimate = saEstimate + devEstimate + qaEstimate;
                    Long totalSpent = saLogged + devLogged + qaLogged;
                    if (totalEstimate == 0) totalEstimate = null;

                    // Build role breakdowns (null if no data)
                    RoleBreakdown saBreakdown = (saEstimate > 0 || saLogged > 0)
                            ? new RoleBreakdown(saEstimate > 0 ? saEstimate : null, saLogged > 0 ? saLogged : null)
                            : null;
                    RoleBreakdown devBreakdown = (devEstimate > 0 || devLogged > 0)
                            ? new RoleBreakdown(devEstimate > 0 ? devEstimate : null, devLogged > 0 ? devLogged : null)
                            : null;
                    RoleBreakdown qaBreakdown = (qaEstimate > 0 || qaLogged > 0)
                            ? new RoleBreakdown(qaEstimate > 0 ? qaEstimate : null, qaLogged > 0 ? qaLogged : null)
                            : null;

                    // Calculate expected done date based on remaining work
                    LocalDate expectedDone = null;
                    if (totalEstimate != null && totalEstimate > 0) {
                        long remainingSeconds = totalEstimate - (totalSpent != null ? totalSpent : 0);
                        if (remainingSeconds > 0) {
                            // Simple calculation: remaining hours / 8 hours per day
                            double remainingHours = remainingSeconds / 3600.0;
                            int workDays = (int) Math.ceil(remainingHours / 8.0);
                            expectedDone = LocalDate.now().plusDays(workDays);
                        } else {
                            // Already completed - use today
                            expectedDone = LocalDate.now();
                        }
                    }

                    return new StoryInfo(
                            issue.getIssueKey(),
                            issue.getSummary(),
                            issue.getStatus(),
                            issue.getIssueType(),
                            null, // assignee - TODO: добавить когда будет синкаться
                            null, // startDate - TODO: добавить когда будет синкаться
                            expectedDone, // расчетная дата завершения
                            totalEstimate,
                            totalSpent,
                            StoryInfo.determinePhase(issue.getStatus(), issue.getIssueType()),
                            saBreakdown,
                            devBreakdown,
                            qaBreakdown,
                            issue.getAutoScore()
                    );
                })
                .toList();

        return ResponseEntity.ok(stories);
    }
}

package com.leadboard.planning;

import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.RoleBreakdown;
import com.leadboard.planning.dto.StoryInfo;
import com.leadboard.planning.dto.WipHistoryResponse;
import com.leadboard.planning.dto.WipHistoryResponse.WipDataPoint;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST API для прогнозирования.
 */
@RestController
@RequestMapping("/api/planning")
public class ForecastController {

    private final ForecastService forecastService;
    private final AutoScoreService autoScoreService;
    private final WipSnapshotService wipSnapshotService;
    private final JiraIssueRepository issueRepository;

    public ForecastController(
            ForecastService forecastService,
            AutoScoreService autoScoreService,
            WipSnapshotService wipSnapshotService,
            JiraIssueRepository issueRepository
    ) {
        this.forecastService = forecastService;
        this.autoScoreService = autoScoreService;
        this.wipSnapshotService = wipSnapshotService;
        this.issueRepository = issueRepository;
    }

    /**
     * Получает прогноз для команды.
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

                    Long totalEstimate = issue.getOriginalEstimateSeconds();
                    Long totalSpent = issue.getTimeSpentSeconds();

                    // Role breakdown accumulators
                    long saEstimate = 0, saLogged = 0;
                    long devEstimate = 0, devLogged = 0;
                    long qaEstimate = 0, qaLogged = 0;

                    if (!subtasks.isEmpty()) {
                        // Группируем подзадачи по ролям и суммируем
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

                        long subtaskEstimate = saEstimate + devEstimate + qaEstimate;
                        long subtaskSpent = saLogged + devLogged + qaLogged;

                        // Используем сумму подзадач если она больше или у родителя нет данных
                        if (subtaskEstimate > 0 && (totalEstimate == null || subtaskEstimate > totalEstimate)) {
                            totalEstimate = subtaskEstimate;
                        }
                        if (subtaskSpent > 0) {
                            totalSpent = (totalSpent != null ? totalSpent : 0) + subtaskSpent;
                        }
                    }

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

                    return new StoryInfo(
                            issue.getIssueKey(),
                            issue.getSummary(),
                            issue.getStatus(),
                            issue.getIssueType(),
                            null, // assignee - TODO: добавить когда будет синкаться
                            null, // startDate - TODO: добавить когда будет синкаться
                            null, // endDate - TODO: вычислять или брать из Jira
                            totalEstimate,
                            totalSpent,
                            StoryInfo.determinePhase(issue.getStatus(), issue.getIssueType()),
                            saBreakdown,
                            devBreakdown,
                            qaBreakdown
                    );
                })
                .toList();

        return ResponseEntity.ok(stories);
    }
}

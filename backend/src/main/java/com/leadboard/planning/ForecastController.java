package com.leadboard.planning;

import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.WipHistoryResponse;
import com.leadboard.planning.dto.WipHistoryResponse.WipDataPoint;
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

    public ForecastController(
            ForecastService forecastService,
            AutoScoreService autoScoreService,
            WipSnapshotService wipSnapshotService
    ) {
        this.forecastService = forecastService;
        this.autoScoreService = autoScoreService;
        this.wipSnapshotService = wipSnapshotService;
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
}

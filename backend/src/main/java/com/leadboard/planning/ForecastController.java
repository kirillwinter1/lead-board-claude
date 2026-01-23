package com.leadboard.planning;

import com.leadboard.planning.dto.ForecastResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public ForecastController(ForecastService forecastService, AutoScoreService autoScoreService) {
        this.forecastService = forecastService;
        this.autoScoreService = autoScoreService;
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
}

package com.leadboard.forecast.controller;

import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import com.leadboard.forecast.service.ForecastSnapshotService;
import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.UnifiedPlanningResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST API for forecast snapshots (historical timeline data).
 */
@RestController
@RequestMapping("/api/forecast-snapshots")
public class ForecastSnapshotController {

    private final ForecastSnapshotService snapshotService;

    public ForecastSnapshotController(ForecastSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /**
     * Get unified planning data from a historical snapshot.
     *
     * @param teamId Team ID
     * @param date   Snapshot date (YYYY-MM-DD)
     */
    @GetMapping("/unified")
    public ResponseEntity<UnifiedPlanningResult> getUnifiedPlanningSnapshot(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return snapshotService.getUnifiedPlanningFromSnapshot(teamId, date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get forecast data from a historical snapshot.
     *
     * @param teamId Team ID
     * @param date   Snapshot date (YYYY-MM-DD)
     */
    @GetMapping("/forecast")
    public ResponseEntity<ForecastResponse> getForecastSnapshot(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return snapshotService.getForecastFromSnapshot(teamId, date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all available snapshot dates for a team.
     *
     * @param teamId Team ID
     */
    @GetMapping("/dates")
    public ResponseEntity<List<LocalDate>> getAvailableDates(@RequestParam Long teamId) {
        List<LocalDate> dates = snapshotService.getAvailableDates(teamId);
        return ResponseEntity.ok(dates);
    }

    /**
     * Create a snapshot manually (for testing/admin purposes).
     *
     * @param teamId Team ID
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createSnapshot(@RequestParam Long teamId) {
        ForecastSnapshotEntity snapshot = snapshotService.createSnapshot(teamId);

        if (snapshot == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "exists",
                    "message", "Snapshot already exists for today"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "created",
                "date", snapshot.getSnapshotDate().toString(),
                "teamId", teamId
        ));
    }

    /**
     * Create a snapshot for a specific date (for backfill/testing).
     *
     * @param teamId Team ID
     * @param date   Target date
     */
    @PostMapping("/create-for-date")
    public ResponseEntity<Map<String, Object>> createSnapshotForDate(
            @RequestParam Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        ForecastSnapshotEntity snapshot = snapshotService.createSnapshotForDate(teamId, date);

        return ResponseEntity.ok(Map.of(
                "status", "created",
                "date", snapshot.getSnapshotDate().toString(),
                "teamId", teamId
        ));
    }

    /**
     * Trigger daily snapshot creation for all teams (admin endpoint).
     */
    @PostMapping("/trigger-daily")
    public ResponseEntity<Map<String, String>> triggerDailySnapshots() {
        snapshotService.createDailySnapshots();
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "message", "Daily snapshot creation triggered"
        ));
    }
}
